package io.github.xoyzoom.ymmod

import android.content.Context
import io.github.xoyzoom.ymmod.lyrics.LrclibClient
import io.github.xoyzoom.ymmod.player.PlayerCommandBus
import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.presence.PresenceSinks
import io.github.xoyzoom.ymmod.presence.PresenceController
import io.github.xoyzoom.ymmod.scrobble.Scrobbler
import io.github.xoyzoom.ymmod.settings.SettingsRepository
import io.github.xoyzoom.ymmod.web.PageEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Простое ручное DI: зависимости живут столько же, сколько процесс. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(context)
    val player = PlayerStateRepository()
    val commands = PlayerCommandBus()
    val pageEvents = PageEventBus()
    val presence = PresenceController(player, settings, PresenceSinks.create(), appScope)
    val scrobbler = Scrobbler(player, settings, appScope)
    val lyrics = LrclibClient()
}
