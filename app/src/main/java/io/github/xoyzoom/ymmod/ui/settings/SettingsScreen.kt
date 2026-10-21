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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.xoyzoom.ymmod.R
import io.github.xoyzoom.ymmod.net.RegionInfo
import io.github.xoyzoom.ymmod.net.ReleaseInfo
import io.github.xoyzoom.ymmod.presence.PresenceStatus
import io.github.xoyzoom.ymmod.scrobble.ScrobbleStatus
import io.github.xoyzoom.ymmod.settings.AppSettings
import io.github.xoyzoom.ymmod.settings.FontPreset
import io.github.xoyzoom.ymmod.settings.ThemeMode
import io.github.xoyzoom.ymmod.ui.common.ScreenScaffold
import io.github.xoyzoom.ymmod.ui.common.SectionTitle
import io.github.xoyzoom.ymmod.web.LikesProgress
import kotlin.math.roundToInt

/** Результат разовой проверки (регион, обновления). */
sealed interface CheckResult<out T> {
    data object Idle : CheckResult<Nothing>
    data object Running : CheckResult<Nothing>
    data class Done<T>(val value: T?) : CheckResult<T>
    data class Failed(val message: String) : CheckResult<Nothing>
}

/** Всё, что меню мода показывает, кроме самих настроек. */
data class SettingsStatus(
    val presence: PresenceStatus,
    val scrobble: ScrobbleStatus,
    val likes: LikesProgress,
    val region: CheckResult<RegionInfo>,
    val update: CheckResult<ReleaseInfo>,
    val currentVersion: String,
    val lastFmLogin: CheckResult<String>,
)

/** Действия меню мода. */
class SettingsActions(
    val setThemeMode: (ThemeMode) -> Unit,
    val setFontPreset: (FontPreset) -> Unit,
    val setCustomFontName: (String) -> Unit,
    val setUiScale: (Int) -> Unit,
    val setSnow: (Boolean) -> Unit,
    val setDesktopMode: (Boolean) -> Unit,
    val setAutoBestQuality: (Boolean) -> Unit,
    val setHideAppPromo: (Boolean) -> Unit,
    val setBlockTrackers: (Boolean) -> Unit,
    val setFloatingButton: (Boolean) -> Unit,
    val setDiscordEnabled: (Boolean) -> Unit,
    val setLastFmEnabled: (Boolean) -> Unit,
    val lastFmLogin: (apiKey: String, apiSecret: String, username: String, password: String) -> Unit,
    val lastFmLogout: () -> Unit,
    val openLyrics: () -> Unit,
    val transferLikes: (String) -> Unit,
    val stopLikes: () -> Unit,
    val copyCollectionLink: () -> Unit,
    val checkRegion: () -> Unit,
    val checkUpdates: () -> Unit,
    val openUrl: (String) -> Unit,
    val setDevMode: (Boolean) -> Unit,
    val saveSnapshot: () -> Unit,
    val reloadPage: () -> Unit,
    val openAbout: () -> Unit,
    val openDebug: (() -> Unit)?,
    val back: () -> Unit,
)

