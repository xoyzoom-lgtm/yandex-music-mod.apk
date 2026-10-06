package io.github.xoyzoom.ymmod.presence.discord

/**
 * JNI-обёртка над discordpp::Client (src/presenceDiscord/cpp/discord_presence.cpp).
 * Все вызовы должны идти с одного потока — того же, на котором вызывается [runCallbacks].
 * Строки передаются как UTF-8 байты: JNI-строки в modified UTF-8 портят эмодзи в названиях.
 */
internal object DiscordNative {
    fun interface Listener {
        fun onResult(success: Boolean, message: String)
    }

    fun load() {
        System.loadLibrary("ymmod_discord")
    }

    @JvmStatic
    external fun init(applicationId: Long, listener: Listener)

    @JvmStatic
    external fun update(
        details: ByteArray,
        state: ByteArray,
        largeImage: ByteArray?,
        largeText: ByteArray?,
        startSeconds: Long,
        endSeconds: Long,
    )

    @JvmStatic
    external fun clear()

    @JvmStatic
    external fun runCallbacks()
}
