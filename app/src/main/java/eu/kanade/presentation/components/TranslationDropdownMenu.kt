package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.TranslationAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onTranslateClicked: (TranslationAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DefaultDropdownMenuOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = {
            TranslationDropdownMenuItems(
                onDismissRequest = onDismissRequest,
                onTranslateClicked = onTranslateClicked,
            )
        },
    )
}

@Composable
private fun TranslationDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onTranslateClicked: (TranslationAction) -> Unit,
) {
    // We can reuse MR strings for translation bulk labels
    val options = persistentListOf(
        TranslationAction.NEXT_1_CHAPTER to "Translate next 1 chapter",
        TranslationAction.NEXT_5_CHAPTERS to "Translate next 5 chapters",
        TranslationAction.NEXT_10_CHAPTERS to "Translate next 10 chapters",
        TranslationAction.NEXT_25_CHAPTERS to "Translate next 25 chapters",
        TranslationAction.UNREAD_CHAPTERS to "Translate unread chapters",
        TranslationAction.BOOKMARKED_CHAPTERS to "Translate bookmarked chapters",
    )

    options.forEach { (translateAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onTranslateClicked(translateAction)
                onDismissRequest()
            },
        )
    }
}
