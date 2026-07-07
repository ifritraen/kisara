package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DisplayOverlaySettingsDialog(
    onDismissRequest: () -> Unit,
) {
    val libraryPreferences = Injekt.get<LibraryPreferences>()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_display))
        },
        text = {
            Column {
                CheckboxItem(
                    label = stringResource(MR.strings.action_display_download_badge),
                    pref = libraryPreferences.downloadBadge(),
                )
                CheckboxItem(
                    label = stringResource(MR.strings.action_display_unread_badge),
                    pref = libraryPreferences.unreadBadge(),
                )
                CheckboxItem(
                    label = stringResource(MR.strings.action_display_local_badge),
                    pref = libraryPreferences.localBadge(),
                )
                CheckboxItem(
                    label = stringResource(MR.strings.action_display_language_badge),
                    pref = libraryPreferences.languageBadge(),
                )
                val showLang by libraryPreferences.languageBadge().collectAsState()
                if (showLang) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        CheckboxItem(
                            label = stringResource(KMR.strings.action_display_language_icon),
                            pref = libraryPreferences.useLangIcon(),
                        )
                    }
                }
                CheckboxItem(
                    label = stringResource(KMR.strings.action_display_source_badge),
                    pref = libraryPreferences.sourceBadge(),
                )
            }
        },
    )
}
