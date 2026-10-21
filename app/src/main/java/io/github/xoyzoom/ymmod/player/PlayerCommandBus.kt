package io.github.xoyzoom.ymmod.player

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Команды плееру; [jsAction] совпадает с действиями Media Session в ymmod-bridge.js. */
enum class PlayerCommand(val jsAction: String) {
    PLAY("play"),
    PAUSE("pause"),
    TOGGLE("toggle"),
    NEXT("nexttrack"),
    PREVIOUS("previoustrack"),
    LIKE("like"),
}

/** Передаёт команды из уведомления/гарнитуры в активити, где живёт WebView. */
class PlayerCommandBus {
    private val _commands = MutableSharedFlow<PlayerCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    fun send(command: PlayerCommand) {
        _commands.tryEmit(command)
    }

    private val _seeks = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Перемотка с экрана блокировки / из уведомления, позиция в мс. */
    val seeks: SharedFlow<Long> = _seeks.asSharedFlow()

    fun seekTo(positionMs: Long) {
        _seeks.tryEmit(positionMs)
    }
}
