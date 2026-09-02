package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.data.model.TabSession
import com.example.data.model.UserScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "browser_database.db"
        const val DATABASE_VERSION = 1

        const val TABLE_BOOKMARKS = "bookmarks"
        const val TABLE_HISTORY = "history"
        const val TABLE_USER_SCRIPTS = "user_scripts"
        const val TABLE_TAB_SESSIONS = "tab_sessions"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                favicon TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX index_bookmarks_url ON $TABLE_BOOKMARKS(url)")
        db.execSQL("CREATE INDEX index_bookmarks_createdAt ON $TABLE_BOOKMARKS(createdAt)")

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                visitedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX index_history_visitedAt ON $TABLE_HISTORY(visitedAt)")

        db.execSQL("""
            CREATE TABLE $TABLE_USER_SCRIPTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                code TEXT NOT NULL,
                version TEXT NOT NULL,
                author TEXT NOT NULL,
                matchPattern TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                runAt TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_TAB_SESSIONS (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                isDesktopMode INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX index_tab_sessions_sortOrder ON $TABLE_TAB_SESSIONS(sortOrder)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_SCRIPTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TAB_SESSIONS")
        onCreate(db)
    }

    suspend fun getBookmarks(): List<Bookmark> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bookmark>()
        val cursor = readableDatabase.query(TABLE_BOOKMARKS, null, null, null, null, null, "createdAt DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(Bookmark(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    url = it.getString(it.getColumnIndexOrThrow("url")),
                    favicon = it.getString(it.getColumnIndexOrThrow("favicon")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                ))
            }
        }
        list
    }

    suspend fun insertBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", bookmark.title)
            put("url", bookmark.url)
            put("favicon", bookmark.favicon)
            put("createdAt", bookmark.createdAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_BOOKMARKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BOOKMARKS, "id = ?", arrayOf(bookmark.id.toString()))
    }

    suspend fun getHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<HistoryItem>()
        val cursor = readableDatabase.query(TABLE_HISTORY, null, null, null, null, null, "visitedAt DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(HistoryItem(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    url = it.getString(it.getColumnIndexOrThrow("url")),
                    visitedAt = it.getLong(it.getColumnIndexOrThrow("visitedAt"))
                ))
            }
        }
        list
    }

    suspend fun insertHistory(history: HistoryItem) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", history.title)
            put("url", history.url)
            put("visitedAt", history.visitedAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteHistory(history: HistoryItem) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, "id = ?", arrayOf(history.id.toString()))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, null, null)
    }

    suspend fun getUserScripts(): List<UserScript> = withContext(Dispatchers.IO) {
        val list = mutableListOf<UserScript>()
        val cursor = readableDatabase.query(TABLE_USER_SCRIPTS, null, null, null, null, null, "createdAt DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(UserScript(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    description = it.getString(it.getColumnIndexOrThrow("description")),
                    code = it.getString(it.getColumnIndexOrThrow("code")),
                    version = it.getString(it.getColumnIndexOrThrow("version")),
                    author = it.getString(it.getColumnIndexOrThrow("author")),
                    matchPatterns = it.getString(it.getColumnIndexOrThrow("matchPattern")),
                    isEnabled = it.getInt(it.getColumnIndexOrThrow("enabled")) == 1,
                    runAt = it.getString(it.getColumnIndexOrThrow("runAt")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                ))
            }
        }
        list
    }

    suspend fun getEnabledUserScripts(): List<UserScript> = withContext(Dispatchers.IO) {
        val list = mutableListOf<UserScript>()
        val cursor = readableDatabase.query(TABLE_USER_SCRIPTS, null, "enabled = 1", null, null, null, "createdAt DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(UserScript(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    description = it.getString(it.getColumnIndexOrThrow("description")),
                    code = it.getString(it.getColumnIndexOrThrow("code")),
                    version = it.getString(it.getColumnIndexOrThrow("version")),
                    author = it.getString(it.getColumnIndexOrThrow("author")),
                    matchPatterns = it.getString(it.getColumnIndexOrThrow("matchPattern")),
                    isEnabled = true,
                    runAt = it.getString(it.getColumnIndexOrThrow("runAt")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                ))
            }
        }
        list
    }

    suspend fun insertUserScript(script: UserScript) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            if (script.id > 0) put("id", script.id)
            put("name", script.name)
            put("description", script.description)
            put("code", script.code)
            put("version", script.version)
            put("author", script.author)
            put("matchPattern", script.matchPatterns)
            put("enabled", if (script.isEnabled) 1 else 0)
            put("runAt", script.runAt)
            put("createdAt", script.createdAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_USER_SCRIPTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun updateUserScript(script: UserScript) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", script.name)
            put("description", script.description)
            put("code", script.code)
            put("version", script.version)
            put("author", script.author)
            put("matchPattern", script.matchPatterns)
            put("enabled", if (script.isEnabled) 1 else 0)
            put("runAt", script.runAt)
            put("createdAt", script.createdAt)
        }
        writableDatabase.update(TABLE_USER_SCRIPTS, values, "id = ?", arrayOf(script.id.toString()))
    }

    suspend fun deleteUserScript(script: UserScript) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_USER_SCRIPTS, "id = ?", arrayOf(script.id.toString()))
    }

    suspend fun getTabSessions(): List<TabSession> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TabSession>()
        val cursor = readableDatabase.query(TABLE_TAB_SESSIONS, null, null, null, null, null, "sortOrder ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(TabSession(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    url = it.getString(it.getColumnIndexOrThrow("url")),
                    isDesktopMode = it.getInt(it.getColumnIndexOrThrow("isDesktopMode")) == 1,
                    sortOrder = it.getInt(it.getColumnIndexOrThrow("sortOrder"))
                ))
            }
        }
        list
    }

    suspend fun replaceAllTabSessions(sessions: List<TabSession>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_TAB_SESSIONS, null, null)
            for (session in sessions) {
                val values = ContentValues().apply {
                    put("id", session.id)
                    put("title", session.title)
                    put("url", session.url)
                    put("isDesktopMode", if (session.isDesktopMode) 1 else 0)
                    put("sortOrder", session.sortOrder)
                }
                db.insert(TABLE_TAB_SESSIONS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
