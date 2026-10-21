package io.github.xoyzoom.ymmod.web

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.xoyzoom.ymmod.player.PlayerCommand
import io.github.xoyzoom.ymmod.player.PlayerMessageParser
import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.settings.FontSpec
import io.github.xoyzoom.ymmod.settings.PageConfig
import org.json.JSONObject

/**
 * Связь с внедрённым скриптом `assets/js/ymmod-bridge.js`.
 *
 * Используется WebViewCompat.addWebMessageListener вместо addJavascriptInterface: объект моста
 * виден только страницам из [YandexMusicSite.ALLOWED_ORIGINS], а не любому открытому сайту.
 */
class BridgeController(
    private val script: String,
    private val player: PlayerStateRepository,
    private val events: PageEventBus,
) {
    private var webView: WebView? = null
    private var documentStartHandler: ScriptHandler? = null
    private var documentStartSupported = false
    private var font: FontSpec = FontSpec.NONE
    private var config: PageConfig? = null

    @SuppressLint("RequiresFeature")
    fun install(webView: WebView) {
        this.webView = webView
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                BRIDGE_NAME,
                YandexMusicSite.ALLOWED_ORIGINS,
            ) { _, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame || !YandexMusicSite.isMusicOrigin(sourceOrigin)) return@addWebMessageListener
                val data = message.data ?: return@addWebMessageListener
                val state = PlayerMessageParser.parse(data, System.currentTimeMillis())
                if (state != null) player.update(state) else PageEventParser.parse(data)?.let(events::emit)
            }
        } else {
            Log.w(TAG, "WEB_MESSAGE_LISTENER не поддерживается: обновите Android System WebView")
        }
        documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }

    /**
     * Настройки для скрипта. До загрузки страницы они попадают в document-start скрипт как
     * `window.__ymmodConfig`, на уже открытой странице применяются через `__ymmod.configure`.
     */
    @SuppressLint("RequiresFeature")
    fun setConfig(config: PageConfig) {
        if (config == this.config) return
        this.config = config
        val view = webView ?: return
        if (documentStartSupported) {
            documentStartHandler?.remove()
            documentStartHandler = WebViewCompat.addDocumentStartJavaScript(
                view,
                configPrefix(config) + script,
                YandexMusicSite.ALLOWED_ORIGINS,
            )
        }
        view.evaluateJavascript("window.__ymmod && window.__ymmod.configure(${config.toJson()});", null)
    }

    /** Вызывается из WebViewClient.onPageFinished. */
    fun onPageFinished(webView: WebView, url: String?) {
        if (!YandexMusicSite.isMusicOrigin(url?.let(android.net.Uri::parse))) return
        // Запасной путь для старых WebView: скрипт сам защищён от повторного запуска.
        if (documentStartHandler == null) {
            webView.evaluateJavascript(config?.let(::configPrefix).orEmpty() + script, null)
        }
        applyFont(webView)
        webView.evaluateJavascript("window.__ymmod && window.__ymmod.refresh();", null)
    }

    fun setFont(webView: WebView, spec: FontSpec) {
        if (spec == font) return
        font = spec
        applyFont(webView)
    }

    fun send(webView: WebView, command: PlayerCommand) {
        call(webView, "control", JSONObject.quote(command.jsAction))
    }

    fun seek(webView: WebView, positionMs: Long) {
        call(webView, "seek", positionMs.coerceAtLeast(0).toString())
    }

    fun transferLikes(webView: WebView, playlistUrl: String) {
        call(webView, "transferLikes", JSONObject.quote(playlistUrl))
    }

    fun stopLikes(webView: WebView) = call(webView, "stopLikes", "")

    fun requestCollectionLink(webView: WebView) = call(webView, "collectionLink", "")

    fun requestSnapshot(webView: WebView) = call(webView, "pageSnapshot", "")

    private fun applyFont(webView: WebView) {
        val family = font.cssFamily?.let(JSONObject::quote) ?: "null"
        val url = font.stylesheetUrl?.let(JSONObject::quote) ?: "null"
        call(webView, "setFont", "$family, $url")
    }

    private fun call(webView: WebView, function: String, arguments: String) {
        webView.evaluateJavascript("window.__ymmod && window.__ymmod.$function($arguments);", null)
    }

    private fun configPrefix(config: PageConfig) = "window.__ymmodConfig = ${config.toJson()};\n"

    private companion object {
        const val TAG = "YmModBridge"
        const val BRIDGE_NAME = "YmModAndroid"
    }
}
