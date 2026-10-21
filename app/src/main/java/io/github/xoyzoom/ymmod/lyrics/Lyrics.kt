package io.github.xoyzoom.ymmod.lyrics

import io.github.xoyzoom.ymmod.net.Http
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    /** Строки с таймкодами по возрастанию времени. */
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /** Индекс строки, которая звучит в момент [positionMs], или -1 до первой строки. */
        fun indexAt(positionMs: Long): Int {
            var low = 0
            var high = lines.lastIndex
            var result = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }
    }

    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Разбор формата LRC: `[mm:ss.xx] текст`, несколько таймкодов на строку, метатеги игнорируются. */
object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val result = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { rawLine ->
            val matches = timestamp.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            // Таймкоды идут подряд в начале строки, текст — после последнего.
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                result += LyricLine(minutes * 60_000 + seconds * 1000 + millis, text)
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

/**
 * Тексты из [LRCLIB](https://lrclib.net) — открытой базы текстов песен (как в PulseSync-mod).
 * Нужен на случай, когда у трека нет текста в Яндекс Музыке.
 */
class LrclibClient(private val baseUrl: String = "https://lrclib.net/api") {
    suspend fun find(title: String, artist: String, album: String, durationMs: Long): Lyrics {
        val params = buildMap {
            put("track_name", title)
            put("artist_name", artist)
            if (album.isNotBlank()) put("album_name", album)
            if (durationMs > 0) put("duration", (durationMs / 1000).toString())
        }
        val exact = Http.get("$baseUrl/get?${Http.formEncode(params)}")
        if (exact.isSuccessful) parseRecord(exact.body)?.let { return it }

        // Точного совпадения нет (другой альбом или длительность) — берём лучший результат поиска.
        val search = Http.get("$baseUrl/search?${Http.formEncode(mapOf("track_name" to title, "artist_name" to artist))}")
        if (!search.isSuccessful) return Lyrics.NotFound
        return pickFromSearch(search.body, durationMs) ?: Lyrics.NotFound
    }

    companion object {
        fun parseRecord(json: String): Lyrics? = try {
            fromRecord(JSONObject(json))
        } catch (_: JSONException) {
            null
        }

        fun pickFromSearch(json: String, durationMs: Long): Lyrics? {
            val items = try {
                JSONArray(json)
            } catch (_: JSONException) {
                return null
            }
            val records = (0 until items.length()).mapNotNull(items::optJSONObject)
            val ranked = records.sortedWith(
                compareBy<JSONObject> { if (it.optString("syncedLyrics").isBlank()) 1 else 0 }
                    .thenBy { if (durationMs > 0) kotlin.math.abs(it.optDouble("duration", 0.0) * 1000 - durationMs) else 0.0 },
            )
            return ranked.firstNotNullOfOrNull(::fromRecord)
        }

        private fun fromRecord(record: JSONObject): Lyrics? {
            if (record.optBoolean("instrumental")) return Lyrics.Instrumental
            val synced = record.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (synced != null) {
                val lines = LrcParser.parse(synced)
                if (lines.isNotEmpty()) return Lyrics.Synced(lines)
            }
            val plain = record.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            return plain?.let { Lyrics.Plain(it.trim()) }
        }
    }
}
