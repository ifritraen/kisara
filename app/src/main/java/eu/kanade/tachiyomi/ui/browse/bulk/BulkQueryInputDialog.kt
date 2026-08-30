package eu.kanade.tachiyomi.ui.browse.bulk

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.outlined.Source
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.core.common.util.QueryTransformer
import tachiyomi.domain.source.model.Source

data class BulkSourceItemInfo(
    val id: Long,
    val name: String,
)

private fun extractSourceInfo(source: Any): BulkSourceItemInfo? {
    return when (source) {
        is Source -> BulkSourceItemInfo(source.id, source.name)
        is tachiyomi.domain.source.anime.model.AnimeSource -> BulkSourceItemInfo(source.id, source.name)
        is tachiyomi.domain.source.novel.model.NovelSource -> BulkSourceItemInfo(source.id, source.name)
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkQueryInputDialog(
    sources: List<Any>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var textInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Transform states
    var cleanSearch by remember { mutableStateOf(false) }
    var formatSearch by remember { mutableStateOf(0) }
    var fuzzySearch by remember { mutableStateOf(false) }

    val parsedQueries = remember(textInput) { parseQueries(textInput) }
    val sourceList = remember(sources) { sources.mapNotNull { extractSourceInfo(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
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
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Bulk Multi-Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${sourceList.size} target source${if (sourceList.size > 1) "s" else ""} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(modifier = Modifier.height(14.dp))

            // Source Chips Strip
            if (sourceList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sourceList.forEach { src ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Source,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = src.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Transform Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = cleanSearch,
                    onClick = { cleanSearch = !cleanSearch },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text("Clean (Strip Tags)") },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = formatSearch != 0,
                    onClick = { formatSearch = (formatSearch + 1) % 3 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(
                            when (formatSearch) {
                                1 -> "Format: Key Words"
                                2 -> "Format: Raw Tokens"
                                else -> "Format"
                            },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = fuzzySearch,
                    onClick = { fuzzySearch = !fuzzySearch },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = { Text("Fuzzy Matching") },
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                                    textInput = if (textInput.isBlank()) pasted else "$textInput\n$pasted"
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
                        label = { Text("Paste Clipboard") },
                        shape = RoundedCornerShape(10.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    )

                    if (textInput.isNotEmpty()) {
                        AssistChip(
                            onClick = { textInput = "" },
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
                            text = "${parsedQueries.size} ready",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Query Input Box
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Search Queries") },
                placeholder = { Text("Enter terms separated by newlines, double commas (,,), or quotes \"manga_title\"") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Format: \"One Piece\",, Naruto \n Bleach (Separated by newlines, double commas, or quotes)",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )

            // Live Interactive Query Chips Preview
            if (parsedQueries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "PARSED QUERY TOKENS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    parsedQueries.forEach { rawQuery ->
                        val transformed = QueryTransformer.transform(rawQuery, cleanSearch, formatSearch)
                        InputChip(
                            selected = false,
                            onClick = {
                                val remaining = parsedQueries.filter { it != rawQuery }
                                textInput = remaining.joinToString("\n")
                            },
                            label = {
                                Text(
                                    text = transformed,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove query",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (parsedQueries.isNotEmpty()) {
                            val transformed = parsedQueries.map { q ->
                                QueryTransformer.transform(q, cleanSearch, formatSearch)
                            }
                            onConfirm(transformed)
                        }
                    },
                    enabled = parsedQueries.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.8f),
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
                        text = if (parsedQueries.isEmpty()) "Enter Queries" else "Search (${parsedQueries.size})",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

fun parseQueries(input: String): List<String> {
    val normalized = input.replace("\u201c", "\"").replace("\u201d", "\"")
    val results = mutableListOf<String>()

    // 1. Extract content inside double quotes
    val quoteRegex = Regex("\"([^\"]+)\"")
    val matches = quoteRegex.findAll(normalized)
    for (match in matches) {
        val content = match.groupValues[1].trim()
        if (content.isNotEmpty()) {
            results.add(QueryTransformer.fixMissingLeadingBracket(content))
        }
    }

    // 2. Remove quoted parts
    val remaining = normalized.replace(quoteRegex, "")

    // 3. Split by double commas (,,) and newlines
    val items = remaining.split(Regex(",,|\\r?\\n"))
    for (item in items) {
        val trimmed = item.trim()
        if (trimmed.isNotEmpty()) {
            results.add(QueryTransformer.fixMissingLeadingBracket(trimmed))
        }
    }

    return results.distinct()
}
