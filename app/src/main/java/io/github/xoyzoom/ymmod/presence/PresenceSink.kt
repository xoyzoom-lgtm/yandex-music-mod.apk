package io.github.xoyzoom.ymmod.presence

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PresenceStatus {
    data object Disabled : PresenceStatus
    data object NotConfigured : PresenceStatus
    data object Connecting : PresenceStatus
    data object Connected : PresenceStatus
    data class Error(val message: String) : PresenceStatus
}

/**
 * Куда отправляется активность. Реализация на Discord Social SDK подключается на следующем
 * этапе: SDK распространяется через Discord Developer Portal и требует Application ID,
 * поэтому в репозиторий он не входит.
 */
interface PresenceSink {
    val status: StateFlow<PresenceStatus>

    suspend fun update(presence: RichPresence?)

    suspend fun setEnabled(enabled: Boolean)
}

/** Временная реализация: только пишет активность в logcat (тег YmModPresence). */
class LoggingPresenceSink : PresenceSink {
    private val _status = MutableStateFlow<PresenceStatus>(PresenceStatus.NotConfigured)
    override val status: StateFlow<PresenceStatus> = _status.asStateFlow()

    override suspend fun update(presence: RichPresence?) {
        Log.d(TAG, if (presence == null) "clear" else "update: $presence")
    }

    override suspend fun setEnabled(enabled: Boolean) {
        _status.value = if (enabled) PresenceStatus.NotConfigured else PresenceStatus.Disabled
    }

    private companion object {
        const val TAG = "YmModPresence"
    }
}
