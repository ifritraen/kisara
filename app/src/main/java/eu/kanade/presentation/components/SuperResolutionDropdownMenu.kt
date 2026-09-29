package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.SuperResolutionAction
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SuperResolutionDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSuperResolutionClicked: (SuperResolutionAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DefaultDropdownMenuOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = {
            SuperResolutionDropdownMenuItems(
                onDismissRequest = onDismissRequest,
                onSuperResolutionClicked = onSuperResolutionClicked,
            )
        },
    )
}

@Composable
private fun SuperResolutionDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onSuperResolutionClicked: (SuperResolutionAction) -> Unit,
) {
    val options = persistentListOf(
        SuperResolutionAction.NEXT_1_CHAPTER to "Super-resolve next 1 chapter",
        SuperResolutionAction.NEXT_5_CHAPTERS to "Super-resolve next 5 chapters",
        SuperResolutionAction.NEXT_10_CHAPTERS to "Super-resolve next 10 chapters",
        SuperResolutionAction.NEXT_25_CHAPTERS to "Super-resolve next 25 chapters",
        SuperResolutionAction.UNREAD_CHAPTERS to "Super-resolve unread chapters",
        SuperResolutionAction.BOOKMARKED_CHAPTERS to "Super-resolve bookmarked chapters",
    )

    options.forEach { (srAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onSuperResolutionClicked(srAction)
                onDismissRequest()
            },
        )
    }
}
