package io.github.xoyzoom.ymmod.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
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

    private val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    private var desktopMode: Boolean? = null
    private lateinit var client: YandexWebViewClient

    private val _progress = MutableStateFlow(0)
    /** Прогресс загрузки страницы 0..100. */
    val progress: StateFlow<Int> = _progress.asStateFlow()

    val view: WebView = createWebView(context, container)

    @SuppressLint("SetJavaScriptEnabled")
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

            client = YandexWebViewClient(bridge) { container.player.reset() }
            webViewClient = client
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    _progress.value = newProgress
                }
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
        client.blockTrackers = settings.blockTrackers
        view.settings.textZoom = settings.uiScale
        WebView.setWebContentsDebuggingEnabled(settings.devMode)
        return applyDesktopMode(settings.desktopMode)
    }

    private fun applyDesktopMode(enabled: Boolean): Boolean {
        if (desktopMode == enabled) return false
        val changed = desktopMode != null
        desktopMode = enabled
        view.settings.userAgentString = UserAgents.build(defaultUserAgent, enabled)
        return changed
    }

    /** Открывает ссылку music.yandex.* (например, из «Поделиться»), иначе — главную. */
    fun loadStartPage(deepLink: Uri? = null) {
        val target = deepLink?.takeIf(YandexMusicSite::isMusicOrigin)?.toString() ?: YandexMusicSite.START_URL
        view.loadUrl(target)
    }

    fun destroy() {
        CookieManager.getInstance().flush()
        view.stopLoading()
        view.destroy()
    }

    private companion object {
        const val SCRIPT_ASSET = "js/ymmod-bridge.js"
    }
}
