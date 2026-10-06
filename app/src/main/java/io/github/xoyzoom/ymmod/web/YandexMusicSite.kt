package io.github.xoyzoom.ymmod.web

import android.net.Uri

object YandexMusicSite {
    const val START_URL = "https://music.yandex.ru/"

    /**
     * Origin'ы, в которые внедряется скрипт и от которых принимаются сообщения моста.
     * Страницы входа (passport.yandex.*) сюда намеренно не входят.
     */
    val ALLOWED_ORIGINS: Set<String> = setOf(
        "https://music.yandex.ru",
        "https://next.music.yandex.ru",
        "https://music.yandex.by",
        "https://music.yandex.kz",
        "https://music.yandex.uz",
        "https://music.yandex.com",
    )

    fun isMusicOrigin(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "https") return false
        return "https://${uri.host}" in ALLOWED_ORIGINS && (uri.port == -1 || uri.port == 443)
    }
}
