package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.KisaraBottomSheet
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: ImmutableSet<String>,
    excludedScanlators: ImmutableSet<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sortedAvailableScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val mutableExcludedScanlators = remember(excludedScanlators) { excludedScanlators.toMutableStateList() }
    val colorScheme = MaterialTheme.colorScheme

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(MR.strings.exclude_scanlators),
        headerActions = {
            if (sortedAvailableScanlators.isNotEmpty()) {
                if (mutableExcludedScanlators.isEmpty()) {
                    TextButton(onClick = { mutableExcludedScanlators.addAll(availableScanlators) }) {
                        Text(text = stringResource(MR.strings.action_select_all))
                    }
                } else {
                    TextButton(onClick = mutableExcludedScanlators::clear) {
                        Text(text = stringResource(MR.strings.action_reset))
                    }
                }
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                if (sortedAvailableScanlators.isNotEmpty()) {
                    Button(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            onConfirm(mutableExcludedScanlators.toSet())
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
    ) {
        if (sortedAvailableScanlators.isEmpty()) {
            Text(
                text = stringResource(MR.strings.no_scanlators_found),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                Box {
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        items(
                            items = sortedAvailableScanlators,
                            contentType = { "item" },
                            key = { it },
                        ) { scanlator ->
                            val isExcluded = mutableExcludedScanlators.contains(scanlator)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isExcluded) {
                                            mutableExcludedScanlators.remove(scanlator)
                                        } else {
                                            mutableExcludedScanlators.add(scanlator)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = if (isExcluded) {
                                        Icons.Rounded.DisabledByDefault
                                    } else {
                                        Icons.Rounded.CheckBoxOutlineBlank
                                    },
                                    tint = if (isExcluded) {
                                        colorScheme.error
                                    } else {
                                        LocalContentColor.current
                                    },
                                    contentDescription = null,
                                )
                                Text(
                                    text = scanlator,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                    if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                    if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
