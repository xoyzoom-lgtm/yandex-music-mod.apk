package io.github.xoyzoom.ymmod.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.xoyzoom.ymmod.settings.ThemeMode

private val Purple = Color(0xFF7C4DFF)
private val Yellow = Color(0xFFFFD54F)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Color(0xFF6D5E0F),
    tertiary = Yellow,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB388FF),
    secondary = Yellow,
    tertiary = Yellow,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun YmModTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = themeMode.isDark()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
