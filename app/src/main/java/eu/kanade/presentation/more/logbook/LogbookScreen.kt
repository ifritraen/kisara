package eu.kanade.presentation.more.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tachiyomi.domain.logbook.model.LogbookActionType
import tachiyomi.domain.logbook.model.LogbookEntry
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogbookScreen(
    isLoading: Boolean,
    entries: List<LogbookEntry>,
    selectedType: LogbookActionType?,
    searchQuery: String,
    onSelectType: (LogbookActionType?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToManga: (Long) -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToSettings: (String?) -> Unit,
    onNavigateToExtensions: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(KMR.strings.label_logbook)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            // Search Bar & Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                placeholder = { Text("Search logbook...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                item {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { onSelectType(null) },
                        label = { Text("All") },
                    )
                }
                items(LogbookActionType.entries) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onSelectType(if (selectedType == type) null else type) },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            when {
                isLoading -> LoadingScreen()
                entries.isEmpty() -> EmptyScreen(stringResource(KMR.strings.logbook_empty))
                else -> {
                    val groupedEntries = remember(entries) {
                        entries.groupBy { formatDateHeader(it.timestamp) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(MaterialTheme.padding.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        groupedEntries.forEach { (dateHeader, dateEntries) ->
                            item(key = dateHeader) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
                                )
                            }

                            items(dateEntries, key = { it.id }) { entry ->
                                LogbookItemCard(
                                    entry = entry,
                                    onNavigateToManga = onNavigateToManga,
                                    onNavigateToCategory = onNavigateToCategory,
                                    onNavigateToSettings = onNavigateToSettings,
                                    onNavigateToExtensions = onNavigateToExtensions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(KMR.strings.label_logbook)) },
            text = { Text(stringResource(KMR.strings.logbook_clear_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LogbookItemCard(
    entry: LogbookEntry,
    onNavigateToManga: (Long) -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToSettings: (String?) -> Unit,
    onNavigateToExtensions: () -> Unit,
) {
    val icon = when (entry.actionType) {
        LogbookActionType.LIBRARY -> Icons.Outlined.Book
        LogbookActionType.READING -> Icons.Outlined.MenuBook
        LogbookActionType.SETTINGS -> Icons.Outlined.Settings
        LogbookActionType.EXTENSIONS -> Icons.Outlined.Extension
        LogbookActionType.SYSTEM -> Icons.Outlined.Tune
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val formattedTime = remember(entry.timestamp) { formatTime(entry.timestamp) }

    val annotatedString = remember(entry) {
        buildAnnotatedString {
            val title = entry.title
            val targetName = entry.targetName
            val extraData = entry.extraData

            var current = 0
            val clickables = mutableListOf<Pair<String, Pair<Int, Int>>>()

            if (!targetName.isNullOrEmpty() && title.contains(targetName)) {
                val start = title.indexOf(targetName)
                val end = start + targetName.length
                clickables.add(Pair("TARGET", Pair(start, end)))
            }

            if (!extraData.isNullOrEmpty() && title.contains(extraData)) {
                val start = title.indexOf(extraData)
                val end = start + extraData.length
                clickables.add(Pair("EXTRA", Pair(start, end)))
            }

            clickables.sortBy { it.second.first }

            clickables.forEach { (type, range) ->
                val (start, end) = range
                if (start > current) {
                    append(title.substring(current, start))
                }
                pushStringAnnotation(tag = type, annotation = if (type == "TARGET") (entry.targetId?.toString() ?: targetName.orEmpty()) else extraData.orEmpty())
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                    append(title.substring(start, end))
                }
                pop()
                current = end
            }

            if (current < title.length) {
                append(title.substring(current))
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(offset, offset).firstOrNull()?.let { annotation ->
                        when (annotation.tag) {
                            "TARGET" -> {
                                when (entry.actionType) {
                                    LogbookActionType.LIBRARY, LogbookActionType.READING -> {
                                        entry.targetId?.let { onNavigateToManga(it) }
                                    }
                                    LogbookActionType.SETTINGS -> onNavigateToSettings(entry.extraData)
                                    LogbookActionType.EXTENSIONS -> onNavigateToExtensions()
                                    else -> {}
                                }
                            }
                            "EXTRA" -> {
                                if (entry.actionType == LogbookActionType.LIBRARY) {
                                    onNavigateToCategory()
                                }
                            }
                        }
                    }
                },
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
