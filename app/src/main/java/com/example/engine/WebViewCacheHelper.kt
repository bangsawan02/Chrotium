package com.example.engine

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Helper untuk menginisialisasi dan memastikan struktur direktori cache Chromium WebView
 * (seperti HTTP Cache, Code Cache/js, Code Cache/wasm, GPUCache) selalu tersedia.
 * Mencegah error ENOENT (2) "No such file or directory" saat SimpleCache indexer Chromium membaca disk.
 */
object WebViewCacheHelper {
    private const val TAG = "WebViewCacheHelper"

    fun ensureCacheDirectories(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val dataDir = context.dataDir

            val targetDirs = listOf(
                File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js"),
                File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm"),
                File(cacheDir, "WebView/Default/Code Cache/js"),
                File(cacheDir, "WebView/Default/Code Cache/wasm"),
                File(cacheDir, "WebView/Default/GPUCache"),
                File(cacheDir, "WebView/Default/Service Worker/CacheStorage"),
                File(cacheDir, "WebView/Default/Service Worker/ScriptCache"),
                File(dataDir, "app_webview/Default/HTTP Cache/Code Cache/js"),
                File(dataDir, "app_webview/Default/HTTP Cache/Code Cache/wasm"),
                File(dataDir, "app_webview/Default/Code Cache/js"),
                File(dataDir, "app_webview/Default/Code Cache/wasm"),
                File(dataDir, "app_webview/Default/GPUCache"),
                File(dataDir, "app_webview/Default/Service Worker/CacheStorage"),
                File(dataDir, "app_webview/Default/Service Worker/ScriptCache")
            )

            for (dir in targetDirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ensuring WebView cache directories", e)
        }
    }
}
