package io.github.xoyzoom.ymmod

import android.Manifest
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.xoyzoom.ymmod.debug.DebugTools
import io.github.xoyzoom.ymmod.playback.PlaybackService
import io.github.xoyzoom.ymmod.presence.PresenceSinks
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.ThemeMode
import io.github.xoyzoom.ymmod.settings.toFontSpec
import io.github.xoyzoom.ymmod.ui.about.AboutScreen
import io.github.xoyzoom.ymmod.ui.player.PlayerScreen
import io.github.xoyzoom.ymmod.ui.settings.SettingsScreen
import io.github.xoyzoom.ymmod.ui.theme.YmModTheme
import io.github.xoyzoom.ymmod.ui.theme.isDark
import io.github.xoyzoom.ymmod.web.YandexWebView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class Screen { PLAYER, SETTINGS, ABOUT, DEBUG }

class MainActivity : ComponentActivity() {
    private lateinit var web: YandexWebView
    private var appliedThemeMode: ThemeMode? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* без уведомления сервис всё равно работает */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = appContainer
        PresenceSinks.onActivityCreated(this)
        container.presence.start()
        web = YandexWebView(this, container)

        lifecycleScope.launch {
            // UA и шрифт нужно выставить до первой загрузки страницы.
            val initial = container.settings.settings.first()
            web.applyDesktopMode(initial.desktopMode)
            web.bridge.setFont(web.view, initial.toFontSpec())
            web.loadStartPage()

            container.settings.settings.collect { settings ->
                if (web.applyDesktopMode(settings.desktopMode)) web.view.reload()
                web.bridge.setFont(web.view, settings.toFontSpec())
                applyNightMode(settings.themeMode)
            }
        }

        lifecycleScope.launch {
            container.commands.commands.collect { web.bridge.send(web.view, it) }
        }

        lifecycleScope.launch {
            container.player.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { playing -> if (playing) PlaybackService.start(this@MainActivity) }
        }

        if (savedInstanceState == null) requestNotificationPermission()

        setContent { AppRoot(this, web, container) }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        PlaybackService.stop(this)
        web.destroy()
        super.onDestroy()
    }

    /** На Android 12+ тема применяется ко всему приложению, и WebView получает нужный prefers-color-scheme. */
    private fun applyNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (mode == appliedThemeMode) return
        appliedThemeMode = mode
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(nightMode)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppRoot(activity: ComponentActivity, web: YandexWebView, container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val progress by web.progress.collectAsStateWithLifecycle()
    val presenceStatus by container.presence.status.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.PLAYER) }
    val scope = rememberCoroutineScope()

    val dark = settings.themeMode.isDark()
    LaunchedEffect(dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Назад на главном экране: сначала история страницы, затем сворачиваем приложение,
    // чтобы музыка продолжала играть.
    BackHandler(enabled = screen == Screen.PLAYER) {
        if (web.view.canGoBack()) web.view.goBack() else activity.moveTaskToBack(true)
    }
    BackHandler(enabled = screen != Screen.PLAYER) {
        screen = if (screen == Screen.ABOUT || screen == Screen.DEBUG) Screen.SETTINGS else Screen.PLAYER
    }

    YmModTheme(settings.themeMode) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // WebView всегда остаётся в композиции, а остальные экраны рисуются поверх —
                // так страница и воспроизведение не перезапускаются при переходах.
                PlayerScreen(
                    webView = web.view,
                    progress = progress,
                    playerState = playerState,
                    onReload = { web.view.reload() },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )

                when (screen) {
                    Screen.PLAYER -> Unit
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        presenceStatus = presenceStatus,
                        onThemeModeChange = { scope.launch { container.settings.setThemeMode(it) } },
                        onFontPresetChange = { scope.launch { container.settings.setFontPreset(it) } },
                        onCustomFontNameChange = { scope.launch { container.settings.setCustomFontName(it) } },
                        onDesktopModeChange = { scope.launch { container.settings.setDesktopMode(it) } },
                        onDiscordEnabledChange = { scope.launch { container.settings.setDiscordEnabled(it) } },
                        onOpenAbout = { screen = Screen.ABOUT },
                        onBack = { screen = Screen.PLAYER },
                        onOpenDebug = if (DebugTools.AVAILABLE) ({ screen = Screen.DEBUG }) else null,
                    )
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
                    Screen.DEBUG -> DebugTools.Screen(container, onBack = { screen = Screen.SETTINGS })
                }
            }
        }
    }
}
