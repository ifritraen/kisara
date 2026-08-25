package eu.kanade.tachiyomi.ui.browse.anime.bulk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.source.model.Source

data class AnimeBulkSearchScreen(
    val sourceIds: List<Long>,
    val queries: List<String>,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            AnimeBulkSearchScreenModel(sourceIds = sourceIds, queries = queries)
        }
        val state by screenModel.state.collectAsState()

        // Selection mode state
        val selectedAnimes = remember { mutableStateListOf<Anime>() }
        var showCategoryDialog by remember { mutableStateOf(false) }
        var animeToAddToLibrary by remember { mutableStateOf<Anime?>(null) }
        var queryToEdit by remember { mutableStateOf<String?>(null) }
        var editQueryText by remember { mutableStateOf("") }
        var isMultiSelectActiveMode by remember { mutableStateOf(false) }
        var animeCategoryIdsMap by remember { mutableStateOf(emptyMap<Long, List<Long>>()) }
        val scope = rememberCoroutineScope()
        val allVisibleAnimes = remember(state.queryResults) {
            state.queryResults.flatMap { it.results.map { pair -> pair.first } }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                val isSelectionMode = selectedAnimes.isNotEmpty() || isMultiSelectActiveMode
                AppBar(
                    title = "Anime Bulk Search Results",
                    navigateUp = navigator::pop,
                    actionModeCounter = selectedAnimes.size,
                    onCancelActionMode = {
                        selectedAnimes.clear()
                        isMultiSelectActiveMode = false
                    },
                    actionModeActions = {
                        val areAllSelected = allVisibleAnimes.isNotEmpty() && selectedAnimes.size >= allVisibleAnimes.size
                        IconButton(
                            onClick = {
                                if (areAllSelected) {
                                    selectedAnimes.clear()
                                } else {
                                    selectedAnimes.clear()
                                    selectedAnimes.addAll(allVisibleAnimes)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (areAllSelected) Icons.Filled.SelectAll else Icons.Outlined.SelectAll,
                                contentDescription = "Select All",
                            )
                        }
                        IconButton(onClick = { showCategoryDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.LibraryAdd,
                                contentDescription = "Add selected to library",
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isMultiSelectActiveMode = !isMultiSelectActiveMode
                                if (!isMultiSelectActiveMode) {
                                    selectedAnimes.clear()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = "Multi-select",
                                tint = if (isMultiSelectActiveMode) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.queryResults) { qr ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            queryToEdit = qr.query
                                            editQueryText = qr.query
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = qr.query,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (qr.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        queryToEdit = qr.query
                                        editQueryText = qr.query
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit Search Query",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (qr.isFailed && !qr.isLoading) {
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val resultsBySource = remember(qr.results) {
                                    qr.results.groupBy { it.second }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    resultsBySource.forEach { (source, resultsList) ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = source.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(bottom = 4.dp),
                                            )
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(end = 16.dp),
                                            ) {
                                                items(resultsList) { (anime, source) ->
                                                    val isSelected = selectedAnimes.any { it.id == anime.id }
                                                    val isInLibrary = state.favoriteUrls.contains(anime.url) || anime.favorite

                                                    ResultAnimeCard(
                                                        anime = anime,
                                                        source = source,
                                                        isInLibrary = isInLibrary,
                                                        isSelected = isSelected,
                                                        isMultiSelectActive = isMultiSelectActiveMode || selectedAnimes.isNotEmpty(),
                                                        onClick = {
                                                            if (isMultiSelectActiveMode || selectedAnimes.isNotEmpty()) {
                                                                val idx = selectedAnimes.indexOfFirst { it.id == anime.id }
                                                                if (idx != -1) {
                                                                    selectedAnimes.removeAt(idx)
                                                                } else {
                                                                    selectedAnimes.add(anime)
                                                                }
                                                            } else {
                                                                navigator.push(AnimeScreen(anime.id))
                                                            }
                                                        },
                                                        onLongClick = {
                                                            scope.launch {
                                                                val categoryIds = screenModel.getAnimeCategoryIds(anime.id)
                                                                animeCategoryIdsMap = animeCategoryIdsMap + (anime.id to categoryIds)
                                                                animeToAddToLibrary = anime
                                                            }
                                                        },
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

                // Category Selection Dialog for adding to library (Bulk Mode)
                if (showCategoryDialog) {
                    ChangeCategoryDialog(
                        initialSelection = remember(state.categories) {
                            state.categories.map { category ->
                                CheckboxState.State.None(category)
                            }.toImmutableList()
                        },
                        onDismissRequest = { showCategoryDialog = false },
                        onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen()) },
                        onConfirm = { addedIds, _ ->
                            showCategoryDialog = false
                            screenModel.addAnimesToLibrary(selectedAnimes.toList(), addedIds)
                            selectedAnimes.clear()
                        },
                        onDuplicateCheck = {
                            showCategoryDialog = false
                            val selection = selectedAnimes.map { it.id }
                            selectedAnimes.clear()
                            navigator.push(DuplicateAnimeScreen(selection))
                        },
                    )
                }

                // Category Selection Dialog for adding to library (Single Mode)
                if (animeToAddToLibrary != null) {
                    val targetAnime = animeToAddToLibrary!!
                    val preselectedIds = animeCategoryIdsMap[targetAnime.id] ?: emptyList()
                    ChangeCategoryDialog(
                        initialSelection = remember(state.categories, preselectedIds) {
                            state.categories.map { category ->
                                if (preselectedIds.contains(category.id)) {
                                    CheckboxState.State.Checked(category)
                                } else {
                                    CheckboxState.State.None(category)
                                }
                            }.toImmutableList()
                        },
                        onDismissRequest = { animeToAddToLibrary = null },
                        onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen()) },
                        onConfirm = { addedIds, _ ->
                            screenModel.toggleFavorite(targetAnime, addedIds)
                            animeToAddToLibrary = null
                        },
                        onDuplicateCheck = {
                            animeToAddToLibrary = null
                            navigator.push(DuplicateAnimeScreen(targetAnime.id))
                        },
                        anime = targetAnime,
                    )
                }

                // Edit Query Dialog
                if (queryToEdit != null) {
                    val oldQuery = queryToEdit!!
                    AlertDialog(
                        onDismissRequest = { queryToEdit = null },
                        shape = RoundedCornerShape(20.dp),
                        title = { Text("Edit Query", style = MaterialTheme.typography.titleMedium) },
                        text = {
                            OutlinedTextField(
                                value = editQueryText,
                                onValueChange = { editQueryText = it },
                                label = { Text("Search Query") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    queryToEdit = null
                                    screenModel.editQuery(oldQuery, editQueryText.trim())
                                },
                            ) {
                                Text("Search")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { queryToEdit = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultAnimeCard(
    anime: Anime,
    source: Source,
    isInLibrary: Boolean,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(112.dp),
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            eu.kanade.presentation.library.components.EntryCompactGridItem(
                title = anime.title,
                coverData = anime.asAnimeCover(),
                coverAlpha = if (isInLibrary) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                onClick = onClick,
                onLongClick = onLongClick,
            )

            // Selection indicator/overlay
            if (isMultiSelectActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Not Selected",
                                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = source.name,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
