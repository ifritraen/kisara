@file:Suppress("PropertyName")

package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.KisaraBottomSheet
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun WhatsNewDialog(
    onDismissRequest: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        onDismissRequest()
                        onOpenWhatsNew()
                    },
                ) {
                    Text(text = stringResource(MR.strings.whats_new))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    ) {
        Text(
            text = AboutScreen.getVersionName(withBuildDate = true),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Preview
@Composable
fun WhatsNewDialogPreview() {
    WhatsNewDialog({})
}
