package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.UserScript
import com.example.data.presets.PreinstalledScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BrowserRepository(private val database: AppDatabase) {

    val allScripts: Flow<List<UserScript>> = database.userScriptDao().getAllScripts()
    val allBookmarks: Flow<List<Bookmark>> = database.bookmarkDao().getAllBookmarks()
    val allHistory: Flow<List<HistoryItem>> = database.historyDao().getAllHistory()

    suspend fun getTabSessions(): List<com.example.data.model.TabSession> = withContext(Dispatchers.IO) {
        database.tabSessionDao().getAllSessions()
    }

    suspend fun saveTabSessions(sessions: List<com.example.data.model.TabSession>) = withContext(Dispatchers.IO) {
        database.tabSessionDao().replaceAll(sessions)
    }

    suspend fun initializePresetsIfEmpty(prefs: android.content.SharedPreferences? = null) = withContext(Dispatchers.IO) {
        val alreadyInitialized = prefs?.getBoolean("pref_userscripts_first_run_done", false) ?: false
        if (alreadyInitialized) {
            return@withContext
        }

        val count = database.userScriptDao().getScriptsCount()
        if (count == 0) {
            database.userScriptDao().insertAll(PreinstalledScripts.getDefaultScripts())
        }
        prefs?.edit()?.putBoolean("pref_userscripts_first_run_done", true)?.apply()
    }

    suspend fun getEnabledScripts(): List<UserScript> = withContext(Dispatchers.IO) {
        database.userScriptDao().getEnabledScripts()
    }

    suspend fun insertScript(script: UserScript): Long = withContext(Dispatchers.IO) {
        database.userScriptDao().insertScript(script)
    }

    suspend fun updateScript(script: UserScript) = withContext(Dispatchers.IO) {
        database.userScriptDao().updateScript(script)
    }

    suspend fun toggleScript(id: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        database.userScriptDao().toggleScript(id, isEnabled)
    }

    suspend fun toggleAllScripts(isEnabled: Boolean) = withContext(Dispatchers.IO) {
        database.userScriptDao().toggleAll(isEnabled)
    }

    suspend fun resetDefaultPresets() = withContext(Dispatchers.IO) {
        database.userScriptDao().deleteAllScripts()
        database.userScriptDao().insertAll(PreinstalledScripts.getDefaultScripts())
    }

    suspend fun duplicateScript(script: UserScript) = withContext(Dispatchers.IO) {
        val copy = script.copy(
            id = 0,
            name = "${script.name} (Salinan)",
            createdAt = System.currentTimeMillis()
        )
        database.userScriptDao().insertScript(copy)
    }

    suspend fun deleteScript(script: UserScript) = withContext(Dispatchers.IO) {
        database.userScriptDao().deleteScript(script)
    }

    suspend fun deleteScriptById(id: Long) = withContext(Dispatchers.IO) {
        database.userScriptDao().deleteScriptById(id)
    }

    suspend fun incrementScriptExecution(id: Long) = withContext(Dispatchers.IO) {
        database.userScriptDao().incrementExecution(id, System.currentTimeMillis())
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(Dispatchers.IO) {
        database.bookmarkDao().isBookmarked(url)
    }

    suspend fun toggleBookmark(title: String, url: String) = withContext(Dispatchers.IO) {
        val exists = database.bookmarkDao().isBookmarked(url)
        if (exists) {
            database.bookmarkDao().deleteByUrl(url)
        } else {
            database.bookmarkDao().insertBookmark(Bookmark(title = title, url = url))
        }
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        database.bookmarkDao().deleteBookmark(bookmark)
    }

    suspend fun addHistory(title: String, url: String) = withContext(Dispatchers.IO) {
        if (url.isNotBlank() && !url.startsWith("about:")) {
            database.historyDao().insertHistory(HistoryItem(title = title.ifBlank { url }, url = url))
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.historyDao().clearAllHistory()
    }
}
