package io.github.xoyzoom.ymmod.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.xoyzoom.ymmod.R
import io.github.xoyzoom.ymmod.presence.PresenceStatus
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.FontPreset
import io.github.xoyzoom.ymmod.settings.ThemeMode
import io.github.xoyzoom.ymmod.ui.common.ScreenScaffold
import io.github.xoyzoom.ymmod.ui.common.SectionTitle

@Composable
fun SettingsScreen(
    settings: AppSettings,
    presenceStatus: PresenceStatus,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontPresetChange: (FontPreset) -> Unit,
    onCustomFontNameChange: (String) -> Unit,
    onDesktopModeChange: (Boolean) -> Unit,
    onDiscordEnabledChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        SectionTitle(stringResource(R.string.settings_section_appearance))

        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes) },
            onSelect = onThemeModeChange,
        )
        Hint(stringResource(R.string.settings_theme_hint))

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = FontPreset.entries,
            selected = settings.fontPreset,
            label = { stringResource(it.labelRes) },
            onSelect = onFontPresetChange,
        )
        if (settings.fontPreset == FontPreset.CUSTOM) {
            CustomFontField(initial = settings.customFontName, onApply = onCustomFontNameChange)
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle(stringResource(R.string.settings_section_site))
        SwitchRow(
            title = stringResource(R.string.settings_desktop_mode),
            subtitle = stringResource(R.string.settings_desktop_mode_hint),
            checked = settings.desktopMode,
            onCheckedChange = onDesktopModeChange,
        )

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle(stringResource(R.string.settings_section_discord))
        SwitchRow(
            title = stringResource(R.string.settings_discord_enabled),
            subtitle = stringResource(R.string.settings_discord_status, presenceStatus.label()),
            checked = settings.discordEnabled,
            onCheckedChange = onDiscordEnabledChange,
        )
        Hint(stringResource(R.string.settings_discord_hint))

        HorizontalDivider(Modifier.padding(top = 16.dp))
        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.settings_about))
        }
    }
}

@Composable
private fun PresenceStatus.label(): String = when (this) {
    PresenceStatus.Disabled -> stringResource(R.string.discord_status_disabled)
    PresenceStatus.NotConfigured -> stringResource(R.string.discord_status_not_configured)
    PresenceStatus.Connecting -> stringResource(R.string.discord_status_connecting)
    PresenceStatus.Connected -> stringResource(R.string.discord_status_connected)
    is PresenceStatus.Error -> stringResource(R.string.discord_status_error, message)
}

@Composable
private fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label(option))
            }
        }
    }
}

@Composable
private fun CustomFontField(initial: String, onApply: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(64) },
            label = { Text(stringResource(R.string.settings_custom_font_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.settings_custom_font_hint))
        Button(
            onClick = { onApply(value.trim()) },
            enabled = value.trim() != initial,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.action_apply))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
