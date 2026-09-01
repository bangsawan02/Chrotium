package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.DevToolsEngine
import com.example.engine.WebConfig
import kotlinx.coroutines.launch

enum class DevToolsTab(val label: String) {
    GOOGLE_CDP("🌐 Google DevTools"),
    JS_TERMINAL("💻 JS Console")
}

data class ConsoleLogItem(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val type: String, // "input", "output", "error", "info"
    val content: String,
    val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
)

/**
 * Panel DevTools Terintegrasi untuk Layar Seluler (Always Mobile Screen).
 * Menyediakan 2 mode:
 * 1. Official Google DevTools Frontend (via Local CDP WebSocket Bridge) dengan Mobile Viewport Optimization.
 * 2. Interactive JavaScript Terminal & Snippets Runner.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevToolsPanel(
    isOpen: Boolean,
    isDarkTheme: Boolean,
    onClose: () -> Unit,
    targetWebView: WebView? = null,
    onReloadTarget: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DevToolsTab.GOOGLE_CDP) }

    // CDP Remote State
    var devToolsWebView by remember { mutableStateOf<WebView?>(null) }
    var port by remember { mutableStateOf(9222) }
    var activePageId by remember { mutableStateOf<String?>(null) }
    var currentHash by remember { mutableStateOf(DevToolsEngine.selectedCommitHash) }
    var showVersionMenu by remember { mutableStateOf(false) }
    var cdpStatusText by remember { mutableStateOf("Menghubungkan...") }

    // Terminal State
    var commandInput by remember { mutableStateOf("") }
    val consoleLogs = remember { mutableStateListOf<ConsoleLogItem>() }
    var isPickerActive by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()

    // Evaluasi JavaScript di Target WebView
    fun executeJsOnTarget(script: String, onResult: ((String) -> Unit)? = null) {
        val wv = targetWebView
        if (wv != null) {
            wv.post {
                try {
                    wv.evaluateJavascript(script) { res ->
                        val cleaned = if (res == null || res == "null") "undefined" else res
                        onResult?.invoke(cleaned)
                    }
                } catch (e: Exception) {
                    // WebView may have been destroyed between post() and evaluateJavascript()
                    android.util.Log.w("DevToolsPanel", "Failed to execute JS on target: ${e.message}")
                    onResult?.invoke("Error: ${e.message}")
                }
            }
        }
    }

    // Eksekusi baris perintah konsol
    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        consoleLogs.add(ConsoleLogItem(type = "input", content = trimmed))
        commandInput = ""

        executeJsOnTarget(trimmed) { result ->
            consoleLogs.add(ConsoleLogItem(type = "output", content = result))
            scope.launch {
                if (consoleLogs.isNotEmpty()) {
                    logListState.animateScrollToItem(consoleLogs.size - 1)
                }
            }
        }
    }

    val reloadInspectorUrl: () -> Unit = {
        scope.launch {
            cdpStatusText = "Mencari target..."
            var target = DevToolsEngine.fetchActivePageTarget(port)
            var attempts = 0
            while (target == null && attempts < 6) {
                kotlinx.coroutines.delay(350)
                target = DevToolsEngine.fetchActivePageTarget(port)
                attempts++
            }
            activePageId = target?.id
            var url = target?.devtoolsFrontendUrl ?: DevToolsEngine.getDevToolsUrl(
                port = port,
                pageId = activePageId,
                commitHash = currentHash
            )

            // If the URL uses internal devtools:// scheme or relative path, fallback to CDN
            if (url.startsWith("devtools://") || url.startsWith("/")) {
                url = "https://unpkg.com/chrome-devtools-frontend@1.0.672485/front_end/inspector.html?ws=127.0.0.1:$port/devtools/page/$activePageId"
            } else if (url.contains("serve_file")) {
                url = url.replace("serve_file", "serve_rev")
            }

            if (!url.contains("ws=127.0.0.1:$port")) {
                url = url.replace(Regex("ws=[^&]+"), "ws=127.0.0.1:$port")
            }

            cdpStatusText = "Port: $port | Target: ${target?.title?.take(18) ?: "Page"}"

            var wv = devToolsWebView
            var waitAttempts = 0
            while (wv == null && waitAttempts < 15) {
                kotlinx.coroutines.delay(200)
                wv = devToolsWebView
                waitAttempts++
            }
            wv?.post {
                Log.d("DevToolsPanel", "Loading Mobile Inspector URL: $url")
                wv.loadUrl(url)
            }
        }
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            port = DevToolsEngine.startCdpBridgeServer()
            reloadInspectorUrl()
            if (consoleLogs.isEmpty()) {
                consoleLogs.add(
                    ConsoleLogItem(
                        type = "info",
                        content = "Chrotium DevTools Ready (Always Mobile Screen Mode Active)"
                    )
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 680.dp else 400.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab Selector Mode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DevToolsTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Mobile Screen Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Mobile Screen",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Mobile View",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Action Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Inspect Element Picker Toggle
                        IconButton(
                            onClick = {
                                executeJsOnTarget(DevToolsEngine.ELEMENT_PICKER_SCRIPT) { res ->
                                    isPickerActive = res.contains("ACTIVATED")
                                    val msg = if (isPickerActive) "Element Picker Aktif (Ketuk elemen di web)" else "Element Picker Nonaktif"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("devtools_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = "Inspect Element",
                                tint = if (isPickerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reload Inspector / Target
                        IconButton(
                            onClick = {
                                reloadInspectorUrl()
                                onReloadTarget()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Expand / Collapse
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close DevTools",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Sub-header for Google CDP version picker if active
                if (selectedTab == DevToolsTab.GOOGLE_CDP) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cdpStatusText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showVersionMenu = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Versi Frontend",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Pilih Versi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showVersionMenu,
                                onDismissRequest = { showVersionMenu = false }
                            ) {
                                DevToolsEngine.STABLE_CHROMIUM_HASHES.forEach { (hash, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (currentHash == hash) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "@${hash.take(10)}...",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            showVersionMenu = false
                                            currentHash = hash
                                            DevToolsEngine.selectedCommitHash = currentHash
                                            reloadInspectorUrl()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Panel Body based on Selected Mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedTab) {
                        DevToolsTab.GOOGLE_CDP -> {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        setBackgroundColor(android.graphics.Color.parseColor("#18181b"))

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            // Enforce Mobile User Agent for DevTools UI
                                            userAgentString = WebConfig.getMobileUserAgent()
                                        }

                                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                        webViewClient = object : WebViewClient() {
                                            override fun onReceivedSslError(
                                                view: WebView?,
                                                handler: android.webkit.SslErrorHandler?,
                                                error: android.net.http.SslError?
                                            ) {
                                                handler?.proceed()
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                // Inject mobile touch CSS for optimal screen layout
                                                view?.evaluateJavascript(DevToolsEngine.DEVTOOLS_FRONTEND_MOBILE_CSS, null)
                                                Log.d("DevToolsPanel", "Finished loading URL: $url")
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                Log.d("DevToolsConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                                                return true
                                            }
                                        }

                                        val devToolsUrl = DevToolsEngine.getDevToolsUrl(
                                            port = port,
                                            pageId = activePageId,
                                            commitHash = currentHash
                                        )
                                        loadUrl(devToolsUrl)
                                        devToolsWebView = this
                                    }
                                },
                                update = { wv ->
                                    devToolsWebView = wv
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        DevToolsTab.JS_TERMINAL -> {
                            // Interactive Terminal & JavaScript Execution Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                // Console Output Stream
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(consoleLogs, key = { it.id }) { log ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = if (log.type == "input") ">" else "<",
                                                    color = if (log.type == "input") Color(0xFF38BDF8) else Color(0xFF4ADE80),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = log.content,
                                                    color = when (log.type) {
                                                        "input" -> Color(0xFFF1F5F9)
                                                        "error" -> Color(0xFFF87171)
                                                        "info" -> Color(0xFFFBBF24)
                                                        else -> Color(0xFF86EFAC)
                                                    },
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp
                                                )
                                            }

                                            Text(
                                                text = log.timestamp,
                                                color = Color(0xFF64748B),
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Mobile Quick Action Buttons Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Highlight Box Model
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .clickable {
                                                runCommand("""
                                                    (function(){
                                                        var st = document.getElementById('__outline_dbg');
                                                        if(st){ st.remove(); return 'Outline dinonaktifkan'; }
                                                        var n = document.createElement('style');
                                                        n.id = '__outline_dbg';
                                                        n.textContent = '* { outline: 1px solid rgba(255,0,0,0.3) !important; }';
                                                        document.head.appendChild(n);
                                                        return 'Outline diaktifkan';
                                                    })()
                                                """.trimIndent())
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔍 Highlight Box",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    // Clear Storage
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .clickable {
                                                runCommand("""
                                                    (function(){
                                                        try { localStorage.clear(); sessionStorage.clear(); } catch(e){}
                                                        document.cookie.split(";").forEach(function(c) {
                                                            document.cookie = c.replace(/^ +/, "").replace(/=.*/, "=;expires=" + new Date().toUTCString() + ";path=/");
                                                        });
                                                        return 'Storage & Cookies dibersihkan';
                                                    })()
                                                """.trimIndent())
                                                Toast.makeText(context, "Storage dibersihkan", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🧹 Clear Storage",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    // Screen Metrics
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .clickable {
                                                runCommand("'Screen: ' + window.innerWidth + 'x' + window.innerHeight + ' (DPR: ' + window.devicePixelRatio + ')'")
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "📏 Screen Size",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                // Quick Code Snippet Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "document.title",
                                        "location.href",
                                        "navigator.userAgent",
                                        "document.cookie",
                                        "localStorage.length",
                                        "document.querySelectorAll('img').length"
                                    ).forEach { snippet ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { runCommand(snippet) }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = snippet,
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Clear Terminal
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .clickable { consoleLogs.clear() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Clear Logs",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Interactive JS Evaluation Bar (Available across all tabs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "JS >",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { runCommand(commandInput) })
                    )

                    IconButton(
                        onClick = { runCommand(commandInput) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(isOpen) {
        onDispose {
            if (!isOpen) {
                DevToolsEngine.stopCdpBridgeServer()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            devToolsWebView?.destroy()
            devToolsWebView = null
            DevToolsEngine.stopCdpBridgeServer()
        }
    }
}

@Composable
private fun QuickActionChip(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
