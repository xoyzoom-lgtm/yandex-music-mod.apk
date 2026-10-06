package io.github.xoyzoom.ymmod.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Текущее состояние плеера для UI, уведомления и Discord.
 *
 * Обычно это данные из WebView ([update]). В debug-сборке экран фикстур может временно
 * подменить их через [setOverride], чтобы проверить интерфейс без реального воспроизведения.
 */
class PlayerStateRepository {
    private val lock = Any()
    private var live: PlayerState = PlayerState.EMPTY
    private var override: PlayerState? = null

    private val _state = MutableStateFlow(PlayerState.EMPTY)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _isOverridden = MutableStateFlow(false)
    val isOverridden: StateFlow<Boolean> = _isOverridden.asStateFlow()

    fun update(state: PlayerState) = synchronized(lock) {
        live = state
        publish()
    }

    fun reset() = synchronized(lock) {
        live = PlayerState.EMPTY
        publish()
    }

    /** null — вернуть данные из WebView. */
    fun setOverride(state: PlayerState?) = synchronized(lock) {
        override = state
        publish()
    }

    private fun publish() {
        _state.value = override ?: live
        _isOverridden.value = override != null
    }
}
