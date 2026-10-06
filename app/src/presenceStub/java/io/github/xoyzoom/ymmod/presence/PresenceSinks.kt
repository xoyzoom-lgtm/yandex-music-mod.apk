package io.github.xoyzoom.ymmod.presence

import android.app.Activity

/**
 * Сборка без Discord Social SDK (файла app/libs/discord_partner_sdk.aar нет).
 * Парный файл с реализацией на SDK — src/presenceDiscord/java/.../PresenceSinks.kt.
 */
object PresenceSinks {
    fun create(): PresenceSink = LoggingPresenceSink()

    @Suppress("UNUSED_PARAMETER")
    fun onActivityCreated(activity: Activity) = Unit
}
