package io.github.xoyzoom.ymmod.settings

import androidx.annotation.StringRes
import io.github.xoyzoom.ymmod.R
import org.json.JSONObject
import java.net.URLEncoder

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

/** [cssFamily] — значение CSS font-family; null означает «не менять шрифт сайта». */
enum class FontPreset(@StringRes val labelRes: Int, val cssFamily: String?) {
    SITE_DEFAULT(R.string.font_site_default, null),
    ROBOTO(R.string.font_roboto, "\"Roboto\", sans-serif"),
    SERIF(R.string.font_serif, "\"Noto Serif\", serif"),
    MONO(R.string.font_mono, "\"Droid Sans Mono\", monospace"),
    CONDENSED(R.string.font_condensed, "\"Roboto Condensed\", sans-serif-condensed, sans-serif"),
    CUSTOM(R.string.font_custom, null),
}

/** Учётные данные Last.fm. Ключ и секрет пользователь получает сам на last.fm/api. */
data class LastFmSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val apiSecret: String = "",
    val username: String = "",
    val sessionKey: String = "",
) {
    val isAuthorized: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank() && sessionKey.isNotBlank()
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontPreset: FontPreset = FontPreset.SITE_DEFAULT,
    val customFontName: String = "",
    val desktopMode: Boolean = false,
    val discordEnabled: Boolean = true,
    /** Выбирать «Высокое» качество по умолчанию (как auto-best-quality оригинала). */
    val autoBestQuality: Boolean = true,
    /** Не показывать окно «Скачайте приложение Яндекс Музыки». */
    val hideAppPromo: Boolean = true,
    /** Не загружать счётчики аналитики (Яндекс Метрика). */
    val blockTrackers: Boolean = true,
    val snowEffect: Boolean = false,
    /** Масштаб текста сайта, % (WebSettings.textZoom). */
    val uiScale: Int = DEFAULT_UI_SCALE,
    /** Плавающая кнопка меню мода поверх сайта. */
    val floatingButton: Boolean = false,
    /** Отладка WebView через chrome://inspect. */
    val devMode: Boolean = false,
    val onboardingShown: Boolean = false,
    val lastFm: LastFmSettings = LastFmSettings(),
) {
    companion object {
        const val DEFAULT_UI_SCALE = 100
        const val MIN_UI_SCALE = 75
        const val MAX_UI_SCALE = 150
    }
}

/** Что передать в `window.__ymmod.setFont(family, stylesheetUrl)`. */
data class FontSpec(val cssFamily: String?, val stylesheetUrl: String?) {
    companion object {
        val NONE = FontSpec(null, null)
    }
}

fun AppSettings.toFontSpec(): FontSpec {
    if (fontPreset != FontPreset.CUSTOM) return FontSpec(fontPreset.cssFamily, null)

    // Убираем символы, которыми можно выйти за пределы значения font-family.
    val name = customFontName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim()
    if (name.isEmpty()) return FontSpec.NONE

    // Тот же сервис, что и в font-changer оригинального проекта.
    val family = URLEncoder.encode(name, "UTF-8")
    return FontSpec(
        cssFamily = "\"$name\", sans-serif",
        stylesheetUrl = "https://fonts.bunny.net/css?family=$family:300,400,500,600,700",
    )
}

/** Настройки, которые читает внедрённый скрипт (`window.__ymmodConfig` / `__ymmod.configure`). */
data class PageConfig(
    val autoBestQuality: Boolean,
    val hideAppPromo: Boolean,
    val snow: Boolean,
) {
    fun toJson(): String = JSONObject()
        .put("autoBestQuality", autoBestQuality)
        .put("hideAppPromo", hideAppPromo)
        .put("snow", snow)
        .toString()
}

fun AppSettings.toPageConfig() = PageConfig(
    autoBestQuality = autoBestQuality,
    hideAppPromo = hideAppPromo,
    snow = snowEffect,
)

fun Int.coerceUiScale(): Int = coerceIn(AppSettings.MIN_UI_SCALE, AppSettings.MAX_UI_SCALE)
