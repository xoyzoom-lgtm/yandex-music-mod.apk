package io.github.xoyzoom.ymmod.player

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
)

/**
 * Состояние веб-плеера, как его видит внедрённый скрипт.
 *
 * [positionMs] — позиция на момент [updatedAtMs] (время получения сообщения, System.currentTimeMillis).
 */
data class PlayerState(
    val track: TrackInfo?,
    val isPlaying: Boolean,
    val hasPosition: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
) {
    fun estimatedPositionMs(nowMs: Long): Long {
        val position = if (isPlaying) positionMs + (nowMs - updatedAtMs) else positionMs
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }

    companion object {
        val EMPTY = PlayerState(
            track = null,
            isPlaying = false,
            hasPosition = false,
            positionMs = 0,
            durationMs = 0,
            updatedAtMs = 0,
        )
    }
}
