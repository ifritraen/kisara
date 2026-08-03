package eu.kanade.tachiyomi.ui.browse.bulk

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.core.common.util.QueryTransformer
import tachiyomi.domain.source.model.Source

@Composable
fun BulkQueryInputDialog(
    sources: List<Source>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var textInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    // KMK --> local-session toggle state (not persisted)
    var cleanSearch by remember { mutableStateOf(false) }
    var formatSearch by remember { mutableStateOf(0) }
    var fuzzySearch by remember { mutableStateOf(false) }
    // KMK <--

    val parsedQueries = remember(textInput) { parseQueries(textInput) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "Bulk Search",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${sources.size} target source${if (sources.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Filter chips section header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        label = { Text("Clean") },
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
                                    1 -> "Format (Key)"
                                    2 -> "Format (Raw)"
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
                        label = { Text("Fuzzy") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Input Actions Header: Paste Button & Live Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                                contentDescription = "Paste from Clipboard",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text("Paste Clipboard") },
                        shape = RoundedCornerShape(10.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    )

                    if (parsedQueries.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "${parsedQueries.size} query${if (parsedQueries.size > 1) "ies" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Text field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Search Queries") },
                    placeholder = { Text("Enter terms separated by newlines, double commas (,,), or quotes \"manga_name\"") },
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = { textInput = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = "Clear text",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "💡 Format: \"One Piece\",, Naruto\nBleach",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedQueries.isNotEmpty()) {
                        // KMK --> apply transforms per query
                        val transformed = parsedQueries.map { q ->
                            QueryTransformer.transform(q, cleanSearch, formatSearch)
                        }
                        // KMK <--
                        onConfirm(transformed)
                    }
                },
                enabled = parsedQueries.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel")
            }
        },
    )
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
