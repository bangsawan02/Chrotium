package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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

    private fun getUniqueFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var file = File(downloadsDir, fileName)
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

    /**
     * Memulai pengunduhan file tingkat lanjut secara paralel dan multi-thread
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
            val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val finalFile = getUniqueFile(guessedFileName)
            val safeMimeType = mimeType ?: getMimeTypeFromUrl(url) ?: "application/octet-stream"
            val downloadId = System.currentTimeMillis() // ID unduhan kustom unik

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

            try {
                // Mendapatkan informasi file header (ukuran & dukungan range)
                var connection: HttpURLConnection? = null
                var finalUrl = url
                var redirectCount = 0
                var contentLength = 0L
                var acceptRanges: String? = null
                
                while (redirectCount < 10) {
                    connection = URL(finalUrl).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "GET"
                    
                    val cookies = CookieManager.getInstance().getCookie(finalUrl)
                    if (!cookies.isNullOrBlank()) {
                        connection.setRequestProperty("Cookie", cookies)
                    }
                    if (!userAgent.isNullOrBlank()) {
                        connection.setRequestProperty("User-Agent", userAgent)
                    }
                    connection.setRequestProperty("Referer", url)
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                        responseCode == 307 || responseCode == 308) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location != null) {
                            finalUrl = location
                            redirectCount++
                            continue
                        } else {
                            throw Exception("Redirect tanpa location")
                        }
                    } else if (responseCode in 200..299) {
                        contentLength = connection.contentLengthLong
                        acceptRanges = connection.getHeaderField("Accept-Ranges")
                        connection.disconnect()
                        break
                    } else {
                        throw Exception("Server merespons dengan kode: $responseCode")
                    }
                }

                val supportsRange = acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)
                
                val totalBytes = if (contentLength > 0) contentLength else 0L
                // Hanya gunakan unduhan paralel jika server mendukung range request dan ukuran file > 512KB
                val isParallelSupported = supportsRange && totalBytes > 1024 * 512

                val totalDownloaded = AtomicLong(0L)
                var lastUpdateMillis = System.currentTimeMillis()

                fun updateProgress(downloaded: Long) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMillis >= 500 || downloaded == totalBytes) {
                        lastUpdateMillis = now
                        val progressPercent = if (totalBytes > 0) {
                            ((downloaded.toDouble() / totalBytes) * 100).roundToInt()
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

                        // Update notifikasi sistem
                        if (totalBytes > 0) {
                            notificationBuilder
                                .setContentText("$progressPercent% selesai (${formatSize(downloaded)} / ${formatSize(totalBytes)})")
                                .setProgress(100, progressPercent, false)
                        } else {
                            notificationBuilder
                                .setContentText("Mengunduh... (${formatSize(downloaded)})")
                                .setProgress(0, 0, true)
                        }
                        notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
                    }
                }

                if (isParallelSupported) {
                    // Unduhan paralel multi-thread (4 segmen/utas)
                    val numThreads = 4
                    val chunkSize = totalBytes / numThreads

                    // Alokasikan ukuran file tujuan terlebih dahulu agar mulus saat penulisan offset paralel
                    RandomAccessFile(finalFile, "rw").use { raf ->
                        raf.setLength(totalBytes)
                    }

                    coroutineScope {
                        val deferreds = (0 until numThreads).map { i ->
                            val startByte = i * chunkSize
                            val endByte = if (i == numThreads - 1) totalBytes - 1 else (i + 1) * chunkSize - 1

                            async(Dispatchers.IO) {
                                var chunkConn: HttpURLConnection? = null
                                try {
                                    chunkConn = URL(finalUrl).openConnection() as HttpURLConnection
                                    chunkConn.setRequestProperty("Range", "bytes=$startByte-$endByte")
                                    val chunkCookies = CookieManager.getInstance().getCookie(finalUrl)
                                    if (!chunkCookies.isNullOrBlank()) {
                                        chunkConn.setRequestProperty("Cookie", chunkCookies)
                                    }
                                    if (!userAgent.isNullOrBlank()) {
                                        chunkConn.setRequestProperty("User-Agent", userAgent)
                                    }
                                    chunkConn.setRequestProperty("Referer", url)
                                    chunkConn.connectTimeout = 15000
                                    chunkConn.readTimeout = 15000
                                    chunkConn.connect()

                                    val chunkCode = chunkConn.responseCode
                                    if (chunkCode == HttpURLConnection.HTTP_PARTIAL || chunkCode == HttpURLConnection.HTTP_OK) {
                                        RandomAccessFile(finalFile, "rw").use { raf ->
                                            raf.seek(startByte)
                                            val buffer = ByteArray(8192)
                                            val inputStream = chunkConn.inputStream
                                            var bytesRead: Int
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                raf.write(buffer, 0, bytesRead)
                                                val currentTotal = totalDownloaded.addAndGet(bytesRead.toLong())
                                                updateProgress(currentTotal)
                                            }
                                        }
                                    } else {
                                        throw Exception("Merespons dengan kode partial: $chunkCode")
                                    }
                                } finally {
                                    chunkConn?.disconnect()
                                }
                            }
                        }
                        deferreds.awaitAll()
                    }
                } else {
                    // Unduhan single thread sekuensial (untuk server yang tidak mendukung multi-range)
                    val seqConn = URL(finalUrl).openConnection() as HttpURLConnection
                    val cookiesSeq = CookieManager.getInstance().getCookie(finalUrl)
                    if (!cookiesSeq.isNullOrBlank()) {
                        seqConn.setRequestProperty("Cookie", cookiesSeq)
                    }
                    if (!userAgent.isNullOrBlank()) {
                        seqConn.setRequestProperty("User-Agent", userAgent)
                    }
                    seqConn.setRequestProperty("Referer", url)
                    seqConn.connectTimeout = 15000
                    seqConn.readTimeout = 15000
                    seqConn.connect()

                    if (seqConn.responseCode !in 200..299) {
                        throw Exception("Server merespons dengan kode: ${seqConn.responseCode}")
                    }

                    seqConn.inputStream.use { inputStream ->
                        finalFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                val currentTotal = totalDownloaded.addAndGet(bytesRead.toLong())
                                updateProgress(currentTotal)
                            }
                        }
                    }
                    seqConn.disconnect()
                }

                // Pengunduhan Berhasil
                database.downloadDao().updateDownloadProgress(
                    downloadId = downloadId,
                    status = 1, // Selesai
                    downloadedBytes = totalBytes,
                    totalBytes = totalBytes
                )

                // Notifikasi Selesai
                val finishNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Selesai")
                    .setContentText(finalFile.name)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)

                // Klik notifikasi untuk langsung membuka file
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
                    // Abaikan kesalahan penanganan intent
                }

                notificationManager.cancel(downloadId.toInt())
                notificationManager.notify(downloadId.toInt(), finishNotification.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Unduhan selesai: ${finalFile.name}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                // Pengunduhan Gagal
                database.downloadDao().updateDownloadProgress(
                    downloadId = downloadId,
                    status = 2, // Gagal
                    downloadedBytes = 0,
                    totalBytes = 0
                )

                val failNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Gagal")
                    .setContentText("Gagal mengunduh ${finalFile.name}")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)

                notificationManager.cancel(downloadId.toInt())
                notificationManager.notify(downloadId.toInt(), failNotification.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunduh: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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

    /**
     * Memperbarui progress unduhan aktif dari DownloadManager (Hanya untuk backwards-compatibility)
     */
    fun updateProgressForActiveDownloads(activeDownloads: List<DownloadItem>) {
        // Dilewati untuk unduhan kustom kami karena sudah melakukan pembaruan real-time mandiri
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
