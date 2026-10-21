package io.github.xoyzoom.ymmod.net

import io.github.xoyzoom.ymmod.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ответ HTTP: код и тело (обрезанное до [Http.MAX_BODY_BYTES]). */
data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Минимальный HTTP-клиент на HttpURLConnection, чтобы не тянуть лишние зависимости. */
object Http {
    const val MAX_BODY_BYTES = 2 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000
    val userAgent = "YmMod/${BuildConfig.VERSION_NAME} (+https://github.com/xoyzoom-lgtm/yandex-music-mod.apk)"

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        request("GET", url, headers, body = null)

    suspend fun postForm(url: String, form: Map<String, String>): HttpResponse =
        request("POST", url, mapOf("Content-Type" to "application/x-www-form-urlencoded"), formEncode(form))

    fun formEncode(form: Map<String, String>): String =
        form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                val bytes = input.readNBytesCompat(MAX_BODY_BYTES)
                String(bytes, Charsets.UTF_8)
            }.orEmpty()
            HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
