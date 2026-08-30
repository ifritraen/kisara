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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source

// KMK -->
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSourcePrioritySheet(
    prioritizedSourceIds: List<Long>,
    allInstalledSources: List<CatalogueSource>,
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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
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

                    if (currentPriorityList.size < 8 && allInstalledSources.size > currentPriorityList.size) {
                        TextButton(onClick = { isAddingSource = true }) {
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
                                    Text(
                                        text = sourceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (source != null) {
                                        Text(
                                            text = source.lang.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val unselectedSources = allInstalledSources.filter { it.id !in currentPriorityList }
                if (unselectedSources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("All installed sources are already in your priority list.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(unselectedSources, key = { _, s -> s.id }) { _, source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = currentPriorityList + source.id
                                        currentPriorityList = updated
                                        onSavePriorityList(updated)
                                        isAddingSource = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = source.lang.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Outlined.Add, contentDescription = "Add")
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
