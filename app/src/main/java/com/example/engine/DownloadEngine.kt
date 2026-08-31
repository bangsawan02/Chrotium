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
                val raw = stdMatch?.groupValues?.get(1)?.trim('"', '\'')
                if (!raw.isNullOrBlank()) {
                    URLDecoder.decode(raw, "UTF-8")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFilenameFromQueryOrUrl(urlStr: String): String? {
        return try {
            val uri = Uri.parse(urlStr)
            val path = uri.path ?: ""
            // Penanganan khusus GitHub codeload / archive (misal codeload.github.com/owner/repo/zip/refs/heads/main)
            if (path.contains("/zip/") || path.contains("/tar.gz/") || path.contains("/archive/")) {
                val segments = uri.pathSegments
                val repoIndex = segments.indexOfFirst { it.equals("zip", ignoreCase = true) || it.equals("tar.gz", ignoreCase = true) || it.equals("archive", ignoreCase = true) }
                if (repoIndex > 0) {
                    val repoName = segments[repoIndex - 1]
                    val branchOrTag = segments.lastOrNull() ?: "master"
                    val ext = if (path.contains("tar.gz") || path.contains("tar")) "tar.gz" else "zip"
                    return "$repoName-$branchOrTag.$ext"
                }
            }

            // 1. Cek query parameter response-content-disposition (misalnya S3 GitHub CDN)
            val dispositionParam = uri.getQueryParameter("response-content-disposition")
            val fromDisposition = parseContentDispositionFilename(dispositionParam)
            if (!fromDisposition.isNullOrBlank()) return fromDisposition

            // 2. Cek path segmen terakhir
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                URLDecoder.decode(lastSegment, "UTF-8")
            } else {
                if (!lastSegment.isNullOrBlank()) lastSegment else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Memulai pengunduhan file dengan penanganan multi-redirect cerdas (mendukung GitHub Releases, S3 CDN, Google Drive).
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
            val downloadId = System.currentTimeMillis()
            var activeConnection: HttpURLConnection? = null
            var uriString: String? = null
            var finalFile: File? = null
            var safeMimeType = mimeType ?: getMimeTypeFromUrl(url) ?: "application/octet-stream"

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Menghubungkan unduhan...")
                .setContentText("Memeriksa tautan file...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, 0, true)

            notificationManager.notify(downloadId.toInt(), notificationBuilder.build())

            try {
                var currentUrl = url
                var redirectCount = 0
                val initialHost = try { URL(url).host } catch (e: Exception) { "" }

                while (redirectCount < 15) {
                    val targetUrl = URL(currentUrl)
                    val conn = targetUrl.openConnection() as HttpURLConnection
                    activeConnection = conn
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 25000
                    conn.readTimeout = 30000

                    if (!userAgent.isNullOrBlank()) {
                        conn.setRequestProperty("User-Agent", userAgent)
                    } else {
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    }
                    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8")

                    // Jangan kirim Cookie/Referer ke S3 presigned URL (kecuali redirect github codeload)
                    val currentHost = targetUrl.host
                    val isGithubDomain = currentHost.contains("github.com", ignoreCase = true) || currentHost.contains("codeload.github.com", ignoreCase = true)
                    val isS3OrPresignedCdn = (currentHost.contains("githubusercontent.com", ignoreCase = true) && !currentHost.contains("github.com")) ||
                            currentHost.contains("amazonaws.com", ignoreCase = true) ||
                            currentHost.contains("cloudfront.net", ignoreCase = true) ||
                            currentUrl.contains("X-Amz-", ignoreCase = true) ||
                            currentUrl.contains("response-content-disposition", ignoreCase = true)

                    if (!isS3OrPresignedCdn && (currentHost.equals(initialHost, ignoreCase = true) || isGithubDomain)) {
                        val cookies = CookieManager.getInstance().getCookie(currentUrl)
                            .ifBlank { CookieManager.getInstance().getCookie("https://github.com") }
                        if (!cookies.isNullOrBlank()) {
                            conn.setRequestProperty("Cookie", cookies)
                        }
                        conn.setRequestProperty("Referer", "https://github.com/")
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
                        val headerMime = conn.contentType?.substringBefore(";")?.trim() ?: ""
                        if (headerMime.contains("text/html", ignoreCase = true)) {
                            val resolvedTestName = parseContentDispositionFilename(conn.getHeaderField("Content-Disposition"))
                                ?: extractFilenameFromQueryOrUrl(currentUrl) ?: ""
                            if (resolvedTestName.endsWith(".zip", true) || resolvedTestName.endsWith(".apk", true) || resolvedTestName.endsWith(".tar.gz", true) || resolvedTestName.endsWith(".jar", true) || resolvedTestName.endsWith(".gz", true)) {
                                conn.disconnect()
                                throw Exception("Gagal mengunduh: Server mengembalikan halaman HTML (kemungkinan perlu login, rate limit GitHub, atau tautan kedaluwarsa)")
                            }
                        }

                        // Resolusi nama file yang 100% akurat dari berbagai sumber (Content-Disposition, URL query, URL path, fallback)
                        val headerDisposition = conn.getHeaderField("Content-Disposition")
                        if (headerMime.isNotBlank() && headerMime != "application/octet-stream") {
                            safeMimeType = headerMime
                        }

                        val resolvedFileName = parseContentDispositionFilename(headerDisposition)
                            ?: extractFilenameFromQueryOrUrl(currentUrl)
                            ?: parseContentDispositionFilename(contentDisposition)
                            ?: extractFilenameFromQueryOrUrl(url)
                            ?: URLUtil.guessFileName(currentUrl, headerDisposition, safeMimeType)

                        val chosenFile = getUniqueFile(resolvedFileName)
                        finalFile = chosenFile

                        if (chosenFile.name.endsWith(".apk", ignoreCase = true)) {
                            safeMimeType = "application/vnd.android.package-archive"
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Mengunduh: ${chosenFile.name}", Toast.LENGTH_SHORT).show()
                            onDownloadStarted(chosenFile.name)
                        }

                        // Buat OutputStream di MediaStore / Downloads publik dengan nama file final yang benar
                        val (stream, createdUri) = com.example.util.StorageHelper.getOutputStreamForDownload(
                            context,
                            chosenFile.name,
                            safeMimeType
                        )
                        if (stream == null) {
                            throw Exception("Gagal membuat file ${chosenFile.name} di folder Downloads")
                        }
                        uriString = createdUri

                        // Simpan entri awal unduhan ke database Room
                        val downloadItem = DownloadItem(
                            downloadId = downloadId,
                            fileName = chosenFile.name,
                            fileUrl = url,
                            mimeType = safeMimeType,
                            filePath = chosenFile.absolutePath,
                            fileUri = uriString,
                            status = 0, // Sedang berjalan
                            timestamp = System.currentTimeMillis(),
                            totalBytes = conn.contentLengthLong.coerceAtLeast(0L),
                            downloadedBytes = 0
                        )
                        database.downloadDao().insertDownload(downloadItem)

                        notificationBuilder
                            .setContentTitle("Mengunduh ${chosenFile.name}")
                            .setContentText("Mengunduh data...")
                        notificationManager.notify(downloadId.toInt(), notificationBuilder.build())

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

                                scope.launch {
                                    database.downloadDao().updateDownloadProgress(
                                        downloadId = downloadId,
                                        status = 0,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes
                                    )
                                }

                                if (totalBytes > 0) {
                                    notificationBuilder
                                        .setContentTitle("Mengunduh ${chosenFile.name}")
                                        .setContentText("$progressPercent% • ${formatSize(downloaded)} / ${formatSize(totalBytes)}")
                                        .setProgress(100, progressPercent, false)
                                } else {
                                    notificationBuilder
                                        .setContentTitle("Mengunduh ${chosenFile.name}")
                                        .setContentText(formatSize(downloaded))
                                        .setProgress(0, 0, true)
                                }
                                notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
                            }
                        }

                        // Streaming transfer data
                        conn.inputStream.use { input ->
                            stream.use { output ->
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

                        // Finalisasi MediaStore entry
                        com.example.util.StorageHelper.finalizeDownload(context, uriString)

                        val finalDownloadedBytes = totalDownloaded.get()
                        val finalTotal = if (totalBytes > 0) totalBytes else finalDownloadedBytes

                        // Sukses
                        database.downloadDao().updateDownloadProgress(
                            downloadId = downloadId,
                            status = 1,
                            downloadedBytes = finalDownloadedBytes,
                            totalBytes = finalTotal
                        )

                        val finishNotification = NotificationCompat.Builder(context, channelId)
                            .setContentTitle("Unduhan Selesai")
                            .setContentText("${chosenFile.name} (${formatSize(finalDownloadedBytes)})")
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setOngoing(false)
                            .setAutoCancel(true)
                            .setProgress(0, 0, false)

                        try {
                            val pendingUri: Uri = if (!uriString.isNullOrBlank() && uriString!!.startsWith("content://")) {
                                Uri.parse(uriString)
                            } else {
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    chosenFile
                                )
                            }
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(pendingUri, safeMimeType)
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
                        } catch (e: Exception) {}

                        notificationManager.cancel(downloadId.toInt())
                        notificationManager.notify(downloadId.toInt(), finishNotification.build())

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Unduhan selesai: ${chosenFile.name}", Toast.LENGTH_LONG).show()
                        }
                        break
                    } else {
                        conn.disconnect()
                        throw Exception("Server merespons dengan status $responseCode")
                    }
                }
            } catch (e: Exception) {
                activeConnection?.disconnect()

                if (uriString != null && finalFile != null) {
                    try {
                        com.example.util.StorageHelper.deleteDownload(context, uriString, finalFile!!.absolutePath)
                    } catch (cleanupError: Exception) {}
                }

                database.downloadDao().updateDownloadProgress(
                    downloadId = downloadId,
                    status = 2,
                    downloadedBytes = 0,
                    totalBytes = 0
                )

                val displayName = finalFile?.name ?: "File"
                val failNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Gagal")
                    .setContentText("Gagal mengunduh $displayName: ${e.localizedMessage ?: "Kesalahan koneksi"}")
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
     * Menyimpan file dari Data URL / Blob Base64 yang dihasilkan oleh JavaScript (misalnya GitHub Blob download).
     */
    fun saveBlobData(
        base64Data: String,
        fileName: String?,
        mimeType: String?,
        onDownloadStarted: (String) -> Unit = {}
    ) {
        scope.launch {
            val downloadId = System.currentTimeMillis()
            try {
                val pureBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }
                val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                val totalBytes = decodedBytes.size.toLong()

                val rawName = if (!fileName.isNullOrBlank()) {
                    fileName
                } else {
                    "blob_${System.currentTimeMillis()}"
                }
                val finalFile = getUniqueFile(rawName)
                var safeMimeType = mimeType ?: "application/octet-stream"
                if (finalFile.name.endsWith(".apk", ignoreCase = true)) {
                    safeMimeType = "application/vnd.android.package-archive"
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Mengunduh: ${finalFile.name}", Toast.LENGTH_SHORT).show()
                    onDownloadStarted(finalFile.name)
                }

                val (outputStream, uriString) = com.example.util.StorageHelper.getOutputStreamForDownload(
                    context,
                    finalFile.name,
                    safeMimeType
                )
                if (outputStream == null) {
                    throw Exception("Gagal membuat file di folder Downloads")
                }

                outputStream.use { it.write(decodedBytes) }
                com.example.util.StorageHelper.finalizeDownload(context, uriString)

                // Simpan ke database Room
                val downloadItem = DownloadItem(
                    downloadId = downloadId,
                    fileName = finalFile.name,
                    fileUrl = "blob:${finalFile.name}",
                    mimeType = safeMimeType,
                    filePath = finalFile.absolutePath,
                    fileUri = uriString,
                    status = 1, // Selesai
                    timestamp = System.currentTimeMillis(),
                    totalBytes = totalBytes,
                    downloadedBytes = totalBytes
                )
                database.downloadDao().insertDownload(downloadItem)

                // Tampilkan notifikasi unduhan selesai
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val finishNotification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Unduhan Selesai")
                    .setContentText("${finalFile.name} (${formatSize(totalBytes)})")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)

                try {
                    val pendingUri: Uri = if (!uriString.isNullOrBlank() && uriString.startsWith("content://")) {
                        Uri.parse(uriString)
                    } else {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            finalFile
                        )
                    }
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(pendingUri, safeMimeType)
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
                } catch (e: Exception) {}

                notificationManager.notify(downloadId.toInt(), finishNotification.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Unduhan selesai: ${finalFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunduh Blob: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Membuka file unduhan yang telah selesai dengan aplikasi yang sesuai
     */
    fun openDownloadedFile(item: DownloadItem) {
        try {
            val uri: Uri = if (!item.fileUri.isNullOrBlank() && item.fileUri.startsWith("content://")) {
                Uri.parse(item.fileUri)
            } else {
                val file = File(item.filePath)
                if (!file.exists()) {
                    Toast.makeText(context, "File tidak ditemukan di penyimpanan: ${item.fileName}", Toast.LENGTH_SHORT).show()
                    return
                }
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val effectiveMime = if (item.fileName.endsWith(".apk", ignoreCase = true)) {
                "application/vnd.android.package-archive"
            } else {
                item.mimeType
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, effectiveMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini: ${item.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDownload(item: DownloadItem) {
        scope.launch {
            try {
                com.example.util.StorageHelper.deleteDownload(context, item.fileUri, item.filePath)
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
