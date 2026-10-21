package io.github.xoyzoom.ymmod

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class YmModApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scrobbler.start()
        publishShortcuts()
    }

    /**
     * Долгое нажатие на иконку → «Настройки мода». Ярлык создаётся из кода, а не из
     * res/xml, потому что у debug-сборки другой applicationId.
     */
    private fun publishShortcuts() {
        val shortcut = ShortcutInfoCompat.Builder(this, SHORTCUT_MOD_SETTINGS)
            .setShortLabel(getString(R.string.shortcut_mod_settings_short))
            .setLongLabel(getString(R.string.shortcut_mod_settings_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_settings))
            .setIntent(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_MOD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            .build()
        try {
            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
        } catch (e: RuntimeException) {
            // Некоторые лаунчеры ограничивают ярлыки — приложение работает и без них.
            Log.w("YmModApp", "Не удалось добавить ярлык", e)
        }
    }

    private companion object {
        const val SHORTCUT_MOD_SETTINGS = "mod_settings"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as YmModApp).container
