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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import com.example.ui.dialogs.CookieManagerSheet
import com.example.ui.dialogs.DownloadsSheet
import com.example.ui.dialogs.SettingsSheet
import com.example.ui.dialogs.TabsSheet
import com.example.ui.dialogs.TranslateSheet
import com.example.ui.dialogs.UserScriptManagerSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
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
    val batteryStatus by viewModel.batteryStatus.collectAsStateWithLifecycle()
    val scriptLogs by viewModel.scriptLogs.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val adBlockStats by viewModel.adBlockStats.collectAsStateWithLifecycle()
    val translationBarState by viewModel.translationBarState.collectAsStateWithLifecycle()

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

    val isImeVisible = WindowInsets.isImeVisible

    MyApplicationTheme(
        darkTheme = uiState.isDarkTheme
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!uiState.isFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Omnibox(
                            urlText = uiState.omniboxText,
                            isLoading = activeTab?.isLoading ?: false,
                            progress = activeTab?.progress ?: 0,
                            batteryStatus = batteryStatus,
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
                            onFocusChange = { viewModel.setOmniboxFocused(it) }
                        )
                    }
                }
            },
            bottomBar = {
                if (!uiState.isFullscreen && !isImeVisible) {
                    BottomNavBar(
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (!uiState.isFullscreen) Modifier.padding(innerPadding) else Modifier)
        ) {
            if (activeTab == null || activeTab.url == "about:blank") {
                HomeDashboard(
                    batteryStatus = batteryStatus,
                    scripts = allScripts,
                    bookmarks = allBookmarks,
                    history = allHistory,
                    shortcuts = uiState.shortcuts,
                    onAddShortcut = { title, url -> viewModel.addShortcut(title, url) },
                    onEditShortcut = { oldShortcut, newTitle, newUrl -> viewModel.editShortcut(oldShortcut, newTitle, newUrl) },
                    onDeleteShortcut = { viewModel.deleteShortcut(it) },
                    downloadsCount = downloads.size,
                    onNavigate = { viewModel.openUrl(it) },
                    onOpenScriptManager = { viewModel.showSheet(ActiveSheet.SCRIPTS_MANAGER) },
                    onOpenBookmarks = { viewModel.showSheet(ActiveSheet.BOOKMARKS_HISTORY) },
                    onOpenDownloads = { viewModel.showSheet(ActiveSheet.DOWNLOADS) }
                )
            }
            
            uiState.tabs.forEach { tab ->
                if (tab.url != "about:blank") {
                    val isVisible = (activeTab != null && activeTab.id == tab.id && activeTab.url != "about:blank")
                    androidx.compose.runtime.key(tab.id) {
                        BrowserWebView(
                            tab = tab,
                            isVisible = isVisible,
                            scripts = allScripts,
                            batteryEngine = viewModel.batteryEngine,
                            tampermonkeyBridge = viewModel.tampermonkeyBridge,
                            adBlockEngine = viewModel.adBlockEngine,
                            isDarkTheme = uiState.isDarkTheme,
                            onPageStarted = { viewModel.onPageStarted(it, tab.id) },
                            onPageFinished = { url, title -> viewModel.onPageFinished(url, title, tab.id) },
                            onProgressChanged = { viewModel.onProgressChanged(it, tab.id) },
                            onNavigationStateChanged = { back, forward -> viewModel.onNavigationStateChanged(back, forward, tab.id) },
                            onScriptsExecuted = { viewModel.onScriptsExecuted(it, tab.id) },
                            onScriptInjected = { script -> viewModel.onScriptInjected(script.id) },
                            onAdBlocked = { viewModel.onAdBlocked(tab.id) },
                            onDownloadRequested = { url, ua, cd, mime -> viewModel.startDownload(url, ua, cd, mime) },
                            onFullScreenChanged = { viewModel.setFullscreen(it) },
                            onWebViewCreated = { if (isVisible) activeWebView = it }
                        )
                    }
                }
            }

            // Built-in Chrome DevTools Panel (Dual-WebView Architecture)
            if (activeTab != null && activeTab.url != "about:blank") {
                DevToolsPanel(
                    isOpen = isDevToolsOpen,
                    isDarkTheme = uiState.isDarkTheme,
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
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onCloseAll = { viewModel.closeAllTabs() },
                onNewTab = { viewModel.newTab() },
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
                batteryStatus = batteryStatus,
                isDesktopMode = activeTab?.isDesktopMode ?: false,
                isBookmarked = uiState.isCurrentUrlBookmarked,
                isDarkTheme = uiState.isDarkTheme,
                isDevToolsEnabled = activeTab?.isDevToolsEnabled ?: false,
                isH264ifyEnabled = activeTab?.isH264ifyEnabled ?: true,
                currentSearchEngine = uiState.searchEngine,
                onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                onToggleBookmark = { viewModel.toggleBookmark() },
                onToggleDevTools = { viewModel.toggleDevTools() },
                onToggleH264ify = { viewModel.toggleH264ify() },
                onOpenDownloads = { viewModel.showSheet(ActiveSheet.DOWNLOADS) },
                onOpenCookieManager = { viewModel.showSheet(ActiveSheet.COOKIE_MANAGER) },
                onOpenAdBlock = { viewModel.showSheet(ActiveSheet.ADBLOCK) },
                onOpenTranslate = { viewModel.showSheet(ActiveSheet.TRANSLATE) },
                onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                onClearBrowsingData = { 
                    activeWebView?.clearCache(true)
                    viewModel.clearBrowsingData() 
                },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.DOWNLOADS -> {
            DownloadsSheet(
                downloads = downloads,
                onOpenFile = { viewModel.openDownloadedFile(it) },
                onDeleteDownload = { viewModel.deleteDownload(it) },
                onClearAll = { viewModel.clearAllDownloads() },
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.COOKIE_MANAGER -> {
            CookieManagerSheet(
                currentUrl = activeTab?.url ?: "",
                onDismiss = { viewModel.hideSheet() }
            )
        }
        ActiveSheet.ADBLOCK -> {
            AdBlockSheet(
                stats = adBlockStats,
                currentUrl = activeTab?.url ?: "",
                tabBlockedCount = activeTab?.blockedRequestsCount ?: 0,
                onToggleEnabled = { viewModel.toggleAdBlock() },
                onToggleCosmetic = { viewModel.toggleCosmeticFiltering() },
                onToggleTrackers = { viewModel.toggleTrackerBlocking() },
                onTogglePopups = { viewModel.togglePopupBlocking() },
                onToggleFingerprinting = { viewModel.toggleAntiFingerprinting() },
                onToggleCurrentSiteWhitelist = { viewModel.toggleWhitelistForCurrentSite() },
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
