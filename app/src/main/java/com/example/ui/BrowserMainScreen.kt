package com.example.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.components.BrowserWebView
import com.example.ui.components.DevToolsPanel
import com.example.ui.components.HomeDashboard
import com.example.ui.components.Omnibox
import com.example.ui.components.SearchSuggestionsDropdown
import com.example.ui.components.TranslateBar
import com.example.ui.dialogs.AdBlockSheet
import com.example.ui.dialogs.BookmarksHistorySheet
import com.example.ui.dialogs.SettingsSheet
import com.example.ui.dialogs.TabsSheet
import com.example.ui.dialogs.TranslateSheet
import com.example.ui.dialogs.UserScriptManagerSheet
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel = viewModel(),
    modifier: Modifier = Modifier
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allScripts by viewModel.allScripts.collectAsStateWithLifecycle()
    val allBookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val scriptLogs by viewModel.scriptLogs.collectAsStateWithLifecycle()
    val adBlockStats by viewModel.adBlockStats.collectAsStateWithLifecycle()
    val translationBarState by viewModel.translationBarState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    val activeTab = uiState.activeTab
    val isHome = activeTab == null || activeTab.url == "about:blank" || activeTab.url.isBlank()
    val canGoBack = activeWebView?.canGoBack() == true

    val isDevToolsOpen = activeTab?.isDevToolsEnabled == true

    // Handle back button smoothly
    BackHandler(
        enabled = isDevToolsOpen || uiState.showSuggestions || uiState.activeSheet != ActiveSheet.NONE || canGoBack || !isHome
    ) {
        when {
            isDevToolsOpen -> {
                viewModel.toggleDevTools()
            }
            uiState.showSuggestions -> {
                viewModel.dismissSuggestions()
            }
            uiState.activeSheet != ActiveSheet.NONE -> {
                viewModel.hideSheet()
            }
            activeWebView?.canGoBack() == true -> {
                activeWebView?.goBack()
            }
            !isHome -> {
                viewModel.openUrl("about:blank")
            }
        }
    }

    // Ensure activeWebView is in sync with activeTab
    LaunchedEffect(uiState.activeTabId, isHome) {
        if (isHome) {
            activeWebView = null
        }
    }

    MyApplicationTheme(
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!uiState.isFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                    ) {
                        Omnibox(
                            urlText = uiState.omniboxText,
                            isLoading = activeTab?.isLoading ?: false,
                            progress = activeTab?.progress ?: 0,
                            activeScriptsCount = activeTab?.activeScriptsCount ?: 0,
                            blockedAdsCount = activeTab?.blockedRequestsCount ?: 0,
                            isAdBlockEnabled = adBlockStats.isEnabled,
                            onUrlSubmit = { viewModel.openUrl(it) },
                            onUrlChange = { viewModel.setOmniboxText(it) },
                            onRefresh = {
                                if (activeTab?.url == "about:blank") {
                                    // nothing
                                } else {
                                    activeWebView?.reload()
                                }
                            },
                            onScriptsClick = { viewModel.showSheet(ActiveSheet.SCRIPTS_MANAGER) },
                            onAdBlockClick = { viewModel.showSheet(ActiveSheet.ADBLOCK) },
                            onSecurityIconClick = {
                                if (activeTab?.url != "about:blank" && !activeTab?.url.isNullOrBlank()) {
                                    viewModel.showSheet(ActiveSheet.SITE_SETTINGS)
                                }
                            },
                            onFocusChange = { viewModel.setOmniboxFocused(it) }
                        )
                    }
                }
            },
            bottomBar = {
                val isImeOpen = WindowInsets.isImeVisible
                if (!uiState.isFullscreen && !uiState.isOmniboxFocused && !isImeOpen) {
                    BottomNavBar(
                        modifier = Modifier.navigationBarsPadding(),
                        canGoBack = activeTab?.canGoBack ?: false,
                        canGoForward = activeTab?.canGoForward ?: false,
                        tabCount = uiState.tabs.size,
                        activeScriptsCount = activeTab?.activeScriptsCount ?: 0,
                        onBack = { activeWebView?.goBack() },
                        onForward = { activeWebView?.goForward() },
                        onHome = { viewModel.openUrl("about:blank") },
                        onTabsClick = { viewModel.showSheet(ActiveSheet.TABS) },
                        onScriptsClick = { viewModel.showSheet(ActiveSheet.SCRIPTS_MANAGER) },
                        onMenuClick = { viewModel.showSheet(ActiveSheet.SETTINGS) }
                    )
                }
            }
        ) { innerPadding ->
        val density = LocalDensity.current
        val isImeOpen = WindowInsets.isImeVisible
        val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
        val imePaddingDp = remember(isImeOpen, imeTargetBottomPx) {
            if (isImeOpen && imeTargetBottomPx > 0) {
                with(density) { imeTargetBottomPx.toDp() }
            } else {
                androidx.compose.ui.unit.Dp.Unspecified
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (!uiState.isFullscreen) Modifier.padding(innerPadding) else Modifier)
                .then(
                    if (imePaddingDp != androidx.compose.ui.unit.Dp.Unspecified && imePaddingDp > androidx.compose.ui.unit.Dp.Hairline) {
                        Modifier.padding(bottom = imePaddingDp)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (activeTab == null || activeTab.url == "about:blank") {
                HomeDashboard(
                    scripts = allScripts,
                    bookmarks = allBookmarks,
                    history = allHistory,
                    shortcuts = uiState.shortcuts,
                    onAddShortcut = { title, url -> viewModel.addShortcut(title, url) },
                    onEditShortcut = { oldShortcut, newTitle, newUrl -> viewModel.editShortcut(oldShortcut, newTitle, newUrl) },
                    onDeleteShortcut = { viewModel.deleteShortcut(it) },
                    onNavigate = { viewModel.openUrl(it) },
                    onOpenScriptManager = { viewModel.showSheet(ActiveSheet.SCRIPTS_MANAGER) },
                    onOpenBookmarks = { viewModel.showSheet(ActiveSheet.BOOKMARKS_HISTORY) },
                    onOpenDownloads = { viewModel.openSystemDownloads(context) }
                )
            }
            
            uiState.tabs.forEach { tab ->
                val isVisible = (activeTab != null && activeTab.id == tab.id && activeTab.url != "about:blank")
                
                if (tab.url != "about:blank") {
                    androidx.compose.runtime.key(tab.id) {
                        BrowserWebView(
                            tab = tab,
                            isVisible = isVisible,
                            scripts = allScripts,
                            tampermonkeyBridge = viewModel.tampermonkeyBridge,
                            adBlockEngine = viewModel.adBlockEngine,
                            onPageStarted = { viewModel.onPageStarted(it, tab.id) },
                            onPageFinished = { url, title -> viewModel.onPageFinished(url, title, tab.id) },
                            onProgressChanged = { viewModel.onProgressChanged(it, tab.id) },
                            onNavigationStateChanged = { back, forward -> viewModel.onNavigationStateChanged(back, forward, tab.id) },
                            onScriptsExecuted = { viewModel.onScriptsExecuted(it, tab.id) },
                            onScriptInjected = { script -> viewModel.onScriptInjected(script.id) },
                            onUrlUpdated = { url, title -> viewModel.onUrlUpdated(url, title, tab.id) },
                            onAdBlocked = { viewModel.onAdBlocked(tab.id) },
                            onDownloadRequested = { url, ua, cd, mime -> viewModel.startDownload(url, ua, cd, mime) },
                            onBlobDownloadRequested = { base64, fn, mime -> viewModel.saveBlobData(base64, fn, mime) },
                            onFullScreenChanged = { viewModel.setFullscreen(it) },
                            onMediaStatusChanged = { isPlaying -> viewModel.updateTabMediaStatus(tab.id, isPlaying) },
                            onNewTabRequested = { url -> viewModel.newTab(url) },
                            onWebViewCreated = { wv -> 
                                if (isVisible) activeWebView = wv 
                            }
                        )
                    }
                }
            }

            // Built-in Chrome DevTools Panel (Dual-WebView Architecture)
            if (activeTab != null && activeTab.url != "about:blank") {
                DevToolsPanel(
                    isOpen = isDevToolsOpen,
                    targetWebView = activeWebView,
                    onClose = { viewModel.toggleDevTools() },
                    onReloadTarget = { activeWebView?.reload() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Quick In-Page Translation Floating Bar
            if (!isHome) {
                TranslateBar(
                    state = translationBarState,
                    onTranslate = { lang -> viewModel.translateCurrentPage(lang, activeWebView) },
                    onRestoreOriginal = { viewModel.restoreOriginalPage(activeWebView) },
                    onOpenLanguagePicker = { viewModel.showSheet(ActiveSheet.TRANSLATE) },
                    onClose = { viewModel.hideTranslateBar() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // Real-Time Search Suggestions Overlay
            if (uiState.showSuggestions && uiState.suggestions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.dismissSuggestions() }
                        )
                ) {
                    SearchSuggestionsDropdown(
                        visible = uiState.showSuggestions,
                        suggestions = uiState.suggestions,
                        onSuggestionClick = { viewModel.selectSuggestion(it) },
                        onSuggestionFill = { viewModel.fillSuggestionIntoOmnibox(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    // Modal Sheets
    when (uiState.activeSheet) {
        ActiveSheet.SCRIPTS_MANAGER -> {
            UserScriptManagerSheet(
                scripts = allScripts,
                logs = scriptLogs,
                currentUrl = activeTab?.url ?: "",
                onDismiss = { viewModel.hideSheet() },
                onToggleScript = { id, enabled -> viewModel.toggleScript(id, enabled) },
                onToggleAllScripts = { enabled -> viewModel.toggleAllScripts(enabled) },
                onSaveScript = { script -> viewModel.saveScript(script) },
                onDuplicateScript = { script -> viewModel.duplicateScript(script) },
                onDeleteScript = { id -> viewModel.deleteScript(id) },
                onResetDefaultPresets = { viewModel.resetDefaultPresets() },
                onClearLogs = { viewModel.tampermonkeyBridge.clearLogs() }
            )
        }
        ActiveSheet.TABS -> {
            TabsSheet(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                recentlyClosedTabs = uiState.recentlyClosedTabs,
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onCloseAll = { viewModel.closeAllTabs() },
                onNewTab = { viewModel.newTab() },
                onNewTabInBackground = { viewModel.newTab(openInBackground = true) },
                onDuplicateTab = { viewModel.duplicateTab(it) },
                onTogglePinTab = { viewModel.togglePinTab(it) },
                onMoveTab = { id, up -> viewModel.moveTab(id, up) },
                onCloseOtherTabs = { viewModel.closeOtherTabs(it) },
                onRestoreRecentlyClosedTab = { viewModel.restoreRecentlyClosedTab(it) },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.BOOKMARKS_HISTORY -> {
            BookmarksHistorySheet(
                bookmarks = allBookmarks,
                history = allHistory,
                onSelectUrl = {
                    viewModel.openUrl(it)
                    viewModel.hideSheet()
                },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                onClearHistory = { viewModel.clearHistory() },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.SETTINGS -> {
            SettingsSheet(
                isDesktopMode = activeTab?.isDesktopMode ?: false,
                isBookmarked = uiState.isCurrentUrlBookmarked,
                isDevToolsEnabled = activeTab?.isDevToolsEnabled ?: false,
                isH264ifyEnabled = activeTab?.isH264ifyEnabled ?: true,
                isPullToRefreshEnabled = activeTab?.isPullToRefreshEnabled ?: true,
                currentSearchEngine = uiState.searchEngine,
                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                onToggleBookmark = { viewModel.toggleBookmark() },
                onToggleDevTools = { viewModel.toggleDevTools() },
                onToggleH264ify = { viewModel.toggleH264ify() },
                onTogglePullToRefresh = { viewModel.togglePullToRefresh() },
                onOpenDownloads = { viewModel.openSystemDownloads(context) },
                onOpenAdBlock = { viewModel.showSheet(ActiveSheet.ADBLOCK) },
                onOpenTranslate = { viewModel.showSheet(ActiveSheet.TRANSLATE) },
                onPrintPdf = {
                    viewModel.printCurrentPage(context, activeWebView)
                },
                onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                onClearBrowsingData = { 
                    com.example.engine.DiskCacheManager.clearCache()
                    activeWebView?.clearCache(true)
                    viewModel.clearBrowsingData() 
                },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.SITE_SETTINGS -> {
            val isWhitelisted = activeTab?.url?.let { viewModel.adBlockEngine.isDomainWhitelisted(it) } ?: false
            com.example.ui.dialogs.SiteSettingsSheet(
                url = activeTab?.url ?: "",
                title = activeTab?.title ?: "",
                isDesktopMode = activeTab?.isDesktopMode ?: false,
                isAdBlockWhitelisted = isWhitelisted,
                isDevToolsEnabled = activeTab?.isDevToolsEnabled ?: false,
                onToggleDesktopMode = {
                    viewModel.toggleDesktopMode()
                    activeWebView?.reload()
                },
                onToggleAdBlockWhitelist = {
                    viewModel.toggleWhitelistForCurrentSite()
                    activeWebView?.reload()
                },
                onToggleDevTools = { viewModel.toggleDevTools() },
                onPrintPdf = {
                    viewModel.printCurrentPage(context, activeWebView)
                },
                onClearSiteData = { host ->
                    viewModel.clearSiteData(host, activeWebView)
                },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.ADBLOCK -> {
            AdBlockSheet(
                stats = adBlockStats,
                currentUrl = activeTab?.url ?: "",
                tabBlockedCount = activeTab?.blockedRequestsCount ?: 0,
                onToggleEnabled = { 
                    viewModel.toggleAdBlock()
                    activeWebView?.reload()
                },
                onToggleCosmetic = { viewModel.toggleCosmeticFiltering() },
                onToggleTrackers = { viewModel.toggleTrackerBlocking() },
                onTogglePopups = { viewModel.togglePopupBlocking() },
                onToggleFingerprinting = { viewModel.toggleAntiFingerprinting() },
                onToggleCurrentSiteWhitelist = { 
                    viewModel.toggleWhitelistForCurrentSite() 
                    activeWebView?.reload()
                },
                onRemoveWhitelistedDomain = { viewModel.adBlockEngine.toggleDomainWhitelist(it) },
                onResetStats = { viewModel.resetAdBlockStats() },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.TRANSLATE -> {
            TranslateSheet(
                currentUrl = activeTab?.url ?: "",
                initialTargetLanguage = translationBarState.targetLanguage,
                isCurrentlyTranslated = translationBarState.isTranslated,
                onApplyInPageTranslate = { lang ->
                    viewModel.translateCurrentPage(lang, activeWebView)
                },
                onOpenWebProxyTranslate = { proxyUrl ->
                    viewModel.openUrl(proxyUrl)
                },
                onRestoreOriginal = {
                    viewModel.restoreOriginalPage(activeWebView)
                },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.NONE -> {
            // No sheet
        }
    }
}
}
