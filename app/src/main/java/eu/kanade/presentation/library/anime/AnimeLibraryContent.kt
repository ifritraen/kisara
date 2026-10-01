package eu.kanade.presentation.library.anime

import eu.kanade.presentation.library.components.CategoryBadge
import tachiyomi.core.common.util.lang.compareToWithCollator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.collectAsState
import tachiyomi.presentation.core.util.collectAsState
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.cards.KisaraNormalCard
import eu.kanade.presentation.components.cards.NormalCardStyle
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.components.ColorizedBadge
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.presentation.library.components.UncensoredBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.util.plus

@Composable
fun AnimeLibraryContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<AnimeLibraryItem>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onClearFilters: () -> Unit,
    onChangeCurrentPage: (Int) -> Unit,
    onCategoryLongSelected: ((Int) -> Unit)? = null,
    onAnimeClicked: (AnimeLibraryItem) -> Unit,
    onToggleSelection: (AnimeLibraryItem) -> Unit,
    onToggleRangeSelection: (AnimeLibraryItem) -> Unit,
    onRefresh: (Category?) -> Boolean,
    getNumberOfAnimeForCategory: (Category) -> Int?,
    displayMode: LibraryDisplayMode,
    columns: Int,
    showDownloadBadge: Boolean,
    downloadedAnimeIds: Set<Long>,
    showUnseenBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguageByAnimeId: Map<Long, String>,
    getItemsForCategory: (Category) -> List<AnimeLibraryItem>,
    showParentFilters: Boolean = true,
    activeSubcategoryId: Long? = null,
    onSubcategorySelected: (Long?) -> Unit = {},
    showSubcategories: Boolean = true,
    onGlobalSearchClicked: (() -> Unit)? = null,
    sort: tachiyomi.domain.library.anime.model.AnimeLibrarySort = tachiyomi.domain.library.anime.model.AnimeLibrarySort.default,
) {
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.order }
    }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.sortedBy { it.order } }
    }

    val isSearching = !searchQuery.isNullOrBlank()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val categoryNamesByAnimeId = remember(categories, getItemsForCategory, isSearching) {
        if (!isSearching) {
            emptyMap<Long, List<String>>()
        } else {
            val map = mutableMapOf<Long, MutableList<String>>()
            categories.forEach { cat ->
                val parent = cat.parentId?.let { categoryMap[it] }
                val label = if (parent != null) "${parent.name} > ${cat.name}" else cat.name
                val items = getItemsForCategory(cat)
                items.forEach { item ->
                    map.getOrPut(item.anime.id) { mutableListOf() }.add(label)
                }
            }
            map.mapValues { it.value.distinct() }
        }
    }

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty()) {
            parentCategories
        } else {
            categories
        }

        val initialPage = when {
            tabCategories.isEmpty() -> 0
            currentPage() in tabCategories.indices -> currentPage()
            else -> 0
        }
        val pagerState = rememberPagerState(initialPage) { tabCategories.size }

        LaunchedEffect(currentPage()) {
            val targetPage = when {
                tabCategories.isEmpty() -> 0
                currentPage() in tabCategories.indices -> currentPage()
                pagerState.currentPage >= tabCategories.size -> tabCategories.size - 1
                else -> pagerState.currentPage
            }
            if ((targetPage != pagerState.currentPage || pagerState.currentPageOffsetFraction != 0f) && targetPage != pagerState.targetPage) {
                if (kotlin.math.abs(targetPage - pagerState.currentPage) <= 1 && pagerState.currentPageOffsetFraction == 0f) {
                    pagerState.animateScrollToPage(targetPage)
                } else {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }

        LaunchedEffect(pagerState) {
            var previousPage: Int? = null
            snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                if (previousPage != null && previousPage != settledPage) {
                    if (showParentFilters) {
                        val newParent = parentCategories.getOrNull(settledPage)
                        val sub = activeSubcategoryId?.let { id -> categories.firstOrNull { it.id == id } }
                        if (sub == null || sub.parentId != newParent?.id) {
                            onSubcategorySelected(null)
                        }
                    }
                }
                previousPage = settledPage
                onChangeCurrentPage(settledPage)
            }
        }

        val scope = rememberCoroutineScope()
        if (showPageTabs && !isSearching && tabCategories.isNotEmpty() && (tabCategories.size > 1 || !tabCategories.first().isSystemCategory)) {
            LibraryTabs(
                categories = tabCategories,
                pagerState = pagerState,
                getItemCountForCategory = { getNumberOfAnimeForCategory(it) },
                onTabItemClick = {
                    scope.launch {
                        if (it != pagerState.currentPage || pagerState.currentPageOffsetFraction != 0f) {
                            if (kotlin.math.abs(it - pagerState.currentPage) <= 1 && pagerState.currentPageOffsetFraction == 0f) {
                                pagerState.animateScrollToPage(it)
                            } else {
                                pagerState.scrollToPage(it)
                            }
                        }
                    }
                },
            )
        }

        // Subcategories Filter Chips Bar
        val currentCategory = tabCategories.getOrNull(pagerState.settledPage.coerceIn(0, tabCategories.lastIndex))
        val activeParent = if (currentCategory?.parentId == null) currentCategory else parentCategories.find { it.id == currentCategory.parentId }
        val currentSubcategories = activeParent?.let { childrenByParent[it.id] }.orEmpty()

        val subLazyRowState = rememberLazyListState()
        LaunchedEffect(activeSubcategoryId, currentSubcategories.size) {
            if (currentSubcategories.isNotEmpty()) {
                val activeIndex = if (activeSubcategoryId == null) {
                    0
                } else {
                    val idx = currentSubcategories.indexOfFirst { it.id == activeSubcategoryId }
                    if (idx != -1) idx + 1 else 0
                }
                subLazyRowState.animateScrollToItem(activeIndex)
            }
        }

        if (showSubcategories && !isSearching && currentSubcategories.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = subLazyRowState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            FilterChip(
                                selected = activeSubcategoryId == null,
                                onClick = { onSubcategorySelected(null) },
                                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                        items(currentSubcategories, key = { it.id }) { subcat ->
                            FilterChip(
                                selected = activeSubcategoryId == subcat.id,
                                onClick = { onSubcategorySelected(subcat.id) },
                                label = { Text(subcat.visualName, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = if (activeSubcategoryId == subcat.id) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        var isRefreshing by remember { mutableStateOf(false) }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val currentCat = tabCategories.getOrNull(pagerState.currentPage)
                isRefreshing = onRefresh(currentCat)
            },
            enabled = true,
            modifier = Modifier.weight(1f),
        ) {
            val wrappedGetItemsForCategory: (Category) -> List<AnimeLibraryItem> = { pageCategory ->
                if (showParentFilters) {
                    val selectedSub = activeSubcategoryId?.let { id ->
                        categories.firstOrNull { it.id == id }
                    }

                    if (selectedSub != null && selectedSub.parentId == pageCategory.id) {
                        getItemsForCategory(selectedSub)
                    } else if (activeSubcategoryId == null) {
                        val parentItems = getItemsForCategory(pageCategory)
                        val children = childrenByParent[pageCategory.id].orEmpty()
                        val childItems = children.flatMap { child -> getItemsForCategory(child) }

                        val seen = mutableSetOf<Long>()
                        val merged = mutableListOf<AnimeLibraryItem>()
                        (parentItems + childItems).forEach { item ->
                            if (seen.add(item.anime.id)) merged.add(item)
                        }
                        val categorySort = tachiyomi.domain.library.anime.model.AnimeLibrarySort.valueOf(pageCategory.flags)
                        val effectiveSort = if (categorySort != tachiyomi.domain.library.anime.model.AnimeLibrarySort.default) categorySort else sort
                        if (merged.size > 1) {
                            val comparator: Comparator<AnimeLibraryItem> = when (effectiveSort.type) {
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.LastSeen -> compareBy { it.lastSeen }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.LastUpdate -> compareBy { it.anime.lastUpdate }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.UnseenCount -> compareBy { it.unseenCount }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.TotalEpisodes -> compareBy { it.totalEpisodes }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.LatestEpisode -> compareBy { it.libraryAnime.latestUpload }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.EpisodeFetchDate -> compareBy { it.libraryAnime.episodeFetchedAt }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.DateAdded -> compareBy { it.dateAdded }
                                tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.Random -> compareBy { it.id }
                                else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                            }
                            val sorted = if (effectiveSort.isAscending) merged.sortedWith(comparator) else merged.sortedWith(comparator.reversed())
                            if (effectiveSort.type != tachiyomi.domain.library.anime.model.AnimeLibrarySort.Type.Random) {
                                sorted.sortedByDescending { it.pinned }
                            } else {
                                sorted
                            }
                        } else {
                            merged
                        }
                    } else {
                        getItemsForCategory(pageCategory)
                    }
                } else {
                    getItemsForCategory(pageCategory)
                }
            }

            val onItemClick: (AnimeLibraryItem) -> Unit = { item ->
                if (selection.isNotEmpty()) {
                    onToggleSelection(item)
                } else {
                    onAnimeClicked(item)
                }
            }
            val onItemLongClick: (AnimeLibraryItem) -> Unit = { item ->
                if (selection.isNotEmpty()) {
                    onToggleRangeSelection(item)
                } else {
                    onToggleSelection(item)
                }
            }
            val bottomPadding = contentPadding.calculateBottomPadding()

            if (isSearching) {
                val allMatchingItems = remember(categories, getItemsForCategory, searchQuery) {
                    categories.flatMap { getItemsForCategory(it) }
                        .distinctBy { it.anime.id }
                }

                if (allMatchingItems.isEmpty()) {
                    EmptyScreen(
                        stringRes = MR.strings.information_no_entries_found,
                        modifier = Modifier.fillMaxSize(),
                        actions = buildList {
                            if (hasActiveFilters) {
                                add(
                                    EmptyScreenAction(
                                        stringRes = MR.strings.action_reset,
                                        icon = Icons.Default.Refresh,
                                        onClick = onClearFilters,
                                    ),
                                )
                            }
                            if (onGlobalSearchClicked != null) {
                                add(
                                    EmptyScreenAction(
                                        stringRes = MR.strings.action_global_search_query,
                                        icon = Icons.Default.Search,
                                        onClick = onGlobalSearchClicked,
                                    ),
                                )
                            }
                        }.toImmutableList(),
                    )
                } else {
                    when (displayMode) {
                        LibraryDisplayMode.List -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp),
                            ) {
                                items(allMatchingItems, key = { it.id }) { item ->
                                    AnimeLibraryListItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        categoryBadges = categoryNamesByAnimeId[item.id].orEmpty(),
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                        LibraryDisplayMode.ComfortableGrid,
                        LibraryDisplayMode.ComfortableGridPanorama -> {
                            val gridColumns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
                            LazyVerticalGrid(
                                columns = gridColumns,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(allMatchingItems, key = { it.id }) { item ->
                                    AnimeLibraryComfortableGridItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        categoryBadges = categoryNamesByAnimeId[item.id].orEmpty(),
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                        LibraryDisplayMode.CompactGrid,
                        LibraryDisplayMode.CoverOnlyGrid -> {
                            val gridColumns = if (columns == 0) GridCells.Adaptive(100.dp) else GridCells.Fixed(columns)
                            LazyVerticalGrid(
                                columns = gridColumns,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(allMatchingItems, key = { it.id }) { item ->
                                    AnimeLibraryGridItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        categoryBadges = categoryNamesByAnimeId[item.id].orEmpty(),
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val category = tabCategories.getOrNull(page) ?: return@HorizontalPager
                    val items = wrappedGetItemsForCategory(category)

                    if (items.isEmpty()) {
                        EmptyScreen(
                            stringRes = if (searchQuery.isNullOrEmpty() && !hasActiveFilters) {
                                MR.strings.information_empty_category
                            } else {
                                MR.strings.information_no_entries_found
                            },
                            modifier = Modifier.fillMaxSize(),
                            actions = buildList {
                                if (hasActiveFilters) {
                                    add(
                                        EmptyScreenAction(
                                            stringRes = MR.strings.action_reset,
                                            icon = Icons.Default.Refresh,
                                            onClick = onClearFilters,
                                        ),
                                    )
                                }
                                if (!searchQuery.isNullOrEmpty() && onGlobalSearchClicked != null) {
                                    add(
                                        EmptyScreenAction(
                                            stringRes = MR.strings.action_global_search_query,
                                            icon = Icons.Default.Search,
                                            onClick = onGlobalSearchClicked,
                                        ),
                                    )
                                }
                            }.toImmutableList(),
                        )
                        return@HorizontalPager
                    }

                    when (displayMode) {
                        LibraryDisplayMode.List -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    AnimeLibraryListItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                        LibraryDisplayMode.ComfortableGrid,
                        LibraryDisplayMode.ComfortableGridPanorama -> {
                            val gridColumns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
                            LazyVerticalGrid(
                                columns = gridColumns,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    AnimeLibraryComfortableGridItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                        LibraryDisplayMode.CompactGrid,
                        LibraryDisplayMode.CoverOnlyGrid -> {
                            val gridColumns = if (columns == 0) GridCells.Adaptive(100.dp) else GridCells.Fixed(columns)
                            LazyVerticalGrid(
                                columns = gridColumns,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    AnimeLibraryGridItem(
                                        item = item,
                                        isSelected = selection.any { it.id == item.id },
                                        showDownloadBadge = showDownloadBadge && downloadedAnimeIds.contains(item.id),
                                        showUnseenBadge = showUnseenBadge,
                                        showLanguageBadge = showLanguageBadge,
                                        sourceLanguage = sourceLanguageByAnimeId[item.id],
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeLibraryComfortableGridItem(
    item: AnimeLibraryItem,
    isSelected: Boolean,
    showDownloadBadge: Boolean,
    showUnseenBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguage: String?,
    categoryBadges: List<String> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val normalStyleKey by uiPreferences.normalCardStyle().collectAsState()
    val normalStyle = remember(normalStyleKey) { NormalCardStyle.fromKey(normalStyleKey) }
    val coverTitleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()

    val parsed = remember(item.anime, item.title) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(null, item.title) }
    val cleanTitle = parsed.cleanTitle
    val artistAuthorText = remember(parsed, item.anime.author, item.anime.artist) {
        val parts = mutableListOf<String>()
        val author = item.anime.author?.takeIf { it.isNotBlank() } ?: parsed.author
        val artist = item.anime.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
        if (!artist.isNullOrBlank()) parts.add(artist)
        if (!author.isNullOrBlank() && author != artist) parts.add(author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    val finalBadgeStart: @Composable RowScope.() -> Unit = {
        if (showUnseenBadge && item.unseenCount > 0) {
            UnviewedBadge(count = item.unseenCount)
        }
        if (showDownloadBadge && item.isDownloaded) {
            DownloadsBadge(count = 1)
        }
    }

    val finalBadgeEnd: @Composable RowScope.() -> Unit = {
        val lang = sourceLanguage ?: item.sourceLanguage.takeIf { it.isNotEmpty() } ?: parsed.languageCode
        val hasColor = parsed.isColorized
        val hasUncensored = parsed.isUncensored

        if (showLanguageBadge && lang != null) {
            LanguageBadge(isLocal = false, sourceLanguage = lang)
        }
        if (hasColor) {
            ColorizedBadge()
        }
        if (hasUncensored) {
            UncensoredBadge()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        KisaraNormalCard(
            style = normalStyle,
            title = cleanTitle,
            coverData = item.anime.asAnimeCover(),
            subtitle = artistAuthorText,
            coverBadgeStart = finalBadgeStart,
            coverBadgeEnd = finalBadgeEnd,
            coverTitleStyle = coverTitleStyleKey,
            categoryBadges = categoryBadges,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeLibraryGridItem(
    item: AnimeLibraryItem,
    isSelected: Boolean,
    showDownloadBadge: Boolean,
    showUnseenBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguage: String?,
    categoryBadges: List<String> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val normalStyleKey by uiPreferences.normalCardStyle().collectAsState()
    val normalStyle = remember(normalStyleKey) { NormalCardStyle.fromKey(normalStyleKey) }
    val coverTitleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()

    val parsed = remember(item.anime, item.title) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(null, item.title) }
    val cleanTitle = parsed.cleanTitle
    val artistAuthorText = remember(parsed, item.anime.author, item.anime.artist) {
        val parts = mutableListOf<String>()
        val author = item.anime.author?.takeIf { it.isNotBlank() } ?: parsed.author
        val artist = item.anime.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
        if (!artist.isNullOrBlank()) parts.add(artist)
        if (!author.isNullOrBlank() && author != artist) parts.add(author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    val finalBadgeStart: @Composable RowScope.() -> Unit = {
        if (showUnseenBadge && item.unseenCount > 0) {
            UnviewedBadge(count = item.unseenCount)
        }
        if (showDownloadBadge && item.isDownloaded) {
            DownloadsBadge(count = 1)
        }
    }

    val finalBadgeEnd: @Composable RowScope.() -> Unit = {
        val lang = sourceLanguage ?: item.sourceLanguage.takeIf { it.isNotEmpty() } ?: parsed.languageCode
        val hasColor = parsed.isColorized
        val hasUncensored = parsed.isUncensored

        if (showLanguageBadge && lang != null) {
            LanguageBadge(isLocal = false, sourceLanguage = lang)
        }
        if (hasColor) {
            ColorizedBadge()
        }
        if (hasUncensored) {
            UncensoredBadge()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        KisaraNormalCard(
            style = normalStyle,
            title = cleanTitle,
            coverData = item.anime.asAnimeCover(),
            subtitle = artistAuthorText,
            coverBadgeStart = finalBadgeStart,
            coverBadgeEnd = finalBadgeEnd,
            coverTitleStyle = coverTitleStyleKey,
            categoryBadges = categoryBadges,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeLibraryListItem(
    item: AnimeLibraryItem,
    isSelected: Boolean,
    showDownloadBadge: Boolean,
    showUnseenBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguage: String?,
    categoryBadges: List<String> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val parsed = remember(item.anime, item.title) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(null, item.title) }
    val cleanTitle = parsed.cleanTitle
    val artistAuthorText = remember(parsed, item.anime.author, item.anime.artist) {
        val parts = mutableListOf<String>()
        val author = item.anime.author?.takeIf { it.isNotBlank() } ?: parsed.author
        val artist = item.anime.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
        if (!artist.isNullOrBlank()) parts.add(artist)
        if (!author.isNullOrBlank() && author != artist) parts.add(author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .height(56.dp)
                .aspectRatio(ItemCover.Book.ratio)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            ItemCover.Book(
                data = item.anime.asAnimeCover(),
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artistAuthorText != null) {
                Text(
                    text = artistAuthorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (categoryBadges.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    categoryBadges.take(3).forEach { badge ->
                        CategoryBadge(badge)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showUnseenBadge && item.unseenCount > 0) {
                    UnviewedBadge(count = item.unseenCount)
                }
                if (showDownloadBadge) {
                    DownloadsBadge(count = 1)
                }
                val lang = sourceLanguage ?: item.sourceLanguage.takeIf { it.isNotEmpty() } ?: parsed.languageCode
                if (showLanguageBadge && lang != null) {
                    LanguageBadge(isLocal = false, sourceLanguage = lang)
                }
            }
        }
    }
}
