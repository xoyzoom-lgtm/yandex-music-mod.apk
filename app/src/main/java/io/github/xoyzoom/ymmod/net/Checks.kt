package io.github.xoyzoom.ymmod.net

import org.json.JSONException
import org.json.JSONObject

data class ReleaseInfo(val version: String, val url: String, val apkUrl: String?)

/** Проверка новых версий в Releases этого репозитория (вместо «Доступно обновление» оригинала). */
object UpdateChecker {
    const val LATEST_RELEASE_API = "https://api.github.com/repos/xoyzoom-lgtm/yandex-music-mod.apk/releases/latest"

    suspend fun latest(): ReleaseInfo? {
        val response = Http.get(LATEST_RELEASE_API, mapOf("Accept" to "application/vnd.github+json"))
        return if (response.isSuccessful) parse(response.body) else null
    }

    fun parse(json: String): ReleaseInfo? = try {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").removePrefix("v").trim()
        val assets = root.optJSONArray("assets")
        val apk = assets?.let { list ->
            (0 until list.length()).mapNotNull(list::optJSONObject)
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
        }
        if (tag.isEmpty()) null else ReleaseInfo(tag, root.optString("html_url"), apk?.takeIf { it.startsWith("https://") })
    } catch (_: JSONException) {
        null
    }

    /** true, если [candidate] новее [current]. Суффиксы вроде «-dev»/«-debug» не учитываются. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return emptyList() }
}

data class RegionInfo(val countryCode: String, val isSupported: Boolean)

/**
 * Проверка страны по IP (аналог ip-checker оригинала): Яндекс Музыка работает только в
 * части стран (https://yandex.ru/support/music/ru/access.html).
 */
object RegionChecker {
    /** Список из оригинального мода (src/mod/features/ui/lib/allowedCountries.json). */
    val SUPPORTED = setOf("AZ", "AM", "BY", "GE", "IL", "KZ", "KG", "MD", "RU", "TJ", "TM", "UZ")

    suspend fun check(): RegionInfo? {
        val response = Http.get("https://api.country.is/")
        return if (response.isSuccessful) parse(response.body) else null
    }

    fun parse(json: String): RegionInfo? = try {
        val code = JSONObject(json).optString("country").uppercase()
        if (code.length == 2) RegionInfo(code, code in SUPPORTED) else null
    } catch (_: JSONException) {
        null
    }
}
