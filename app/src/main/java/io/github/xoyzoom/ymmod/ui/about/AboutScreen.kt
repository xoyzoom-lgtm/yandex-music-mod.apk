package io.github.xoyzoom.ymmod.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.xoyzoom.ymmod.BuildConfig
import io.github.xoyzoom.ymmod.R
import io.github.xoyzoom.ymmod.ui.common.ScreenScaffold
import io.github.xoyzoom.ymmod.ui.common.SectionTitle

const val ORIGINAL_REPO_URL = "https://github.com/Vzlomhik2005/Yandex-Music-Mod"
const val THIS_REPO_URL = "https://github.com/xoyzoom-lgtm/yandex-music-mod.apk"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.about_description), modifier = Modifier.padding(top = 12.dp))

        SectionTitle(stringResource(R.string.about_based_on_title))
        Text(stringResource(R.string.about_based_on))
        TextButton(onClick = { uriHandler.openUri(ORIGINAL_REPO_URL) }) {
            Text(stringResource(R.string.about_original_repo))
        }
        TextButton(onClick = { uriHandler.openUri(THIS_REPO_URL) }) {
            Text(stringResource(R.string.about_this_repo))
        }

        SectionTitle(stringResource(R.string.about_disclaimer_title))
        Text(stringResource(R.string.about_disclaimer))

        SectionTitle(stringResource(R.string.about_licenses_title))
        LicenseCard(stringResource(R.string.about_license_this), "licenses/LICENSE.txt")
        LicenseCard(stringResource(R.string.about_license_original), "licenses/ORIGINAL_LICENSE.txt")
    }
}

@Composable
private fun LicenseCard(title: String, assetPath: String) {
    val context = LocalContext.current
    var expanded by rememberSaveable(assetPath) { mutableStateOf(false) }
    val text = remember(assetPath) {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(title) }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
