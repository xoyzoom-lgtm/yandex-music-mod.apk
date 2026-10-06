package io.github.xoyzoom.ymmod.debug

import androidx.compose.runtime.Composable
import io.github.xoyzoom.ymmod.AppContainer

/** Отладочные инструменты. В release-сборке — пустая версия из src/release. */
object DebugTools {
    const val AVAILABLE = true

    @Composable
    fun Screen(container: AppContainer, onBack: () -> Unit) {
        PlayerFixturesScreen(container, onBack)
    }
}
