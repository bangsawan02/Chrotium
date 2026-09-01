package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ShortcutItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_settings")
private val browserJson = Json { ignoreUnknownKeys = true }

@Serializable
data class SavedTabState(
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = false
)

fun defaultShortcuts(): List<ShortcutItem> = listOf(
    ShortcutItem("DuckDuckGo", "https://duckduckgo.com", "#FF006D3B", "D"),
    ShortcutItem("Wikipedia", "https://id.wikipedia.org", "#FF006874", "W"),
    ShortcutItem("GitHub", "https://github.com", "#FF334155", "G"),
    ShortcutItem("Reddit", "https://www.reddit.com", "#FFFF5722", "R"),
    ShortcutItem("GreasyFork", "https://greasyfork.org/en/scripts", "#FFFFB300", "GF"),
    ShortcutItem("HackerNews", "https://news.ycombinator.com", "#FFFF6600", "Y"),
    ShortcutItem("MDN Web", "https://developer.mozilla.org", "#FF0284C7", "M"),
    ShortcutItem("YouTube", "https://m.youtube.com", "#FFFF0000", "YT")
)

fun serializeShortcuts(shortcuts: List<ShortcutItem>): String = browserJson.encodeToString(shortcuts)

fun deserializeShortcuts(jsonStr: String?): List<ShortcutItem> {
    if (jsonStr.isNullOrBlank()) return defaultShortcuts()
    return try {
        browserJson.decodeFromString<List<ShortcutItem>>(jsonStr)
    } catch (e: IllegalArgumentException) {
        android.util.Log.w("BrowserSettingsDataStore", "Invalid shortcuts JSON: ${e.message}")
        defaultShortcuts()
    } catch (e: Exception) {
        android.util.Log.e("BrowserSettingsDataStore", "Unexpected error deserializing shortcuts", e)
        defaultShortcuts()
    }
}

class BrowserSettingsDataStore(private val context: Context) {

    private val legacyPrefs = context.getSharedPreferences("crotium_browser_settings", Context.MODE_PRIVATE)
    private val jsonFormatter = browserJson

    companion object {
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        val DARK_THEME = booleanPreferencesKey("pref_dark_theme")
        val QUICK_SCROLL = booleanPreferencesKey("pref_quick_scroll")
        val SEARCH_ENGINE = stringPreferencesKey("pref_search_engine")
        val TRANSLATE_LANGUAGE_CODE = stringPreferencesKey("pref_translate_lang_code")
        val SHORTCUTS_JSON = stringPreferencesKey("pref_shortcuts_json")
        val SAVED_TABS_JSON = stringPreferencesKey("saved_tabs_json")
    }

    val isAdBlockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AD_BLOCK_ENABLED] ?: legacyPrefs.getBoolean("ad_block_enabled", true)
    }

    val isDesktopModeDefault: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DESKTOP_MODE_DEFAULT] ?: legacyPrefs.getBoolean("desktop_mode_default", false)
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME] ?: legacyPrefs.getBoolean("pref_dark_theme", false)
    }

    val quickScrollEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[QUICK_SCROLL] ?: legacyPrefs.getBoolean("pref_quick_scroll", true)
    }

    val searchEngineName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SEARCH_ENGINE] ?: legacyPrefs.getString("pref_search_engine", "DUCKDUCKGO") ?: "DUCKDUCKGO"
    }

    val translateLanguageCode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TRANSLATE_LANGUAGE_CODE] ?: legacyPrefs.getString("pref_translate_lang_code", "id") ?: "id"
    }

    val shortcuts: Flow<List<ShortcutItem>> = context.dataStore.data.map { preferences ->
        deserializeShortcuts(preferences[SHORTCUTS_JSON] ?: legacyPrefs.getString("pref_shortcuts_json", null))
    }

    val savedTabs: Flow<List<SavedTabState>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[SAVED_TABS_JSON] ?: ""
        if (jsonStr.isBlank()) {
            emptyList()
        } else {
            try {
                jsonFormatter.decodeFromString<List<SavedTabState>>(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun getDarkThemeSync(default: Boolean = false): Boolean =
        legacyPrefs.getBoolean("pref_dark_theme", default)

    fun getQuickScrollSync(default: Boolean = true): Boolean =
        legacyPrefs.getBoolean("pref_quick_scroll", default)

    fun getSearchEngineNameSync(default: String = "DUCKDUCKGO"): String =
        legacyPrefs.getString("pref_search_engine", default) ?: default

    fun getTranslateLanguageCodeSync(default: String = "id"): String =
        legacyPrefs.getString("pref_translate_lang_code", default) ?: default

    fun getShortcutsSync(): List<ShortcutItem> =
        deserializeShortcuts(legacyPrefs.getString("pref_shortcuts_json", null))

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AD_BLOCK_ENABLED] = enabled
        }
        legacyPrefs.edit().putBoolean("ad_block_enabled", enabled).apply()
    }

    suspend fun setDesktopModeDefault(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DESKTOP_MODE_DEFAULT] = enabled
        }
        legacyPrefs.edit().putBoolean("desktop_mode_default", enabled).apply()
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME] = enabled
        }
        legacyPrefs.edit().putBoolean("pref_dark_theme", enabled).apply()
    }

    suspend fun setQuickScrollEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_SCROLL] = enabled
        }
        legacyPrefs.edit().putBoolean("pref_quick_scroll", enabled).apply()
    }

    suspend fun setSearchEngineName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_ENGINE] = name
        }
        legacyPrefs.edit().putString("pref_search_engine", name).apply()
    }

    suspend fun setTranslateLanguageCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[TRANSLATE_LANGUAGE_CODE] = code
        }
        legacyPrefs.edit().putString("pref_translate_lang_code", code).apply()
    }

    suspend fun setShortcuts(shortcuts: List<ShortcutItem>) {
        val encoded = serializeShortcuts(shortcuts)
        context.dataStore.edit { preferences ->
            preferences[SHORTCUTS_JSON] = encoded
        }
        legacyPrefs.edit().putString("pref_shortcuts_json", encoded).apply()
    }

    suspend fun setSearchEngineValue(engineName: String) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_ENGINE] = engineName
        }
        legacyPrefs.edit().putString("pref_search_engine", engineName).apply()
    }

    suspend fun saveTabsState(tabs: List<SavedTabState>) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_TABS_JSON] = jsonFormatter.encodeToString(tabs)
        }
    }
}
