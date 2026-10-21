package io.github.xoyzoom.ymmod.web

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URISyntaxException

class YandexWebViewClient(
    private val bridge: BridgeController,
    private val onNewPage: () -> Unit,
) : WebViewClient() {

    /** Читается из потока WebView, поэтому volatile. */
    @Volatile
    var blockTrackers: Boolean = true

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        when (LinkPolicy.classify(uri)) {
            // Обычные страницы (включая вход через Яндекс ID) открываем внутри приложения.
            LinkPolicy.Target.IN_APP -> return false
            LinkPolicy.Target.EXTERNAL -> {
                // Сайт сам перенаправляет в магазин или официальное приложение («Откройте в приложении»).
                // Без нажатия пользователя такие переходы просто игнорируем.
                if (!request.hasGesture()) return true
                if (uri.scheme == "intent") openIntentUri(view, uri.toString()) else launch(view, Intent(Intent.ACTION_VIEW, uri))
            }
            LinkPolicy.Target.BLOCK -> Unit
        }
        // Неизвестные схемы WebView всё равно не откроет — не даём ему показать ошибку.
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!blockTrackers || !TrackerBlocklist.isBlocked(request.url)) return null
        // Пустой скрипт вместо ошибки сети: сайт не заметит разницы и не будет повторять запрос.
        return WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onNewPage()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        bridge.onPageFinished(view, url)
    }

    private fun openIntentUri(view: WebView, url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Некорректный intent: URI", e)
            return
        }
        // Страница не должна иметь возможность адресовать конкретные компоненты.
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)

        if (launch(view, intent)) return

        intent.getStringExtra("browser_fallback_url")
            ?.takeIf { it.startsWith("https://") }
            ?.let(view::loadUrl)
    }

    /** @return true, если нашлось приложение, которое открыло ссылку. */
    private fun launch(view: WebView, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            view.context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Log.i(TAG, "Нет приложения для ${intent.data}")
            false
        }
    }

    private companion object {
        const val TAG = "YmModWebClient"
    }
}

/** Куда открывать ссылку со страницы. */
object LinkPolicy {
    enum class Target { IN_APP, EXTERNAL, BLOCK }

    /** Ссылки «скачайте приложение»: магазины и сервисы deep link. */
    private val externalHosts = listOf(
        "play.google.com",
        "apps.apple.com",
        "itunes.apple.com",
        "appgallery.huawei.com",
        "apps.rustore.ru",
        "onelink.me",
        "app.link",
    )

    fun classify(uri: Uri): Target = classify(uri.scheme, uri.host)

    fun classify(scheme: String?, host: String?): Target = when (scheme?.lowercase()) {
        "http", "https" -> {
            val name = host.orEmpty().lowercase()
            if (externalHosts.any { name == it || name.endsWith(".$it") }) Target.EXTERNAL else Target.IN_APP
        }
        "javascript", "file", "content", "data", "about", null -> Target.BLOCK
        else -> Target.EXTERNAL
    }
}

/**
 * Счётчики аналитики, которые не нужны для работы плеера (как в оригинальном моде,
 * где блокировался mc.yandex.ru/metrika). Реклама и статистика прослушиваний не трогаются.
 */
object TrackerBlocklist {
    private val hostPrefixes = listOf("mc.yandex.", "mc.webvisor.")

    fun isBlocked(uri: Uri): Boolean = isBlockedHost(uri.host)

    fun isBlockedHost(host: String?): Boolean {
        val name = host?.lowercase() ?: return false
        return hostPrefixes.any { name.startsWith(it) }
    }
}
