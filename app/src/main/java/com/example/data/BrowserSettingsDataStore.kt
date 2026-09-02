package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ShortcutItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

fun serializeShortcuts(shortcuts: List<ShortcutItem>): String {
    val array = JSONArray()
    for (s in shortcuts) {
        val obj = JSONObject()
        obj.put("title", s.title)
        obj.put("url", s.url)
        obj.put("iconColorHex", s.iconColorHex)
        obj.put("initial", s.initial)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeShortcuts(jsonStr: String?): List<ShortcutItem> {
    if (jsonStr.isNullOrBlank()) return defaultShortcuts()
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<ShortcutItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(ShortcutItem(
                title = obj.optString("title", ""),
                url = obj.optString("url", ""),
                iconColorHex = obj.optString("iconColorHex", ""),
                initial = obj.optString("initial", "")
            ))
        }
        list
    } catch (e: Exception) {
        android.util.Log.w("BrowserSettingsDataStore", "Invalid shortcuts JSON: ${e.message}")
        defaultShortcuts()
    }
}

class BrowserSettingsDataStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("crotium_browser_settings", Context.MODE_PRIVATE)

    private val _isAdBlockEnabled = MutableStateFlow(prefs.getBoolean("ad_block_enabled", true))
    val isAdBlockEnabled: StateFlow<Boolean> = _isAdBlockEnabled.asStateFlow()

    private val _isDesktopModeDefault = MutableStateFlow(prefs.getBoolean("desktop_mode_default", false))
    val isDesktopModeDefault: StateFlow<Boolean> = _isDesktopModeDefault.asStateFlow()

    private val _searchEngineName = MutableStateFlow(prefs.getString("pref_search_engine", "DUCKDUCKGO") ?: "DUCKDUCKGO")
    val searchEngineName: StateFlow<String> = _searchEngineName.asStateFlow()

    private val _translateLanguageCode = MutableStateFlow(prefs.getString("pref_translate_lang_code", "id") ?: "id")
    val translateLanguageCode: StateFlow<String> = _translateLanguageCode.asStateFlow()

    private val _shortcuts = MutableStateFlow(deserializeShortcuts(prefs.getString("pref_shortcuts_json", null)))
    val shortcuts: StateFlow<List<ShortcutItem>> = _shortcuts.asStateFlow()

    private val _savedTabs = MutableStateFlow(deserializeSavedTabs(prefs.getString("saved_tabs_json", "")))
    val savedTabs: StateFlow<List<SavedTabState>> = _savedTabs.asStateFlow()

    private fun deserializeSavedTabs(jsonStr: String?): List<SavedTabState> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SavedTabState>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SavedTabState(
                    url = obj.optString("url", ""),
                    title = obj.optString("title", ""),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false)
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeSavedTabs(tabs: List<SavedTabState>): String {
        val array = JSONArray()
        for (t in tabs) {
            val obj = JSONObject()
            obj.put("url", t.url)
            obj.put("title", t.title)
            obj.put("isDesktopMode", t.isDesktopMode)
            array.put(obj)
        }
        return array.toString()
    }

    fun getSearchEngineNameSync(default: String = "DUCKDUCKGO"): String =
        prefs.getString("pref_search_engine", default) ?: default

    fun getTranslateLanguageCodeSync(default: String = "id"): String =
        prefs.getString("pref_translate_lang_code", default) ?: default

    fun getShortcutsSync(): List<ShortcutItem> =
        deserializeShortcuts(prefs.getString("pref_shortcuts_json", null))

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ad_block_enabled", enabled).apply()
        _isAdBlockEnabled.value = enabled
    }

    fun setDesktopModeDefault(enabled: Boolean) {
        prefs.edit().putBoolean("desktop_mode_default", enabled).apply()
        _isDesktopModeDefault.value = enabled
    }

    fun setSearchEngineName(name: String) {
        prefs.edit().putString("pref_search_engine", name).apply()
        _searchEngineName.value = name
    }

    fun setTranslateLanguageCode(code: String) {
        prefs.edit().putString("pref_translate_lang_code", code).apply()
        _translateLanguageCode.value = code
    }

    fun setShortcuts(shortcuts: List<ShortcutItem>) {
        val encoded = serializeShortcuts(shortcuts)
        prefs.edit().putString("pref_shortcuts_json", encoded).apply()
        _shortcuts.value = shortcuts
    }

    fun setSearchEngineValue(engineName: String) {
        setSearchEngineName(engineName)
    }

    fun saveTabsState(tabs: List<SavedTabState>) {
        val encoded = serializeSavedTabs(tabs)
        prefs.edit().putString("saved_tabs_json", encoded).apply()
        _savedTabs.value = tabs
    }
}
