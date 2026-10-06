package io.github.xoyzoom.ymmod.presence

import io.github.xoyzoom.ymmod.player.PlayerState

/**
 * Не зависящее от SDK описание активности в Discord.
 * Discord требует для details/state от 2 до 128 символов — это обеспечивает [PresenceMapper].
 */
data class RichPresence(
    val details: String,
    val state: String,
    val largeImageUrl: String?,
    val largeImageText: String?,
    val startTimestampMs: Long?,
    val endTimestampMs: Long?,
)

object PresenceMapper {
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 128

    /** null — активность нужно очистить. */
    fun map(state: PlayerState, enabled: Boolean): RichPresence? {
        if (!enabled || !state.isPlaying) return null
        val track = state.track ?: return null

        val start = if (state.hasPosition) state.updatedAtMs - state.positionMs else null
        val end = if (start != null && state.durationMs > 0) start + state.durationMs else null

        return RichPresence(
            details = track.title.ifBlank { track.artist }.forDiscord(),
            state = track.artist.ifBlank { track.album }.forDiscord(),
            largeImageUrl = track.artworkUrl,
            largeImageText = track.album.takeIf { it.isNotBlank() }?.forDiscord(),
            startTimestampMs = start,
            endTimestampMs = end,
        )
    }

    internal fun String.forDiscord(): String {
        val text = trim().take(MAX_LENGTH)
        // Неразрывный пробел, чтобы дотянуть короткие строки до минимальной длины.
        return if (text.length >= MIN_LENGTH) text else text.padEnd(MIN_LENGTH, ' ')
    }
}
