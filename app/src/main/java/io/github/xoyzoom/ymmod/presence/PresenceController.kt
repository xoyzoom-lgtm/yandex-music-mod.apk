package io.github.xoyzoom.ymmod.presence

import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Связывает состояние плеера и настройки с [PresenceSink]. */
class PresenceController(
    private val player: PlayerStateRepository,
    private val settings: SettingsRepository,
    private val sink: PresenceSink,
    private val scope: CoroutineScope,
) {
    val status: StateFlow<PresenceStatus> get() = sink.status

    private var started = false

    /** Запускается из активити: Discord Social SDK требует активити до первого вызова. */
    fun start() {
        if (started) return
        started = true

        val enabled = settings.settings
            .map { it.discordEnabled }
            .distinctUntilChanged()
            .onEach { sink.setEnabled(it) }

        scope.launch {
            combine(player.state, enabled) { state, isEnabled -> PresenceMapper.map(state, isEnabled) }
                .distinctUntilChanged()
                .collectLatest { sink.update(it) }
        }
    }
}
