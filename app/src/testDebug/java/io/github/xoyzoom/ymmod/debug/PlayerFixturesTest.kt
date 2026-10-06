package io.github.xoyzoom.ymmod.debug

import io.github.xoyzoom.ymmod.presence.PresenceMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFixturesTest {
    private val now = 1_700_000_000_000L

    @Test
    fun titlesAreUnique() {
        val titles = PlayerFixtures.all.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun everyFixtureMapsToValidDiscordPresence() {
        PlayerFixtures.all.forEach { fixture ->
            val state = fixture.build(now)
            assertEquals(fixture.title, now, state.updatedAtMs)

            val presence = PresenceMapper.map(state, enabled = true)
            if (!state.isPlaying || state.track == null) {
                assertNull(fixture.title, presence)
                return@forEach
            }
            presence!!
            assertTrue(fixture.title, presence.details.length in 2..128)
            assertTrue(fixture.title, presence.state.length in 2..128)
            presence.largeImageText?.let { assertTrue(fixture.title, it.length in 2..128) }
            if (presence.startTimestampMs != null && presence.endTimestampMs != null) {
                assertTrue(fixture.title, presence.startTimestampMs!! <= presence.endTimestampMs!!)
                assertTrue(fixture.title, presence.endTimestampMs!! >= now)
            }
        }
    }

    @Test
    fun coversEdgeCases() {
        val states = PlayerFixtures.all.map { it.build(now) }
        assertTrue("пустое состояние", states.any { it.track == null })
        assertTrue("пауза", states.any { it.track != null && !it.isPlaying })
        assertTrue("без позиции", states.any { it.isPlaying && !it.hasPosition })
        assertTrue("длинные строки", states.any { (it.track?.title?.length ?: 0) > 128 })
        assertTrue("эмодзи", states.any { it.track?.title?.any(Char::isSurrogate) == true })
    }
}
