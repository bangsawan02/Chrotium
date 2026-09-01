package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_settings")

@Serializable
data class SavedTabState(
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = false
)

class BrowserSettingsDataStore(private val context: Context) {

    private val jsonFormatter = Json { ignoreUnknownKeys = true }

    companion object {
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val SAVED_TABS_JSON = stringPreferencesKey("saved_tabs_json")
    }

    val isAdBlockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AD_BLOCK_ENABLED] ?: true
    }

    val isDesktopModeDefault: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DESKTOP_MODE_DEFAULT] ?: false
    }

    val searchEngine: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SEARCH_ENGINE] ?: "https://www.google.com/search?q="
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

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AD_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun setDesktopModeDefault(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DESKTOP_MODE_DEFAULT] = enabled
        }
    }

    suspend fun setSearchEngine(engineUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_ENGINE] = engineUrl
        }
    }

    suspend fun saveTabsState(tabs: List<SavedTabState>) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_TABS_JSON] = jsonFormatter.encodeToString(tabs)
        }
    }
}
