package io.github.xoyzoom.ymmod.presence.discord

import android.util.Log
import io.github.xoyzoom.ymmod.presence.PresenceSink
import io.github.xoyzoom.ymmod.presence.PresenceStatus
import io.github.xoyzoom.ymmod.presence.RichPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Rich Presence через Discord Social SDK без OAuth: SDK передаёт активность в установленное
 * приложение Discord (Social SDK 1.10+, нужно, чтобы в Discord был выполнен вход).
 */
class DiscordSocialPresenceSink(private val applicationId: Long) : PresenceSink {
    // Клиент SDK не потокобезопасен: все вызовы и RunCallbacks — на одном потоке.
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "discord-sdk").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _status = MutableStateFlow<PresenceStatus>(PresenceStatus.Disabled)
    override val status: StateFlow<PresenceStatus> = _status.asStateFlow()

    private var initialized = false
    private var enabled = false
    private var callbacksJob: Job? = null

    override suspend fun setEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        this@DiscordSocialPresenceSink.enabled = enabled
        if (!enabled) {
            if (initialized) {
                DiscordNative.clear()
                // Даём SDK отправить очистку, затем останавливаем цикл колбэков.
                repeat(CLEAR_FLUSH_TICKS) {
                    DiscordNative.runCallbacks()
                    delay(CALLBACK_INTERVAL_MS)
                }
            }
            callbacksJob?.cancel()
            _status.value = PresenceStatus.Disabled
            return@withContext
        }

        if (!ensureInitialized()) return@withContext
        _status.value = PresenceStatus.Connecting
        if (callbacksJob?.isActive != true) {
            callbacksJob = scope.launch {
                while (isActive) {
                    DiscordNative.runCallbacks()
                    delay(CALLBACK_INTERVAL_MS)
                }
            }
        }
    }

    override suspend fun update(presence: RichPresence?): Unit = withContext(dispatcher) {
        if (!enabled || !initialized) return@withContext
        if (presence == null) {
            DiscordNative.clear()
            return@withContext
        }
        DiscordNative.update(
            details = presence.details.utf8(),
            state = presence.state.utf8(),
            largeImage = presence.largeImageUrl?.utf8(),
            largeText = presence.largeImageText?.utf8(),
            startSeconds = presence.startTimestampMs?.div(1000) ?: 0L,
            endSeconds = presence.endTimestampMs?.div(1000) ?: 0L,
        )
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        return try {
            DiscordNative.load()
            DiscordNative.init(applicationId) { success, message ->
                _status.value = if (success) PresenceStatus.Connected else PresenceStatus.Error(message)
                if (!success) Log.w(TAG, "UpdateRichPresence: $message")
            }
            initialized = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Не удалось загрузить нативную библиотеку Discord", e)
            _status.value = PresenceStatus.Error(e.message ?: "native library")
            false
        }
    }

    private fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)

    private companion object {
        const val TAG = "YmModDiscord"
        const val CALLBACK_INTERVAL_MS = 250L
        const val CLEAR_FLUSH_TICKS = 8
    }
}
