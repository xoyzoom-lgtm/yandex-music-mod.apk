package io.github.xoyzoom.ymmod

import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.player.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateRepositoryTest {
    private val live = PlayerState(TrackInfo("Live", "A", "", null), true, true, 1_000, 10_000, 5)
    private val fixture = PlayerState(TrackInfo("Fixture", "B", "", null), false, false, 0, 0, 7)

    @Test
    fun overrideWinsUntilCleared() {
        val repository = PlayerStateRepository()
        repository.update(live)
        assertEquals(live, repository.state.value)
        assertFalse(repository.isOverridden.value)

        repository.setOverride(fixture)
        assertEquals(fixture, repository.state.value)
        assertTrue(repository.isOverridden.value)

        // Данные из WebView продолжают приходить, но не видны, пока активна фикстура.
        val newer = live.copy(positionMs = 2_000)
        repository.update(newer)
        assertEquals(fixture, repository.state.value)

        repository.setOverride(null)
        assertEquals(newer, repository.state.value)
        assertFalse(repository.isOverridden.value)
    }

    @Test
    fun resetClearsLiveStateOnly() {
        val repository = PlayerStateRepository()
        repository.update(live)
        repository.setOverride(fixture)
        repository.reset()
        assertEquals(fixture, repository.state.value)

        repository.setOverride(null)
        assertEquals(PlayerState.EMPTY, repository.state.value)
    }
}
