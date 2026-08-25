package eu.kanade.tachiyomi.ui.suggestions.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.suggestions.anime.AnimeSuggestionsReport
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.animeSuggestionsTab(
    screenModel: AnimeSuggestionsScreenModel = rememberScreenModel { AnimeSuggestionsScreenModel() },
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val haptic = LocalHapticFeedback.current
    val state by screenModel.state.collectAsState()

    val fetchedCount by AnimeSuggestionsReport.fetchedCount.collectAsState()
    val failedCount by AnimeSuggestionsReport.failedCount.collectAsState()
    val fetchedBySource by AnimeSuggestionsReport.fetchedBySource.collectAsState()
    val failedBySource by AnimeSuggestionsReport.failedBySource.collectAsState()
    val libraryFilteredCount by AnimeSuggestionsReport.libraryFilteredCount.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = KMR.strings.action_suggestions,
        badgeNumber = state.suggestions.size,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_update_library),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::loadSuggestions,
            ),
        ),
        content = { contentPadding, _ ->
            val dialog = state.dialog
            if (dialog is AnimeSuggestionsScreenModel.State.Dialog.ChangeCategory) {
                val changeCategoryAnime = dialog.anime
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setAnimeCategories(changeCategoryAnime, included)
                    },
                    onDuplicateCheck = {
                        screenModel.dismissDialog()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen(changeCategoryAnime.id))
                    },
                    onDelete = {
                        screenModel.deleteAnime(changeCategoryAnime)
                    },
                    anime = changeCategoryAnime,
                    onCreateCategory = screenModel::createCategory,
                )
            }

            PullRefresh(
                refreshing = state.isLoading,
                enabled = true,
                onRefresh = screenModel::loadSuggestions,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
                ) {
                    // 1. Progress Bar at the top
                    if (state.isLoading) {
                        val progress = state.fetchProgress
                        val total = state.fetchTotal
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { progress.toFloat() / total.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 2. Info Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            IconButton(
                                onClick = screenModel::loadSuggestions,
                                enabled = !state.isLoading,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Refresh Suggestions",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.isLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Total: ${state.suggestions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.isLoading && state.fetchProgress > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val newCount = state.suggestions.size - screenModel.initialCount
                                val displayedNew = if (newCount > 0) newCount else 0
                                Text(
                                    text = "New: $displayedNew",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.isLoading && state.fetchTotal > 0) {
                                val left = (state.fetchTotal - state.fetchProgress).coerceAtLeast(0)
                                Text(
                                    text = "Left: $left tags",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { showReportDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.heightIn(max = 28.dp),
                            ) {
                                Text("Report", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // 3. Grid Content
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.isLoading && state.suggestions.isEmpty() -> LoadingScreen(modifier = Modifier.fillMaxSize())
                            state.suggestions.isEmpty() && !state.isLoading -> EmptyScreen(
                                modifier = Modifier.fillMaxSize(),
                                message = stringResource(KMR.strings.pref_suggestions_summary) + "\n\nPull down or tap Refresh to search anime sources.",
                            )
                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(128.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                                        start = 8.dp,
                                        end = 8.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.suggestions, key = { it.id }) { anime ->
                                        val isFavorite = state.favoriteUrls.contains(anime.url)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { navigator.push(AnimeScreen(anime.id)) },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        screenModel.toggleFavorite(anime, isFavorite)
                                                    },
                                                ),
                                            shape = MaterialTheme.shapes.medium,
                                        ) {
                                            Column {
                                                Box {
                                                    MangaCover.Book(
                                                        data = anime.asAnimeCover(),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    if (isFavorite) {
                                                        Badge(
                                                            text = "IN LIB",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            textColor = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(4.dp),
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(4.dp)
                                                                .size(22.dp)
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                                    shape = RoundedCornerShape(4.dp),
                                                                )
                                                                .clickable { screenModel.dismissSuggestion(anime.url, anime.title) },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Close,
                                                                contentDescription = "Dismiss",
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = anime.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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

            // Suggestions Generation Report Popup Dialog
            if (showReportDialog) {
                val allSourceNames = (fetchedBySource.keys + failedBySource.keys).sorted()
                AlertDialog(
                    onDismissRequest = { showReportDialog = false },
                    title = { Text("Suggestions Generation Report") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text("Total anime fetched: $fetchedCount", fontWeight = FontWeight.Bold)
                            Text("Total search failures: $failedCount", fontWeight = FontWeight.Bold)
                            Text("Excluded (Already in library): $libraryFilteredCount")
                            Text("Active suggestions: ${state.suggestions.size}")
                            HorizontalDivider()
                            if (allSourceNames.isEmpty()) {
                                Text("No source queries made yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                allSourceNames.forEach { sourceName ->
                                    val fetchedVal = fetchedBySource[sourceName] ?: 0
                                    val failedVal = failedBySource[sourceName] ?: 0
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = sourceName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Fetched: $fetchedVal | Failed: $failedVal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showReportDialog = false }) {
                            Text("Close")
                        }
                    },
                )
            }
        },
    )
}
