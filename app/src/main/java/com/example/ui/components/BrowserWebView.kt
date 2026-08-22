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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.example.engine.BatterySaverEngine
import com.example.engine.TampermonkeyBridge
import com.example.engine.UserScriptEngine
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp

private data class JsAlertData(val message: String, val result: JsResult)
private data class JsConfirmData(val message: String, val result: JsResult)
private data class JsPromptData(val message: String, val defaultValue: String, val result: JsPromptResult)

class BackgroundPlayWebView(context: Context) : WebView(context) {
    var isBackgroundPlayEnabled: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
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
    batteryEngine: BatterySaverEngine,
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
    onFullScreenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val batteryStatus by batteryEngine.status.collectAsStateWithLifecycle()

    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    var pendingPermissionRequest by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    var pendingGeolocationCallback by remember { mutableStateOf<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var jsAlertData by remember { mutableStateOf<JsAlertData?>(null) }
    var jsConfirmData by remember { mutableStateOf<JsConfirmData?>(null) }
    var jsPromptData by remember { mutableStateOf<JsPromptData?>(null) }
    var promptInputText by remember { mutableStateOf("") }

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
            val defaultBg = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            setBackgroundColor(defaultBg)

            // Configure WebSettings for battery & performance
            isNestedScrollingEnabled = false
            isFocusable = true
            isFocusableInTouchMode = true
            batteryEngine.configureWebSettings(settings, isDarkTheme)
            @Suppress("DEPRECATION")
            settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            if (tab.isDesktopMode) {
                settings.userAgentString = com.example.engine.WebConfig.getDesktopUserAgent()
            } else {
                settings.userAgentString = com.example.engine.WebConfig.getMobileUserAgent()
            }
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Enable full Cookie & 3rd Party Cookie Support
            com.example.engine.CookieHelper.configureWebViewCookies(this, true)
            // Enable WebContents Debugging for DevTools Remote CDP Inspector
            com.example.engine.DevToolsEngine.enableWebViewDebugging(this)

            // Set Downloader Listener
            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                if (url.startsWith("blob:")) {
                    val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    val js = """
                        (function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$url', true);
                            xhr.responseType = 'blob';
                            xhr.onload = function(e) {
                                if (this.status == 200) {
                                    var blob = this.response;
                                    var reader = new FileReader();
                                    reader.readAsDataURL(blob);
                                    reader.onloadend = function() {
                                        var base64data = reader.result;
                                        window.${TampermonkeyBridge.INTERFACE_NAME}.processBlobDownload(base64data, '${mimetype ?: ""}', '$guessedName');
                                    }
                                }
                            };
                            xhr.send();
                        })();
                    """.trimIndent()
                    evaluateJavascript(js, null)
                } else {
                    onDownloadRequested(url, userAgent, contentDisposition, mimetype)
                }
            }

            // Inject Tampermonkey JS Bridge
            addJavascriptInterface(tampermonkeyBridge, TampermonkeyBridge.INTERFACE_NAME)

