package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.player.TrackInfo
import io.github.xoyzoom.ymmod.presence.PresenceMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceMapperTest {
    private val playing = PlayerState(
        track = TrackInfo("Song", "Artist", "Album", "https://example.com/a.jpg"),
        isPlaying = true,
        hasPosition = true,
        positionMs = 30_000,
        durationMs = 180_000,
        updatedAtMs = 1_000_000,
    )

    @Test
    fun mapsPlayingTrack() {
        val presence = PresenceMapper.map(playing, enabled = true)!!
        assertEquals("Song", presence.details)
        assertEquals("Artist", presence.state)
        assertEquals("Album", presence.largeImageText)
        assertEquals("https://example.com/a.jpg", presence.largeImageUrl)
        assertEquals(970_000L, presence.startTimestampMs)
        assertEquals(1_150_000L, presence.endTimestampMs)
    }

    @Test
    fun clearsWhenPausedDisabledOrEmpty() {
        assertNull(PresenceMapper.map(playing.copy(isPlaying = false), enabled = true))
        assertNull(PresenceMapper.map(playing, enabled = false))
        assertNull(PresenceMapper.map(playing.copy(track = null), enabled = true))
    }

    @Test
    fun noTimestampsWithoutPosition() {
        val presence = PresenceMapper.map(playing.copy(hasPosition = false), enabled = true)!!
        assertNull(presence.startTimestampMs)
        assertNull(presence.endTimestampMs)
    }

    @Test
    fun enforcesDiscordLengthLimits() {
        val presence = PresenceMapper.map(
            playing.copy(track = TrackInfo("A", "x".repeat(300), "", null)),
            enabled = true,
        )!!
        assertEquals(2, presence.details.length)
        assertEquals(128, presence.state.length)
        assertNull(presence.largeImageText)
    }
}
