package io.github.xoyzoom.ymmod.web

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.xoyzoom.ymmod.player.PlayerCommand
import io.github.xoyzoom.ymmod.player.PlayerMessageParser
import io.github.xoyzoom.ymmod.player.PlayerStateRepository
import io.github.xoyzoom.ymmod.settings.FontSpec
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
) {
    private var documentStartInjected = false
    private var font: FontSpec = FontSpec.NONE

    @SuppressLint("RequiresFeature")
    fun install(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                BRIDGE_NAME,
                YandexMusicSite.ALLOWED_ORIGINS,
            ) { _, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame || !YandexMusicSite.isMusicOrigin(sourceOrigin)) return@addWebMessageListener
                val data = message.data ?: return@addWebMessageListener
                PlayerMessageParser.parse(data, System.currentTimeMillis())?.let(player::update)
            }
        } else {
            Log.w(TAG, "WEB_MESSAGE_LISTENER не поддерживается: обновите Android System WebView")
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, YandexMusicSite.ALLOWED_ORIGINS)
            documentStartInjected = true
        }
    }

    /** Вызывается из WebViewClient.onPageFinished. */
    fun onPageFinished(webView: WebView, url: String?) {
        if (!YandexMusicSite.isMusicOrigin(url?.let(android.net.Uri::parse))) return
        // Запасной путь для старых WebView: скрипт сам защищён от повторного запуска.
        if (!documentStartInjected) webView.evaluateJavascript(script, null)
        applyFont(webView)
        webView.evaluateJavascript("window.__ymmod && window.__ymmod.refresh();", null)
    }

    fun setFont(webView: WebView, spec: FontSpec) {
        if (spec == font) return
        font = spec
        applyFont(webView)
    }

    fun send(webView: WebView, command: PlayerCommand) {
        val action = JSONObject.quote(command.jsAction)
        webView.evaluateJavascript("window.__ymmod && window.__ymmod.control($action);", null)
    }

    private fun applyFont(webView: WebView) {
        val family = font.cssFamily?.let(JSONObject::quote) ?: "null"
        val url = font.stylesheetUrl?.let(JSONObject::quote) ?: "null"
        webView.evaluateJavascript("window.__ymmod && window.__ymmod.setFont($family, $url);", null)
    }

    private companion object {
        const val TAG = "YmModBridge"
        const val BRIDGE_NAME = "YmModAndroid"
    }
}
