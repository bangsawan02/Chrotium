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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicLong

class DownloadEngine(
    private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channelId = "chrotium_downloads"
    private val PARALLEL_CHUNKS = 4
    private val MIN_CHUNK_SIZE = 3 * 1024 * 1024L // 3 MB minimum to split chunks

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

            val dispositionParam = uri.getQueryParameter("response-content-disposition")
            val fromDisposition = parseContentDispositionFilename(dispositionParam)
            if (!fromDisposition.isNullOrBlank()) return fromDisposition

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

    private fun getMimeTypeFromUrl(url: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else null
    }

    /**
     * Memulai pengunduhan file dengan penanganan multi-redirect cerdas berbasis Ktor CIO Coroutines Engine.
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
                // Get redirection resolved header info via Ktor CIO
                val reqUserAgent = userAgent ?: "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                val cookieStr = com.example.engine.CookieHelper.getRawCookieHeader(url)
                val headerInfo = KtorNetworkEngine.getHeaderInfo(url, reqUserAgent, cookieStr)
                val totalBytes = headerInfo.contentLength.coerceAtLeast(0L)
                val supportsRanges = headerInfo.supportsRanges
                val resolvedMime = headerInfo.contentType.substringBefore(";").trim()

                if (resolvedMime.isNotBlank() && resolvedMime != "application/octet-stream") {
                    safeMimeType = resolvedMime
                }
                
                val finalContentDisposition = headerInfo.contentDisposition ?: contentDisposition

                val resolvedFileName = parseContentDispositionFilename(finalContentDisposition)
                    ?: extractFilenameFromQueryOrUrl(url)
                    ?: URLUtil.guessFileName(url, finalContentDisposition, safeMimeType)

                val chosenFile = getUniqueFile(resolvedFileName)
                if (chosenFile.name.endsWith(".apk", ignoreCase = true)) {
                    safeMimeType = "application/vnd.android.package-archive"
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Mengunduh: ${chosenFile.name}", Toast.LENGTH_SHORT).show()
                    onDownloadStarted(chosenFile.name)
                }

                // Create a temporary cache file to enable RandomAccess parallel chunks
                val tempFile = File(context.cacheDir, "temp_${downloadId}_${chosenFile.name}")
                if (tempFile.exists()) tempFile.delete()
                tempFile.createNewFile()

                // Register download inside database Room with no initial URI
                val downloadItem = DownloadItem(
                    downloadId = downloadId,
                    fileName = chosenFile.name,
                    fileUrl = url,
                    mimeType = safeMimeType,
                    filePath = chosenFile.absolutePath,
                    fileUri = null,
                    status = 0, // Running
                    timestamp = System.currentTimeMillis(),
                    totalBytes = totalBytes,
                    downloadedBytes = 0
                )
                database.downloadDao().insertDownload(downloadItem)

                notificationBuilder
                    .setContentTitle("Mengunduh ${chosenFile.name}")
                    .setContentText("Mengunduh data...")
                notificationManager.notify(downloadId.toInt(), notificationBuilder.build())

                val progressTracker = AtomicLong(0L)
                val progressMutex = Mutex()
                val lastUpdateMillis = AtomicLong(System.currentTimeMillis())

                fun updateProgress(downloaded: Long) {
                    val now = System.currentTimeMillis()
                    val last = lastUpdateMillis.get()
                    if (now - last >= 500 || (totalBytes > 0 && downloaded >= totalBytes)) {
                        if (lastUpdateMillis.compareAndSet(last, now)) {
                            scope.launch(Dispatchers.IO) {
                                progressMutex.withLock {
                                    database.downloadDao().updateDownloadProgress(downloadId, 0, downloaded, totalBytes)
                                }
                            }
                            val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                            val text = if (totalBytes > 0) {
                                "${(downloaded / (1024 * 1024))}MB / ${(totalBytes / (1024 * 1024))}MB ($percent%)"
                            } else {
                                "${(downloaded / (1024 * 1024))}MB"
                            }
                            notificationBuilder.setProgress(100, if (totalBytes > 0) percent else 0, totalBytes <= 0)
                                .setContentText(text)
                            notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
                        }
                    }
                }

                val downloadSuccess = if (supportsRanges && totalBytes > MIN_CHUNK_SIZE) {
                    val chunkSize = totalBytes / PARALLEL_CHUNKS
                    val tasks = (0 until PARALLEL_CHUNKS).map { index ->
                        val startByte = index * chunkSize
                        val endByte = if (index == PARALLEL_CHUNKS - 1) totalBytes - 1 else (startByte + chunkSize - 1)
                        async(Dispatchers.IO) {
                            KtorNetworkEngine.downloadRangeChunk(
                                url = url,
                                startByte = startByte,
                                endByte = endByte,
                                targetFile = tempFile,
                                userAgent = reqUserAgent,
                                cookieHeader = cookieStr,
                                onProgress = { bytesRead ->
                                    val currentTotal = progressTracker.addAndGet(bytesRead.toLong())
                                    updateProgress(currentTotal)
                                }
                            )
                        }
                    }
                    val results = tasks.awaitAll()
                    results.all { it }
                } else {
                    KtorNetworkEngine.downloadRangeChunk(
                        url = url,
                        startByte = 0L,
                        endByte = -1L,
                        targetFile = tempFile,
                        userAgent = reqUserAgent,
                        cookieHeader = cookieStr,
                        onProgress = { bytesRead ->
                            val currentTotal = progressTracker.addAndGet(bytesRead.toLong())
                            updateProgress(currentTotal)
                        }
                    )
                }

                if (downloadSuccess && tempFile.exists()) {
                    // Get target MediaStore OutputStream after the download is fully successful
                    val (stream, createdUri) = com.example.util.StorageHelper.getOutputStreamForDownload(
                        context,
                        chosenFile.name,
                        safeMimeType
                    )
                    if (stream == null) {
                        throw Exception("Gagal membuat file ${chosenFile.name} di folder Downloads")
                    }

                    // Copy temporary file to target MediaStore OutputStream safely
                    stream.use { output ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                        output.flush()
                    }
                    tempFile.delete()

                    // Finalize download to remove .pending extension and make it readable by system (API 29+)
                    com.example.util.StorageHelper.finalizeDownload(context, createdUri)

                    database.downloadDao().updateDownloadProgress(downloadId, 1, totalBytes, totalBytes)
                    database.downloadDao().updateDownloadStatusWithUri(downloadId, 1, createdUri ?: "")

                    notificationBuilder
                        .setContentTitle("Selesai Mengunduh")
                        .setContentText(chosenFile.name)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setOngoing(false)
                        .setProgress(0, 0, false)
                    notificationManager.notify(downloadId.toInt(), notificationBuilder.build())

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Selesai: ${chosenFile.name}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("Pengunduhan gagal")
                }

            } catch (e: Exception) {
                database.downloadDao().updateDownloadProgress(downloadId, 2, 0, 0)
                notificationBuilder
                    .setContentTitle("Unduhan Gagal")
                    .setContentText(e.localizedMessage ?: "Terjadi kesalahan")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
            } finally {
                // Ensure temporary cache file is always cleaned up after move or on failure
                // We check context.cacheDir for any files starting with temp_${downloadId}_ to be safe
                try {
                    context.cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("temp_${downloadId}_")) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Menyimpan file dari Data URL / Blob Base64 yang dihasilkan oleh JavaScript.
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

                val downloadItem = DownloadItem(
                    downloadId = downloadId,
                    fileName = finalFile.name,
                    fileUrl = "blob:${finalFile.name}",
                    mimeType = safeMimeType,
                    filePath = finalFile.absolutePath,
                    fileUri = uriString,
                    status = 1, // Completed
                    timestamp = System.currentTimeMillis(),
                    totalBytes = totalBytes,
                    downloadedBytes = totalBytes
                )
                database.downloadDao().insertDownload(downloadItem)

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
     * Membuka file unduhan yang telah selesai dengan aplikasi yang sesuai.
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
                // Ignore
            }
        }
    }

    fun clearAll() {
        scope.launch {
            try {
                database.downloadDao().clearAllDownloads()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun updateProgressForActiveDownloads(activeDownloads: List<DownloadItem>) {
        // Ignored
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
