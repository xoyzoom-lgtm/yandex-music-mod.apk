package io.github.xoyzoom.ymmod.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_PRESET = stringPreferencesKey("font_preset")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val DISCORD_ENABLED = booleanPreferencesKey("discord_enabled")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = enumValueOrDefault(prefs[Keys.THEME_MODE], defaults.themeMode),
                fontPreset = enumValueOrDefault(prefs[Keys.FONT_PRESET], defaults.fontPreset),
                customFontName = prefs[Keys.CUSTOM_FONT_NAME] ?: defaults.customFontName,
                desktopMode = prefs[Keys.DESKTOP_MODE] ?: defaults.desktopMode,
                discordEnabled = prefs[Keys.DISCORD_ENABLED] ?: defaults.discordEnabled,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) = dataStore.edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setFontPreset(value: FontPreset) = dataStore.edit { it[Keys.FONT_PRESET] = value.name }

    suspend fun setCustomFontName(value: String) = dataStore.edit { it[Keys.CUSTOM_FONT_NAME] = value }

    suspend fun setDesktopMode(value: Boolean) = dataStore.edit { it[Keys.DESKTOP_MODE] = value }

    suspend fun setDiscordEnabled(value: Boolean) = dataStore.edit { it[Keys.DISCORD_ENABLED] = value }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