@Composable
fun SettingsScreen(settings: AppSettings, status: SettingsStatus, actions: SettingsActions) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = actions.back) {
        Hint(stringResource(R.string.settings_where_menu))

        // --- Плеер ---
        SectionTitle(stringResource(R.string.settings_section_player))
        SwitchRow(
            title = stringResource(R.string.settings_auto_quality),
            subtitle = stringResource(R.string.settings_auto_quality_hint),
            checked = settings.autoBestQuality,
            onCheckedChange = actions.setAutoBestQuality,
        )
        SwitchRow(
            title = stringResource(R.string.settings_hide_app_promo),
            subtitle = stringResource(R.string.settings_hide_app_promo_hint),
            checked = settings.hideAppPromo,
            onCheckedChange = actions.setHideAppPromo,
        )
        SwitchRow(
            title = stringResource(R.string.settings_block_trackers),
            subtitle = stringResource(R.string.settings_block_trackers_hint),
            checked = settings.blockTrackers,
            onCheckedChange = actions.setBlockTrackers,
        )
        OutlinedButton(onClick = actions.openLyrics, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_open_lyrics))
        }
        Hint(stringResource(R.string.settings_open_lyrics_hint))

        // --- Оформление ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_appearance))
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes) },
            onSelect = actions.setThemeMode,
        )
        Hint(stringResource(R.string.settings_theme_hint))

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = FontPreset.entries,
            selected = settings.fontPreset,
            label = { stringResource(it.labelRes) },
            onSelect = actions.setFontPreset,
        )
        if (settings.fontPreset == FontPreset.CUSTOM) {
            CustomFontField(initial = settings.customFontName, onApply = actions.setCustomFontName)
        }

        Spacer(Modifier.height(12.dp))
        ScaleSlider(value = settings.uiScale, onChange = actions.setUiScale)

        SwitchRow(
            title = stringResource(R.string.settings_snow),
            subtitle = stringResource(R.string.settings_snow_hint),
            checked = settings.snowEffect,
            onCheckedChange = actions.setSnow,
        )
        SwitchRow(
            title = stringResource(R.string.settings_desktop_mode),
            subtitle = stringResource(R.string.settings_desktop_mode_hint),
            checked = settings.desktopMode,
            onCheckedChange = actions.setDesktopMode,
        )
        SwitchRow(
            title = stringResource(R.string.settings_floating_button),
            subtitle = stringResource(R.string.settings_floating_button_hint),
            checked = settings.floatingButton,
            onCheckedChange = actions.setFloatingButton,
        )

        // --- Discord ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_discord))
        SwitchRow(
            title = stringResource(R.string.settings_discord_enabled),
            subtitle = stringResource(R.string.settings_discord_status, status.presence.label()),
            checked = settings.discordEnabled,
            onCheckedChange = actions.setDiscordEnabled,
        )
        Hint(stringResource(R.string.settings_discord_hint))

        // --- Last.fm ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_lastfm))
        LastFmSection(settings, status, actions)

        // --- Инструменты ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_tools))
        LikesTransfer(status.likes, actions)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.copyCollectionLink) {
            Text(stringResource(R.string.settings_collection_link))
        }
        Hint(stringResource(R.string.settings_collection_link_hint))
        Spacer(Modifier.height(8.dp))
        RegionCheck(status.region, actions.checkRegion)

        // --- Для разработчиков ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_dev))
        SwitchRow(
            title = stringResource(R.string.settings_dev_mode),
            subtitle = stringResource(R.string.settings_dev_mode_hint),
            checked = settings.devMode,
            onCheckedChange = actions.setDevMode,
        )
        OutlinedButton(onClick = actions.saveSnapshot, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_save_snapshot))
        }
        Hint(stringResource(R.string.settings_save_snapshot_hint))
        OutlinedButton(onClick = actions.reloadPage, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.action_reload))
        }
        if (actions.openDebug != null) {
            OutlinedButton(onClick = actions.openDebug, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_debug_fixtures))
            }
        }

        // --- Обновления и о программе ---
        Divider()
        SectionTitle(stringResource(R.string.settings_section_updates))
        UpdateCheck(status.update, status.currentVersion, actions)
        OutlinedButton(onClick = actions.openAbout, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_about))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LastFmSection(settings: AppSettings, status: SettingsStatus, actions: SettingsActions) {
    val lastFm = settings.lastFm
    if (lastFm.isAuthorized) {
        SwitchRow(
            title = stringResource(R.string.lastfm_enabled),
            subtitle = stringResource(R.string.lastfm_logged_in, lastFm.username, status.scrobble.label()),
            checked = lastFm.enabled,
            onCheckedChange = actions.setLastFmEnabled,
        )
        OutlinedButton(onClick = actions.lastFmLogout) { Text(stringResource(R.string.lastfm_logout)) }
        return
    }

    var apiKey by remember(lastFm.apiKey) { mutableStateOf(lastFm.apiKey) }
    var apiSecret by remember(lastFm.apiSecret) { mutableStateOf(lastFm.apiSecret) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Hint(stringResource(R.string.lastfm_hint))
    TextButton(stringResource(R.string.lastfm_create_key)) { actions.openUrl("https://www.last.fm/api/account/create") }
    Field(apiKey, { apiKey = it.trim() }, stringResource(R.string.lastfm_api_key))
    Field(apiSecret, { apiSecret = it.trim() }, stringResource(R.string.lastfm_api_secret))
    Field(username, { username = it.trim() }, stringResource(R.string.lastfm_username))
    Field(password, { password = it }, stringResource(R.string.lastfm_password), password = true)
    val running = status.lastFmLogin == CheckResult.Running
    Button(
        onClick = { actions.lastFmLogin(apiKey, apiSecret, username, password) },
        enabled = !running && apiKey.isNotBlank() && apiSecret.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(if (running) R.string.lastfm_logging_in else R.string.lastfm_login))
    }
    when (val login = status.lastFmLogin) {
        is CheckResult.Failed -> ErrorText(stringResource(R.string.lastfm_login_error, login.message))
        else -> Unit
    }
}

