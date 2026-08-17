package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

data class TabItem(
    val id: String,
    val title: String = "New Tab",
    val url: String = "about:blank",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isDesktopMode: Boolean = false,
    val isIncognito: Boolean = false,
    val isReaderMode: Boolean = false,
    val isDevToolsEnabled: Boolean = false,
    val blockedRequestsCount: Int = 0,
    val activeScriptsCount: Int = 0,
    val activeScriptNames: List<String> = emptyList()
)

data class ShortcutItem(
    val title: String,
    val url: String,
    val iconColorHex: String,
    val initial: String
)

