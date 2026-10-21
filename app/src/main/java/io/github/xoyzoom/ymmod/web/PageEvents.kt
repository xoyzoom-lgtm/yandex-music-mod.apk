package io.github.xoyzoom.ymmod.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

/** Сообщения от `assets/js/ymmod-bridge.js`, кроме состояния плеера (его разбирает PlayerMessageParser). */
sealed interface PageEvent {
    /** Цвет фона страницы (ARGB) — им красятся области под системными панелями. */
    data class Theme(val backgroundArgb: Int) : PageEvent

    /** Нажат пункт «YM Mod» в настройках сайта. */
    data object OpenModSettings : PageEvent

    data class Likes(val progress: LikesProgress) : PageEvent

    data class CollectionLink(val url: String?, val error: String?) : PageEvent

    data class Snapshot(val path: String, val width: Int, val height: Int, val html: String) : PageEvent
}

data class LikesProgress(
    val status: Status,
    val done: Int,
    val total: Int,
    val message: String,
) {
    enum class Status { IDLE, PROGRESS, DONE, STOPPED, ERROR }

    val isRunning: Boolean get() = status == Status.PROGRESS

    companion object {
        val IDLE = LikesProgress(Status.IDLE, 0, 0, "")
    }
}

object PageEventParser {
    private const val MAX_TEXT_LENGTH = 512
    private const val MAX_SNAPSHOT_LENGTH = 16 * 1024 * 1024

    fun parse(raw: String): PageEvent? {
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return null
        }
        val payload = root.optJSONObject("payload") ?: JSONObject()
        return when (root.optString("type")) {
            "theme" -> parseCssColor(payload.optString("background"))?.let(PageEvent::Theme)
            "openModSettings" -> PageEvent.OpenModSettings
            "likes" -> PageEvent.Likes(
                LikesProgress(
                    status = when (payload.optString("status")) {
                        "progress" -> LikesProgress.Status.PROGRESS
                        "done" -> LikesProgress.Status.DONE
                        "stopped" -> LikesProgress.Status.STOPPED
                        else -> LikesProgress.Status.ERROR
                    },
                    done = payload.optInt("done").coerceAtLeast(0),
                    total = payload.optInt("total").coerceAtLeast(0),
                    message = payload.optString("message").take(MAX_TEXT_LENGTH),
                ),
            )
            "collectionLink" -> PageEvent.CollectionLink(
                url = payload.optString("url").takeIf { it.startsWith("https://") && it.length <= MAX_TEXT_LENGTH },
                error = payload.optString("error").takeIf { it.isNotEmpty() }?.take(MAX_TEXT_LENGTH),
            )
            "snapshot" -> {
                val html = payload.optString("html")
                if (html.isEmpty() || html.length > MAX_SNAPSHOT_LENGTH) {
                    null
                } else {
                    PageEvent.Snapshot(
                        path = payload.optString("url").take(MAX_TEXT_LENGTH),
                        width = payload.optInt("width"),
                        height = payload.optInt("height"),
                        html = html,
                    )
                }
            }
            else -> null
        }
    }

    private val rgbPattern = Regex("""rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+%?))?\s*\)""")

    /** Разбирает `rgb(r, g, b)` / `rgba(r, g, b, a)` из getComputedStyle. Прозрачные цвета не подходят. */
    fun parseCssColor(value: String): Int? {
        val match = rgbPattern.matchEntire(value.trim()) ?: return null
        val (r, g, b) = match.destructured.toList().take(3).map { it.toInt().coerceIn(0, 255) }
        val alpha = match.groupValues[4].takeIf { it.isNotEmpty() }?.let {
            if (it.endsWith("%")) it.dropLast(1).toFloatOrNull()?.div(100f) else it.toFloatOrNull()
        } ?: 1f
        if (alpha < 0.99f) return null
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** Канал между WebView и Compose-экранами. */
class PageEventBus {
    private val _events = MutableSharedFlow<PageEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PageEvent> = _events.asSharedFlow()

    private val _pageBackground = MutableStateFlow<Int?>(null)
    val pageBackground: StateFlow<Int?> = _pageBackground.asStateFlow()

    private val _likes = MutableStateFlow(LikesProgress.IDLE)
    val likes: StateFlow<LikesProgress> = _likes.asStateFlow()

    fun emit(event: PageEvent) {
        when (event) {
            is PageEvent.Theme -> _pageBackground.value = event.backgroundArgb
            is PageEvent.Likes -> _likes.value = event.progress
            else -> Unit
        }
        _events.tryEmit(event)
    }
}
