package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        androidx.room.Index(value = ["url"]),
        androidx.room.Index(value = ["createdAt"])
    ]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    indices = [
        androidx.room.Index(value = ["url"], unique = true),
        androidx.room.Index(value = ["visitedAt"])
    ]
)
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
    val isH264ifyEnabled: Boolean = false,
    val isPullToRefreshEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val isPlayingMedia: Boolean = false,
    val blockedRequestsCount: Int = 0,
    val activeScriptsCount: Int = 0,
    val activeScriptNames: List<String> = emptyList()
)

@Entity(
    tableName = "tab_sessions",
    indices = [androidx.room.Index(value = ["sortOrder"])]
)
data class TabSession(
    @PrimaryKey
    val id: String,
    val title: String,
    val url: String,
    val isDesktopMode: Boolean,
    val sortOrder: Int
)

data class ShortcutItem(
    val title: String,
    val url: String,
    val iconColorHex: String,
    val initial: String
)

