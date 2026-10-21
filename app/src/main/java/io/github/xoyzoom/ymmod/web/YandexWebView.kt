package io.github.xoyzoom.ymmod.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.xoyzoom.ymmod.AppContainer
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.toFontSpec
import io.github.xoyzoom.ymmod.settings.toPageConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Владелец единственного WebView приложения и его настроек. */
class YandexWebView(context: Context, container: AppContainer) {
    val bridge = BridgeController(
        script = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() },
        player = container.player,
        events = container.pageEvents,
    )

    private val probeScript = context.assets.open(PROBE_ASSET).bufferedReader().use { it.readText() }
    private val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    private var desktopMode: Boolean = false
    private var appliedUserAgent: String? = null
    private lateinit var client: YandexWebViewClient
    private val handler = Handler(Looper.getMainLooper())
    private val probeCheck = Runnable { probe(automaticCheck = true) }

    val diagnostics = PageDiagnostics(
        webViewVersion = WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "неизвестно",
        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )

    private val _progress = MutableStateFlow(0)
    /** Прогресс загрузки страницы 0..100. */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _safeMode = MutableStateFlow(false)
    /** Режим «без мода»: сайт открывается как в обычном браузере, без скрипта и подмены user agent. */
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    val view: WebView = createWebView(context, container)

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun createWebView(context: Context, container: AppContainer): WebView {
        val webView = WebView(context)
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Чтобы управление из уведомления работало без касания страницы.
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
            }
            // Без «пружины» в конце прокрутки страница ведёт себя как нативный экран.
            overScrollMode = WebView.OVER_SCROLL_NEVER

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                // Нужны для входа через Яндекс ID.
                setAcceptThirdPartyCookies(webView, true)
            }

            client = YandexWebViewClient(
                bridge = bridge,
                diagnostics = diagnostics,
                onNewPage = {
                    container.player.reset()
                    diagnostics.onPageStarted()
                    scheduleProbe()
                },
                onLoaded = ::scheduleProbe,
            )
            webViewClient = client
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    _progress.value = newProgress
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        diagnostics.onConsoleError("${message.message()} @ ${message.sourceId().substringAfterLast('/')}:${message.lineNumber()}")
                    }
                    return false
                }
            }

            // Диагностика внедряется всегда, даже без мода: только так видно, почему сайт пустой.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, probeScript, YandexMusicSite.ALLOWED_ORIGINS)
            }

            bridge.install(this)
        }
        return webView
    }

    /**
     * Применяет настройки к WebView. Возвращает true, если поменялся user agent
     * и открытую страницу нужно перезагрузить.
     */
    fun apply(settings: AppSettings): Boolean {
        bridge.setConfig(settings.toPageConfig())
        bridge.setFont(view, settings.toFontSpec())
        client.blockTrackers = settings.blockTrackers && !_safeMode.value
        view.settings.textZoom = settings.uiScale
        WebView.setWebContentsDebuggingEnabled(settings.devMode)
        desktopMode = settings.desktopMode
        return applyUserAgent()
    }

    /** Включает/выключает режим «без мода» и перезагружает страницу. */
    fun setSafeMode(enabled: Boolean) {
        _safeMode.value = enabled
        bridge.setEnabled(!enabled)
        if (enabled) client.blockTrackers = false
        applyUserAgent()
        view.reload()
    }

    private fun applyUserAgent(): Boolean {
        val userAgent = if (_safeMode.value) defaultUserAgent else UserAgents.build(defaultUserAgent, desktopMode)
        if (userAgent == appliedUserAgent) return false
        val changed = appliedUserAgent != null
        appliedUserAgent = userAgent
        view.settings.userAgentString = userAgent
        return changed
    }

    /** Открывает ссылку music.yandex.* (например, из «Поделиться»), иначе — главную. */
    fun loadStartPage(deepLink: Uri? = null) {
        val target = deepLink?.takeIf(YandexMusicSite::isMusicOrigin)?.toString() ?: YandexMusicSite.START_URL
        view.loadUrl(target)
    }

    fun reload() {
        // Если страница так и не открылась, reload() повторит ошибку — грузим заново.
        if (view.url.isNullOrEmpty() || diagnostics.state.value.mainFrameError != null) loadStartPage() else view.reload()
    }

    /** Через [PROBE_DELAY_MS] после загрузки проверяем, видно ли что-нибудь на экране. */
    private fun scheduleProbe() {
        handler.removeCallbacks(probeCheck)
        handler.postDelayed(probeCheck, PROBE_DELAY_MS)
    }

    /** Ручной запуск проверки из меню мода. */
    fun showDiagnostics() {
        diagnostics.show()
        probe(automaticCheck = false)
    }

    private fun probe(automaticCheck: Boolean) {
        // Скрипт защищён от повторного запуска; так проверка работает и без document-start скриптов.
        view.evaluateJavascript("$probeScript;window.__ymmodProbe ? window.__ymmodProbe() : null") { result ->
            diagnostics.onProbe(PageProbe.parse(result), automaticCheck)
        }
    }

    fun destroy() {
        handler.removeCallbacks(probeCheck)
        CookieManager.getInstance().flush()
        view.stopLoading()
        view.destroy()
    }

    private companion object {
        const val SCRIPT_ASSET = "js/ymmod-bridge.js"
        const val PROBE_ASSET = "js/ymmod-probe.js"
        const val PROBE_DELAY_MS = 15_000L
    }
}
