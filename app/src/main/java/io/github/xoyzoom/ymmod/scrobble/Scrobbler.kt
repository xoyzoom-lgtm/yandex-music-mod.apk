package io.github.xoyzoom.ymmod.scrobble

import android.util.Log
import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.settings.LastFmSettings
import io.github.xoyzoom.ymmod.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Решает, когда отправлять «сейчас играет» и скроббл, по правилам Last.fm:
 * трек длиннее 30 секунд прослушан наполовину или 4 минуты. Время считается
 * по часам, а не по позиции, поэтому перемотка вперёд не засчитывается.
 */
class ScrobbleTracker {
    sealed interface Action {
        val scrobble: Scrobble

        data class NowPlaying(override val scrobble: Scrobble) : Action
        data class Submit(override val scrobble: Scrobble) : Action
    }

    private class Play(
        val key: String,
        var scrobble: Scrobble,
        var listenedMs: Long = 0,
        var playingSinceMs: Long? = null,
        var announced: Boolean = false,
        var submitted: Boolean = false,
        var lastPositionMs: Long = 0,
        var lastStateAtMs: Long = 0,
        var lastPlaying: Boolean = false,
    ) {
        /** Позиция сейчас: сайт присылает её только при смене состояния или перемотке. */
        fun expectedPositionMs(nowMs: Long): Long =
            lastPositionMs + if (lastPlaying) (nowMs - lastStateAtMs).coerceAtLeast(0) else 0
    }

    private var current: Play? = null

    fun onState(state: PlayerState, nowMs: Long): List<Action> {
        val actions = ArrayList<Action>()
        val track = state.track
        if (track == null || track.title.isBlank() || track.artist.isBlank()) {
            current?.pause(nowMs)
            current = null
            return actions
        }

        val key = "${track.artist}\u0000${track.title}"
        var play = current
        val restarted = play != null && play.key == key && play.submitted && state.hasPosition &&
            state.positionMs < RESTART_POSITION_MS && play.expectedPositionMs(nowMs) - state.positionMs > RESTART_JUMP_MS
        if (play == null || play.key != key || restarted) {
            play?.pause(nowMs)
            play = Play(
                key = key,
                scrobble = Scrobble(
                    artist = track.artist,
                    track = track.title,
                    album = track.album,
                    durationMs = state.durationMs,
                    startedAtSec = (nowMs - if (state.hasPosition) state.positionMs else 0) / 1000,
                ),
            )
            current = play
        }

        if (state.durationMs > 0 && play.scrobble.durationMs != state.durationMs) {
            play.scrobble = play.scrobble.copy(durationMs = state.durationMs)
        }
        if (state.hasPosition) play.lastPositionMs = state.positionMs
        play.lastStateAtMs = nowMs
        play.lastPlaying = state.isPlaying

        if (state.isPlaying) {
            if (play.playingSinceMs == null) play.playingSinceMs = nowMs
            if (!play.announced) {
                play.announced = true
                actions += Action.NowPlaying(play.scrobble)
            }
        } else {
            play.pause(nowMs)
        }

        checkThreshold(play, nowMs)?.let(actions::add)
        return actions
    }

    fun tick(nowMs: Long): List<Action> = listOfNotNull(current?.let { checkThreshold(it, nowMs) })

    private fun Play.pause(nowMs: Long) {
        playingSinceMs?.let { listenedMs += (nowMs - it).coerceAtLeast(0) }
        playingSinceMs = null
    }

    private fun checkThreshold(play: Play, nowMs: Long): Action? {
        if (play.submitted) return null
        val duration = play.scrobble.durationMs
        if (duration in 1 until MIN_TRACK_MS) return null
        val threshold = if (duration > 0) minOf(duration / 2, MAX_THRESHOLD_MS) else MAX_THRESHOLD_MS
        val listened = play.listenedMs + (play.playingSinceMs?.let { nowMs - it } ?: 0)
        if (listened < threshold) return null
        play.submitted = true
        return Action.Submit(play.scrobble)
    }

    private companion object {
        const val MIN_TRACK_MS = 30_000L
        const val MAX_THRESHOLD_MS = 4 * 60_000L
        const val RESTART_POSITION_MS = 5_000L
        const val RESTART_JUMP_MS = 10_000L
    }
}

sealed interface ScrobbleStatus {
    data object Off : ScrobbleStatus
    data object Ready : ScrobbleStatus
    data class Scrobbled(val title: String) : ScrobbleStatus
    data class Error(val message: String) : ScrobbleStatus
}

/** Связывает плеер, настройки и [LastFmClient]. */
class Scrobbler(
    private val player: PlayerStateRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val tracker = ScrobbleTracker()
    @Volatile
    private var config: LastFmSettings = LastFmSettings()

    private val _status = MutableStateFlow<ScrobbleStatus>(ScrobbleStatus.Off)
    val status: StateFlow<ScrobbleStatus> = _status.asStateFlow()

    fun start() {
        scope.launch {
            settings.settings.map { it.lastFm }.distinctUntilChanged().collect {
                config = it
                _status.value = if (it.enabled && it.isAuthorized) ScrobbleStatus.Ready else ScrobbleStatus.Off
            }
        }
        scope.launch {
            player.state.collect { state -> handle(tracker.onState(state, System.currentTimeMillis())) }
        }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                handle(tracker.tick(System.currentTimeMillis()))
            }
        }
    }

    private fun handle(actions: List<ScrobbleTracker.Action>) {
        val lastFm = config
        if (actions.isEmpty() || !lastFm.enabled || !lastFm.isAuthorized) return
        val client = LastFmClient(lastFm.apiKey, lastFm.apiSecret)
        actions.forEach { action ->
            scope.launch {
                try {
                    when (action) {
                        is ScrobbleTracker.Action.NowPlaying -> client.updateNowPlaying(lastFm.sessionKey, action.scrobble)
                        is ScrobbleTracker.Action.Submit -> {
                            client.scrobble(lastFm.sessionKey, action.scrobble)
                            _status.value = ScrobbleStatus.Scrobbled("${action.scrobble.artist} — ${action.scrobble.track}")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Last.fm: ${e.message}")
                    _status.value = ScrobbleStatus.Error(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private companion object {
        const val TAG = "YmModScrobbler"
        const val TICK_MS = 5_000L
    }
}
