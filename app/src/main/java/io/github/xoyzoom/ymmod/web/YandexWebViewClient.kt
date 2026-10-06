package io.github.xoyzoom.ymmod.web

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URISyntaxException

class YandexWebViewClient(
    private val bridge: BridgeController,
    private val onNewPage: () -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        when (uri.scheme) {
            // Обычные страницы (включая вход через Яндекс ID) открываем внутри приложения.
            "http", "https" -> return false
            "intent" -> openIntentUri(view, uri.toString())
            else -> launch(view, Intent(Intent.ACTION_VIEW, uri))
        }
        // Неизвестные схемы WebView всё равно не откроет — не даём ему показать ошибку.
        return true
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
