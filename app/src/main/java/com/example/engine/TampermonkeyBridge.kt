package com.example.engine

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ScriptLogEntry(
    val timestamp: String,
    val level: String,
    val scriptName: String,
    val message: String
)

class TampermonkeyBridge(private val context: Context) {

    // Persistent storage for GM_setValue / GM_getValue
    private val prefs = context.getSharedPreferences("tampermonkey_storage", Context.MODE_PRIVATE)

    // URL change listener for SPA (e.g. YouTube, Twitter, Reddit)
    var onUrlChangeListener: ((url: String, title: String) -> Unit)? = null

    // Live logs flow
    private val _logs = MutableStateFlow<List<ScriptLogEntry>>(emptyList())
    val logs: StateFlow<List<ScriptLogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @JavascriptInterface
    fun GM_setValue(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun GM_getValue(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun GM_deleteValue(key: String) {
        prefs.edit().remove(key).apply()
    }

    @JavascriptInterface
    fun GM_listValues(): String {
        return GM_listValues("")
    }

    @JavascriptInterface
    fun GM_listValues(prefix: String): String {
        val keys = if (prefix.isEmpty()) {
            prefs.all.keys.toList()
        } else {
            prefs.all.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        }
        val jsonArray = JSONArray(keys)
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun GM_log(scriptName: String, message: String) {
        addLog("INFO", scriptName, message)
    }

    @JavascriptInterface
    fun GM_notification(text: String, title: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "[$title] $text", Toast.LENGTH_SHORT).show()
        }
        addLog("NOTIFY", title, text)
    }

    @JavascriptInterface
    fun logConsole(level: String, scriptName: String, message: String) {
        addLog(level.uppercase(), scriptName, message)
    }

    @JavascriptInterface
    fun onSpaUrlChanged(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                onUrlChangeListener?.invoke(url, title)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @JavascriptInterface
    fun processBlobDownload(base64Data: String, mimeType: String, fileName: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val pureBase64Encoded = base64Data.substring(base64Data.indexOf(",") + 1)
                val decodedBytes = android.util.Base64.decode(pureBase64Encoded, android.util.Base64.DEFAULT)

                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val safeFileName = fileName.ifEmpty { "downloaded_blob" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var file = java.io.File(downloadsDir, safeFileName)
                
                if (file.exists()) {
                    val nameWithoutExt = file.nameWithoutExtension
                    val ext = file.extension
                    val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
                    var counter = 1
                    while (file.exists()) {
                        file = java.io.File(downloadsDir, "$nameWithoutExt ($counter)$extSuffix")
                        counter++
                    }
                }

                java.io.FileOutputStream(file).use {
                    it.write(decodedBytes)
                }
                
                Toast.makeText(context, "Blob disimpan: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengunduh Blob: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(level: String, scriptName: String, message: String) {
        val time = dateFormat.format(Date())
        val newEntry = ScriptLogEntry(
            timestamp = time,
            level = level,
            scriptName = scriptName.ifBlank { "Script" },
            message = message
        )
        _logs.value = (listOf(newEntry) + _logs.value).take(150)
    }

    companion object {
        const val INTERFACE_NAME = "_DarkBrowserBridge"
    }
}
