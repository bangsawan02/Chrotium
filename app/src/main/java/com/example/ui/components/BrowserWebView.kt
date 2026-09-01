package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient as AndroidXWebViewRenderProcessClient
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.TabItem
import com.example.data.model.UserScript
import com.example.engine.AdBlockEngine
import com.example.engine.TampermonkeyBridge
import com.example.engine.UserScriptEngine
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp

private data class JsAlertData(val message: String, val result: JsResult)
private data class JsConfirmData(val message: String, val result: JsResult)
private data class JsPromptData(val message: String, val defaultValue: String, val result: JsPromptResult)

private fun shouldInjectHeavyPageOptimizations(url: String): Boolean {
    val normalized = url.lowercase()
    return normalized.contains("youtube.com") ||
        normalized.contains("youtu.be") ||
        normalized.contains("ai.studio") ||
        normalized.contains("aistudio.google.com")
}

class BackgroundPlayWebView(context: Context) : WebView(context) {
    var isBackgroundPlayEnabled: Boolean = true
    var isMediaPlaying: Boolean = false

    fun refreshVisibility() {
        // Panggil internal visibility logic dengan status saat ini
        onWindowVisibilityChanged(windowVisibility)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Jika Background Play aktif, selalu pertahankan visibilitas VISIBLE agar WebView tidak mati saat berpindah lagu di background / YouTube Mix.
        if (isBackgroundPlayEnabled) {
            super.onWindowVisibilityChanged(android.view.View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        if (isBackgroundPlayEnabled) {
            super.onVisibilityChanged(changedView, android.view.View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }
}

/**
 * Interface internal untuk kontrol peramban dari sisi JavaScript.
 */
class ChrotiumInterface(
    private val webView: BackgroundPlayWebView,
    private val onDownload: (String, String?) -> Unit,
    private val onBlobDownload: (String, String?, String?) -> Unit,
    private val onUrlChange: (String) -> Unit,
    private val onMediaStatusChanged: (Boolean) -> Unit = {}
) {
    @android.webkit.JavascriptInterface
    fun triggerDownload(url: String, filename: String?) {
        webView.post {
            onDownload(url, filename)
        }
    }

    @android.webkit.JavascriptInterface
    fun saveBlobDownload(base64Data: String, filename: String?, mimeType: String?) {
        webView.post {
            onBlobDownload(base64Data, filename, mimeType)
        }
    }

    @android.webkit.JavascriptInterface
    fun onUrlChange(url: String) {
        onUrlChange(url)
    }

    @android.webkit.JavascriptInterface
    fun copyToClipboard(text: String) {
        webView.post {
            try {
                val clipboard = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Chrotium Clipboard", text)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {}
        }
    }

    @android.webkit.JavascriptInterface
    fun getClipboardText(): String {
        return try {
            val clipboard = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(webView.context).toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    @android.webkit.JavascriptInterface
    fun updateMediaStatus(isPlaying: Boolean) {
        webView.post {
            if (webView.isMediaPlaying != isPlaying) {
                webView.isMediaPlaying = isPlaying
                // Picu evaluasi ulang visibilitas
                webView.refreshVisibility()
                onMediaStatusChanged(isPlaying)

                // Keep screen awake while watching video in foreground
                val activity = webView.context as? android.app.Activity
                if (isPlaying && webView.visibility == android.view.View.VISIBLE) {
                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}

/**
 * SwipeRefreshLayout dengan touch-slop terkalibrasi agar tidak terlalu sensitif saat scroll biasa.
 * Hanya bereaksi ketika pengguna benar-benar berada di paling atas halaman dan menarik ke bawah
 * secara vertikal dengan sengaja.
 */
class RobustSwipeRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    // Memperbesar touch slop secara signifikan (x4) agar jauh lebih tidak sensitif
    // sehingga pengguna web modern (seperti ai.studio) dapat scroll ke atas tanpa sengaja me-refresh.
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 4
    private var initialDownX = 0f
    private var initialDownY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = ev.x
                initialDownY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - initialDownX)
                val dy = ev.y - initialDownY

                // Jangan aktifkan jika gerakan horizontal atau scroll ke atas
                if (dy <= 0 || dx > dy * 0.5f) {
                    return false
                }

                // Harus melewati batas tarikan vertikal yang disengaja yang jauh lebih panjang
                if (dy < touchSlop) {
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: TabItem,
    scripts: List<UserScript>,
    tampermonkeyBridge: TampermonkeyBridge,
    adBlockEngine: AdBlockEngine,
    isDarkTheme: Boolean,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
    onScriptsExecuted: (List<String>) -> Unit,
    onScriptInjected: (UserScript) -> Unit,
    onUrlUpdated: (String, String?) -> Unit = { _, _ -> },
    onAdBlocked: () -> Unit = {},
    onDownloadRequested: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) -> Unit = { _, _, _, _ -> },
    onBlobDownloadRequested: (base64Data: String, fileName: String?, mimeType: String?) -> Unit = { _, _, _ -> },
    onFullScreenChanged: (Boolean) -> Unit = {},
    onMediaStatusChanged: (Boolean) -> Unit = {},
    onNewTabRequested: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    var pendingPermissionRequest by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    var pendingGeolocationCallback by remember { mutableStateOf<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var jsAlertData by remember { mutableStateOf<JsAlertData?>(null) }
    var jsConfirmData by remember { mutableStateOf<JsConfirmData?>(null) }
    var jsPromptData by remember { mutableStateOf<JsPromptData?>(null) }
    var promptInputText by remember { mutableStateOf("") }
    
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val pending = pendingPermissionRequest ?: return@rememberLauncherForActivityResult
        val grantedResources = mutableListOf<String>()
        
        for (resource in pending.resources) {
            when (resource) {
                android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (permissionsMap[android.Manifest.permission.CAMERA] == true) {
                        grantedResources.add(resource)
                    }
                }
                android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (permissionsMap[android.Manifest.permission.RECORD_AUDIO] == true) {
                        grantedResources.add(resource)
                    }
                }
                else -> {
                    grantedResources.add(resource)
                }
            }
        }
        
        if (grantedResources.isNotEmpty()) {
            try {
                pending.grant(grantedResources.toTypedArray())
            } catch (e: Exception) {
                // ignore
            }
        } else {
            try {
                pending.deny()
            } catch (e: Exception) {
                // ignore
            }
        }
        pendingPermissionRequest = null
    }

    val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val pair = pendingGeolocationCallback ?: return@rememberLauncherForActivityResult
        val (origin, callback) = pair
        val isGranted = permissionsMap[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissionsMap[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        try {
            callback.invoke(origin, isGranted, false)
        } catch (e: Exception) {
            // ignore
        }
        pendingGeolocationCallback = null
    }

    val webView = remember(tab.id) {
        BackgroundPlayWebView(context).apply {
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val defaultBg = if (isDarkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
            setBackgroundColor(defaultBg)

            // Configure WebSettings for optimal performance and smooth 60fps rendering
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            requestFocusFromTouch()
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

            // Ensure touching the webview requests focus reliably without losing software keyboard
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
                false
            }
            
            // Aktifkan pre-rasterisasi GPU untuk mempercepat rendering saat scroll 
            // meniru arsitektur Google Chrome (mengorbankan sedikit RAM untuk FPS tinggi).
            @Suppress("DEPRECATION")
            settings.offscreenPreRaster = true
            
            com.example.engine.WebConfig.configureWebSettings(settings, isDarkTheme)

            // Renderer Priority Policy (Memastikan proses render WebView diprioritaskan secara maksimal oleh OS)
            try {
                setRendererPriorityPolicy(
                    android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT,
                    false // Prioritas penuh tanpa penahanan saat halaman me-render animasi/script berat
                )
            } catch (_: Exception) {}
            settings.userAgentString = com.example.engine.WebConfig.getCustomUserAgent(tab.url, tab.isDesktopMode)
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Enable full Cookie & 3rd Party Cookie Support
            com.example.engine.CookieHelper.configureWebViewCookies(this, true)
            // Enable WebContents Debugging for DevTools Remote CDP Inspector
            com.example.engine.DevToolsEngine.enableWebViewDebugging(this)

            // Multi-Process Renderer Process Client to isolate rendering process and handle OOM crashes gracefully
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
                    WebViewCompat.setWebViewRenderProcessClient(
                        this,
                        object : AndroidXWebViewRenderProcessClient() {
                            override fun onRenderProcessUnresponsive(
                                view: WebView,
                                renderer: WebViewRenderProcess?
                            ) {
                                android.util.Log.w(
                                    "BrowserWebView",
                                    "WebViewRenderProcessClient: Renderer process unresponsive (OOM / heavy script execution). Terminating isolated renderer process safely to ensure main application UI thread remains responsive."
                                )
                                // Terminating the renderer in a separate process immediately frees memory resources
                                // and triggers onRenderProcessGone recovery without locking the main UI thread.
                                renderer?.terminate()
                            }

                            override fun onRenderProcessResponsive(
                                view: WebView,
                                renderer: WebViewRenderProcess?
                            ) {
                                android.util.Log.i(
                                    "BrowserWebView",
                                    "WebViewRenderProcessClient: Renderer process is responsive again."
                                )
                            }
                        }
                    )
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    webViewRenderProcessClient = object : android.webkit.WebViewRenderProcessClient() {
                        override fun onRenderProcessUnresponsive(
                            view: WebView,
                            renderer: android.webkit.WebViewRenderProcess?
                        ) {
                            android.util.Log.w(
                                "BrowserWebView",
                                "Renderer process unresponsive. Terminating renderer to trigger onRenderProcessGone recovery."
                            )
                            renderer?.terminate()
                        }

                        override fun onRenderProcessResponsive(
                            view: WebView,
                            renderer: android.webkit.WebViewRenderProcess?
                        ) {
                            android.util.Log.i(
                                "BrowserWebView",
                                "Renderer process responsive again."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BrowserWebView", "Failed to configure WebViewRenderProcessClient", e)
            }

            // Set Downloader Listener
            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                if (url.startsWith("blob:")) {
                    val guessedName = if (!contentDisposition.isNullOrBlank()) {
                        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    } else {
                        "download_${System.currentTimeMillis()}"
                    }
                    val js = """
                        (function() {
                            var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs['$url'];
                            if (cachedBlob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                        window.ChrotiumInterface.saveBlobDownload(reader.result, '$guessedName', cachedBlob.type || '${mimetype ?: "application/octet-stream"}');
                                    }
                                };
                                reader.readAsDataURL(cachedBlob);
                                return;
                            }
                            fetch('$url')
                                .then(function(res) { return res.blob(); })
                                .then(function(blob) {
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                            window.ChrotiumInterface.saveBlobDownload(reader.result, '$guessedName', '${mimetype ?: "application/octet-stream"}');
                                        }
                                    };
                                    reader.readAsDataURL(blob);
                                })
                                .catch(function(err) {
                                    console.error('[Chrotium] WebView DownloadListener blob fetch error:', err);
                                });
                        })();
                    """.trimIndent()
                    evaluateJavascript(js, null)
                } else {
                    onDownloadRequested(url, userAgent, contentDisposition, mimetype)
                }
            }

            // Inject Tampermonkey JS Bridge
            addJavascriptInterface(tampermonkeyBridge, TampermonkeyBridge.INTERFACE_NAME)
            // Inject Internal Chrotium JS Interface
            addJavascriptInterface(
                ChrotiumInterface(
                    webView = this,
                    onDownload = { downloadUrl, customFilename ->
                        if (downloadUrl.startsWith("blob:")) {
                            val js = """
                                (function() {
                                    var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs['$downloadUrl'];
                                    if (cachedBlob) {
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                window.ChrotiumInterface.saveBlobDownload(reader.result, '${customFilename ?: "download"}', cachedBlob.type || 'application/octet-stream');
                                            }
                                        };
                                        reader.readAsDataURL(cachedBlob);
                                        return;
                                    }
                                    fetch('$downloadUrl')
                                        .then(function(res) { return res.blob(); })
                                        .then(function(blob) {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                    window.ChrotiumInterface.saveBlobDownload(reader.result, '${customFilename ?: "download"}', '${if (downloadUrl.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream"}');
                                                }
                                            };
                                            reader.readAsDataURL(blob);
                                        })
                                        .catch(function(err) {
                                            console.error('[Chrotium] Blob fetch error:', err);
                                        });
                                })();
                            """.trimIndent()
                            evaluateJavascript(js, null)
                        } else {
                            val contentDisposition = if (!customFilename.isNullOrBlank()) "attachment; filename=\"$customFilename\"" else null
                            val mimeType = if (downloadUrl.endsWith(".apk", ignoreCase = true)) "application/vnd.android.package-archive" else null
                            onDownloadRequested(downloadUrl, settings.userAgentString, contentDisposition, mimeType)
                        }
                    },
                    onBlobDownload = { base64, filename, mime ->
                        onBlobDownloadRequested(base64, filename, mime)
                    },
                    onUrlChange = { newUrl ->
                        onUrlUpdated(newUrl, this.title)
                    },
                    onMediaStatusChanged = onMediaStatusChanged
                ),
                "ChrotiumInterface"
            )

            var lastKnownUrl = tab.url

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                    if (request != null) {
                        val url = request.url?.toString() ?: ""
                        
                        // 1. Bypass total domain streaming video agar buffer instan
                        if (url.contains("googlevideo.com") || url.contains("videoplayback")) {
                            return null // Biarkan engine native Chromium yang menangani
                        }

                        if (adBlockEngine.shouldBlockRequest(request, lastKnownUrl)) {
                            onAdBlocked()
                            return adBlockEngine.createEmptyResourceResponse()
                        }
                        
                        // Aggressive Disk Cache for static assets
                        val cachedResponse = com.example.engine.DiskCacheManager.shouldInterceptRequest(request)
                        if (cachedResponse != null) {
                            return cachedResponse
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val safeUrl = url ?: return
                    lastKnownUrl = safeUrl
                    val isAuth = com.example.engine.WebConfig.isGoogleAuthUrl(safeUrl)
                    
                    view?.settings?.userAgentString = com.example.engine.WebConfig.getCustomUserAgent(safeUrl, tab.isDesktopMode)

                    onPageStarted(safeUrl)

                    view?.let { wv ->
                        injectPageEnhancements(wv, safeUrl, isAuth, isDarkTheme, tab.isH264ifyEnabled, tab.isDesktopMode, adBlockEngine)
                    }

                    // Inject document-start scripts (only for non-auth pages)
                    if (!isAuth) {
                        view?.let { wv ->
                            val executed = UserScriptEngine.injectScriptsForStage(
                                webView = wv,
                                url = safeUrl,
                                stage = "document-start",
                                allScripts = scripts,
                                tabId = tab.id,
                                onScriptInjected = onScriptInjected
                            )
                            if (executed.isNotEmpty()) {
                                onScriptsExecuted(executed)
                            }
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val safeUrl = url ?: return
                    val isAuth = com.example.engine.WebConfig.isGoogleAuthUrl(safeUrl)
                    
                    view?.post {
                        (view.parent as? SwipeRefreshLayout)?.isRefreshing = false
                    }

                    onPageFinished(safeUrl, view?.title)
                    onNavigationStateChanged(view?.canGoBack() == true, view?.canGoForward() == true)

                    // Ensure cookie sync after auth redirect on background thread
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            android.webkit.CookieManager.getInstance().flush()
                        } catch (_: Exception) {}
                    }

                    view?.let { wv ->
                        injectPageEnhancements(wv, safeUrl, isAuth, isDarkTheme, tab.isH264ifyEnabled, tab.isDesktopMode, adBlockEngine)
                    }

                    // Inject document-end & document-idle scripts (only for non-auth pages)
                    if (!isAuth) {
                        view?.let { wv ->
                            val executedEnd = UserScriptEngine.injectScriptsForStage(
                                webView = wv,
                                url = safeUrl,
                                stage = "document-end",
                                allScripts = scripts,
                                tabId = tab.id,
                                onScriptInjected = onScriptInjected
                            )
                            val executedIdle = UserScriptEngine.injectScriptsForStage(
                                webView = wv,
                                url = safeUrl,
                                stage = "document-idle",
                                allScripts = scripts,
                                tabId = tab.id,
                                onScriptInjected = onScriptInjected
                            )
                            val totalExecuted = (executedEnd + executedIdle).distinct()
                            if (totalExecuted.isNotEmpty()) {
                                onScriptsExecuted(totalExecuted)
                            }
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    val safeUrl = url ?: view?.url ?: return
                    lastKnownUrl = safeUrl
                    onUrlUpdated(safeUrl, view?.title)
                    onNavigationStateChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val urlStr = uri.toString()
                    val scheme = uri.scheme ?: ""

                    view?.settings?.userAgentString = com.example.engine.WebConfig.getCustomUserAgent(urlStr, tab.isDesktopMode)

                    if (scheme == "http" || scheme == "https" || scheme == "about") {
                        return false
                    }

                    // Handle in-memory Blob URLs and Data URLs gracefully
                    if (scheme == "blob" || scheme == "data") {
                        val js = """
                            (function() {
                                try {
                                    if ('$scheme' === 'data') {
                                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                            window.ChrotiumInterface.saveBlobDownload('$urlStr', 'download_${System.currentTimeMillis()}', '');
                                        }
                                        return;
                                    }
                                    var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs['$urlStr'];
                                    if (cachedBlob) {
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                window.ChrotiumInterface.saveBlobDownload(reader.result, 'download_${System.currentTimeMillis()}', cachedBlob.type || 'application/octet-stream');
                                            }
                                        };
                                        reader.readAsDataURL(cachedBlob);
                                        return;
                                    }
                                    fetch('$urlStr')
                                        .then(function(res) { return res.blob(); })
                                        .then(function(blob) {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                    window.ChrotiumInterface.saveBlobDownload(reader.result, 'download_${System.currentTimeMillis()}', blob.type || 'application/octet-stream');
                                                }
                                            };
                                            reader.readAsDataURL(blob);
                                        })
                                        .catch(function(err) {
                                            var xhr = new XMLHttpRequest();
                                            xhr.open('GET', '$urlStr', true);
                                            xhr.responseType = 'blob';
                                            xhr.onload = function() {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                        window.ChrotiumInterface.saveBlobDownload(reader.result, 'download_${System.currentTimeMillis()}', xhr.response.type || 'application/octet-stream');
                                                    }
                                                };
                                                reader.readAsDataURL(xhr.response);
                                            };
                                            xhr.send();
                                        });
                                } catch(e) {}
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                        return true
                    }

                    // Handle external intents (mailto, tel, intent:, etc.)
                    return try {
                        val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                        context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        try {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(fallbackIntent)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    val didCrash = detail?.didCrash() == true
                    android.util.Log.e(
                        "BrowserWebView",
                        "Renderer process gone (Multi-process isolated renderer). didCrash=$didCrash. Recovering renderer state to maintain responsive main UI."
                    )
                    // Gracefully recover the renderer process without taking down the main application UI
                    view?.post {
                        try {
                            view.clearCache(false)
                            val targetUrl = view.url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: tab.url
                            android.util.Log.i("BrowserWebView", "Reloading URL in new renderer process: $targetUrl")
                            view.loadUrl(targetUrl)
                        } catch (e: Exception) {
                            android.util.Log.e("BrowserWebView", "Failed to reload after render process gone", e)
                        }
                    }
                    return true // Return true to indicate we handled the crash and prevent host app termination
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onProgressChanged(newProgress)
                    if (newProgress >= 100) {
                        view?.post {
                            (view.parent as? SwipeRefreshLayout)?.isRefreshing = false
                        }
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                    if (request == null) return
                    val neededPermissions = mutableListOf<String>()
                    var hasDangerous = false
                    
                    for (res in request.resources) {
                        if (res == android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            neededPermissions.add(android.Manifest.permission.CAMERA)
                            hasDangerous = true
                        } else if (res == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            neededPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                            hasDangerous = true
                        }
                    }
                    
                    if (hasDangerous) {
                        pendingPermissionRequest = request
                        permissionLauncher.launch(neededPermissions.toTypedArray())
                    } else {
                        try {
                            request.grant(request.resources)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: android.webkit.GeolocationPermissions.Callback?
                ) {
                    if (origin == null || callback == null) return
                    pendingGeolocationCallback = Pair(origin, callback)
                    locationLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                override fun onGeolocationPermissionsHidePrompt() {
                    super.onGeolocationPermissionsHidePrompt()
                    pendingGeolocationCallback = null
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallbackParam: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = filePathCallbackParam

                    val mimeTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }
                    val mime = if (!mimeTypes.isNullOrEmpty()) mimeTypes[0] else "*/*"
                    return try {
                        fileChooserLauncher.launch(mime)
                        true
                    } catch (e: Exception) {
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                        false
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    if (resultMsg == null || view == null) return false
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                    val tempWebView = BackgroundPlayWebView(view.context).apply {
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        com.example.engine.WebConfig.configureWebSettings(settings, isDarkTheme)
                        settings.userAgentString = view.settings.userAgentString
                        com.example.engine.CookieHelper.configureWebViewCookies(this, true)

                        // Renderer Priority Policy (Memastikan proses render WebView diprioritaskan oleh OS)
                        try {
                            setRendererPriorityPolicy(
                                android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT,
                                false
                            )
                        } catch (_: Exception) {}

                        // Set Downloader Listener untuk popup webview
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                            if (url.startsWith("blob:")) {
                                val guessedName = if (!contentDisposition.isNullOrBlank()) {
                                    android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                                } else {
                                    "download_${System.currentTimeMillis()}"
                                }
                                val js = """
                                    (function() {
                                        var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs['$url'];
                                        if (cachedBlob) {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                    window.ChrotiumInterface.saveBlobDownload(reader.result, '$guessedName', cachedBlob.type || '${mimetype ?: "application/octet-stream"}');
                                                }
                                            };
                                            reader.readAsDataURL(cachedBlob);
                                            return;
                                        }
                                        fetch('$url')
                                            .then(function(res) { return res.blob(); })
                                            .then(function(blob) {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                        window.ChrotiumInterface.saveBlobDownload(reader.result, '$guessedName', '${mimetype ?: "application/octet-stream"}');
                                                    }
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                console.error('[Chrotium] WebView DownloadListener blob fetch error:', err);
                                            });
                                    })();
                                """.trimIndent()
                                evaluateJavascript(js, null)
                            } else {
                                onDownloadRequested(url, userAgent, contentDisposition, mimetype)
                            }
                            popupWebView = null
                            destroy()
                        }

                        // Hubungkan antarmuka JavaScript internal untuk mendeteksi unduhan di popup
                        addJavascriptInterface(
                            ChrotiumInterface(
                                webView = this,
                                onDownload = { downloadUrl, customFilename ->
                                    if (downloadUrl.startsWith("blob:")) {
                                        val js = """
                                            (function() {
                                                var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs['$downloadUrl'];
                                                if (cachedBlob) {
                                                    var reader = new FileReader();
                                                    reader.onloadend = function() {
                                                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                            window.ChrotiumInterface.saveBlobDownload(reader.result, '${customFilename ?: "download"}', cachedBlob.type || 'application/octet-stream');
                                                        }
                                                    };
                                                    reader.readAsDataURL(cachedBlob);
                                                    return;
                                                }
                                                fetch('$downloadUrl')
                                                    .then(function(res) { return res.blob(); })
                                                    .then(function(blob) {
                                                        var reader = new FileReader();
                                                        reader.onloadend = function() {
                                                            if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                                                                window.ChrotiumInterface.saveBlobDownload(reader.result, '${customFilename ?: "download"}', '${if (downloadUrl.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream"}');
                                                            }
                                                        };
                                                        reader.readAsDataURL(blob);
                                                    })
                                                    .catch(function(err) {
                                                        console.error('[Chrotium] Blob fetch error:', err);
                                                    });
                                            })();
                                        """.trimIndent()
                                        evaluateJavascript(js, null)
                                    } else {
                                        val contentDisposition = if (!customFilename.isNullOrBlank()) "attachment; filename=\"$customFilename\"" else null
                                        val mimeType = if (downloadUrl.endsWith(".apk", ignoreCase = true)) "application/vnd.android.package-archive" else null
                                        onDownloadRequested(downloadUrl, settings.userAgentString, contentDisposition, mimeType)
                                    }
                                    popupWebView = null
                                    destroy()
                                },
                                onBlobDownload = { base64, filename, mime ->
                                    onBlobDownloadRequested(base64, filename, mime)
                                    popupWebView = null
                                    destroy()
                                },
                                onUrlChange = { newUrl ->
                                    onUrlUpdated(newUrl, this.title)
                                },
                                onMediaStatusChanged = onMediaStatusChanged
                            ),
                            "ChrotiumInterface"
                        )

                        // Multi-Process Renderer Process Client for popup WebView
                        try {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
                                WebViewCompat.setWebViewRenderProcessClient(
                                    this,
                                    object : AndroidXWebViewRenderProcessClient() {
                                        override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                                            android.util.Log.w("BrowserWebView", "Popup WebView renderer unresponsive. Terminating renderer to keep UI fluid.")
                                            renderer?.terminate()
                                        }
                                        override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {}
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("BrowserWebView", "Failed to set WebViewRenderProcessClient on popup", e)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString()
                                if (!targetUrl.isNullOrBlank()) {
                                    if (com.example.engine.WebConfig.isGoogleAuthUrl(targetUrl)) {
                                        v?.settings?.userAgentString = com.example.engine.WebConfig.getGoogleAuthUserAgent(tab.isDesktopMode)
                                    }
                                }
                                return false
                            }

                            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(v, url, favicon)
                                if (!url.isNullOrBlank()) {
                                    if (com.example.engine.WebConfig.isGoogleAuthUrl(url)) {
                                        v?.settings?.userAgentString = com.example.engine.WebConfig.getGoogleAuthUserAgent(tab.isDesktopMode)
                                    }
                                }
                            }

                            override fun onPageFinished(v: WebView?, url: String?) {
                                super.onPageFinished(v, url)
                                v?.evaluateJavascript(com.example.engine.WebConfig.DOWNLOAD_LINK_INTERCEPTOR_SCRIPT, null)
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                android.util.Log.e("BrowserWebView", "Popup WebView renderer gone. didCrash=${detail?.didCrash()}")
                                popupWebView = null
                                view?.destroy()
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView?) {
                                super.onCloseWindow(window)
                                popupWebView = null
                                window?.destroy()
                            }
                        }
                    }
                    transport.webView = tempWebView
                    resultMsg.sendToTarget()
                    popupWebView = tempWebView
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    super.onCloseWindow(window)
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (result == null) return false
                    jsAlertData = JsAlertData(message ?: "", result)
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (result == null) return false
                    jsConfirmData = JsConfirmData(message ?: "", result)
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    if (result == null) return false
                    promptInputText = defaultValue ?: ""
                    jsPromptData = JsPromptData(message ?: "", defaultValue ?: "", result)
                    return true
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    // Prevent blocking main thread with default video poster decoding
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }

                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    if (view != null) {
                        // Explicitly enable hardware acceleration for fullscreen video playback view
                        view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        customView = view
                        customViewCallback = callback
                        onFullScreenChanged(true)
                        
                        val activity = context as? android.app.Activity
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        val window = activity?.window
                        window?.let { w ->
                            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).let { controller ->
                                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }
                    }
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    customView = null
                    customViewCallback = null
                    onFullScreenChanged(false)
                    
                    val activity = context as? android.app.Activity
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = activity?.window
                    window?.let { w ->
                        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).let { controller ->
                            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
            }

            onWebViewCreated(this)
        }
    }

    // Report WebView instance to parent when visible and register with bridge
    LaunchedEffect(isVisible, webView) {
        if (isVisible) {
            onWebViewCreated(webView)
        }
        tampermonkeyBridge.registerWebView(tab.id, webView)
    }

    // Handle App Backgrounding (Clear History & GC)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                System.gc()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tampermonkeyBridge.unregisterWebView(tab.id)
        }
    }

    // Dynamic dark theme listener for real-time algorithmic darkening & color scheme override
    LaunchedEffect(isDarkTheme) {
        val defaultBg = if (isDarkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
        webView.setBackgroundColor(defaultBg)
        com.example.engine.WebConfig.configureWebSettings(webView.settings, isDarkTheme)
        injectColorSchemeScript(webView, isDarkTheme)
    }

    // Update User Agent if Desktop Mode changes
    LaunchedEffect(tab.isDesktopMode) {
        webView.settings.userAgentString = com.example.engine.WebConfig.getCustomUserAgent(tab.url, tab.isDesktopMode)
        if (tab.url != "about:blank" && tab.url.isNotBlank()) {
            webView.reload()
        }
    }

    // Navigate to new URL when tab URL changes
    LaunchedEffect(tab.url) {
        if (tab.url.isNotBlank() && tab.url != "about:blank" && tab.url != webView.url) {
            webView.loadUrl(tab.url)
        }
    }

    // Handle isVisible resume (we don't pause on hidden to allow background audio/video playback)
    LaunchedEffect(isVisible) {
        if (isVisible) {
            webView.onResume()
            webView.resumeTimers()
        }
        // Removed else { webView.onPause(); webView.pauseTimers() } to allow background tab playback
    }

    // Handle Lifecycle onPause/onResume for the WebView
    DisposableEffect(tab.id) {
        webView.onResume()
        webView.resumeTimers()
        onDispose {
            try {
                if (customView != null) {
                    onFullScreenChanged(false)
                    val activity = context as? android.app.Activity
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                webView.stopLoading()
                webView.onPause()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                val parent = webView.parent as? ViewGroup
                parent?.removeView(webView)
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()

    val swipeRefreshLayout = remember(tab.id) {
        RobustSwipeRefreshLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setColorSchemeColors(primaryColor)
            setProgressBackgroundColorSchemeColor(backgroundColor)
            
            val density = context.resources.displayMetrics.density
            setDistanceToTriggerSync((160 * density).toInt())
            setSlingshotDistance((190 * density).toInt())
            
            addView(webView)
            
            setOnRefreshListener {
                webView.reload()
            }

            // Mencegah tarik-turun (refresh) jika sedang melakukan scroll di dalam halaman web
            setOnChildScrollUpCallback { _, _ ->
                webView.canScrollVertically(-1) || webView.scrollY > 0
            }
        }
    }

    LaunchedEffect(tab.url, tab.isPullToRefreshEnabled) {
        val isHome = tab.url == "about:blank" || tab.url.isBlank()
        val isSPA = tab.url.contains("aistudio.google.com") || tab.url.contains("youtube.com")
        swipeRefreshLayout.isEnabled = tab.isPullToRefreshEnabled && !isHome && !isSPA
    }

    // Tab Memory & CPU Management: Freeze background tabs when not playing media, unfreeze on focus
    LaunchedEffect(isVisible, webView.isMediaPlaying, webView.isBackgroundPlayEnabled) {
        if (isVisible) {
            webView.onResume()
        } else {
            if (!webView.isMediaPlaying && !webView.isBackgroundPlayEnabled) {
                webView.onPause()
            }
        }
    }

    // Lifecycle Observer for Background App States
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    // Sesuai instruksi: Jangan panggil webView.onPause() saat app masuk background agar playback media terus berjalan
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (isVisible) {
                        webView.onResume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Proper Resource Cleanup on Tab Closed
    DisposableEffect(tab.id) {
        onDispose {
            try {
                swipeRefreshLayout.removeView(webView)
                webView.apply {
                    stopLoading()
                    clearHistory()
                    removeAllViews()
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = android.webkit.WebViewClient()
                    destroy()
                }
            } catch (_: Exception) {}
        }
    }

    // Handle system back navigation to navigate back in web history
    BackHandler(enabled = webView.canGoBack() && customView == null) {
        webView.goBack()
    }

    if (customView != null) {
        BackHandler {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            onFullScreenChanged(false)
            
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).let { controller ->
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { swipeRefreshLayout },
            update = { view ->
                view.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )

        if (customView != null) {
            AndroidView(
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (customView?.parent as? ViewGroup)?.removeView(customView)
                        addView(customView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // JavaScript Alert Dialog
        jsAlertData?.let { alert ->
            AlertDialog(
                onDismissRequest = {
                    alert.result.confirm()
                    jsAlertData = null
                },
                title = { Text("Pemberitahuan Situs Web") },
                text = { Text(alert.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            alert.result.confirm()
                            jsAlertData = null
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        // JavaScript Confirm Dialog
        jsConfirmData?.let { confirm ->
            AlertDialog(
                onDismissRequest = {
                    confirm.result.cancel()
                    jsConfirmData = null
                },
                title = { Text("Konfirmasi Situs Web") },
                text = { Text(confirm.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            confirm.result.confirm()
                            jsConfirmData = null
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            confirm.result.cancel()
                            jsConfirmData = null
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        // JavaScript Prompt Dialog
        jsPromptData?.let { prompt ->
            AlertDialog(
                onDismissRequest = {
                    prompt.result.cancel()
                    jsPromptData = null
                },
                title = { Text("Permintaan Input Situs Web") },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Text(prompt.message)
                        OutlinedTextField(
                            value = promptInputText,
                            onValueChange = { promptInputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prompt.result.confirm(promptInputText)
                            jsPromptData = null
                        }
                    ) {
                        Text("Kirim")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.result.cancel()
                            jsPromptData = null
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        // Popup WebView Dialog
        popupWebView?.let { popupView ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {
                    popupWebView = null
                    popupView.destroy()
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = true
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Header Bar untuk Popup Window
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = popupView.title?.ifBlank { popupView.url } ?: "Jendela Popup",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                val popupUrl = popupView.url
                                popupWebView = null
                                popupView.destroy()
                                if (!popupUrl.isNullOrBlank() && popupUrl != "about:blank") {
                                    onNewTabRequested(popupUrl)
                                }
                            }
                        ) {
                            Text("Buka di Tab Baru", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                popupWebView = null
                                popupView.destroy()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup Popup",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AndroidView(
                            factory = { 
                                (popupView.parent as? ViewGroup)?.removeView(popupView)
                                popupView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun injectColorSchemeScript(wv: WebView, isDark: Boolean) {
    val js = """
        (function() {
            try {
                var isDark = $isDark;
                if (!window.__colorSchemeOverrideActive) {
                    window.__colorSchemeOverrideActive = true;
                    var origMatchMedia = window.matchMedia;
                    window.matchMedia = function(q) {
                        if (q && q.indexOf('prefers-color-scheme') !== -1) {
                            var matches = isDark ? (q.indexOf('dark') !== -1) : (q.indexOf('light') !== -1);
                            return {
                                matches: matches,
                                media: q,
                                onchange: null,
                                addListener: function() {},
                                removeListener: function() {},
                                addEventListener: function() {},
                                removeEventListener: function() {},
                                dispatchEvent: function() { return false; }
                            };
                        }
                        return origMatchMedia ? origMatchMedia.call(window, q) : { matches: false, media: q };
                    };
                }
                if (document.documentElement) {
                    var themeValue = isDark ? "dark" : "light";
                    var meta = document.querySelector('meta[name="color-scheme"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        if (document.head) {
                            document.head.appendChild(meta);
                        }
                    }
                    if (meta) {
                        meta.content = themeValue;
                    }

                    if (isDark) {
                        if (location.hostname.indexOf('youtube.com') !== -1) {
                            document.documentElement.setAttribute('dark', 'true');
                        }
                    } else {
                        if (location.hostname.indexOf('youtube.com') !== -1) {
                            document.documentElement.removeAttribute('dark');
                            document.documentElement.setAttribute('dark', 'false');
                        }
                    }
                }
            } catch(e) {}
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

private fun injectPageEnhancements(
    wv: WebView,
    safeUrl: String,
    isAuth: Boolean,
    isDarkTheme: Boolean,
    isH264ifyEnabled: Boolean,
    isDesktopMode: Boolean,
    adBlockEngine: com.example.engine.AdBlockEngine
) {
    val normalizedUrl = safeUrl.lowercase()
    if (wv.tag as? String == normalizedUrl) return

    // Disable smooth scrolling everywhere & activate GPU subpixel rendering
    wv.evaluateJavascript(com.example.engine.WebConfig.DISABLE_SMOOTH_SCROLLING_SCRIPT, null)
    wv.evaluateJavascript(com.example.engine.WebConfig.GPU_RENDER_ACCELERATION_SCRIPT, null)

    if (!isAuth) {
        injectColorSchemeScript(wv, isDarkTheme)
        adBlockEngine.injectCosmeticAdBlocking(wv, safeUrl)
        wv.evaluateJavascript(com.example.engine.WebConfig.BACKGROUND_PLAY_SCRIPT, null)
        wv.evaluateJavascript(com.example.engine.WebConfig.ANGULAR_SPA_OPTIMIZATION_SCRIPT, null)
        wv.evaluateJavascript(com.example.engine.WebConfig.GLOBAL_VIDEO_PERFORMANCE_SCRIPT, null)
        wv.evaluateJavascript(com.example.engine.WebConfig.DOWNLOAD_LINK_INTERCEPTOR_SCRIPT, null)

        if (shouldInjectHeavyPageOptimizations(normalizedUrl)) {
            wv.evaluateJavascript(com.example.engine.WebConfig.INPUT_SCROLL_FOCUS_SCRIPT, null)
        }

        if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be")) {
            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_PERFORMANCE_SCRIPT, null)
            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_AD_BYPASS_SCRIPT, null)
            if (isH264ifyEnabled) {
                wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_H264IFY_SCRIPT, null)
            }
        }
        if (normalizedUrl.contains("ai.studio") || normalizedUrl.contains("aistudio.google.com")) {
            wv.evaluateJavascript(com.example.engine.WebConfig.AI_STUDIO_OPTIMIZATION_SCRIPT, null)
        }
        if (isDesktopMode) {
            wv.evaluateJavascript(com.example.engine.WebConfig.DESKTOP_VIEWPORT_SCRIPT, null)
        } else {
            wv.evaluateJavascript(com.example.engine.WebConfig.MOBILE_VIEWPORT_SCRIPT, null)
        }

        wv.tag = normalizedUrl
    }
}
