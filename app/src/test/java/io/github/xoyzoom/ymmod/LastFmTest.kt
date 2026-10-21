package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.player.TrackInfo
import io.github.xoyzoom.ymmod.scrobble.LastFmClient
import io.github.xoyzoom.ymmod.scrobble.ScrobbleTracker
import io.github.xoyzoom.ymmod.scrobble.ScrobbleTracker.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmTest {
    @Test
    fun signsSortedParams() {
        val signed = LastFmClient.sign(
            mapOf("method" to "track.scrobble", "sk" to "SK", "artist" to "A", "track" to "T", "timestamp" to "100", "api_key" to "KEY"),
            "SECRET",
        )
        assertEquals("12c266dc93266f9a9399e5378b1a2d71", signed["api_sig"])
    }

    private fun state(title: String?, playing: Boolean, positionMs: Long = 0, durationMs: Long = 200_000, at: Long = 0) =
        PlayerState(
            track = title?.let { TrackInfo(it, "Artist", "Album", null) },
            isPlaying = playing,
            hasPosition = true,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtMs = at,
        )

    @Test
    fun announcesThenScrobblesAfterHalf() {
        val tracker = ScrobbleTracker()
        val start = tracker.onState(state("Song", playing = true), nowMs = 1_000_000)
        assertEquals(1, start.size)
        assertTrue(start.single() is Action.NowPlaying)
        assertEquals(1_000L, start.single().scrobble.startedAtSec)

        assertTrue(tracker.tick(1_000_000 + 99_000).isEmpty())
        val submit = tracker.tick(1_000_000 + 100_000)
        assertTrue(submit.single() is Action.Submit)
        // Повторно тот же проигрыш не отправляется.
        assertTrue(tracker.tick(1_000_000 + 150_000).isEmpty())
    }

    @Test
    fun pausedTimeDoesNotCount() {
        val tracker = ScrobbleTracker()
        tracker.onState(state("Song", playing = true), 0)
        tracker.onState(state("Song", playing = false, positionMs = 60_000), 60_000)
        assertTrue(tracker.tick(600_000).isEmpty())
        tracker.onState(state("Song", playing = true, positionMs = 60_000), 600_000)
        assertTrue(tracker.tick(639_000).isEmpty())
        assertTrue(tracker.tick(640_000).single() is Action.Submit)
    }

    @Test
    fun longTracksNeedFourMinutesAndShortOnesNever() {
        val long = ScrobbleTracker()
        long.onState(state("Long", playing = true, durationMs = 20 * 60_000), 0)
        assertTrue(long.tick(239_000).isEmpty())
        assertTrue(long.tick(240_000).single() is Action.Submit)

        val short = ScrobbleTracker()
        short.onState(state("Jingle", playing = true, durationMs = 20_000), 0)
        assertTrue(short.tick(60_000).isEmpty())
    }

    @Test
    fun newTrackAndRepeatStartNewPlays() {
        val tracker = ScrobbleTracker()
        tracker.onState(state("One", playing = true), 0)
        tracker.tick(100_000)
        val repeat = tracker.onState(state("One", playing = true, positionMs = 1_000), 200_000)
        assertTrue(repeat.single() is Action.NowPlaying)
        val next = tracker.onState(state("Two", playing = true), 210_000)
        assertEquals("Two", next.single().scrobble.track)
        assertTrue(tracker.onState(state(null, playing = false), 220_000).isEmpty())
    }
}
