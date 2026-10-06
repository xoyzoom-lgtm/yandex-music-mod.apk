package io.github.xoyzoom.ymmod.player

import org.json.JSONException
import org.json.JSONObject

/** Разбирает сообщения от `assets/js/ymmod-bridge.js`. Данные приходят со страницы, поэтому проверяем всё. */
object PlayerMessageParser {
    private const val MAX_TEXT_LENGTH = 512
    private const val MAX_URL_LENGTH = 2048

    fun parse(raw: String, receivedAtMs: Long): PlayerState? {
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return null
        }
        if (root.optString("type") != "state") return null
        val payload = root.optJSONObject("payload") ?: return null

        val title = payload.text("title")
        val artist = payload.text("artist")
        val track = if (title.isEmpty() && artist.isEmpty()) {
            null
        } else {
            TrackInfo(
                title = title,
                artist = artist,
                album = payload.text("album"),
                artworkUrl = payload.optString("artworkUrl")
                    .takeIf { it.startsWith("https://") && it.length <= MAX_URL_LENGTH },
            )
        }

        return PlayerState(
            track = track,
            isPlaying = payload.optBoolean("playing", false),
            hasPosition = payload.optBoolean("hasPosition", false),
            positionMs = payload.optLong("positionMs", 0).coerceAtLeast(0),
            durationMs = payload.optLong("durationMs", 0).coerceAtLeast(0),
            updatedAtMs = receivedAtMs,
        )
    }

    private fun JSONObject.text(key: String): String =
        optString(key).trim().take(MAX_TEXT_LENGTH)
}
