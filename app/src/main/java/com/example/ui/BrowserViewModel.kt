package com.example.ui

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Bookmark
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryItem
import com.example.data.model.SuggestionItem
import com.example.data.model.SuggestionType
import com.example.data.model.TabItem
import com.example.data.model.UserScript
import com.example.data.model.ShortcutItem
import com.example.data.repository.BrowserRepository
import com.example.engine.DownloadEngine
import com.example.engine.DevToolsEngine
import com.example.engine.ScriptLogEntry
import com.example.engine.SuggestionEngine
import com.example.engine.TampermonkeyBridge
import com.example.engine.TargetLanguage
import com.example.engine.TranslateEngine
import com.example.engine.TranslationBarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    DUCKDUCKGO("DuckDuckGo (Privacy)", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
    BING("Bing", "https://www.bing.com/search?q=")
}

enum class ActiveSheet {
    NONE,
    TABS,
    SCRIPTS_MANAGER,
    BOOKMARKS_HISTORY,
    SETTINGS,
    DOWNLOADS,
    ADBLOCK,
    TRANSLATE
}

data class BrowserUiState(
    val tabs: List<TabItem> = listOf(TabItem(id = UUID.randomUUID().toString())),
    val activeTabId: String = "",
    val recentlyClosedTabs: List<TabItem> = emptyList(),
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isCurrentUrlBookmarked: Boolean = false,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val editingScript: UserScript? = null,
    val omniboxText: String = "",
    val isOmniboxFocused: Boolean = false,
    val suggestions: List<SuggestionItem> = emptyList(),
    val showSuggestions: Boolean = false,
    val isDarkTheme: Boolean = false,
    val shortcuts: List<ShortcutItem> = emptyList(),
    val isFullscreen: Boolean = false,
    val isQuickScrollEnabled: Boolean = true
) {
    val activeTab: TabItem?
        get() = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database by lazy { AppDatabase.getDatabase(application) }
    val repository by lazy { BrowserRepository(database) }
    val tampermonkeyBridge by lazy { TampermonkeyBridge(application) }
    val suggestionEngine by lazy { SuggestionEngine(database) }
    val downloadEngine by lazy { DownloadEngine(application, database) }
    val adBlockEngine by lazy { com.example.engine.AdBlockEngine(application) }

    val adBlockStats: StateFlow<com.example.engine.AdBlockStats> by lazy { adBlockEngine.stats }

    val downloads: StateFlow<List<DownloadItem>> by lazy {
        downloadEngine.downloads.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private var suggestionJob: Job? = null

    private val settingsPrefs = application.getSharedPreferences("crotium_browser_settings", Context.MODE_PRIVATE)

    private val savedDarkTheme = settingsPrefs.getBoolean("pref_dark_theme", false)
    private val savedQuickScroll = settingsPrefs.getBoolean("pref_quick_scroll", true)
    private val savedSearchEngineName = settingsPrefs.getString("pref_search_engine", SearchEngine.DUCKDUCKGO.name)
    private val savedSearchEngine = try {
        SearchEngine.valueOf(savedSearchEngineName ?: SearchEngine.DUCKDUCKGO.name)
    } catch (e: Exception) {
        SearchEngine.DUCKDUCKGO
    }

    private val initialTabId = UUID.randomUUID().toString()
    private val _uiState = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(TabItem(id = initialTabId, title = "Home", url = "about:blank")),
            activeTabId = initialTabId,
            isDarkTheme = savedDarkTheme,
            isQuickScrollEnabled = savedQuickScroll,
            searchEngine = savedSearchEngine,
            shortcuts = loadShortcuts()
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val allScripts: StateFlow<List<UserScript>> by lazy {
        repository.allScripts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val allBookmarks: StateFlow<List<Bookmark>> by lazy {
        repository.allBookmarks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val allHistory: StateFlow<List<HistoryItem>> by lazy {
        repository.allHistory
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val scriptLogs: StateFlow<List<ScriptLogEntry>> by lazy { tampermonkeyBridge.logs }

    private val savedTranslateLangCode = settingsPrefs.getString("pref_translate_lang_code", "id") ?: "id"
    private val initialTargetLanguage = TranslateEngine.getLanguageByCode(savedTranslateLangCode)

    private val _translationBarState = MutableStateFlow(
        com.example.engine.TranslationBarState(
            targetLanguage = initialTargetLanguage
        )
    )
    val translationBarState: StateFlow<com.example.engine.TranslationBarState> = _translationBarState.asStateFlow()

    fun showTranslateBar(targetLanguage: TargetLanguage? = null) {
        val lang = targetLanguage ?: _translationBarState.value.targetLanguage
        _translationBarState.value = _translationBarState.value.copy(
            isVisible = true,
            targetLanguage = lang
        )
    }

    fun hideTranslateBar() {
        _translationBarState.value = _translationBarState.value.copy(isVisible = false)
    }

    fun setTargetLanguage(language: TargetLanguage) {
        settingsPrefs.edit().putString("pref_translate_lang_code", language.code).apply()
        _translationBarState.value = _translationBarState.value.copy(targetLanguage = language)
    }

    fun translateCurrentPage(targetLanguage: TargetLanguage, webView: android.webkit.WebView?) {
        setTargetLanguage(targetLanguage)
        _translationBarState.value = _translationBarState.value.copy(
            isVisible = true,
            isTranslating = true,
            targetLanguage = targetLanguage
        )

        webView?.let { wv ->
            val script = TranslateEngine.buildInPageTranslateScript(targetLanguage.code)
            wv.evaluateJavascript(script) {
                _translationBarState.value = _translationBarState.value.copy(
                    isTranslating = false,
                    isTranslated = true
                )
            }
        } ?: run {
            _translationBarState.value = _translationBarState.value.copy(isTranslating = false)
        }
    }

    fun restoreOriginalPage(webView: android.webkit.WebView?) {
        _translationBarState.value = _translationBarState.value.copy(
            isTranslating = true
        )

        webView?.let { wv ->
            val script = TranslateEngine.buildRestoreOriginalScript()
            wv.evaluateJavascript(script) {
                _translationBarState.value = _translationBarState.value.copy(
                    isTranslating = false,
                    isTranslated = false
                )
            }
        } ?: run {
            _translationBarState.value = _translationBarState.value.copy(
                isTranslating = false,
                isTranslated = false
            )
        }
    }

    init {
        tampermonkeyBridge.onUrlChangeListener = { url, title ->
            onUrlUpdated(url, title)
        }

        viewModelScope.launch {
            try {
                // Mark interrupted downloads as failed
                val activeDownloads = repository.getDownloadsSync()
                activeDownloads.forEach { 
                    if (it.status == 0) {
                        repository.updateDownloadProgress(it.downloadId, 2, it.downloadedBytes, it.totalBytes)
                    }
                }

                repository.initializePresetsIfEmpty(settingsPrefs)
                
                // Restore tabs from DB
                val savedSessions = repository.getTabSessions()
                if (savedSessions.isNotEmpty()) {
                    val restoredTabs = savedSessions.map {
                        TabItem(
                            id = it.id,
                            title = it.title,
                            url = it.url,
                            isDesktopMode = it.isDesktopMode
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        tabs = restoredTabs,
                        activeTabId = restoredTabs.first().id,
                        omniboxText = restoredTabs.first().url.let { url -> if (url == "about:blank") "" else url }
                    )
                }
            } catch (e: Exception) {
                // Ignore initialization errors
            }
        }
        
        viewModelScope.launch {
            _uiState.map { it.tabs }.distinctUntilChanged().collectLatest { tabs ->
                delay(1500)
                try {
                    val sessions = tabs.mapIndexed { index, tab -> 
                        com.example.data.model.TabSession(
                            id = tab.id,
                            title = tab.title,
                            url = tab.url,
                            isDesktopMode = tab.isDesktopMode,
                            sortOrder = index
                        )
                    }
                    repository.saveTabSessions(sessions)
                } catch (e: Exception) {
                    // Ignore background save errors
                }
            }
        }
    }

    fun onScriptInjected(scriptId: Long) {
        viewModelScope.launch {
            try {
                repository.incrementScriptExecution(scriptId)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = fullscreen)
    }

    fun toggleQuickScroll() {
        val newValue = !_uiState.value.isQuickScrollEnabled
        settingsPrefs.edit().putBoolean("pref_quick_scroll", newValue).apply()
        _uiState.value = _uiState.value.copy(isQuickScrollEnabled = newValue)
    }

    fun openUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return

        // Sembunyikan saran saat navigasi
        dismissSuggestions()

        val finalUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("about:", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> _uiState.value.searchEngine.searchUrl + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }

        updateActiveTab { it.copy(url = finalUrl, isLoading = true, progress = 10) }
        _uiState.value = _uiState.value.copy(omniboxText = finalUrl, isOmniboxFocused = false)
        checkBookmarkStatus(finalUrl)
    }

    fun onPageStarted(url: String, tabId: String? = null) {
        updateTabById(tabId) { it.copy(url = url, isLoading = true, progress = 15, blockedRequestsCount = 0) }
        if (tabId == null || tabId == _uiState.value.activeTabId) {
            _uiState.value = _uiState.value.copy(omniboxText = if (url == "about:blank") "" else url)
            checkBookmarkStatus(url)
        }
    }

    private var lastStoredUrl: String? = null
    private var lastStoredTime: Long = 0

    private fun addHistoryEntry(title: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        
        val currentTime = System.currentTimeMillis()
        // Deduplikasi: Jangan simpan jika URL sama dengan yang terakhir disimpan dalam 3 detik terakhir
        if (url == lastStoredUrl && (currentTime - lastStoredTime < 3000)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pageTitle = title.ifBlank { url }
            repository.addHistory(pageTitle, url)
            lastStoredUrl = url
            lastStoredTime = currentTime
        }
    }

    fun onPageFinished(url: String, title: String?, tabId: String? = null) {
        val pageTitle = title?.ifBlank { url } ?: url
        updateTabById(tabId) {
            it.copy(
                url = url,
                title = if (url == "about:blank") "Home" else pageTitle,
                isLoading = false,
                progress = 100
            )
        }
        if (tabId == null || tabId == _uiState.value.activeTabId) {
            _uiState.value = _uiState.value.copy(omniboxText = if (url == "about:blank") "" else url)
            checkBookmarkStatus(url)
        }
        
        addHistoryEntry(pageTitle, url)
    }

    fun onUrlUpdated(url: String, title: String? = null, tabId: String? = null) {
        if (url.isBlank() || url == "about:blank") return
        val targetTabId = tabId ?: _uiState.value.activeTabId
        val pageTitle = title?.ifBlank { url } ?: url

        updateTabById(targetTabId) {
            it.copy(
                url = url,
                title = if (pageTitle.isBlank() || pageTitle == "about:blank") url else pageTitle
            )
        }

        if (targetTabId == _uiState.value.activeTabId) {
            if (!_uiState.value.isOmniboxFocused && _uiState.value.omniboxText != url) {
                _uiState.value = _uiState.value.copy(omniboxText = url)
            }
            checkBookmarkStatus(url)
        }

        addHistoryEntry(pageTitle, url)
    }

    fun onProgressChanged(progress: Int, tabId: String? = null) {
        updateTabById(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean, tabId: String? = null) {
        updateTabById(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun onScriptsExecuted(scriptNames: List<String>, tabId: String? = null) {
        updateTabById(tabId) {
            val updatedNames = (it.activeScriptNames + scriptNames).distinct()
            it.copy(
                activeScriptsCount = updatedNames.size,
                activeScriptNames = updatedNames
            )
        }
    }

    fun newTab(url: String = "about:blank", openInBackground: Boolean = false) {
        val newId = UUID.randomUUID().toString()
        val newTab = TabItem(
            id = newId,
            title = if (url == "about:blank") "Home" else "Loading...",
            url = url
        )
        if (openInBackground) {
            _uiState.value = _uiState.value.copy(
                tabs = _uiState.value.tabs + newTab
            )
        } else {
            _uiState.value = _uiState.value.copy(
                tabs = _uiState.value.tabs + newTab,
                activeTabId = newId,
                omniboxText = if (url == "about:blank") "" else url,
                activeSheet = ActiveSheet.NONE
            )
            checkBookmarkStatus(url)
        }
    }

    fun duplicateTab(tabId: String) {
        val target = _uiState.value.tabs.find { it.id == tabId } ?: return
        val newId = UUID.randomUUID().toString()
        val duplicatedTab = target.copy(
            id = newId,
            title = target.title,
            url = target.url,
            isDesktopMode = target.isDesktopMode,
            isPinned = false
        )
        val currentIndex = _uiState.value.tabs.indexOfFirst { it.id == tabId }
        val newTabs = _uiState.value.tabs.toMutableList()
        if (currentIndex >= 0 && currentIndex < newTabs.size) {
            newTabs.add(currentIndex + 1, duplicatedTab)
        } else {
            newTabs.add(duplicatedTab)
        }
        _uiState.value = _uiState.value.copy(
            tabs = newTabs,
            activeTabId = newId,
            omniboxText = if (duplicatedTab.url == "about:blank") "" else duplicatedTab.url,
            activeSheet = ActiveSheet.NONE
        )
        checkBookmarkStatus(duplicatedTab.url)
    }

    fun togglePinTab(tabId: String) {
        updateTabById(tabId) { it.copy(isPinned = !it.isPinned) }
    }

    fun moveTab(tabId: String, moveUp: Boolean) {
        val currentTabs = _uiState.value.tabs.toMutableList()
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in currentTabs.indices) {
            val item = currentTabs.removeAt(index)
            currentTabs.add(targetIndex, item)
            _uiState.value = _uiState.value.copy(tabs = currentTabs)
        }
    }

    fun closeOtherTabs(keepTabId: String) {
        val currentTabs = _uiState.value.tabs
        val keepTab = currentTabs.find { it.id == keepTabId } ?: return
        val closedTabs = currentTabs.filter { it.id != keepTabId && !it.isPinned && it.url != "about:blank" }
        val updatedRecentlyClosed = (closedTabs + _uiState.value.recentlyClosedTabs).take(10)
        val remainingTabs = currentTabs.filter { it.id == keepTabId || it.isPinned }
        _uiState.value = _uiState.value.copy(
            tabs = remainingTabs,
            activeTabId = keepTab.id,
            recentlyClosedTabs = updatedRecentlyClosed,
            omniboxText = if (keepTab.url == "about:blank") "" else keepTab.url
        )
        checkBookmarkStatus(keepTab.url)
    }

    fun restoreRecentlyClosedTab(tab: TabItem) {
        val updatedRecent = _uiState.value.recentlyClosedTabs.filter { it.id != tab.id }
        val restoredTab = tab.copy(id = UUID.randomUUID().toString())
        _uiState.value = _uiState.value.copy(
            tabs = _uiState.value.tabs + restoredTab,
            activeTabId = restoredTab.id,
            recentlyClosedTabs = updatedRecent,
            omniboxText = if (restoredTab.url == "about:blank") "" else restoredTab.url,
            activeSheet = ActiveSheet.NONE
        )
        checkBookmarkStatus(restoredTab.url)
    }

    fun undoCloseTab() {
        val lastClosed = _uiState.value.recentlyClosedTabs.firstOrNull() ?: return
        restoreRecentlyClosedTab(lastClosed)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        val tabToClose = currentTabs.find { it.id == tabId }
        
        // Simpan ke riwayat tab yang baru ditutup jika bukan about:blank
        val updatedRecentlyClosed = if (tabToClose != null && tabToClose.url != "about:blank" && tabToClose.url.isNotBlank()) {
            (listOf(tabToClose) + _uiState.value.recentlyClosedTabs).take(10)
        } else {
            _uiState.value.recentlyClosedTabs
        }

        if (currentTabs.size <= 1) {
            // If last tab is closed, reset to a new Home tab
            val freshId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                tabs = listOf(TabItem(id = freshId, title = "Home", url = "about:blank")),
                activeTabId = freshId,
                recentlyClosedTabs = updatedRecentlyClosed,
                omniboxText = ""
            )
            checkBookmarkStatus("about:blank")
            return
        }

        val index = currentTabs.indexOfFirst { it.id == tabId }
        val remainingTabs = currentTabs.filter { it.id != tabId }
        val newActiveId = if (_uiState.value.activeTabId == tabId) {
            val nextIndex = (index - 1).coerceAtLeast(0)
            remainingTabs[nextIndex].id
        } else {
            _uiState.value.activeTabId
        }

        val activeTab = remainingTabs.find { it.id == newActiveId }
        val activeUrl = activeTab?.url ?: "about:blank"

        _uiState.value = _uiState.value.copy(
            tabs = remainingTabs,
            activeTabId = newActiveId,
            recentlyClosedTabs = updatedRecentlyClosed,
            omniboxText = if (activeUrl == "about:blank") "" else activeUrl
        )
        checkBookmarkStatus(activeUrl)
    }

    fun closeAllTabs() {
        val currentTabs = _uiState.value.tabs
        val closedTabs = currentTabs.filter { it.url != "about:blank" && it.url.isNotBlank() }
        val updatedRecentlyClosed = (closedTabs + _uiState.value.recentlyClosedTabs).take(10)
        val freshId = UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(
            tabs = listOf(TabItem(id = freshId, title = "Home", url = "about:blank")),
            activeTabId = freshId,
            recentlyClosedTabs = updatedRecentlyClosed,
            omniboxText = "",
            activeSheet = ActiveSheet.NONE
        )
        checkBookmarkStatus("about:blank")
    }

    fun selectTab(tabId: String) {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        _uiState.value = _uiState.value.copy(
            activeTabId = tabId,
            omniboxText = if (tab.url == "about:blank") "" else tab.url,
            activeSheet = ActiveSheet.NONE
        )
        checkBookmarkStatus(tab.url)
    }

    fun updateTabMediaStatus(tabId: String, isPlaying: Boolean) {
        updateTabById(tabId) { it.copy(isPlayingMedia = isPlaying) }
    }

    fun toggleDesktopMode() {
        updateActiveTab { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun toggleReaderMode() {
        updateActiveTab { it.copy(isReaderMode = !it.isReaderMode) }
    }

    fun toggleDevTools() {
        updateActiveTab { it.copy(isDevToolsEnabled = !it.isDevToolsEnabled) }
    }

    fun toggleH264ify() {
        updateActiveTab { it.copy(isH264ifyEnabled = !it.isH264ifyEnabled) }
    }

    fun togglePullToRefresh() {
        updateActiveTab { it.copy(isPullToRefreshEnabled = !it.isPullToRefreshEnabled) }
    }

    fun startDownload(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null
    ) {
        downloadEngine.startDownload(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType
        )
    }

    fun saveBlobData(
        base64Data: String,
        fileName: String?,
        mimeType: String?
    ) {
        downloadEngine.saveBlobData(
            base64Data = base64Data,
            fileName = fileName,
            mimeType = mimeType
        )
    }

    fun openDownloadedFile(item: DownloadItem) {
        downloadEngine.openDownloadedFile(item)
    }

    fun deleteDownload(item: DownloadItem) {
        downloadEngine.deleteDownload(item)
    }

    fun clearAllDownloads() {
        downloadEngine.clearAll()
    }

    private val pendingAdBlockCounts = mutableMapOf<String?, Int>()
    private var adBlockUpdateJob: Job? = null

    fun onAdBlocked(tabId: String? = null) {
        val targetId = tabId ?: _uiState.value.activeTabId
        pendingAdBlockCounts[targetId] = (pendingAdBlockCounts[targetId] ?: 0) + 1
        
        if (adBlockUpdateJob?.isActive == true) return
        
        adBlockUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            delay(600)
            val countsToApply = pendingAdBlockCounts.toMap()
            pendingAdBlockCounts.clear()
            
            countsToApply.forEach { (id, added) ->
                updateTabById(id) { it.copy(blockedRequestsCount = it.blockedRequestsCount + added) }
            }
        }
    }

    fun toggleAdBlock() {
        val current = adBlockEngine.stats.value.isEnabled
        adBlockEngine.setAdBlockEnabled(!current)
    }

    fun toggleCosmeticFiltering() {
        val current = adBlockEngine.stats.value.isCosmeticFilteringEnabled
        adBlockEngine.setCosmeticFilteringEnabled(!current)
    }

    fun toggleTrackerBlocking() {
        val current = adBlockEngine.stats.value.isTrackerBlockingEnabled
        adBlockEngine.setTrackerBlockingEnabled(!current)
    }

    fun togglePopupBlocking() {
        val current = adBlockEngine.stats.value.isPopupBlockingEnabled
        adBlockEngine.setPopupBlockingEnabled(!current)
    }

    fun toggleAntiFingerprinting() {
        val current = adBlockEngine.stats.value.isAntiFingerprintingEnabled
        adBlockEngine.setAntiFingerprintingEnabled(!current)
    }

    fun toggleWhitelistForCurrentSite() {
        val currentUrl = uiState.value.activeTab?.url ?: return
        adBlockEngine.toggleDomainWhitelist(currentUrl)
    }

    fun resetAdBlockStats() {
        adBlockEngine.resetStats()
    }

    fun toggleDarkTheme() {
        val newDarkTheme = !_uiState.value.isDarkTheme
        settingsPrefs.edit().putBoolean("pref_dark_theme", newDarkTheme).apply()
        _uiState.value = _uiState.value.copy(isDarkTheme = newDarkTheme)
    }

    fun setSearchEngine(engine: SearchEngine) {
        settingsPrefs.edit().putString("pref_search_engine", engine.name).apply()
        _uiState.value = _uiState.value.copy(searchEngine = engine)
    }

    fun toggleScript(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleScript(id, isEnabled)
        }
    }

    fun toggleAllScripts(isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAllScripts(isEnabled)
        }
    }

    fun resetDefaultPresets() {
        viewModelScope.launch {
            repository.resetDefaultPresets()
        }
    }

    fun duplicateScript(script: UserScript) {
        viewModelScope.launch {
            repository.duplicateScript(script)
        }
    }

    fun saveScript(script: UserScript) {
        viewModelScope.launch {
            if (script.id == 0L) {
                repository.insertScript(script)
            } else {
                repository.updateScript(script)
            }
            _uiState.value = _uiState.value.copy(editingScript = null)
        }
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch {
            repository.deleteScriptById(id)
        }
    }

    fun setEditingScript(script: UserScript?) {
        _uiState.value = _uiState.value.copy(editingScript = script)
    }

    fun toggleBookmark() {
        val currentTab = _uiState.value.activeTab ?: return
        if (currentTab.url.isBlank() || currentTab.url == "about:blank") return

        viewModelScope.launch {
            repository.toggleBookmark(currentTab.title, currentTab.url)
            checkBookmarkStatus(currentTab.url)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
            checkBookmarkStatus(_uiState.value.activeTab?.url ?: "")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearBrowsingData() {
        viewModelScope.launch {
            try {
                // 1. Clear database history
                repository.clearHistory()
                
                // 2. Clear system cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                
                // 3. Clear WebStorage (IndexedDB, LocalStorage, etc.)
                WebStorage.getInstance().deleteAllData()
                
                // 4. Note: WebView.clearCache(true) needs to be called on a WebView instance.
                // We'll set a flag or handle it via a UI event if needed, 
                // but since we are clearing the system storage, it's already very effective.
            } catch (e: Exception) {
                // safe ignore
            }
        }
    }

    fun showSheet(sheet: ActiveSheet) {
        _uiState.value = _uiState.value.copy(activeSheet = sheet)
    }

    fun hideSheet() {
        _uiState.value = _uiState.value.copy(activeSheet = ActiveSheet.NONE, editingScript = null)
    }

    fun setOmniboxText(text: String) {
        _uiState.value = _uiState.value.copy(omniboxText = text)
        querySuggestions(text)
    }

    fun setOmniboxFocused(focused: Boolean) {
        _uiState.value = _uiState.value.copy(isOmniboxFocused = focused)
        if (focused && _uiState.value.omniboxText.isNotBlank()) {
            querySuggestions(_uiState.value.omniboxText)
        } else if (!focused) {
            // Beri sedikit jeda agar klik pada item saran tidak terpotong
            viewModelScope.launch {
                delay(150)
                if (!_uiState.value.isOmniboxFocused) {
                    dismissSuggestions()
                }
            }
        }
    }

    fun querySuggestions(query: String) {
        val trimmed = query.trim()
        suggestionJob?.cancel()

        if (trimmed.isBlank() || trimmed.startsWith("about:")) {
            _uiState.value = _uiState.value.copy(suggestions = emptyList(), showSuggestions = false)
            return
        }

        suggestionJob = viewModelScope.launch {
            delay(180) // Debounce 180ms
            val results = suggestionEngine.getSuggestions(trimmed)
            _uiState.value = _uiState.value.copy(
                suggestions = results,
                showSuggestions = results.isNotEmpty()
            )
        }
    }

    fun selectSuggestion(suggestion: SuggestionItem) {
        dismissSuggestions()
        when (suggestion.type) {
            SuggestionType.DIRECT_URL, SuggestionType.BOOKMARK, SuggestionType.HISTORY -> {
                openUrl(suggestion.destinationUrl)
            }
            SuggestionType.QUERY -> {
                openUrl(suggestion.title)
            }
        }
    }

    fun fillSuggestionIntoOmnibox(suggestion: SuggestionItem) {
        val textToFill = if (suggestion.type == SuggestionType.QUERY) suggestion.title else suggestion.destinationUrl
        _uiState.value = _uiState.value.copy(omniboxText = textToFill)
        querySuggestions(textToFill)
    }

    fun dismissSuggestions() {
        suggestionJob?.cancel()
        _uiState.value = _uiState.value.copy(showSuggestions = false)
    }

    private fun checkBookmarkStatus(url: String) {
        if (url.isBlank() || url == "about:blank") {
            _uiState.value = _uiState.value.copy(isCurrentUrlBookmarked = false)
            return
        }
        viewModelScope.launch {
            val isBookmarked = repository.isBookmarked(url)
            _uiState.value = _uiState.value.copy(isCurrentUrlBookmarked = isBookmarked)
        }
    }

    private fun updateActiveTab(transform: (TabItem) -> TabItem) {
        updateTabById(null, transform)
    }

    private fun updateTabById(targetTabId: String?, transform: (TabItem) -> TabItem) {
        val currentTabs = _uiState.value.tabs
        val effectiveId = targetTabId ?: _uiState.value.activeTabId
        val updatedTabs = currentTabs.map { tab ->
            if (tab.id == effectiveId) transform(tab) else tab
        }
        _uiState.value = _uiState.value.copy(tabs = updatedTabs)
    }

    fun loadShortcuts(): List<ShortcutItem> {
        val jsonStr = settingsPrefs.getString("pref_shortcuts_json", null)
        if (jsonStr.isNullOrEmpty()) {
            return listOf(
                ShortcutItem("DuckDuckGo", "https://duckduckgo.com", "#FF006D3B", "D"),
                ShortcutItem("Wikipedia", "https://id.wikipedia.org", "#FF006874", "W"),
                ShortcutItem("GitHub", "https://github.com", "#FF334155", "G"),
                ShortcutItem("Reddit", "https://www.reddit.com", "#FFFF5722", "R"),
                ShortcutItem("GreasyFork", "https://greasyfork.org/en/scripts", "#FFFFB300", "GF"),
                ShortcutItem("HackerNews", "https://news.ycombinator.com", "#FFFF6600", "Y"),
                ShortcutItem("MDN Web", "https://developer.mozilla.org", "#FF0284C7", "M"),
                ShortcutItem("YouTube", "https://m.youtube.com", "#FFFF0000", "YT")
            )
        }
        val list = mutableListOf<ShortcutItem>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ShortcutItem(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        iconColorHex = obj.getString("iconColorHex"),
                        initial = obj.getString("initial")
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    fun addShortcut(title: String, url: String) {
        if (title.isBlank() || url.isBlank()) return
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        val initial = if (title.length >= 2) {
            title.take(2).uppercase()
        } else {
            title.take(1).uppercase()
        }
        val colors = listOf("#FF006D3B", "#FF006874", "#FF334155", "#FFFF5722", "#FFFFB300", "#FFFF6600", "#FF0284C7", "#FFFF0000")
        val randomColor = colors.random()
        
        val newItem = ShortcutItem(title, formattedUrl, randomColor, initial)
        val updatedList = _uiState.value.shortcuts + newItem
        
        val array = org.json.JSONArray()
        for (item in updatedList) {
            val obj = org.json.JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("iconColorHex", item.iconColorHex)
            obj.put("initial", item.initial)
            array.put(obj)
        }
        settingsPrefs.edit().putString("pref_shortcuts_json", array.toString()).apply()
        _uiState.value = _uiState.value.copy(shortcuts = updatedList)
    }

    fun deleteShortcut(shortcut: ShortcutItem) {
        val updatedList = _uiState.value.shortcuts.filter { it.url != shortcut.url || it.title != shortcut.title }
        
        val array = org.json.JSONArray()
        for (item in updatedList) {
            val obj = org.json.JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("iconColorHex", item.iconColorHex)
            obj.put("initial", item.initial)
            array.put(obj)
        }
        settingsPrefs.edit().putString("pref_shortcuts_json", array.toString()).apply()
        _uiState.value = _uiState.value.copy(shortcuts = updatedList)
    }

    fun editShortcut(oldShortcut: ShortcutItem, newTitle: String, newUrl: String) {
        var formattedUrl = newUrl.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        val initial = if (newTitle.isNotBlank()) newTitle.trim().first().uppercaseChar().toString() else "U"

        val updatedList = _uiState.value.shortcuts.map {
            if (it.url == oldShortcut.url && it.title == oldShortcut.title) {
                it.copy(title = newTitle.trim(), url = formattedUrl, initial = initial)
            } else {
                it
            }
        }

        val array = org.json.JSONArray()
        for (item in updatedList) {
            val obj = org.json.JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("iconColorHex", item.iconColorHex)
            obj.put("initial", item.initial)
            array.put(obj)
        }
        settingsPrefs.edit().putString("pref_shortcuts_json", array.toString()).apply()
        _uiState.value = _uiState.value.copy(shortcuts = updatedList)
    }

    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // Trim suggestion in-memory cache
            suggestionEngine.clearCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.engine.KtorNetworkEngine.close()
        try {
            suggestionEngine.close()
        } catch (e: Exception) {
            // safe ignore
        }
    }
}
