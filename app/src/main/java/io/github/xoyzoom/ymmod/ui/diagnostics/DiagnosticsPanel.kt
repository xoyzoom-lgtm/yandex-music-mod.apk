package io.github.xoyzoom.ymmod.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.xoyzoom.ymmod.R

/**
 * Окно «Сайт не загрузился»: что видно на странице, ошибки и кнопки, которые помогают
 * понять, виноват мод или сам сайт. Всё помещается на один скриншот.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsPanel(
    report: String,
    automatic: Boolean,
    safeMode: Boolean,
    onReload: () -> Unit,
    onToggleSafeMode: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(if (automatic) R.string.diagnostics_title_stuck else R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (safeMode) R.string.diagnostics_hint_safe_mode else R.string.diagnostics_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SelectionContainer {
                    Text(
                        report,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Button(onClick = onReload) { Text(stringResource(R.string.action_reload)) }
                    OutlinedButton(onClick = onToggleSafeMode) {
                        Text(stringResource(if (safeMode) R.string.diagnostics_enable_mod else R.string.diagnostics_safe_mode))
                    }
                    OutlinedButton(onClick = onShare) { Text(stringResource(R.string.diagnostics_share)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.diagnostics_close)) }
                }
            }
        }
    }
}