@Composable
private fun LikesTransfer(likes: LikesProgress, actions: SettingsActions) {
    var url by remember { mutableStateOf("") }
    Text(stringResource(R.string.likes_title), style = MaterialTheme.typography.bodyLarge)
    Hint(stringResource(R.string.likes_hint))
    Field(url, { url = it.trim() }, stringResource(R.string.likes_playlist_url), enabled = !likes.isRunning)
    if (likes.status != LikesProgress.Status.IDLE) {
        val text = if (likes.total > 0) "${likes.message}: ${likes.done} / ${likes.total}" else likes.message
        if (likes.isRunning) {
            LinearProgressIndicator(
                progress = { if (likes.total > 0) likes.done.toFloat() / likes.total else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        if (likes.status == LikesProgress.Status.ERROR) ErrorText(text) else Hint(text)
    }
    Button(
        onClick = { if (likes.isRunning) actions.stopLikes() else actions.transferLikes(url) },
        enabled = likes.isRunning || url.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(if (likes.isRunning) R.string.likes_stop else R.string.likes_start))
    }
}

@Composable
private fun RegionCheck(region: CheckResult<RegionInfo>, onCheck: () -> Unit) {
    OutlinedButton(onClick = onCheck, enabled = region != CheckResult.Running) {
        Text(stringResource(R.string.region_check))
    }
    when (region) {
        CheckResult.Idle -> Hint(stringResource(R.string.region_hint))
        CheckResult.Running -> Hint(stringResource(R.string.checking))
        is CheckResult.Failed -> ErrorText(region.message)
        is CheckResult.Done -> {
            val info = region.value
            when {
                info == null -> ErrorText(stringResource(R.string.region_unknown))
                info.isSupported -> Hint(stringResource(R.string.region_ok, info.countryCode))
                else -> ErrorText(stringResource(R.string.region_blocked, info.countryCode))
            }
        }
    }
}

@Composable
private fun UpdateCheck(update: CheckResult<ReleaseInfo>, currentVersion: String, actions: SettingsActions) {
    Hint(stringResource(R.string.update_current, currentVersion))
    OutlinedButton(onClick = actions.checkUpdates, enabled = update != CheckResult.Running) {
        Text(stringResource(R.string.update_check))
    }
    when (update) {
        CheckResult.Idle -> Unit
        CheckResult.Running -> Hint(stringResource(R.string.checking))
        is CheckResult.Failed -> ErrorText(update.message)
        is CheckResult.Done -> {
            val release = update.value
            if (release == null) {
                Hint(stringResource(R.string.update_none))
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.update_available, release.version), style = MaterialTheme.typography.titleSmall)
                        Button(
                            onClick = { actions.openUrl(release.apkUrl ?: release.url) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.update_download))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleSlider(value: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Text(stringResource(R.string.settings_ui_scale, current.roundToInt()), style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = current,
        onValueChange = { current = it },
        onValueChangeFinished = { onChange(current.roundToInt()) },
        valueRange = AppSettings.MIN_UI_SCALE.toFloat()..AppSettings.MAX_UI_SCALE.toFloat(),
        steps = (AppSettings.MAX_UI_SCALE - AppSettings.MIN_UI_SCALE) / 5 - 1,
    )
    Hint(stringResource(R.string.settings_ui_scale_hint))
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
private fun ScrobbleStatus.label(): String = when (this) {
    ScrobbleStatus.Off -> stringResource(R.string.lastfm_status_off)
    ScrobbleStatus.Ready -> stringResource(R.string.lastfm_status_ready)
    is ScrobbleStatus.Scrobbled -> stringResource(R.string.lastfm_status_scrobbled, title)
    is ScrobbleStatus.Error -> stringResource(R.string.lastfm_status_error, message)
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
        Field(value, { value = it.take(64) }, stringResource(R.string.settings_custom_font_label))
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
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
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
private fun TextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(top = 16.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
