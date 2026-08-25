package eu.kanade.presentation.entries.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EntryBottomActionMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    isManga: Boolean = true,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    onFillermarkClicked: (() -> Unit)? = null,
    onRemoveFillermarkClicked: (() -> Unit)? = null,
    onMarkAsViewedClicked: (() -> Unit)? = null,
    onMarkAsUnviewedClicked: (() -> Unit)? = null,
    onMarkPreviousAsViewedClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    onExternalClicked: (() -> Unit)? = null,
    onInternalClicked: (() -> Unit)? = null,
    onTranslationBatchClicked: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBookmarkClicked != null) {
                    IconButton(onClick = onBookmarkClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Bookmark,
                            contentDescription = stringResource(MR.strings.action_bookmark),
                        )
                    }
                }
                if (onRemoveBookmarkClicked != null) {
                    IconButton(onClick = onRemoveBookmarkClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_remove_bookmark),
                        )
                    }
                }
                if (onMarkAsViewedClicked != null) {
                    IconButton(onClick = onMarkAsViewedClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(MR.strings.action_mark_as_read),
                        )
                    }
                }
                if (onMarkAsUnviewedClicked != null) {
                    IconButton(onClick = onMarkAsUnviewedClicked) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveDone,
                            contentDescription = stringResource(MR.strings.action_mark_as_unread),
                        )
                    }
                }
                if (onMarkPreviousAsViewedClicked != null) {
                    IconButton(onClick = onMarkPreviousAsViewedClicked) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = stringResource(MR.strings.action_mark_previous_as_read),
                        )
                    }
                }
                if (onDownloadClicked != null) {
                    IconButton(onClick = onDownloadClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(MR.strings.action_download),
                        )
                    }
                }
                if (onTranslationBatchClicked != null) {
                    IconButton(onClick = onTranslationBatchClicked) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = stringResource(KMR.strings.action_translate),
                        )
                    }
                }
                if (onExternalClicked != null) {
                    IconButton(onClick = onExternalClicked) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = stringResource(KMR.strings.action_play_externally),
                        )
                    }
                }
            }
        }
    }
}

fun normalizeAuroraGlobalSearchQuery(title: String): String {
    return title.trim().takeIf { it.isNotBlank() } ?: ""
}

