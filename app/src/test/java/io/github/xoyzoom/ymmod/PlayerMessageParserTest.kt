package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.player.PlayerMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMessageParserTest {
    private fun message(payload: String) = """{"type":"state","payload":$payload}"""

    @Test
    fun parsesFullState() {
        val state = PlayerMessageParser.parse(
            message(
                """{"title":"Song","artist":"Artist","album":"Album",
                   "artworkUrl":"https://avatars.yandex.net/cover/400x400",
                   "playing":true,"hasPosition":true,"positionMs":15000,"durationMs":200000}""",
            ),
            receivedAtMs = 1_000_000,
        )

        assertNotNull(state)
        state!!
        assertEquals("Song", state.track?.title)
        assertEquals("Artist", state.track?.artist)
        assertEquals("Album", state.track?.album)
        assertEquals("https://avatars.yandex.net/cover/400x400", state.track?.artworkUrl)
        assertTrue(state.isPlaying)
        assertEquals(15_000L, state.positionMs)
        assertEquals(200_000L, state.durationMs)
        assertEquals(1_000_000L, state.updatedAtMs)
    }

    @Test
    fun emptyMetadataMeansNoTrack() {
        val state = PlayerMessageParser.parse(message("""{"title":"","artist":"","playing":false}"""), 0)
        assertNotNull(state)
        assertNull(state!!.track)
        assertFalse(state.isPlaying)
    }

    @Test
    fun rejectsNonHttpsArtwork() {
        val state = PlayerMessageParser.parse(
            message("""{"title":"Song","artist":"A","artworkUrl":"javascript:alert(1)"}"""),
            0,
        )
        assertNull(state!!.track?.artworkUrl)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(PlayerMessageParser.parse("not json", 0))
        assertNull(PlayerMessageParser.parse("""{"type":"other","payload":{}}""", 0))
        assertNull(PlayerMessageParser.parse("""{"type":"state"}""", 0))
    }

    @Test
    fun clampsNegativeNumbersAndLongStrings() {
        val longTitle = "x".repeat(2000)
        val state = PlayerMessageParser.parse(
            message("""{"title":"$longTitle","artist":"A","positionMs":-5,"durationMs":-1}"""),
            0,
        )!!
        assertEquals(512, state.track!!.title.length)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
    }
}
