package eu.kanade.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.browse.bulk.parseQueries
import tachiyomi.core.common.util.QueryTransformer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchBottomSheet(
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onDismissRequest: () -> Unit,
    title: String = "Search & Filters",
    placeholderText: String = "Search...",
    searchClean: Boolean? = null,
    onToggleClean: (() -> Unit)? = null,
    searchFormat: Int? = null,
    onToggleFormat: (() -> Unit)? = null,
    searchFuzzy: Boolean? = null,
    onToggleFuzzy: (() -> Unit)? = null,
    filterContent: (@Composable () -> Unit)? = null,
    onToggleSelectionMode: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val globalSearchClean by sourcePreferences.searchClean().collectAsState()
    val globalSearchFormat by sourcePreferences.searchFormat().collectAsState()

    val effectiveClean = searchClean ?: globalSearchClean
    val effectiveFormat = searchFormat ?: globalSearchFormat
    val effectiveFuzzy = searchFuzzy ?: false

    val currentText = searchQuery ?: ""
    val parsedQueries = remember(currentText) { parseQueries(currentText) }

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val scrimColor = colorScheme.scrim.copy(alpha = 0.32f)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Transparent,
        scrimColor = scrimColor,
        tonalElevation = 0.dp,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (parsedQueries.isEmpty()) "Enter title or paste multiline text" else "${parsedQueries.size} term${if (parsedQueries.size > 1) "s" else ""} ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleSelectionMode != null) {
                        IconButton(
                            onClick = {
                                onToggleSelectionMode()
                                onDismissRequest()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = "Bulk selection mode",
                                tint = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Contextual Filters (e.g. Pinned, All, Custom Group, Has Results, Tags, Language, Statuses)
            if (filterContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                filterContent()
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Smart Transform Controls (Clean, Format, Fuzzy, Bulk)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggleSelectionMode != null) {
                    FilterChip(
                        selected = isSelectionMode,
                        onClick = {
                            onToggleSelectionMode()
                            onDismissRequest()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = { Text("Bulk Select") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                FilterChip(
                    selected = effectiveClean,
                    onClick = {
                        if (onToggleClean != null) {
                            onToggleClean()
                        } else {
                            sourcePreferences.searchClean().set(!effectiveClean)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text("Clean") },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = effectiveFormat != 0,
                    onClick = {
                        if (onToggleFormat != null) {
                            onToggleFormat()
                        } else {
                            val next = (effectiveFormat + 1) % 3
                            sourcePreferences.searchFormat().set(next)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(
                            when (effectiveFormat) {
                                1 -> "Format: Key"
                                2 -> "Format: Raw"
                                else -> "Format"
                            },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                if (onToggleFuzzy != null || searchFuzzy != null) {
                    FilterChip(
                        selected = effectiveFuzzy,
                        onClick = { onToggleFuzzy?.invoke() },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = { Text("Fuzzy") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Paste & Clear Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            clipboardManager.getText()?.text?.let { pasted ->
                                if (pasted.isNotBlank()) {
                                    val newQuery = if (currentText.isBlank()) pasted else "$currentText\n$pasted"
                                    onChangeSearchQuery(newQuery)
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "Paste Clipboard",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text("Paste") },
                        shape = RoundedCornerShape(10.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    )

                    if (currentText.isNotEmpty()) {
                        AssistChip(
                            onClick = { onChangeSearchQuery("") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text("Clear") },
                            shape = RoundedCornerShape(10.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }

                if (parsedQueries.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${parsedQueries.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-Line Search Input Box
            OutlinedTextField(
                value = currentText,
                onValueChange = { onChangeSearchQuery(it) },
                label = { Text("Search Terms") },
                placeholder = { Text(placeholderText) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 150.dp),
            )

            // Live Interactive Query Chips (for multiline / multi-token searches)
            if (parsedQueries.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "SEARCH TOKENS (${parsedQueries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    parsedQueries.forEach { rawQuery ->
                        val transformed = QueryTransformer.transform(rawQuery, effectiveClean, effectiveFormat)
                        InputChip(
                            selected = false,
                            onClick = {
                                val remaining = parsedQueries.filter { it != rawQuery }
                                onChangeSearchQuery(remaining.joinToString("\n"))
                            },
                            label = {
                                Text(
                                    text = transformed,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove query",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape),
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sticky Bottom Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        val queryToSubmit = if (parsedQueries.isNotEmpty()) {
                            parsedQueries.joinToString("\n") {
                                QueryTransformer.transform(it, effectiveClean, effectiveFormat)
                            }
                        } else {
                            currentText.trim()
                        }
                        onSearch(queryToSubmit)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.6f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (parsedQueries.size > 1) "Search (${parsedQueries.size})" else "Search",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
}
}