package com.example.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class DownloadEngine(
    private val context: Context
) {
    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mimeType.isNullOrEmpty()) {
                    setMimeType(mimeType)
                }
                
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null) {
                    addRequestHeader("cookie", cookies)
                }
                
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            
            downloadManager.enqueue(request)
            
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveBlobData(
        base64Data: String,
        mimeType: String?,
        fileName: String? = "downloaded_file"
    ) {
        val finalFileName = (fileName ?: "downloaded_file").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val finalMimeType = mimeType ?: "application/octet-stream"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = base64Data.substringAfter("base64,")
                val bytes = Base64.decode(data, Base64.DEFAULT)
                
                val (outputStream, uriString) = com.example.util.StorageHelper.getOutputStreamForDownload(
                    context,
                    finalFileName,
                    finalMimeType
                )
                
                if (outputStream != null) {
                    outputStream.use { it.write(bytes) }
                    com.example.util.StorageHelper.finalizeDownload(context, uriString)
                } else {
                    // Fallback for older legacy storage or when MediaStore is unavailable
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    var file = File(downloadsDir, finalFileName)
                    var counter = 1
                    val nameWithoutExt = file.nameWithoutExtension
                    val ext = file.extension
                    val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
                    while (file.exists()) {
                        file = File(downloadsDir, "$nameWithoutExt ($counter)$extSuffix")
                        counter++
                    }
                    FileOutputStream(file).use { it.write(bytes) }
                }
                
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "File saved to Downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
