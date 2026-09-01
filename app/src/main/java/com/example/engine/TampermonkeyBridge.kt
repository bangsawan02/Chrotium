package com.example.engine

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*
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
    // Multi-tab support: Map of tabId to WebView reference
    private val webViews = ConcurrentHashMap<String, java.lang.ref.WeakReference<android.webkit.WebView>>()

    fun registerWebView(tabId: String, wv: android.webkit.WebView) {
        webViews[tabId] = java.lang.ref.WeakReference(wv)
    }

    fun unregisterWebView(tabId: String) {
        webViews.remove(tabId)
    }

    private fun getWebViewForTab(tabId: String): android.webkit.WebView? {
        return webViews[tabId]?.get()
    }

    // Persistent storage for GM_setValue / GM_getValue
    private val prefs = context.getSharedPreferences("tampermonkey_storage", Context.MODE_PRIVATE)

    // URL change listener for SPA (e.g. YouTube, Twitter, Reddit)
    var onUrlChangeListener: ((url: String, title: String) -> Unit)? = null

    // Live logs flow
    private val _logs = MutableStateFlow<List<ScriptLogEntry>>(emptyList())
    val logs: StateFlow<List<ScriptLogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @JavascriptInterface
    fun GM_setValue(scriptName: String, key: String, value: String) {
        val scriptId = scriptName.hashCode().toString()
        val storageKey = "sc_${scriptId}_$key"
        prefs.edit().putString(storageKey, value).apply()
    }

    @JavascriptInterface
    fun GM_getValue(scriptName: String, key: String, defaultValue: String): String {
        val scriptId = scriptName.hashCode().toString()
        val storageKey = "sc_${scriptId}_$key"
        return prefs.getString(storageKey, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun GM_deleteValue(scriptName: String, key: String) {
        val scriptId = scriptName.hashCode().toString()
        val storageKey = "sc_${scriptId}_$key"
        prefs.edit().remove(storageKey).apply()
    }

    @JavascriptInterface
    fun GM_listValues(scriptName: String): String {
        val scriptId = scriptName.hashCode().toString()
        val prefix = "sc_${scriptId}_"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
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

    private val xhrCallbacks = java.util.concurrent.ConcurrentHashMap<String, java.lang.ref.WeakReference<android.webkit.WebView>>()

    @JavascriptInterface
    fun GM_xmlhttpRequest_proxy(jsonDetails: String, scriptName: String, tabId: String): String {
        val details = try { JSONObject(jsonDetails) } catch (e: org.json.JSONException) { JSONObject() } catch (e: IllegalArgumentException) { JSONObject() }
        val requestId = details.optString("requestId").ifBlank { (System.currentTimeMillis().toString() + (0..999).random()) }
        val targetWebView = getWebViewForTab(tabId)
        if (targetWebView != null) {
            xhrCallbacks[requestId] = java.lang.ref.WeakReference(targetWebView)
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val urlStr = details.optString("url")
                    val method = details.optString("method", "GET").uppercase()
                    val headers = details.optJSONObject("headers")
                    val data = details.optString("data")

                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = method
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.instanceFollowRedirects = true

                    headers?.keys()?.forEach { key ->
                        conn.setRequestProperty(key, headers.getString(key))
                    }

                    if (method == "POST" || method == "PUT" || method == "PATCH") {
                        conn.doOutput = true
                        if (!data.isNullOrBlank()) {
                            conn.outputStream.use { it.write(data.toByteArray()) }
                        }
                    }

                    val respCode = conn.responseCode
                    val respMsg = conn.responseMessage
                    val respHeaders = conn.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
                    val isError = respCode >= 400
                    val inputStream = if (isError) conn.errorStream else conn.inputStream
                    val responseText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                    val result = JSONObject().apply {
                        put("status", respCode)
                        put("statusText", respMsg)
                        put("responseText", responseText)
                        put("readyState", 4)
                        put("finalUrl", conn.url.toString())
                        put("responseHeaders", respHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" })
                    }
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val finalWv = xhrCallbacks[requestId]?.get()
                        if (finalWv != null) {
                            try {
                                val js = "if(window._gm_xhr_callbacks && window._gm_xhr_callbacks['$requestId']) { window._gm_xhr_callbacks['$requestId'](${result}); delete window._gm_xhr_callbacks['$requestId']; }"
                                finalWv.evaluateJavascript(js, null)
                            } catch (e: Exception) {
                                // WebView may have been destroyed between null check and execution
                                addLog("WARN", "TampermonkeyBridge", "WebView destroyed before callback: ${e.message}")
                            }
                        }
                        xhrCallbacks.remove(requestId)
                    }
                } catch (e: java.io.IOException) {
                    addLog("ERROR", scriptName, "GM_xmlhttpRequest network error: ${e.message}")
                    handleXhrError(requestId, "Network error: ${e.message}")
                } catch (e: java.net.MalformedURLException) {
                    addLog("ERROR", scriptName, "GM_xmlhttpRequest invalid URL: ${e.message}")
                    handleXhrError(requestId, "Invalid URL: ${e.message}")
                } catch (e: Exception) {
                    addLog("ERROR", scriptName, "GM_xmlhttpRequest failed: ${e.message}")
                    handleXhrError(requestId, "Request failed: ${e.message}")
                }
            }
        }
        return requestId
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

                val safeFileName = fileName.ifEmpty { "downloaded_blob" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val (outputStream, uriString) = com.example.util.StorageHelper.getOutputStreamForDownload(context, safeFileName, mimeType)
                if (outputStream == null) {
                    throw Exception("Gagal membuat file di folder Downloads")
                }

                outputStream.use {
                    it.write(decodedBytes)
                }

                com.example.util.StorageHelper.finalizeDownload(context, uriString)
                
                Toast.makeText(context, "Blob disimpan di folder Downloads", Toast.LENGTH_LONG).show()
            } catch (e: java.io.IOException) {
                Toast.makeText(context, "Gagal menyimpan file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, "Format base64 tidak valid: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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

    private fun handleXhrError(requestId: String, errorMessage: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val errorJson = JSONObject().apply {
                put("status", 0)
                put("error", errorMessage)
            }
            val finalWv = xhrCallbacks[requestId]?.get()
            if (finalWv != null) {
                try {
                    val js = "if(window._gm_xhr_callbacks && window._gm_xhr_callbacks['$requestId']) { window._gm_xhr_callbacks['$requestId']($errorJson); delete window._gm_xhr_callbacks['$requestId']; }"
                    finalWv.evaluateJavascript(js, null)
                } catch (e: Exception) {
                    // WebView may have been destroyed between null check and execution
                    addLog("WARN", "TampermonkeyBridge", "WebView destroyed before error callback: ${e.message}")
                }
            }
            xhrCallbacks.remove(requestId)
        }
    }

    companion object {
        const val INTERFACE_NAME = "_DarkBrowserBridge"
    }
}
