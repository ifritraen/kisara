package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tachiyomi.presentation.core.components.material.DialogButtonRole
import tachiyomi.presentation.core.components.material.LocalDialogButtonRole

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    properties: DialogProperties = DialogProperties(),
) {
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (window != null) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.attributes.blurBehindRadius = 60
            }
        }
        onDispose {}
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        GlassSurface(
            modifier = modifier
                .sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            shape = shape,
            style = GlassDefaults.prominentStyle(),
            dialogSurface = true,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
