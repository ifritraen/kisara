package eu.kanade.tachiyomi.ui.track.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.track.matcher.TrackerSourceItem

// KMK -->
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSourcePrioritySheet(
    prioritizedSourceIds: List<Long>,
    allInstalledSources: List<TrackerSourceItem>,
    activeSourceId: Long?,
    onSavePriorityList: (List<Long>) -> Unit,
    onDirectSelectSource: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPriorityList by remember(prioritizedSourceIds) {
        mutableStateOf(
            if (prioritizedSourceIds.isNotEmpty()) prioritizedSourceIds
            else allInstalledSources.take(5).map { it.id }
        )
    }
    var isAddingSource by remember { mutableStateOf(false) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var selectedLangFilter by remember { mutableStateOf("all") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.Transparent,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
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
                androidx.compose.material3.BottomSheetDefaults.DragHandle()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
            if (!isAddingSource) {
                // Main Priority List Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Source Hierarchy (Top ${currentPriorityList.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    if (allInstalledSources.size > currentPriorityList.size) {
                        TextButton(onClick = {
                            sourceSearchQuery = ""
                            selectedLangFilter = "all"
                            isAddingSource = true
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Source")
                        }
                    }
                }

                Text(
                    text = "Extensions are searched in this exact order. The top source will provide chapters/episodes automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(currentPriorityList, key = { _, id -> id }) { index, sourceId ->
                        val source = allInstalledSources.firstOrNull { it.id == sourceId }
                        val sourceName = source?.name ?: "Source ($sourceId)"
                        val isActive = sourceId == activeSourceId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDirectSelectSource(sourceId)
                                    onDismissRequest()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(32.dp),
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = sourceName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (source != null && source.lang.isNotBlank()) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(source.lang.uppercase(), style = MaterialTheme.typography.labelSmall) },
                                            )
                                        }
                                    }
                                }

                                // Move Up
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = currentPriorityList.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            currentPriorityList = mutable
                                            onSavePriorityList(mutable)
                                        }
                                    },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move Up")
                                }

                                // Move Down
                                IconButton(
                                    onClick = {
                                        if (index < currentPriorityList.size - 1) {
                                            val mutable = currentPriorityList.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            currentPriorityList = mutable
                                            onSavePriorityList(mutable)
                                        }
                                    },
                                    enabled = index < currentPriorityList.size - 1,
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move Down")
                                }

                                // Remove
                                IconButton(
                                    onClick = {
                                        val mutable = currentPriorityList.toMutableList()
                                        mutable.removeAt(index)
                                        currentPriorityList = mutable
                                        onSavePriorityList(mutable)
                                    },
                                    enabled = currentPriorityList.size > 1,
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            } else {
                // Add Source View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = { isAddingSource = false }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Add to Priority Hierarchy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Search Bar
                OutlinedTextField(
                    value = sourceSearchQuery,
                    onValueChange = { sourceSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    placeholder = { Text("Search installed extensions...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (sourceSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { sourceSearchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )

                // Language Filter Chips
                val availableLangs = remember(allInstalledSources) {
                    listOf("all") + allInstalledSources
                        .map { it.lang.lowercase() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }

                if (availableLangs.size > 2) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(availableLangs) { lang ->
                            val isSelected = selectedLangFilter == lang
                            val labelText = if (lang == "all") "All Languages" else lang.uppercase()
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLangFilter = lang },
                                label = { Text(labelText, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Filtered and Alphabetically Sorted Sources
                val unselectedSources = remember(allInstalledSources, currentPriorityList, sourceSearchQuery, selectedLangFilter) {
                    allInstalledSources
                        .filter { it.id !in currentPriorityList }
                        .filter { source ->
                            val matchesQuery = sourceSearchQuery.isBlank() ||
                                source.name.contains(sourceSearchQuery, ignoreCase = true) ||
                                source.lang.contains(sourceSearchQuery, ignoreCase = true) ||
                                source.id.toString().contains(sourceSearchQuery)
                            val matchesLang = selectedLangFilter == "all" || source.lang.equals(selectedLangFilter, ignoreCase = true)
                            matchesQuery && matchesLang
                        }
                        .sortedBy { it.name.lowercase() }
                }

                if (unselectedSources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (sourceSearchQuery.isNotBlank() || selectedLangFilter != "all") {
                                "No matching sources found."
                            } else {
                                "All installed sources are already in your priority list."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(unselectedSources, key = { it.id }) { source ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val updated = currentPriorityList + source.id
                                        currentPriorityList = updated
                                        onSavePriorityList(updated)
                                        isAddingSource = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (source.lang.isNotBlank()) {
                                            Text(
                                                text = source.lang.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = currentPriorityList + source.id
                                            currentPriorityList = updated
                                            onSavePriorityList(updated)
                                            isAddingSource = false
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}
// KMK <--