            var lastKnownUrl = tab.url

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                    if (request != null) {
                        if (adBlockEngine.shouldBlockRequest(request, lastKnownUrl)) {
                            onAdBlocked()
                            return adBlockEngine.createEmptyResourceResponse()
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val safeUrl = url ?: return
                    lastKnownUrl = safeUrl
                    onPageStarted(safeUrl)

                    view?.let { wv ->
                        injectColorSchemeScript(wv, isDarkTheme)
                        adBlockEngine.injectCosmeticAdBlocking(wv, safeUrl)
                        wv.evaluateJavascript(com.example.engine.WebConfig.BACKGROUND_PLAY_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.ANGULAR_SPA_OPTIMIZATION_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.GLOBAL_VIDEO_PERFORMANCE_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.INPUT_SCROLL_FOCUS_SCRIPT, null)
                        if (safeUrl.contains("youtube.com") || safeUrl.contains("youtu.be")) {
                            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_PERFORMANCE_SCRIPT, null)
                            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_AD_BYPASS_SCRIPT, null)
                            if (tab.isH264ifyEnabled) {
                                wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_H264IFY_SCRIPT, null)
                            }
                        }
                        if (tab.isDesktopMode) {
                            wv.evaluateJavascript(com.example.engine.WebConfig.DESKTOP_VIEWPORT_SCRIPT, null)
                        } else {
                            wv.evaluateJavascript(com.example.engine.WebConfig.MOBILE_VIEWPORT_SCRIPT, null)
                        }
                    }

                    // Inject document-start scripts
                    view?.let { wv ->
                        val executed = UserScriptEngine.injectScriptsForStage(
                            webView = wv,
                            url = safeUrl,
                            stage = "document-start",
                            allScripts = scripts,
                            onScriptInjected = onScriptInjected
                        )
                        if (executed.isNotEmpty()) {
                            onScriptsExecuted(executed)
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val safeUrl = url ?: return
                    
                    view?.post {
                        (view.parent as? SwipeRefreshLayout)?.isRefreshing = false
                    }

                    onPageFinished(safeUrl, view?.title)
                    onNavigationStateChanged(view?.canGoBack() == true, view?.canGoForward() == true)

                    view?.let { wv ->
                        injectColorSchemeScript(wv, isDarkTheme)
                        // Apply cosmetic ad-hiding CSS
                        adBlockEngine.injectCosmeticAdBlocking(wv, safeUrl)
                        wv.evaluateJavascript(com.example.engine.WebConfig.BACKGROUND_PLAY_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.ANGULAR_SPA_OPTIMIZATION_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.GLOBAL_VIDEO_PERFORMANCE_SCRIPT, null)
                        wv.evaluateJavascript(com.example.engine.WebConfig.INPUT_SCROLL_FOCUS_SCRIPT, null)
                        if (safeUrl.contains("youtube.com") || safeUrl.contains("youtu.be")) {
                            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_PERFORMANCE_SCRIPT, null)
                            wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_AD_BYPASS_SCRIPT, null)
                            if (tab.isH264ifyEnabled) {
                                wv.evaluateJavascript(com.example.engine.WebConfig.YOUTUBE_H264IFY_SCRIPT, null)
                            }
                        }

                        if (tab.isDesktopMode) {
                            wv.evaluateJavascript(com.example.engine.WebConfig.DESKTOP_VIEWPORT_SCRIPT, null)
                        } else {
                            wv.evaluateJavascript(com.example.engine.WebConfig.MOBILE_VIEWPORT_SCRIPT, null)
                        }

                        // Inject document-end & document-idle scripts
                        val executedEnd = UserScriptEngine.injectScriptsForStage(
                            webView = wv,
                            url = safeUrl,
                            stage = "document-end",
                            allScripts = scripts,
                            onScriptInjected = onScriptInjected
                        )
                        val executedIdle = UserScriptEngine.injectScriptsForStage(
                            webView = wv,
                            url = safeUrl,
                            stage = "document-idle",
                            allScripts = scripts,
                            onScriptInjected = onScriptInjected
                        )
                        val totalExecuted = (executedEnd + executedIdle).distinct()
                        if (totalExecuted.isNotEmpty()) {
                            onScriptsExecuted(totalExecuted)
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
                    val scheme = uri.scheme ?: ""
                    if (scheme == "http" || scheme == "https" || scheme == "about") {
                        return false
                    }
                    // Handle external intents (mailto, tel, etc.)
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    android.util.Log.e("BrowserWebView", "Renderer process gone. Crashed: ${detail?.didCrash()}")
                    // Gracefully reload to recover the renderer process
                    view?.post {
                        try {
                            view.reload()
                        } catch (e: Exception) {
                            view.loadUrl(view.url ?: tab.url)
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

    LaunchedEffect(isVisible, webView) {
        if (isVisible) {
            onWebViewCreated(webView)
        }
    }

    // Handle App Backgrounding (Clear History & GC)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                webView.clearHistory()
                System.gc()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic dark theme listener for real-time algorithmic darkening & color scheme override
    LaunchedEffect(isDarkTheme) {
        val defaultBg = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        webView.setBackgroundColor(defaultBg)
        batteryEngine.configureWebSettings(webView.settings, isDarkTheme)
        injectColorSchemeScript(webView, isDarkTheme)
    }

    // Update User Agent if Desktop Mode changes
    LaunchedEffect(tab.isDesktopMode) {
        if (tab.isDesktopMode) {
            webView.settings.userAgentString = com.example.engine.WebConfig.getDesktopUserAgent()
        } else {
            webView.settings.userAgentString = com.example.engine.WebConfig.getMobileUserAgent()
        }
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
                webView.pauseTimers()
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

    LaunchedEffect(tab.url) {
        val isHome = tab.url == "about:blank" || tab.url.isBlank()
        swipeRefreshLayout.isEnabled = !isHome
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
