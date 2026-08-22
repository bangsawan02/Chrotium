package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.db.AppDatabase
import com.example.data.model.DownloadItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class DownloadEngine(
    private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channelId = "chrotium_downloads"

    val downloads: Flow<List<DownloadItem>> = database.downloadDao().getAllDownloads()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Unduhan Chrotium"
            val descriptionText = "Menampilkan progress pengunduhan file di Chrotium"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getDownloadsDirectory(): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(rawName: String): String {
        var clean = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        clean = clean.replace(Regex("^\\.+"), "")
        if (clean.isBlank()) {
            clean = "download_${System.currentTimeMillis()}"
        }
        return clean
    }

    private fun getUniqueFile(fileName: String): File {
        val downloadsDir = getDownloadsDirectory()
        val safeName = sanitizeFileName(fileName)
        var file = File(downloadsDir, safeName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        while (file.exists()) {
            file = File(downloadsDir, "$nameWithoutExt ($counter)$extSuffix")
            counter++
        }
        return file
    }

    private fun parseContentDispositionFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        return try {
            // RFC 5987 / 6266: filename*=UTF-8''encoded_name.ext
            val rfcMatch = Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""", RegexOption.IGNORE_CASE).find(contentDisposition)
            if (rfcMatch != null) {
                val encoded = rfcMatch.groupValues[1].trim('"', '\'')
                URLDecoder.decode(encoded, "UTF-8")
            } else {
                val stdMatch = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(contentDisposition)
                    ?: Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE).find(contentDisposition)
                stdMatch?.groupValues?.get(1)?.trim('"', '\'')
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Memulai pengunduhan file dengan penanganan multi-redirect cerdas (mendukung GitHub, S3 CDN, Google Drive).
     */
    fun startDownload(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        onDownloadStarted: (String) -> Unit = {}
    ) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("javascript:")) {
            return
        }

        scope.launch {
            val initialFilename = parseContentDispositionFilename(contentDisposition)
                ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
            var finalFile = getUniqueFile(initialFilename)
            var safeMimeType = mimeType ?: getMimeTypeFromUrl(url) ?: "application/octet-stream"
            val downloadId = System.currentTimeMillis()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Memulai unduhan: ${finalFile.name}", Toast.LENGTH_SHORT).show()
                onDownloadStarted(finalFile.name)
            }

            // Simpan catatan awal unduhan ke database lokal
            val downloadItem = DownloadItem(
                downloadId = downloadId,
                fileName = finalFile.name,
                fileUrl = url,
                mimeType = safeMimeType,
                filePath = finalFile.absolutePath,
                status = 0, // Sedang berjalan
                timestamp = System.currentTimeMillis(),
                totalBytes = 0,
                downloadedBytes = 0
            )
            database.downloadDao().insertDownload(downloadItem)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Mengunduh ${finalFile.name}")
                .setContentText("Menghubungkan...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, true)

            notificationManager.notify(downloadId.toInt(), notificationBuilder.build())

            var activeConnection: HttpURLConnection? = null

            try {
                var currentUrl = url
                var redirectCount = 0
                val initialHost = try { URL(url).host } catch (e: Exception) { "" }

                while (redirectCount < 10) {
                    val targetUrl = URL(currentUrl)
                    val conn = targetUrl.openConnection() as HttpURLConnection
                    activeConnection = conn
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 20000
                    conn.readTimeout = 25000

                    if (!userAgent.isNullOrBlank()) {
                        conn.setRequestProperty("User-Agent", userAgent)
                    }
                    conn.setRequestProperty("Accept", "*/*")

                    // Hanya kirim cookie dan referer jika domain sama, hindari error S3 presigned URL di GitHub releases
                    val currentHost = targetUrl.host
                    if (currentHost.equals(initialHost, ignoreCase = true) || !currentHost.contains("githubusercontent.com")) {
                        val cookies = CookieManager.getInstance().getCookie(currentUrl)
                        if (!cookies.isNullOrBlank()) {
                            conn.setRequestProperty("Cookie", cookies)
                        }
                        if (redirectCount == 0) {
                            conn.setRequestProperty("Referer", url)
                        }
                    }

                    conn.connect()
                    val responseCode = conn.responseCode

                    if (responseCode in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            throw Exception("Server melakukan redirect tanpa lokasi tujuan")
                        }
                        currentUrl = URL(targetUrl, location).toString()
                        redirectCount++
                        continue
                    }

                    if (responseCode in 200..299) {
                        // Periksa apakah server memberikan nama file lebih spesifik di Content-Disposition
                        val headerDisposition = conn.getHeaderField("Content-Disposition")
                        val headerMime = conn.contentType?.substringBefore(";")?.trim()
                        if (!headerMime.isNullOrBlank()) {
                            safeMimeType = headerMime
                        }

                        val refinedName = parseContentDispositionFilename(headerDisposition)
                        if (!refinedName.isNullOrBlank() && refinedName != initialFilename) {
                            val newFile = getUniqueFile(refinedName)
                            finalFile = newFile
                            // Perbarui nama di database
                            database.downloadDao().updateDownloadProgress(
                                downloadId = downloadId,
                                status = 0,
                                downloadedBytes = 0,
                                totalBytes = 0
                            )
                        }
                        break
                    } else {
                        conn.disconnect()
                        throw Exception("Server merespons dengan kode: $responseCode")
                    }
                }

                val conn = activeConnection ?: throw Exception("Gagal membuat koneksi unduhan")
                val totalBytes = conn.contentLengthLong.coerceAtLeast(0L)
                val totalDownloaded = AtomicLong(0L)
                var lastUpdateMillis = System.currentTimeMillis()

                fun updateProgress(downloaded: Long) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMillis >= 500 || (totalBytes > 0 && downloaded >= totalBytes)) {
                        lastUpdateMillis = now
                        val progressPercent = if (totalBytes > 0) {
                            ((downloaded.toDouble() / totalBytes) * 100).roundToInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        // Update database
                        scope.launch {
                            database.downloadDao().updateDownloadProgress(
                                downloadId = downloadId,
                                status = 0,
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes
                            )
                        }

                        // Update notifikasi
                        if (totalBytes > 0) {
                            notificationBuilder
                                .setContentTitle("Mengunduh ${finalFile.name}")
                                .setContentText("$progressPercent% • ${formatSize(downloaded)} / ${formatSize(totalBytes)}")
                                .setProgress(100, progressPercent, false)
                        } else {
                            notificationBuilder
                                .setContentTitle("Mengunduh ${finalFile.name}")
                                .setContentText(formatSize(downloaded))
                                .setProgress(0, 0, true)
                        }
                        notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
                    }
                }

                // Streaming unduhan langsung dengan buffer 32KB
                conn.inputStream.use { input ->
                    FileOutputStream(finalFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            val currentTotal = totalDownloaded.addAndGet(bytesRead.toLong())
                            updateProgress(currentTotal)
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                val finalDownloadedBytes = totalDownloaded.get()
                val finalTotal = if (totalBytes > 0) totalBytes else finalDownloadedBytes

                // Pengunduhan Berhasil
                database.downloadDao().updateDownloadProgress(
                    downloadId = downloadId,
                    status = 1, // Selesai
                    downloadedBytes = finalDownloadedBytes,
                    totalBytes = finalTotal
                )

                // Notifikasi Selesai
                val finishNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Selesai")
                    .setContentText("${finalFile.name} (${formatSize(finalDownloadedBytes)})")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)

                // Intent klik untuk membuka file
                try {
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        finalFile
                    )
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, safeMimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        downloadId.toInt(),
                        viewIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    finishNotification.setContentIntent(pendingIntent)
                } catch (e: Exception) {
                    // Abaikan kesalahan pembuatan pending intent
                }

                notificationManager.cancel(downloadId.toInt())
                notificationManager.notify(downloadId.toInt(), finishNotification.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Unduhan selesai: ${finalFile.name}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                activeConnection?.disconnect()

                database.downloadDao().updateDownloadProgress(
                    downloadId = downloadId,
                    status = 2, // Gagal
                    downloadedBytes = 0,
                    totalBytes = 0
                )

                val failNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Gagal")
                    .setContentText("Gagal mengunduh ${finalFile.name}: ${e.localizedMessage ?: "Kesalahan koneksi"}")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)

                notificationManager.cancel(downloadId.toInt())
                notificationManager.notify(downloadId.toInt(), failNotification.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunduh: ${e.localizedMessage ?: "Kesalahan koneksi"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Membuka file unduhan yang telah selesai dengan aplikasi yang sesuai
     */
    fun openDownloadedFile(item: DownloadItem) {
        try {
            val file = File(item.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File tidak ditemukan di penyimpanan: ${item.fileName}", Toast.LENGTH_SHORT).show()
                return
            }

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka tipe file ini", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDownload(item: DownloadItem) {
        scope.launch {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
                database.downloadDao().deleteDownload(item)
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }

    fun clearAll() {
        scope.launch {
            try {
                database.downloadDao().clearAllDownloads()
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }

    fun updateProgressForActiveDownloads(activeDownloads: List<DownloadItem>) {
        // Dilewati untuk unduhan kustom
    }

    private fun getMimeTypeFromUrl(url: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            "application/octet-stream"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
