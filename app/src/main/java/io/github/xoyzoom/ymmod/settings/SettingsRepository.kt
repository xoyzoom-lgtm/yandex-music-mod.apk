package io.github.xoyzoom.ymmod.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
        val AUTO_BEST_QUALITY = booleanPreferencesKey("auto_best_quality")
        val HIDE_APP_PROMO = booleanPreferencesKey("hide_app_promo")
        val BLOCK_TRACKERS = booleanPreferencesKey("block_trackers")
        val SNOW_EFFECT = booleanPreferencesKey("snow_effect")
        val UI_SCALE = intPreferencesKey("ui_scale")
        val FLOATING_BUTTON = booleanPreferencesKey("floating_button")
        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        val LASTFM_ENABLED = booleanPreferencesKey("lastfm_enabled")
        val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key")
        val LASTFM_API_SECRET = stringPreferencesKey("lastfm_api_secret")
        val LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
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
                autoBestQuality = prefs[Keys.AUTO_BEST_QUALITY] ?: defaults.autoBestQuality,
                hideAppPromo = prefs[Keys.HIDE_APP_PROMO] ?: defaults.hideAppPromo,
                blockTrackers = prefs[Keys.BLOCK_TRACKERS] ?: defaults.blockTrackers,
                snowEffect = prefs[Keys.SNOW_EFFECT] ?: defaults.snowEffect,
                uiScale = (prefs[Keys.UI_SCALE] ?: defaults.uiScale).coerceUiScale(),
                floatingButton = prefs[Keys.FLOATING_BUTTON] ?: defaults.floatingButton,
                devMode = prefs[Keys.DEV_MODE] ?: defaults.devMode,
                onboardingShown = prefs[Keys.ONBOARDING_SHOWN] ?: defaults.onboardingShown,
                lastFm = LastFmSettings(
                    enabled = prefs[Keys.LASTFM_ENABLED] ?: false,
                    apiKey = prefs[Keys.LASTFM_API_KEY].orEmpty(),
                    apiSecret = prefs[Keys.LASTFM_API_SECRET].orEmpty(),
                    username = prefs[Keys.LASTFM_USERNAME].orEmpty(),
                    sessionKey = prefs[Keys.LASTFM_SESSION_KEY].orEmpty(),
                ),
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setFontPreset(value: FontPreset) = edit { it[Keys.FONT_PRESET] = value.name }

    suspend fun setCustomFontName(value: String) = edit { it[Keys.CUSTOM_FONT_NAME] = value }

    suspend fun setDesktopMode(value: Boolean) = edit { it[Keys.DESKTOP_MODE] = value }

    suspend fun setDiscordEnabled(value: Boolean) = edit { it[Keys.DISCORD_ENABLED] = value }

    suspend fun setAutoBestQuality(value: Boolean) = edit { it[Keys.AUTO_BEST_QUALITY] = value }

    suspend fun setHideAppPromo(value: Boolean) = edit { it[Keys.HIDE_APP_PROMO] = value }

    suspend fun setBlockTrackers(value: Boolean) = edit { it[Keys.BLOCK_TRACKERS] = value }

    suspend fun setSnowEffect(value: Boolean) = edit { it[Keys.SNOW_EFFECT] = value }

    suspend fun setUiScale(value: Int) = edit { it[Keys.UI_SCALE] = value.coerceUiScale() }

    suspend fun setFloatingButton(value: Boolean) = edit { it[Keys.FLOATING_BUTTON] = value }

    suspend fun setDevMode(value: Boolean) = edit { it[Keys.DEV_MODE] = value }

    suspend fun setOnboardingShown() = edit { it[Keys.ONBOARDING_SHOWN] = true }

    suspend fun setLastFmEnabled(value: Boolean) = edit { it[Keys.LASTFM_ENABLED] = value }

    suspend fun setLastFmCredentials(apiKey: String, apiSecret: String) = edit {
        it[Keys.LASTFM_API_KEY] = apiKey.trim()
        it[Keys.LASTFM_API_SECRET] = apiSecret.trim()
    }

    suspend fun setLastFmSession(username: String, sessionKey: String) = edit {
        it[Keys.LASTFM_USERNAME] = username
        it[Keys.LASTFM_SESSION_KEY] = sessionKey
        it[Keys.LASTFM_ENABLED] = true
    }

    suspend fun clearLastFmSession() = edit {
        it.remove(Keys.LASTFM_USERNAME)
        it.remove(Keys.LASTFM_SESSION_KEY)
        it[Keys.LASTFM_ENABLED] = false
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
