package io.github.xoyzoom.ymmod.presence

import android.app.Activity
import com.discord.socialsdk.DiscordSocialSdkInit
import io.github.xoyzoom.ymmod.BuildConfig
import io.github.xoyzoom.ymmod.presence.discord.DiscordSocialPresenceSink

/**
 * Сборка с Discord Social SDK. Подключается в app/build.gradle.kts, когда есть
 * app/libs/discord_partner_sdk.aar. Парный файл без SDK — src/presenceStub.
 */
object PresenceSinks {
    fun create(): PresenceSink {
        val applicationId = BuildConfig.DISCORD_APPLICATION_ID
        // Без Application ID SDK ничего не опубликует — ведём себя как сборка без SDK.
        return if (applicationId != 0L) DiscordSocialPresenceSink(applicationId) else LoggingPresenceSink()
    }

    /** Требование SDK на Android: передать активити до использования клиента. */
    fun onActivityCreated(activity: Activity) {
        DiscordSocialSdkInit.setEngineActivity(activity)
    }
}
