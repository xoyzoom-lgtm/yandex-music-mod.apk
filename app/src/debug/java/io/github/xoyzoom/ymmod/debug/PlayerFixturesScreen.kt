package io.github.xoyzoom.ymmod.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xoyzoom.ymmod.AppContainer
import io.github.xoyzoom.ymmod.player.PlayerState
import io.github.xoyzoom.ymmod.presence.PresenceMapper
import io.github.xoyzoom.ymmod.presence.RichPresence
import io.github.xoyzoom.ymmod.ui.common.ScreenScaffold
import io.github.xoyzoom.ymmod.ui.common.SectionTitle

/**
 * Debug-экран: подменяет состояние плеера готовыми фикстурами. Сайт и его трафик не трогаются —
 * меняется только то, что видят наш UI, уведомление и Discord.
 */
@Composable
fun PlayerFixturesScreen(container: AppContainer, onBack: () -> Unit) {
    val state by container.player.state.collectAsStateWithLifecycle()
    val overridden by container.player.isOverridden.collectAsStateWithLifecycle()
    val presenceStatus by container.presence.status.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Отладка: фикстуры плеера", onBack = onBack) {
        Text(
            "Выбранное состояние подменяет данные из WebView для интерфейса, уведомления и Discord. " +
                "Сайт продолжает работать как обычно.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(
            onClick = {
                container.player.setOverride(null)
                selected = null
            },
            enabled = overridden,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text("Вернуть данные из WebView")
        }

        SectionTitle("Сейчас")
        Text(
            text = describe(state, PresenceMapper.map(state, enabled = true), presenceStatus.toString(), overridden),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )

        SectionTitle("Фикстуры")
        PlayerFixtures.all.forEach { fixture ->
            val isSelected = overridden && selected == fixture.title
            Card(
                colors = if (isSelected) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        container.player.setOverride(fixture.build(System.currentTimeMillis()))
                        selected = fixture.title
                    },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(fixture.title, style = MaterialTheme.typography.titleSmall)
                    Text(fixture.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun describe(state: PlayerState, presence: RichPresence?, presenceStatus: String, overridden: Boolean): String =
    buildString {
        appendLine("источник: ${if (overridden) "фикстура" else "WebView"}")
        appendLine("трек: ${state.track?.let { "${it.title} — ${it.artist}" } ?: "нет"}")
        appendLine("играет: ${state.isPlaying}, позиция: ${if (state.hasPosition) "${state.positionMs} мс" else "нет"}")
        appendLine("длительность: ${state.durationMs} мс")
        appendLine()
        appendLine("Discord ($presenceStatus):")
        if (presence == null) {
            append("  очистить активность")
        } else {
            appendLine("  details: ${presence.details} (${presence.details.length})")
            appendLine("  state: ${presence.state} (${presence.state.length})")
            appendLine("  обложка: ${presence.largeImageUrl ?: "нет"}")
            append("  таймер: ${presence.startTimestampMs ?: "-"} → ${presence.endTimestampMs ?: "-"}")
        }
    }
