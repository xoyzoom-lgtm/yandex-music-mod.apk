package io.github.xoyzoom.ymmod.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

/** Что `ymmod-probe.js` увидел на странице. */
data class PageProbe(
    val url: String,
    val title: String,
    val readyState: String,
    val elements: Int,
    val textLength: Int,
    val text: String,
    val loader: Boolean,
    val background: String,
    val width: Int,
    val height: Int,
    val modLoaded: Boolean,
    val errors: List<String>,
) {
    /** На странице нет текста или висит заставка сайта — пользователь видит пустой экран. */
    val looksEmpty: Boolean get() = loader || textLength < MIN_VISIBLE_TEXT

    companion object {
        private const val MIN_VISIBLE_TEXT = 40

        /** [raw] — результат evaluateJavascript: JSON-строка, закодированная ещё раз как строка JS. */
        fun parse(raw: String?): PageProbe? {
            if (raw.isNullOrEmpty() || raw == "null") return null
            return try {
                val json = JSONObject(if (raw.startsWith("\"")) JSONObject("{\"v\":$raw}").getString("v") else raw)
                val errors = json.optJSONArray("errors")
                PageProbe(
                    url = json.optString("url").take(300),
                    title = json.optString("title").take(200),
                    readyState = json.optString("readyState"),
                    elements = json.optInt("elements"),
                    textLength = json.optInt("textLength"),
                    text = json.optString("text").take(200),
                    loader = json.optBoolean("loader"),
                    background = json.optString("background").take(60),
                    width = json.optInt("width"),
                    height = json.optInt("height"),
                    modLoaded = json.optBoolean("modLoaded"),
                    errors = (0 until (errors?.length() ?: 0)).map { errors!!.optString(it).take(300) },
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}

data class DiagnosticsState(
    val probe: PageProbe? = null,
    val failedRequests: List<String> = emptyList(),
    val consoleErrors: List<String> = emptyList(),
    val mainFrameError: String? = null,
    /** Показать окно диагностики поверх сайта. */
    val visible: Boolean = false,
    /** Окно открылось само, потому что страница пустая. */
    val automatic: Boolean = false,
)

/**
 * Сбор диагностики, когда сайт не показывается: ошибки загрузки, консоль, содержимое экрана.
 * Нужен, потому что разработчик не видит сайт из своего региона и не может проверить приложение сам.
 */
class PageDiagnostics(private val webViewVersion: String, private val deviceInfo: String) {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    /** Пользователь закрыл автоматическое окно — до следующей загрузки страницы не показываем. */
    private var dismissedForPage = false

    fun onPageStarted() {
        dismissedForPage = false
        _state.value = DiagnosticsState()
    }

    fun onRequestFailed(description: String, mainFrame: Boolean) {
        _state.value = _state.value.let { current ->
            current.copy(
                failedRequests = (current.failedRequests + description.take(200)).takeLast(MAX_ITEMS),
                mainFrameError = if (mainFrame) description.take(200) else current.mainFrameError,
            )
        }
    }

    fun onConsoleError(message: String) {
        _state.value = _state.value.let {
            it.copy(consoleErrors = (it.consoleErrors + message.take(300)).takeLast(MAX_ITEMS))
        }
    }

    /** Результат проверки страницы через несколько секунд после загрузки. */
    fun onProbe(probe: PageProbe?, automaticCheck: Boolean) {
        val current = _state.value
        val stuck = probe == null || probe.looksEmpty || current.mainFrameError != null
        val showAutomatically = automaticCheck && stuck && !dismissedForPage
        _state.value = current.copy(
            probe = probe,
            visible = current.visible || showAutomatically,
            automatic = if (current.visible) current.automatic else showAutomatically,
        )
    }

    fun show() {
        _state.value = _state.value.copy(visible = true, automatic = false)
    }

    fun hide() {
        dismissedForPage = true
        _state.value = _state.value.copy(visible = false)
    }

    /** Текст для «Поделиться» — его можно переслать разработчику. */
    fun report(safeMode: Boolean): String = buildString {
        val state = _state.value
        appendLine("YM Mod — диагностика")
        appendLine(deviceInfo)
        appendLine("WebView: $webViewVersion")
        appendLine("Режим без мода: ${if (safeMode) "да" else "нет"}")
        state.mainFrameError?.let { appendLine("Ошибка загрузки страницы: $it") }
        val probe = state.probe
        if (probe == null) {
            appendLine("Страница не ответила на проверку (скрипт не выполнился).")
        } else {
            appendLine("Адрес: ${probe.url}")
            appendLine("Заголовок: ${probe.title}")
            appendLine("Состояние: ${probe.readyState}, элементов: ${probe.elements}, экран ${probe.width}x${probe.height}")
            appendLine("Заставка сайта на экране: ${if (probe.loader) "да" else "нет"}")
            appendLine("Фон: ${probe.background}")
            appendLine("Скрипт мода загружен: ${if (probe.modLoaded) "да" else "нет"}")
            appendLine("Текст на экране (${probe.textLength} симв.): ${probe.text.ifEmpty { "—" }}")
            if (probe.errors.isNotEmpty()) {
                appendLine("Ошибки страницы:")
                probe.errors.forEach { appendLine(" • $it") }
            }
        }
        if (state.consoleErrors.isNotEmpty()) {
            appendLine("Консоль:")
            state.consoleErrors.forEach { appendLine(" • $it") }
        }
        if (state.failedRequests.isNotEmpty()) {
            appendLine("Не загрузилось:")
            state.failedRequests.forEach { appendLine(" • $it") }
        }
    }.trimEnd()

    private companion object {
        const val MAX_ITEMS = 15
    }
}
