package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    activeCategoryIndex: Int,
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    showParentFilters: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    // KMK -->
    activeSubcategoryId: Long? = null,
    onSubcategorySelected: (Long?) -> Unit = {},
    showSubcategories: Boolean = true,
    // KMK <--
) {
    // Derive parent categories and child mapping
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.order }
    }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { entry -> entry.value.sortedBy { it.order } }
    }

    // Track which parent categories have collapsed subcategory chips
    var collapsedParentIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    // Track which parent categories have "exclude subcategories" mode enabled
    var excludeSubcategoriesParentIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    // Track parent categories where the tab was clicked to bypass showSubcategoryTabs setting
    var clickedTabParentIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    val isSearching = !searchQuery.isNullOrBlank()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val categoryNamesByMangaId = remember(categories, getItemsForCategory, isSearching) {
        if (!isSearching) {
            emptyMap<Long, List<String>>()
        } else {
            val map = mutableMapOf<Long, MutableList<String>>()
            categories.forEach { cat ->
                val parent = cat.parentId?.let { categoryMap[it] }
                val label = if (parent != null) "${parent.name} > ${cat.name}" else cat.name
                val items = getItemsForCategory(cat)
                items.forEach { item ->
                    map.getOrPut(item.libraryManga.manga.id) { mutableListOf() }.add(label)
                }
            }
            map.mapValues { it.value.distinct() }
        }
    }

    LaunchedEffect(searchQuery) {
        if (!searchQuery.isNullOrEmpty()) {
            collapsedParentIds = emptySet()
        }
    }

    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        // Determine which categories to show in tabs based on showParentFilters
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty()) {
            parentCategories
        } else {
            categories
        }

        // Calculate initial page based on activeCategoryIndex
        val initialPage = when {
            tabCategories.isEmpty() -> 0
            activeCategoryIndex in tabCategories.indices -> activeCategoryIndex
            currentPage in tabCategories.indices -> currentPage
            else -> 0
        }

        val pagerState = rememberPagerState(initialPage = initialPage) { tabCategories.size }
        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        LaunchedEffect(tabCategories, activeCategoryIndex) {
            val targetPage = when {
                tabCategories.isEmpty() -> 0
                activeCategoryIndex != pagerState.currentPage && activeCategoryIndex in tabCategories.indices -> activeCategoryIndex
                pagerState.currentPage >= tabCategories.size -> tabCategories.size - 1
                else -> pagerState.currentPage
            }
            if (targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }

        // Show tabs if needed
        if (showPageTabs && !isSearching && tabCategories.isNotEmpty() && (tabCategories.size > 1 || !tabCategories.first().isSystemCategory)) {
            LibraryTabs(
                categories = tabCategories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        val targetCategory = tabCategories[it]
                        val hasSubcategories = childrenByParent[targetCategory.id]?. isNotEmpty() == true

                        // Toggle collapse state if clicking on current page with subcategories
                        if (it == pagerState.currentPage && hasSubcategories && showParentFilters) {
                            collapsedParentIds = if (targetCategory.id in collapsedParentIds) {
                                collapsedParentIds - targetCategory.id
                            } else {
                                collapsedParentIds + targetCategory.id
                            }
                        } else {
                            // Navigate to the tab instantly to avoid composing intermediate pages
                            pagerState.scrollToPage(it)
                            collapsedParentIds = collapsedParentIds - targetCategory.id
                        }
                        clickedTabParentIds = clickedTabParentIds + targetCategory.id
                    }
                },
            )
        }

        // Show subcategory filter chips if parent filters are enabled
        if (showParentFilters && !isSearching && parentCategories.isNotEmpty()) {
            val activeParent = parentCategories.getOrNull(pagerState.currentPage)
            val subcategoriesForActiveParent = activeParent?.let { childrenByParent[it.id] }.orEmpty()
            val isCollapsed = activeParent?.id?.let { it in collapsedParentIds } ?: false
            val isExcludingSubcategories = activeParent?.id?.let { it in excludeSubcategoriesParentIds } ?: false

            val subLazyRowState = rememberLazyListState()

            // Reset activeSubcategoryId if no subcategories for current parent
            LaunchedEffect(subcategoriesForActiveParent) {
                if (subcategoriesForActiveParent.isEmpty()) {
                    onSubcategorySelected(null)
                }
            }

            // KMK --> ponytail: auto-center selected subcategory in top chip carousel
            LaunchedEffect(activeSubcategoryId, subcategoriesForActiveParent.size) {
                if (subcategoriesForActiveParent.isNotEmpty()) {
                    val activeIndex = if (activeSubcategoryId == null) {
                        0
                    } else {
                        val idx = subcategoriesForActiveParent.indexOfFirst { it.id == activeSubcategoryId }
                        if (idx != -1) idx + 1 else 0
                    }
                    val layoutInfo = subLazyRowState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val targetItem = visibleItems.firstOrNull { it.index == activeIndex }
                    if (targetItem != null && viewportWidth > 0) {
                        val offset = (viewportWidth - targetItem.size) / 2
                        subLazyRowState.animateScrollToItem(activeIndex, -offset)
                    } else {
                        subLazyRowState.animateScrollToItem(activeIndex)
                    }
                }
            }
            // KMK <--

            // Animated visibility for subcategory chips with smooth expand/collapse
            AnimatedVisibility(
                visible = subcategoriesForActiveParent.isNotEmpty() && !isCollapsed && (showSubcategories || (activeParent != null && activeParent.id in clickedTabParentIds)),
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = androidx.compose.ui.Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = androidx.compose.ui.Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            ) {
                LazyRow(
                    state = subLazyRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    // "All" chip with integrated exclude mode toggle
                    item(key = "subcategory_all", contentType = "subcategory_chip") {
                        FilterChip(
                            selected = activeSubcategoryId == null,
                            onClick = {
                                if (activeSubcategoryId == null && activeParent != null) {
                                    // Already on "All" - toggle exclude mode
                                    excludeSubcategoriesParentIds = if (isExcludingSubcategories) {
                                        excludeSubcategoriesParentIds - activeParent.id
                                    } else {
                                        excludeSubcategoriesParentIds + activeParent.id
                                    }
                                } else {
                                    // Switch to "All" and turn off exclude mode
                                    onSubcategorySelected(null)
                                    if (isExcludingSubcategories && activeParent != null) {
                                        excludeSubcategoriesParentIds = excludeSubcategoriesParentIds - activeParent.id
                                    }
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(text = "All", fontSize = 11.sp)
                                    if (isExcludingSubcategories) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Excluding subcategories",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(26.dp)
                                .padding(vertical = 2.dp),
                        )
                    }

                    // Subcategory chips
                    items(
                        subcategoriesForActiveParent,
                        key = { it.id },
                        contentType = { "subcategory_chip" },
                    ) { sub ->
                        val selected = activeSubcategoryId == sub.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onSubcategorySelected(if (selected) null else sub.id)
                                // When selecting a subcategory, turn off exclude mode
                                if (activeParent != null && isExcludingSubcategories) {
                                    excludeSubcategoriesParentIds = excludeSubcategoriesParentIds - activeParent.id
                                }
                            },
                            label = { Text(text = sub.visualName, fontSize = 11.sp) },
                            modifier = Modifier
                                .height(26.dp)
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            enabled = selection.isEmpty(),
            onRefresh = {
                val started = onRefresh()
                if (!started) return@PullRefresh
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
        ) {
            val cachedCategoryItems = remember(
                categories,
                activeSubcategoryId,
                excludeSubcategoriesParentIds,
                showParentFilters,
                parentCategories,
                childrenByParent,
                getItemsForCategory,
            ) {
                mutableMapOf<Long, List<LibraryItem>>()
            }

            // KMK --> ponytail: remember lambda to avoid closure re-allocation on recomposition
            val wrappedGetItemsForCategory: (Category) -> List<LibraryItem> = remember(
                cachedCategoryItems, showParentFilters, parentCategories,
                activeSubcategoryId, excludeSubcategoriesParentIds,
                categories, childrenByParent, getItemsForCategory,
            ) { { pageCategory: Category ->
                cachedCategoryItems.getOrPut(pageCategory.id) {
                    if (showParentFilters && parentCategories.isNotEmpty()) {
                        val isExcludingSubcategories = pageCategory.id in excludeSubcategoriesParentIds

                        // Parent filters enabled: respect subcategory selection
                        val selectedSub = activeSubcategoryId?.let { id ->
                            categories.firstOrNull { it.id == id }
                        }

                        if (selectedSub != null && selectedSub.parentId == pageCategory.id) {
                            // Show only the selected subcategory's items
                            getItemsForCategory(selectedSub)
                        } else if (activeSubcategoryId == null) {
                            if (isExcludingSubcategories) {
                                // Exclude mode: show only parent's own items (no subcategory items)
                                getItemsForCategory(pageCategory)
                            } else {
                                // Include mode: merge parent + all subcategory items (deduped) and sort by parent category sort
                                val parentItems = getItemsForCategory(pageCategory)
                                val children = childrenByParent[pageCategory.id].orEmpty()
                                if (children.isEmpty()) {
                                    parentItems
                                } else {
                                    val childItems = children.flatMap { child -> getItemsForCategory(child) }

                                    val seen = mutableSetOf<Long>()
                                    val merged = mutableListOf<LibraryItem>()
                                    (parentItems + childItems).forEach { item ->
                                        val mangaId = item.libraryManga.manga.id
                                        if (seen.add(mangaId)) merged.add(item)
                                    }

                                    val sort = pageCategory.sort
                                    if (merged.size > 1) {
                                        val sortAlpha: (LibraryItem, LibraryItem) -> Int = { m1, m2 ->
                                            val t1 = m1.libraryManga.manga.title.lowercase()
                                            val t2 = m2.libraryManga.manga.title.lowercase()
                                            t1.compareToWithCollator(t2)
                                        }
                                        val comp = Comparator<LibraryItem> { manga1, manga2 ->
                                            when (sort.type) {
                                                tachiyomi.domain.library.model.LibrarySort.Type.Alphabetical -> sortAlpha(manga1, manga2)
                                                tachiyomi.domain.library.model.LibrarySort.Type.LastRead -> manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
                                                tachiyomi.domain.library.model.LibrarySort.Type.LastUpdate -> manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
                                                tachiyomi.domain.library.model.LibrarySort.Type.UnreadCount -> when {
                                                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                                                    manga1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                                                    manga2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                                                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                                                }
                                                tachiyomi.domain.library.model.LibrarySort.Type.TotalChapters -> manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                                                tachiyomi.domain.library.model.LibrarySort.Type.LatestChapter -> manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
                                                tachiyomi.domain.library.model.LibrarySort.Type.ChapterFetchDate -> manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
                                                tachiyomi.domain.library.model.LibrarySort.Type.DateAdded -> manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
                                                else -> sortAlpha(manga1, manga2)
                                            }
                                        }
                                        val finalComp = (if (sort.isAscending) comp else comp.reversed()).thenComparator(sortAlpha)
                                        merged.sortedWith(finalComp)
                                    } else {
                                        merged
                                    }
                                }
                            }
                        } else {
                            // Subcategory selected but doesn't belong to current parent
                            getItemsForCategory(pageCategory)
                        }
                    } else {
                        getItemsForCategory(pageCategory)
                    }
                }
            } }
            // KMK <--

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isSearching) {
                    val allMatchingItems = remember(categories, getItemsForCategory, searchQuery) {
                        categories.flatMap { getItemsForCategory(it) }
                            .distinctBy { it.libraryManga.manga.id }
                    }

                    if (allMatchingItems.isEmpty()) {
                        LibraryPagerEmptyScreen(
                            searchQuery = searchQuery,
                            hasActiveFilters = hasActiveFilters,
                            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                            onGlobalSearchClicked = onGlobalSearchClicked,
                        )
                    } else {
                        val displayMode by getDisplayMode(0)
                        val columns by if (displayMode != LibraryDisplayMode.List) {
                            val configuration = LocalConfiguration.current
                            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            remember(isLandscape) { getColumnsForOrientation(isLandscape) }
                        } else {
                            remember { mutableIntStateOf(0) }
                        }

                        val firstCategory = categories.firstOrNull() ?: Category(0, "", 0, 0, null, false)

                        when (displayMode) {
                            LibraryDisplayMode.List -> {
                                LibraryList(
                                    items = allMatchingItems,
                                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                    selection = selection,
                                    onClick = { manga ->
                                        if (selection.isNotEmpty()) {
                                            onToggleSelection(firstCategory, manga)
                                        } else {
                                            onClickManga(manga.manga.id)
                                        }
                                    },
                                    onLongClick = { onToggleRangeSelection(firstCategory, it) },
                                    onClickContinueReading = onContinueReadingClicked,
                                    searchQuery = searchQuery,
                                    onGlobalSearchClicked = onGlobalSearchClicked,
                                    categoryNamesByMangaId = categoryNamesByMangaId,
                                )
                            }
                            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                                LibraryCompactGrid(
                                    items = allMatchingItems,
                                    showTitle = displayMode is LibraryDisplayMode.CompactGrid,
                                    columns = columns,
                                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                    selection = selection,
                                    onClick = { manga ->
                                        if (selection.isNotEmpty()) {
                                            onToggleSelection(firstCategory, manga)
                                        } else {
                                            onClickManga(manga.manga.id)
                                        }
                                    },
                                    onLongClick = { onToggleRangeSelection(firstCategory, it) },
                                    onClickContinueReading = onContinueReadingClicked,
                                    searchQuery = searchQuery,
                                    onGlobalSearchClicked = onGlobalSearchClicked,
                                    categoryNamesByMangaId = categoryNamesByMangaId,
                                )
                            }
                            LibraryDisplayMode.ComfortableGrid, LibraryDisplayMode.ComfortableGridPanorama -> {
                                LibraryComfortableGrid(
                                    items = allMatchingItems,
                                    columns = columns,
                                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                    selection = selection,
                                    onClick = { manga ->
                                        if (selection.isNotEmpty()) {
                                            onToggleSelection(firstCategory, manga)
                                        } else {
                                            onClickManga(manga.manga.id)
                                        }
                                    },
                                    onLongClick = { onToggleRangeSelection(firstCategory, it) },
                                    onClickContinueReading = onContinueReadingClicked,
                                    searchQuery = searchQuery,
                                    onGlobalSearchClicked = onGlobalSearchClicked,
                                    categoryNamesByMangaId = categoryNamesByMangaId,
                                )
                            }
                        }
                    }
                } else {
                    LibraryPager(
                        state = pagerState,
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        hasActiveFilters = hasActiveFilters,
                        selection = selection,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        getCategoryForPage = { page -> tabCategories[page] },
                        getDisplayMode = getDisplayMode,
                        getColumnsForOrientation = getColumnsForOrientation,
                        getItemsForCategory = wrappedGetItemsForCategory,
                        onClickManga = { category, manga ->
                            if (selection.isNotEmpty()) {
                                onToggleSelection(category, manga)
                            } else {
                                onClickManga(manga.manga.id)
                            }
                        },
                        onLongClickManga = onToggleRangeSelection,
                        onClickContinueReading = onContinueReadingClicked,
                        userScrollEnabled = true,
                    )
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            // Reset subcategory selection when parent page changes
            if (showParentFilters) {
                onSubcategorySelected(null)
            }
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
