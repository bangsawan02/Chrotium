package com.example.engine

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Aggressive Disk Cache Manager for static web assets.
 * Improves page load speed and battery efficiency by intercepting and caching static resources
 * (images, styles, scripts) directly to disk, bypassing WebView's default cache expiration limits.
 */
object DiskCacheManager {
    private var cacheDir: File? = null
    
    // Extensions safe for aggressive caching
    private val staticExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".css", ".js", ".woff", ".woff2", ".ttf")

    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "static_web_cache")
            if (!cacheDir!!.exists()) {
                cacheDir!!.mkdirs()
            }
        }
    }

    fun clearCache() {
        cacheDir?.deleteRecursively()
        cacheDir?.mkdirs()
    }

    fun getCacheSizeMb(): Float {
        val bytes = cacheDir?.walkTopDown()?.filter { it.isFile }?.map { it.length() }?.sum() ?: 0L
        return bytes / (1024f * 1024f)
    }

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        if (request.method != "GET") return null
        
        val urlString = request.url.toString()
        if (!isStaticAsset(urlString)) return null

        val cacheFile = getCacheFile(urlString) ?: return null

        if (cacheFile.exists() && cacheFile.length() > 0) {
            // Cache Hit!
            val mimeType = getMimeType(urlString)
            try {
                // Prepare response headers to tell WebView this is heavily cached
                val response = WebResourceResponse(mimeType, "UTF-8", FileInputStream(cacheFile))
                val headers = mutableMapOf(
                    "Cache-Control" to "public, max-age=31536000",
                    "Access-Control-Allow-Origin" to "*"
                )
                response.responseHeaders = headers
                return response
            } catch (e: Exception) {
                // Ignore, fallback to network
            }
        }

        // Download, cache, and serve
        return downloadAndCache(urlString, cacheFile)
    }

    private fun isStaticAsset(url: String): Boolean {
        val lowerUrl = url.substringBefore("?").substringBefore("#").lowercase()
        return staticExtensions.any { lowerUrl.endsWith(it) }
    }

    private fun getMimeType(url: String): String {
        val lowerUrl = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            lowerUrl.endsWith(".css") -> "text/css"
            lowerUrl.endsWith(".js") -> "application/javascript"
            lowerUrl.endsWith(".png") -> "image/png"
            lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") -> "image/jpeg"
            lowerUrl.endsWith(".webp") -> "image/webp"
            lowerUrl.endsWith(".svg") -> "image/svg+xml"
            lowerUrl.endsWith(".gif") -> "image/gif"
            lowerUrl.endsWith(".woff2") -> "font/woff2"
            lowerUrl.endsWith(".woff") -> "font/woff"
            lowerUrl.endsWith(".ttf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }

    private fun getCacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        val hash = hashString(url)
        return File(dir, hash)
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun downloadAndCache(urlString: String, cacheFile: File): WebResourceResponse? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val tempFile = File(cacheFile.absolutePath + ".tmp")
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile.renameTo(cacheFile)

                val mimeType = getMimeType(urlString)
                val response = WebResourceResponse(mimeType, "UTF-8", FileInputStream(cacheFile))
                response.responseHeaders = mapOf(
                    "Cache-Control" to "public, max-age=31536000",
                    "Access-Control-Allow-Origin" to "*"
                )
                return response
            }
        } catch (e: Exception) {
            // Cleanup on failure
            if (cacheFile.exists() && cacheFile.length() == 0L) {
                cacheFile.delete()
            }
        }
        return null
    }
}
