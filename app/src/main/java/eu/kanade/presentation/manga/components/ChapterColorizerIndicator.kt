package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.translation.model.Translation
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun ChapterColorizerIndicator(
    enabled: Boolean,
    colorizerStateProvider: () -> Translation.State,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = colorizerStateProvider()) {
        Translation.State.NOT_TRANSLATED -> NotColorizedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        Translation.State.QUEUE, Translation.State.TRANSLATING -> ColorizingIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        Translation.State.TRANSLATED -> ColorizedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        Translation.State.ERROR -> ColorizerErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotColorizedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.START) },
                onClick = { onClick(ChapterTranslationAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Palette,
            contentDescription = "Colorize Chapter",
            modifier = Modifier.size(ColorizerIndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorizingIndicator(
    enabled: Boolean,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant

        CircularProgressIndicator(
            modifier = ColorizerIndicatorModifier,
            color = strokeColor,
            strokeWidth = ColorizerIndicatorStrokeWidth,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
        )

        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(ChapterTranslationAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
        Icon(
            imageVector = Icons.Outlined.Palette,
            contentDescription = null,
            modifier = ColorizingIndicatorModifier,
            tint = strokeColor,
        )
    }
}

@Composable
private fun ColorizedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = null,
            modifier = Modifier.size(ColorizerIndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete)) },
                onClick = {
                    onClick(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ColorizerErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.START) },
                onClick = { onClick(ChapterTranslationAction.START) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "Colorize error",
            modifier = Modifier.size(ColorizerIndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val ColorizerIndicatorSize = 23.dp
private val ColorizerIndicatorPadding = 2.dp
private val ColorizerIndicatorStrokeWidth = ColorizerIndicatorPadding
private val ColorizerIndicatorModifier = Modifier
    .size(ColorizerIndicatorSize)
    .padding(ColorizerIndicatorPadding)
private val ColorizingIndicatorModifier = Modifier
    .size(ColorizerIndicatorSize - 7.dp)
