package io.github.xoyzoom.ymmod

import android.Manifest
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.xoyzoom.ymmod.debug.DebugTools
import io.github.xoyzoom.ymmod.net.RegionChecker
import io.github.xoyzoom.ymmod.net.RegionInfo
import io.github.xoyzoom.ymmod.net.ReleaseInfo
import io.github.xoyzoom.ymmod.net.UpdateChecker
import io.github.xoyzoom.ymmod.playback.PlaybackService
import io.github.xoyzoom.ymmod.presence.PresenceSinks
import io.github.xoyzoom.ymmod.scrobble.LastFmClient
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.ThemeMode
import io.github.xoyzoom.ymmod.ui.about.AboutScreen
import io.github.xoyzoom.ymmod.ui.lyrics.LyricsScreen
import io.github.xoyzoom.ymmod.ui.player.PlayerScreen
import io.github.xoyzoom.ymmod.ui.settings.CheckResult
import io.github.xoyzoom.ymmod.ui.settings.SettingsActions
import io.github.xoyzoom.ymmod.ui.settings.SettingsScreen
import io.github.xoyzoom.ymmod.ui.settings.SettingsStatus
import io.github.xoyzoom.ymmod.ui.theme.YmModTheme
import io.github.xoyzoom.ymmod.ui.theme.isDark
import io.github.xoyzoom.ymmod.web.PageEvent
import io.github.xoyzoom.ymmod.web.YandexWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor

enum class Screen { WEB, SETTINGS, LYRICS, ABOUT, DEBUG }

class MainActivity : ComponentActivity() {
    private lateinit var web: YandexWebView
    private var appliedThemeMode: ThemeMode? = null

