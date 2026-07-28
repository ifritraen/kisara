package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.ColorizerAction
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ColorizerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onColorizeClicked: (ColorizerAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DefaultDropdownMenuOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = {
            ColorizerDropdownMenuItems(
                onDismissRequest = onDismissRequest,
                onColorizeClicked = onColorizeClicked,
            )
        },
    )
}

@Composable
private fun ColorizerDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onColorizeClicked: (ColorizerAction) -> Unit,
) {
    val options = persistentListOf(
        ColorizerAction.NEXT_1_CHAPTER to "Colorize next 1 chapter",
        ColorizerAction.NEXT_5_CHAPTERS to "Colorize next 5 chapters",
        ColorizerAction.NEXT_10_CHAPTERS to "Colorize next 10 chapters",
        ColorizerAction.NEXT_25_CHAPTERS to "Colorize next 25 chapters",
        ColorizerAction.UNREAD_CHAPTERS to "Colorize unread chapters",
        ColorizerAction.BOOKMARKED_CHAPTERS to "Colorize bookmarked chapters",
    )

    options.forEach { (colorizeAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onColorizeClicked(colorizeAction)
                onDismissRequest()
            },
        )
    }
}
