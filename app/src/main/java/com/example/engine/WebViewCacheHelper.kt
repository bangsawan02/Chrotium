package com.example.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Helper untuk mengelola dan merapikan direktori cache Chromium WebView secara aman.
 * Menghindari modifikasi struktur internal SimpleCache yang dapat memicu error 'Failed to write a new fake index'.
 */
object WebViewCacheHelper {
    private const val TAG = "WebViewCacheHelper"

    fun ensureCacheDirectories(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val dataDir = context.dataDir

            // Bersihkan jika ada folder Code Cache yang salah letak di dalam HTTP Cache
            val corruptHttpCacheDirs = listOf(
                File(cacheDir, "WebView/Default/HTTP Cache/Code Cache"),
                File(dataDir, "app_webview/Default/HTTP Cache/Code Cache")
            )
            for (corruptDir in corruptHttpCacheDirs) {
                if (corruptDir.exists() && corruptDir.isDirectory) {
                    corruptDir.deleteRecursively()
                }
            }

            // Pastikan direktori root WebView ada dan writable
            val rootDirs = listOf(
                File(cacheDir, "WebView/Default"),
                File(dataDir, "app_webview/Default")
            )
            for (dir in rootDirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ensuring WebView cache directories", e)
        }
    }
}