    private var screen by mutableStateOf(Screen.WEB)
    private var region by mutableStateOf<CheckResult<RegionInfo>>(CheckResult.Idle)
    private var update by mutableStateOf<CheckResult<ReleaseInfo>>(CheckResult.Idle)
    private var lastFmLogin by mutableStateOf<CheckResult<String>>(CheckResult.Idle)
    private var pendingSnapshot: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* без уведомления сервис всё равно работает */ }

    private val saveSnapshot =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri -> writeSnapshot(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = appContainer
        PresenceSinks.onActivityCreated(this)
        container.presence.start()
        web = YandexWebView(this, container)
        savedInstanceState?.getString(STATE_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }
            ?.let { screen = it }

        lifecycleScope.launch {
            // UA, шрифт и настройки скрипта нужно выставить до первой загрузки страницы.
            val initial = container.settings.settings.first()
            web.apply(initial)
            web.loadStartPage(intent?.musicLink())
            applyNightMode(initial.themeMode)

            container.settings.settings.collect { settings ->
                if (web.apply(settings)) web.view.reload()
                applyNightMode(settings.themeMode)
            }
        }

        lifecycleScope.launch {
            container.commands.commands.collect { web.bridge.send(web.view, it) }
        }
        lifecycleScope.launch {
            container.commands.seeks.collect { web.bridge.seek(web.view, it) }
        }
        lifecycleScope.launch {
            container.pageEvents.events.collect(::onPageEvent)
        }

        lifecycleScope.launch {
            container.player.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { playing -> if (playing) PlaybackService.start(this@MainActivity) }
        }

        if (savedInstanceState == null) {
            requestNotificationPermission()
            handleModIntent(intent)
        }

        setContent { AppRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleModIntent(intent)) return
        intent.musicLink()?.let { link ->
            screen = Screen.WEB
            web.loadStartPage(link)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, screen.name)
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

    /** Ярлык «Настройки мода» на иконке приложения. */
    private fun handleModIntent(intent: Intent?): Boolean {
        if (intent?.action != ACTION_OPEN_MOD_SETTINGS) return false
        screen = Screen.SETTINGS
        return true
    }

    private fun Intent.musicLink(): Uri? = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
            ?.let { text -> LINK_PATTERN.find(text)?.value }
            ?.let(Uri::parse)
        else -> null
    }

    private fun onPageEvent(event: PageEvent) {
        when (event) {
            PageEvent.OpenModSettings -> screen = Screen.SETTINGS
            is PageEvent.CollectionLink -> {
                if (event.url != null) {
                    copyToClipboard(event.url)
                    toast(getString(R.string.collection_link_copied))
                } else {
                    toast(getString(R.string.error_with_message, event.error.orEmpty()))
                }
            }
            is PageEvent.Snapshot -> {
                pendingSnapshot = event.html
                val name = "ymmod-${event.path.trim('/').replace('/', '-').ifEmpty { "home" }}-${event.width}x${event.height}.html"
                try {
                    saveSnapshot.launch(name)
                } catch (_: ActivityNotFoundException) {
                    pendingSnapshot = null
                    toast(getString(R.string.snapshot_no_picker))
                }
            }
            is PageEvent.Theme, is PageEvent.Likes -> Unit
        }
    }

    private fun writeSnapshot(uri: Uri?) {
        val html = pendingSnapshot ?: return
        pendingSnapshot = null
        if (uri == null) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) } != null }
                    .getOrDefault(false)
            }
            toast(getString(if (ok) R.string.snapshot_saved else R.string.snapshot_failed))
        }
    }

    private fun checkRegion() {
        region = CheckResult.Running
        lifecycleScope.launch {
            region = runCheck { RegionChecker.check() }
        }
    }

    private fun checkUpdates() {
        update = CheckResult.Running
        lifecycleScope.launch {
            update = when (val result = runCheck { UpdateChecker.latest() }) {
                is CheckResult.Done -> CheckResult.Done(
                    result.value?.takeIf { UpdateChecker.isNewer(it.version, BuildConfig.VERSION_NAME) },
                )
                else -> result
            }
        }
    }

    private fun loginLastFm(apiKey: String, apiSecret: String, username: String, password: String) {
        lastFmLogin = CheckResult.Running
        lifecycleScope.launch {
            lastFmLogin = try {
                val (name, sessionKey) = LastFmClient(apiKey, apiSecret).mobileSession(username, password)
                appContainer.settings.setLastFmCredentials(apiKey, apiSecret)
                appContainer.settings.setLastFmSession(name, sessionKey)
                CheckResult.Done(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun <T> runCheck(block: suspend () -> T?): CheckResult<T> = try {
        CheckResult.Done(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CheckResult.Failed(getString(R.string.error_with_message, e.message ?: e.javaClass.simpleName))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            toast(url)
        }
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("YM Mod", text))
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

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

    private fun settingsActions(container: AppContainer, launch: (suspend () -> Unit) -> Unit) = SettingsActions(
        setThemeMode = { launch { container.settings.setThemeMode(it) } },
        setFontPreset = { launch { container.settings.setFontPreset(it) } },
        setCustomFontName = { launch { container.settings.setCustomFontName(it) } },
        setUiScale = { launch { container.settings.setUiScale(it) } },
        setSnow = { launch { container.settings.setSnowEffect(it) } },
        setDesktopMode = { launch { container.settings.setDesktopMode(it) } },
        setAutoBestQuality = { launch { container.settings.setAutoBestQuality(it) } },
        setHideAppPromo = { launch { container.settings.setHideAppPromo(it) } },
        setBlockTrackers = { launch { container.settings.setBlockTrackers(it) } },
        setFloatingButton = { launch { container.settings.setFloatingButton(it) } },
        setDiscordEnabled = { launch { container.settings.setDiscordEnabled(it) } },
        setLastFmEnabled = { launch { container.settings.setLastFmEnabled(it) } },
        lastFmLogin = ::loginLastFm,
        lastFmLogout = {
            lastFmLogin = CheckResult.Idle
            launch { container.settings.clearLastFmSession() }
        },
        openLyrics = { screen = Screen.LYRICS },
        transferLikes = { web.bridge.transferLikes(web.view, it) },
        stopLikes = { web.bridge.stopLikes(web.view) },
        copyCollectionLink = { web.bridge.requestCollectionLink(web.view) },
        checkRegion = ::checkRegion,
        checkUpdates = ::checkUpdates,
        openUrl = ::openUrl,
        setDevMode = { launch { container.settings.setDevMode(it) } },
        saveSnapshot = {
            screen = Screen.WEB
            web.bridge.requestSnapshot(web.view)
        },
        reloadPage = {
            screen = Screen.WEB
            web.view.reload()
        },
        openAbout = { screen = Screen.ABOUT },
        openDebug = if (DebugTools.AVAILABLE) ({ screen = Screen.DEBUG }) else null,
        back = { screen = Screen.WEB },
    )

    @Composable
    private fun AppRoot(container: AppContainer) {
        val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings(onboardingShown = true))
        val playerState by container.player.state.collectAsStateWithLifecycle()
        val progress by web.progress.collectAsStateWithLifecycle()
        val presenceStatus by container.presence.status.collectAsStateWithLifecycle()
        val scrobbleStatus by container.scrobbler.status.collectAsStateWithLifecycle()
        val likes by container.pageEvents.likes.collectAsStateWithLifecycle()
        val pageBackground by container.pageEvents.pageBackground.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val actions = settingsActions(container) { block -> scope.launch { block() } }

        val pageColor = pageBackground?.let { ComposeColor(it) }
        val appDark = settings.themeMode.isDark()
        // На экране сайта значки системных панелей подбираются под цвет страницы.
        val darkBars = if (screen == Screen.WEB && pageColor != null) pageColor.luminance() < 0.5f else appDark
        LaunchedEffect(darkBars) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkBars }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        // Назад на главном экране: сначала история страницы, затем сворачиваем приложение,
        // чтобы музыка продолжала играть.
        BackHandler(enabled = screen == Screen.WEB) {
            if (web.view.canGoBack()) web.view.goBack() else moveTaskToBack(true)
        }
        BackHandler(enabled = screen != Screen.WEB) {
            screen = if (screen == Screen.ABOUT || screen == Screen.DEBUG) Screen.SETTINGS else Screen.WEB
        }

        YmModTheme(settings.themeMode) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    // WebView всегда остаётся в композиции, а остальные экраны рисуются поверх —
                    // так страница и воспроизведение не перезапускаются при переходах.
                    PlayerScreen(
                        webView = web.view,
                        progress = progress,
                        pageBackground = pageColor,
                        showFloatingButton = settings.floatingButton && screen == Screen.WEB,
                        onOpenModMenu = { screen = Screen.SETTINGS },
                    )

                    when (screen) {
                        Screen.WEB -> Unit
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            status = SettingsStatus(
                                presence = presenceStatus,
                                scrobble = scrobbleStatus,
                                likes = likes,
                                region = region,
                                update = update,
                                currentVersion = BuildConfig.VERSION_NAME,
                                lastFmLogin = lastFmLogin,
                            ),
                            actions = actions,
                        )
                        Screen.LYRICS -> LyricsScreen(
                            playerState = playerState,
                            client = container.lyrics,
                            onSeek = container.commands::seekTo,
                            onBack = { screen = Screen.WEB },
                        )
                        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
                        Screen.DEBUG -> DebugTools.Screen(container, onBack = { screen = Screen.SETTINGS })
                    }

                    if (!settings.onboardingShown) {
                        AlertDialog(
                            onDismissRequest = { scope.launch { container.settings.setOnboardingShown() } },
                            title = { Text(stringResource(R.string.onboarding_title)) },
                            text = { Text(stringResource(R.string.onboarding_text)) },
                            confirmButton = {
                                TextButton(onClick = { scope.launch { container.settings.setOnboardingShown() } }) {
                                    Text(stringResource(R.string.onboarding_ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    scope.launch { container.settings.setOnboardingShown() }
                                    screen = Screen.SETTINGS
                                }) {
                                    Text(stringResource(R.string.onboarding_open))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_MOD_SETTINGS = "io.github.xoyzoom.ymmod.action.OPEN_MOD_SETTINGS"
        private const val STATE_SCREEN = "screen"
        private val LINK_PATTERN = Regex("""https://music\.yandex\.(ru|by|kz|uz|com)/\S+""")
    }
}
