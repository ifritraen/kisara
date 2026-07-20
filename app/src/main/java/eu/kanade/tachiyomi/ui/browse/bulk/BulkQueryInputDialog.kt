package eu.kanade.tachiyomi.ui.browse.bulk

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.util.QueryTransformer
import tachiyomi.domain.source.model.Source

@Composable
fun BulkQueryInputDialog(
    sources: List<Source>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var textInput by remember { mutableStateOf("") }
    // KMK --> local-session toggle state (not persisted)
    var cleanSearch by remember { mutableStateOf(false) }
    var formatSearch by remember { mutableStateOf(false) }
    var fuzzySearch by remember { mutableStateOf(false) }
    // KMK <--

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Bulk Search on ${sources.size} sources") },
        text = {
            Column {
                Text("Enter search terms separated by newlines, double commas (,,), or enclosed in double quotes \"manga_name\".")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "💡 Format: \"One Piece\",, Naruto\nBleach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // KMK -->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    )
                    FilterChip(
                        selected = formatSearch,
                        onClick = { formatSearch = !formatSearch },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = { Text("Format") },
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
                    )
                }
                // KMK <--
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Search Queries") },
                    placeholder = { Text("e.g. \"One Piece\",, Naruto\nBleach") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseQueries(textInput)
                    if (parsed.isNotEmpty()) {
                        // KMK --> apply transforms per query
                        val transformed = parsed.map { q ->
                            QueryTransformer.transform(q, cleanSearch, formatSearch)
                        }
                        // KMK <--
                        onConfirm(transformed)
                    }
                },
                enabled = textInput.isNotBlank(),
            ) {
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
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
            results.add(content)
        }
    }

    // 2. Remove quoted parts
    val remaining = normalized.replace(quoteRegex, "")

    // 3. Split by double commas (,,) and newlines
    val items = remaining.split(Regex(",,|\\r?\\n"))
    for (item in items) {
        val trimmed = item.trim()
        if (trimmed.isNotEmpty()) {
            results.add(trimmed)
        }
    }

    return results.distinct()
}
