package io.github.xoyzoom.ymmod.debug

import androidx.compose.runtime.Composable
import io.github.xoyzoom.ymmod.AppContainer

/** В release-сборке отладочных инструментов нет; полная версия — в src/debug. */
object DebugTools {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Screen(container: AppContainer, onBack: () -> Unit) = Unit
}
