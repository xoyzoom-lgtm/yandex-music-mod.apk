package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.lyrics.LrcParser
import io.github.xoyzoom.ymmod.lyrics.LrclibClient
import io.github.xoyzoom.ymmod.lyrics.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesTimestampsAndSkipsMetadata() {
        val lines = LrcParser.parse(
            """
            [ar: Artist]
            [00:12.34] First line
            [00:05.1]Intro
            [01:02.003][02:00.00] Chorus
            no timestamp here
            """.trimIndent(),
        )
        assertEquals(listOf(5_100L, 12_340L, 62_003L, 120_000L), lines.map { it.timeMs })
        assertEquals(listOf("Intro", "First line", "Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun findsActiveLine() {
        val synced = Lyrics.Synced(LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c"))
        assertEquals(-1, synced.indexAt(500))
        assertEquals(0, synced.indexAt(1_000))
        assertEquals(1, synced.indexAt(8_999))
        assertEquals(2, synced.indexAt(60_000))
    }

    @Test
    fun prefersSyncedLyricsFromRecord() {
        val lyrics = LrclibClient.parseRecord(
            """{"instrumental":false,"plainLyrics":"a","syncedLyrics":"[00:01.00]a"}""",
        )
        assertTrue(lyrics is Lyrics.Synced)
        assertEquals(Lyrics.Plain("plain"), LrclibClient.parseRecord("""{"plainLyrics":"plain","syncedLyrics":null}"""))
        assertEquals(Lyrics.Instrumental, LrclibClient.parseRecord("""{"instrumental":true}"""))
        assertNull(LrclibClient.parseRecord("""{"plainLyrics":null,"syncedLyrics":null}"""))
        assertNull(LrclibClient.parseRecord("not json"))
    }

    @Test
    fun searchPicksSyncedClosestByDuration() {
        val json = """[
            {"duration":300,"plainLyrics":"far plain","syncedLyrics":null},
            {"duration":100,"plainLyrics":"x","syncedLyrics":"[00:01.00]far synced"},
            {"duration":181,"plainLyrics":"x","syncedLyrics":"[00:01.00]close synced"}
        ]"""
        val lyrics = LrclibClient.pickFromSearch(json, durationMs = 180_000) as Lyrics.Synced
        assertEquals("close synced", lyrics.lines.single().text)
        assertNull(LrclibClient.pickFromSearch("[]", 0))
    }
}
