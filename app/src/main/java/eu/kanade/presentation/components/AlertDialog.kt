package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tachiyomi.presentation.core.components.material.DialogButtonRole
import tachiyomi.presentation.core.components.material.LocalDialogButtonRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    properties: DialogProperties = DialogProperties(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrimColor = colorScheme.scrim.copy(alpha = 0.32f)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.Transparent,
        scrimColor = scrimColor,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = null,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            style = GlassDefaults.prominentStyle(),
            dialogSurface = true,
            isStandardSurface = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomSheetDefaults.DragHandle(
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp),
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.secondary,
                            content = icon,
                        )
                    }
                }
                if (title != null) {
                    Box(
                        modifier = Modifier
                            .align(if (icon != null) Alignment.CenterHorizontally else Alignment.Start)
                            .padding(bottom = 16.dp),
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                        }
                    }
                }
                if (text != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 24.dp),
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                        }
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) {
                            CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Dismiss) {
                                dismissButton()
                            }
                        }
                        CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Confirm) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
}
