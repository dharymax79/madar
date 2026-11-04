package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "madar_browser_prefs")

data class BrowserPreferencesState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isRtl: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val javaScriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val askBeforeDownload: Boolean = true,
    val defaultCustomFolderUri: String = "",
    val defaultCustomFolderName: String = "مجلد التحميلات الافتراضي (Downloads)",
    val savedCustomFolders: List<CustomFolderOption> = emptyList()
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_RTL = booleanPreferencesKey("is_rtl")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val DEFAULT_FOLDER_URI = stringPreferencesKey("default_folder_uri")
        val DEFAULT_FOLDER_NAME = stringPreferencesKey("default_folder_name")
        val SAVED_FOLDERS = stringPreferencesKey("saved_folders")
    }

    val preferencesFlow: Flow<BrowserPreferencesState> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val engineStr = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.name
        val savedFoldersRaw = prefs[Keys.SAVED_FOLDERS].orEmpty()

        val parsedFolders = savedFoldersRaw
            .split(";;;")
            .mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size >= 3 && parts[0].isNotBlank()) {
                    CustomFolderOption(
                        uriString = parts[0],
                        displayName = parts[1],
                        subtitlePath = parts[2]
                    )
                } else null
            }

        BrowserPreferencesState(
            themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.SYSTEM),
            isRtl = prefs[Keys.IS_RTL] ?: true,
            searchEngine = runCatching { SearchEngine.valueOf(engineStr) }.getOrDefault(SearchEngine.GOOGLE),
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            cookiesEnabled = prefs[Keys.COOKIES_ENABLED] ?: true,
            askBeforeDownload = prefs[Keys.ASK_BEFORE_DOWNLOAD] ?: true,
            defaultCustomFolderUri = prefs[Keys.DEFAULT_FOLDER_URI] ?: "",
            defaultCustomFolderName = prefs[Keys.DEFAULT_FOLDER_NAME] ?: "مجلد التحميلات الافتراضي (Downloads)",
            savedCustomFolders = parsedFolders
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setRtlDirection(isRtl: Boolean) {
        context.dataStore.edit { it[Keys.IS_RTL] = isRtl }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setCookiesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.COOKIES_ENABLED] = enabled }
    }

    suspend fun setAskBeforeDownload(ask: Boolean) {
        context.dataStore.edit { it[Keys.ASK_BEFORE_DOWNLOAD] = ask }
    }

    suspend fun setDefaultDownloadFolder(uriString: String, displayName: String, subtitlePath: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_FOLDER_URI] = uriString
            prefs[Keys.DEFAULT_FOLDER_NAME] = displayName
            if (uriString.isNotBlank()) {
                val existingRaw = prefs[Keys.SAVED_FOLDERS].orEmpty()
                val existing = existingRaw.split(";;;").filter { it.isNotBlank() && !it.startsWith("$uriString|||") }
                val updated = listOf("$uriString|||$displayName|||$subtitlePath") + existing.take(5)
                prefs[Keys.SAVED_FOLDERS] = updated.joinToString(";;;")
            }
        }
    }
}
