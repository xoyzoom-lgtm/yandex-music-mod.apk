package io.github.xoyzoom.ymmod.scrobble

import io.github.xoyzoom.ymmod.net.Http
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

class LastFmException(message: String) : Exception(message)

/**
 * Клиент [Last.fm API](https://www.last.fm/api) для скробблинга (как в PulseSync-mod).
 * Ключ и секрет API пользователь создаёт сам на https://www.last.fm/api/account/create.
 */
class LastFmClient(
    private val apiKey: String,
    private val apiSecret: String,
    private val endpoint: String = "https://ws.audioscrobbler.com/2.0/",
) {
    /** Вход по логину и паролю (auth.getMobileSession). Пароль не сохраняется. */
    suspend fun mobileSession(username: String, password: String): Pair<String, String> {
        val json = call(mapOf("method" to "auth.getMobileSession", "username" to username, "password" to password))
        val session = json.optJSONObject("session") ?: throw LastFmException("Нет сессии в ответе Last.fm")
        val key = session.optString("key").takeIf { it.isNotBlank() } ?: throw LastFmException("Пустой ключ сессии")
        return session.optString("name", username) to key
    }

    suspend fun updateNowPlaying(sessionKey: String, scrobble: Scrobble) {
        call(
            mapOf("method" to "track.updateNowPlaying", "sk" to sessionKey) + scrobble.params(withTimestamp = false),
        )
    }

    suspend fun scrobble(sessionKey: String, scrobble: Scrobble) {
        call(mapOf("method" to "track.scrobble", "sk" to sessionKey) + scrobble.params(withTimestamp = true))
    }

    private suspend fun call(params: Map<String, String>): JSONObject {
        val signed = sign(params + ("api_key" to apiKey), apiSecret) + ("format" to "json")
        val response = Http.postForm(endpoint, signed)
        val json = try {
            JSONObject(response.body)
        } catch (_: JSONException) {
            throw LastFmException("HTTP ${response.code}")
        }
        if (json.has("error")) throw LastFmException(json.optString("message", "Ошибка ${json.optInt("error")}"))
        if (!response.isSuccessful) throw LastFmException("HTTP ${response.code}")
        return json
    }

    companion object {
        /** Подпись api_sig: md5 от отсортированных пар «ключзначение» и секрета. format и callback не входят. */
        fun sign(params: Map<String, String>, secret: String): Map<String, String> {
            val base = params.toSortedMap().entries.joinToString("") { (key, value) -> key + value } + secret
            val digest = MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8))
            return params + ("api_sig" to digest.joinToString("") { "%02x".format(it) })
        }
    }
}

data class Scrobble(
    val artist: String,
    val track: String,
    val album: String,
    val durationMs: Long,
    /** Время начала прослушивания, секунды Unix. */
    val startedAtSec: Long,
) {
    fun params(withTimestamp: Boolean): Map<String, String> = buildMap {
        put("artist", artist)
        put("track", track)
        if (album.isNotBlank()) put("album", album)
        if (durationMs > 0) put("duration", (durationMs / 1000).toString())
        if (withTimestamp) put("timestamp", startedAtSec.toString())
    }
}
