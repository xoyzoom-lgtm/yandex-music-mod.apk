package io.github.xoyzoom.ymmod.debug

import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.player.TrackInfo

/** Набор состояний плеера для ручной проверки UI, уведомления и Discord (только debug). */
data class PlayerFixture(
    val title: String,
    val description: String,
    val build: (nowMs: Long) -> PlayerState,
)

object PlayerFixtures {
    private fun playing(
        nowMs: Long,
        track: TrackInfo?,
        positionMs: Long = 42_000,
        durationMs: Long = 215_000,
        isPlaying: Boolean = true,
        hasPosition: Boolean = true,
    ) = PlayerState(
        track = track,
        isPlaying = isPlaying,
        hasPosition = hasPosition,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtMs = nowMs,
    )

    private val regular = TrackInfo(
        title = "Тестовый трек",
        artist = "Тестовый исполнитель",
        album = "Тестовый альбом",
        artworkUrl = null,
    )

    val all: List<PlayerFixture> = listOf(
        PlayerFixture("Ничего не играет", "Пустое состояние: Discord очищается, в уведомлении заглушка") {
            PlayerState.EMPTY.copy(updatedAtMs = it)
        },
        PlayerFixture("Играет", "Обычный трек с позицией и длительностью") {
            playing(it, regular)
        },
        PlayerFixture("Пауза", "Тот же трек на паузе: Discord очищается, уведомление можно смахнуть") {
            playing(it, regular, isPlaying = false)
        },
        PlayerFixture("Конец трека", "Позиция равна длительности") {
            playing(it, regular, positionMs = 215_000)
        },
        PlayerFixture("Без позиции", "Сайт не отдал позицию и длительность: без таймера в Discord") {
            playing(it, regular, positionMs = 0, durationMs = 0, hasPosition = false)
        },
        PlayerFixture("Длинные строки", "300 символов: обрезка до 128 в Discord и многоточие в UI") {
            playing(
                it,
                TrackInfo(
                    title = "Очень длинное название ".repeat(14).trim(),
                    artist = "Исполнитель с длинным именем ".repeat(11).trim(),
                    album = "Альбом ".repeat(43).trim(),
                    artworkUrl = null,
                ),
            )
        },
        PlayerFixture("Эмодзи и разные алфавиты", "Проверка UTF-8 в JNI и в уведомлении") {
            playing(
                it,
                TrackInfo(
                    title = "🎧 Ночной город 夜の街 🌃",
                    artist = "Ünïcödé & Ко 🎸",
                    album = "Ελληνικά · العربية · 한국어",
                    artworkUrl = null,
                ),
            )
        },
        PlayerFixture("Однобуквенное название", "Discord требует минимум 2 символа") {
            playing(it, regular.copy(title = "Я", artist = "X"))
        },
        PlayerFixture("Только исполнитель", "Пустое название: в Discord идёт имя исполнителя") {
            playing(it, regular.copy(title = ""))
        },
        PlayerFixture("Недоступная обложка", "Ошибка загрузки обложки не должна ломать уведомление") {
            playing(it, regular.copy(artworkUrl = "https://example.invalid/cover.jpg"))
        },
    )
}
