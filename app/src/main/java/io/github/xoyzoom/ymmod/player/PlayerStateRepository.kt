package io.github.xoyzoom.ymmod.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerStateRepository {
    private val _state = MutableStateFlow(PlayerState.EMPTY)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun update(state: PlayerState) {
        _state.value = state
    }

    fun reset() {
        _state.value = PlayerState.EMPTY
    }
}
