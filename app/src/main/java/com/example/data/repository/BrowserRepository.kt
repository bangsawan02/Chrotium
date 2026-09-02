package com.example.data.repository

import android.content.Context
import com.example.data.db.DatabaseHelper
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.UserScript
import com.example.data.presets.PreinstalledScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BrowserRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    private val _allScripts = MutableStateFlow<List<UserScript>>(emptyList())
    val allScripts: Flow<List<UserScript>> = _allScripts.asStateFlow()

    private val _allBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val allBookmarks: Flow<List<Bookmark>> = _allBookmarks.asStateFlow()

    private val _allHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val allHistory: Flow<List<HistoryItem>> = _allHistory.asStateFlow()

    suspend fun refreshData() = withContext(Dispatchers.IO) {
        refreshScripts()
        refreshBookmarks()
        refreshHistory()
    }

    suspend fun refreshScripts() = withContext(Dispatchers.IO) {
        _allScripts.value = dbHelper.getUserScripts()
    }

    suspend fun refreshBookmarks() = withContext(Dispatchers.IO) {
        _allBookmarks.value = dbHelper.getBookmarks()
    }

    suspend fun refreshHistory() = withContext(Dispatchers.IO) {
        _allHistory.value = dbHelper.getHistory()
    }

    suspend fun getTabSessions(): List<com.example.data.model.TabSession> = withContext(Dispatchers.IO) {
        dbHelper.getTabSessions()
    }

    suspend fun saveTabSessions(sessions: List<com.example.data.model.TabSession>) = withContext(Dispatchers.IO) {
        dbHelper.replaceAllTabSessions(sessions)
    }

    suspend fun initializePresetsIfEmpty(prefs: android.content.SharedPreferences? = null) = withContext(Dispatchers.IO) {
        val alreadyInitialized = prefs?.getBoolean("pref_userscripts_first_run_done", false) ?: false
        if (alreadyInitialized) {
            refreshData()
            return@withContext
        }

        val scripts = dbHelper.getUserScripts()
        if (scripts.isEmpty()) {
            PreinstalledScripts.getDefaultScripts().forEach { dbHelper.insertUserScript(it) }
        }
        refreshData()
        prefs?.edit()?.putBoolean("pref_userscripts_first_run_done", true)?.apply()
    }

    suspend fun getEnabledScripts(): List<UserScript> = withContext(Dispatchers.IO) {
        dbHelper.getEnabledUserScripts()
    }

    suspend fun insertScript(script: UserScript): Long = withContext(Dispatchers.IO) {
        dbHelper.insertUserScript(script)
        refreshScripts()
        0L // Returning 0 as ID mapping is handled internally
    }

    suspend fun updateScript(script: UserScript) = withContext(Dispatchers.IO) {
        dbHelper.updateUserScript(script)
        refreshScripts()
    }

    suspend fun toggleScript(id: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val script = dbHelper.getUserScripts().find { it.id == id }
        if (script != null) {
            dbHelper.updateUserScript(script.copy(isEnabled = isEnabled))
            refreshScripts()
        }
    }

    suspend fun toggleAllScripts(isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val scripts = dbHelper.getUserScripts()
        scripts.forEach {
            dbHelper.updateUserScript(it.copy(isEnabled = isEnabled))
        }
        refreshScripts()
    }

    suspend fun resetDefaultPresets() = withContext(Dispatchers.IO) {
        val scripts = dbHelper.getUserScripts()
        scripts.forEach { dbHelper.deleteUserScript(it) }
        PreinstalledScripts.getDefaultScripts().forEach { dbHelper.insertUserScript(it) }
        refreshScripts()
    }

    suspend fun duplicateScript(script: UserScript) = withContext(Dispatchers.IO) {
        val copy = script.copy(
            id = 0,
            name = "${script.name} (Salinan)",
            createdAt = System.currentTimeMillis()
        )
        dbHelper.insertUserScript(copy)
        refreshScripts()
    }

    suspend fun deleteScript(script: UserScript) = withContext(Dispatchers.IO) {
        dbHelper.deleteUserScript(script)
        refreshScripts()
    }

    suspend fun deleteScriptById(id: Long) = withContext(Dispatchers.IO) {
        val script = dbHelper.getUserScripts().find { it.id == id }
        if (script != null) {
            dbHelper.deleteUserScript(script)
            refreshScripts()
        }
    }

    suspend fun incrementScriptExecution(id: Long) = withContext(Dispatchers.IO) {
        val script = dbHelper.getUserScripts().find { it.id == id }
        if (script != null) {
            dbHelper.updateUserScript(script.copy(executionCount = script.executionCount + 1, lastExecutedTimestamp = System.currentTimeMillis()))
            refreshScripts()
        }
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.getBookmarks().any { it.url == url }
    }

    suspend fun toggleBookmark(title: String, url: String) = withContext(Dispatchers.IO) {
        val bookmarks = dbHelper.getBookmarks()
        val exists = bookmarks.find { it.url == url }
        if (exists != null) {
            dbHelper.deleteBookmark(exists)
        } else {
            dbHelper.insertBookmark(Bookmark(title = title, url = url))
        }
        refreshBookmarks()
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        dbHelper.deleteBookmark(bookmark)
        refreshBookmarks()
    }

    suspend fun addHistory(title: String, url: String) = withContext(Dispatchers.IO) {
        if (url.isNotBlank() && !url.startsWith("about:")) {
            dbHelper.insertHistory(HistoryItem(title = title.ifBlank { url }, url = url))
            refreshHistory()
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dbHelper.clearHistory()
        refreshHistory()
    }
}
