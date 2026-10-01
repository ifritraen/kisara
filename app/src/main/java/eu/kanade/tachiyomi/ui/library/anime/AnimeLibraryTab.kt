package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.components.SearchBottomSheet
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.home.LocalActiveSubTabPopup
import eu.kanade.tachiyomi.ui.home.LocalEditCategory
import eu.kanade.tachiyomi.ui.home.SubTabButton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object AnimeLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = MR.strings.label_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val sourceManager = remember { Injekt.get<AnimeSourceManager>() }

        val displayMode by libraryPreferences.animeDisplayMode().collectAsStateWithLifecycle()
        val showDownloadBadge by libraryPreferences.downloadBadge().collectAsStateWithLifecycle()
        val showUnseenBadge by libraryPreferences.unreadBadge().collectAsStateWithLifecycle()
        val showLanguageBadge by libraryPreferences.languageBadge().collectAsStateWithLifecycle()

        val configuration = LocalConfiguration.current
        val columnPreference = remember(configuration.orientation) {
            if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                libraryPreferences.animeLandscapeColumns()
            } else {
                libraryPreferences.animePortraitColumns()
            }
        }
        val columns by columnPreference.collectAsStateWithLifecycle()

        val sourceLanguageByAnimeId = remember(state.library, showLanguageBadge) {
            if (!showLanguageBadge) return@remember emptyMap<Long, String>()
            state.library.values.flatten().mapNotNull { item ->
                val source = sourceManager.get(item.anime.source) ?: return@mapNotNull null
                item.id to source.lang
            }.toMap()
        }

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            val started = AnimeLibraryUpdateJob.startNow(context)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_library else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        var activeSubcategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
        var previousParentId by rememberSaveable { mutableStateOf<Long?>(null) }
        var showSearchSheet by rememberSaveable { mutableStateOf(false) }
        val showSubcategoriesAtTop by uiPreferences.showSubcategoriesAtTop().collectAsState()

        val kisaraShowSubcategoriesInMainBar = uiPreferences.kisaraShowSubcategoriesInMainBar().collectAsState().value
        val parentCategories = remember(state.categories) {
            state.categories.filter { it.parentId == null }.sortedBy { it.order }
        }
        val childrenByParent = remember(state.categories) {
            state.categories.filter { it.parentId != null }
                .groupBy { it.parentId }
                .mapValues { entry -> entry.value.sortedBy { it.order } }
        }
        val showParentFilters = state.categories.any { it.parentId == null && !it.isSystemCategory }
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
            parentCategories
        } else {
            state.categories
        }

        val activeCategory = state.categories.getOrNull(screenModel.activeCategoryIndex)
        val activeParent = remember(activeCategory, parentCategories) {
            if (activeCategory == null) {
                null
            } else if (activeCategory.parentId == null) {
                activeCategory
            } else {
                parentCategories.find { it.id == activeCategory.parentId }
            }
        }
        val subcategories = activeParent?.let { childrenByParent[it.id] }.orEmpty()
        val activeSubcategoryIdOfActivePage = if (showParentFilters) {
            activeSubcategoryId
        } else {
            if (activeCategory?.parentId != null) activeCategory.id else null
        }

        LaunchedEffect(activeParent?.id) {
            if (previousParentId != null && activeParent?.id != null && previousParentId != activeParent?.id) {
                val sub = activeSubcategoryId?.let { id -> state.categories.firstOrNull { it.id == id } }
                if (sub == null || sub.parentId != activeParent.id) {
                    activeSubcategoryId = null
                }
            }
            if (activeParent?.id != null) {
                previousParentId = activeParent?.id
            }
        }

        LaunchedEffect(activeParent?.id, activeSubcategoryId) {
            eu.kanade.tachiyomi.ui.library.LibraryTab.activeCategoryFlow.value = Pair(activeParent?.id, activeSubcategoryId)
        }

        var showCategoryBar by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.toggleCategoryBarEvent.receiveAsFlow().collectLatest {
                    showCategoryBar = !showCategoryBar
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.searchEvent.receiveAsFlow().collectLatest {
                    showSearchSheet = true
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.filterSettingsEvent.receiveAsFlow().collectLatest {
                    screenModel.showSettingsDialog()
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.randomMangaEvent.receiveAsFlow().collectLatest {
                    val randomItem = state.library.values.flatten().randomOrNull()
                    if (randomItem != null) {
                        navigator.push(AnimeScreen(randomItem.id))
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.information_no_entries_found))
                        }
                    }
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.globalUpdateEvent.receiveAsFlow().collectLatest {
                    onClickRefresh(null)
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.categoryUpdateEvent.receiveAsFlow().collectLatest {
                    onClickRefresh(null)
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.selectCategoryEvent.receiveAsFlow().collectLatest { index ->
                    screenModel.activeCategoryIndex = index
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.nextCategoryEvent.receiveAsFlow().collectLatest {
                    val totalCategories = tabCategories.size
                    if (totalCategories > 1) {
                        val next = (screenModel.activeCategoryIndex + 1).coerceAtMost(totalCategories - 1)
                        screenModel.activeCategoryIndex = next
                    }
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.prevCategoryEvent.receiveAsFlow().collectLatest {
                    val totalCategories = tabCategories.size
                    if (totalCategories > 1) {
                        val prev = (screenModel.activeCategoryIndex - 1).coerceAtLeast(0)
                        screenModel.activeCategoryIndex = prev
                    }
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.selectSubcategoryEvent.receiveAsFlow().collectLatest { subId ->
                    if (subId != null) {
                        val sub = state.categories.find { it.id == subId }
                        if (sub?.parentId != null) {
                            val pIndex = state.categories.indexOfFirst { it.id == sub.parentId }
                            if (pIndex != -1) {
                                previousParentId = sub.parentId
                                screenModel.activeCategoryIndex = pIndex
                            }
                        }
                    }
                    activeSubcategoryId = subId
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.nextSubcategoryEvent.receiveAsFlow().collectLatest {
                    if (subcategories.isNotEmpty()) {
                        val currentIdx = if (activeSubcategoryId == null) -1 else subcategories.indexOfFirst { it.id == activeSubcategoryId }
                        val nextIdx = currentIdx + 1
                        if (nextIdx < subcategories.size) {
                            activeSubcategoryId = subcategories[nextIdx].id
                        }
                    }
                }
            }
            launch {
                eu.kanade.tachiyomi.ui.library.LibraryTab.prevSubcategoryEvent.receiveAsFlow().collectLatest {
                    if (subcategories.isNotEmpty()) {
                        val currentIdx = if (activeSubcategoryId == null) 0 else subcategories.indexOfFirst { it.id == activeSubcategoryId }
                        val prevIdx = currentIdx - 1
                        if (prevIdx < 0) {
                            activeSubcategoryId = null
                        } else {
                            activeSubcategoryId = subcategories[prevIdx].id
                        }
                    }
                }
            }
        }

        val categoryBarPinnedPref = libraryPreferences.categoryBarPinned()
        val isCategoryBarPinned by categoryBarPinnedPref.collectAsState()

        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsState()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsState()
        val showCategoryTabs by uiPreferences.showCategoryTabs().collectAsState()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()
        val categoryBarCarouselStyle by uiPreferences.categoryBarCarouselStyle().collectAsState()
        val alwaysShowSubTabsLibrary by uiPreferences.alwaysShowSubTabsLibrary().collectAsState()
        val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsState()
        val kisaraShowItemCountInTabs by uiPreferences.kisaraShowItemCountInTabs().collectAsState()
        val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
        val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
        val standardBottomBarHeight by uiPreferences.standardBottomBarHeight().collectAsState()
        val standardBottomBarBottomMargin by uiPreferences.standardBottomBarBottomMargin().collectAsState()
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
        val showSubcategoryTabs by libraryPreferences.subcategoryTabs().collectAsState()

        var topBarVisible by remember { mutableStateOf(true) }
        val bottomBarVisible by HomeScreen.showBottomNavFlow.collectAsState()
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (delta < -10f) {
                        if (hideTopBarOnScroll && topBarVisible) {
                            topBarVisible = false
                        }
                    } else if (delta > 10f) {
                        if (!topBarVisible) {
                            topBarVisible = true
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        val searchClean by sourcePreferences.searchClean().collectAsState()
        val searchFormat by sourcePreferences.searchFormat().collectAsState()
        val searchFuzzy by sourcePreferences.searchFuzzy().collectAsState()
        val localHazeState = remember { dev.chrisbanes.haze.HazeState() }

        CompositionLocalProvider(LocalHazeState provides localHazeState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (frostedGlass) Modifier.hazeSource(state = localHazeState) else Modifier),
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    topBar = { scrollBehavior ->
                    val selectedCount = state.selection.size
                    if (state.searchQuery != null || selectedCount > 0) {
                        Column {
                            LibraryToolbar(
                                hasActiveFilters = state.hasActiveFilters,
                                selectedCount = selectedCount,
                                title = state.getToolbarTitle(
                                    defaultTitle = stringResource(MR.strings.label_library),
                                    defaultCategoryTitle = stringResource(MR.strings.label_default),
                                    page = screenModel.activeCategoryIndex,
                                ),
                                onClickUnselectAll = screenModel::clearSelection,
                                onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex, activeSubcategoryId) },
                                onClickInvertSelection = { screenModel.invertSelection(screenModel.activeCategoryIndex, activeSubcategoryId) },
                                onClickFilter = screenModel::showSettingsDialog,
                                onClickRefresh = { onClickRefresh(null) },
                                onClickGlobalUpdate = { onClickRefresh(null) },
                                onClickOpenRandomManga = {
                                    val randomItem = state.library.values.flatten().randomOrNull()
                                    if (randomItem != null) {
                                        navigator.push(AnimeScreen(randomItem.id))
                                    }
                                },
                                onClickSyncNow = {},
                                onClickSyncExh = null,
                                isSyncEnabled = false,
                                onInvalidateDownloadCache = {},
                                searchQuery = state.searchQuery,
                                onSearchQueryChange = screenModel::search,
                                onOpenSearchSheet = { showSearchSheet = true },
                                scrollBehavior = scrollBehavior,
                            )
                            if (state.searchQuery != null) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    FilterChip(
                                        selected = searchClean,
                                        onClick = { sourcePreferences.searchClean().set(!searchClean) },
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
                                        selected = searchFormat != 0,
                                        onClick = { sourcePreferences.searchFormat().set((searchFormat + 1) % 3) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            )
                                        },
                                        label = {
                                            Text(
                                                when (searchFormat) {
                                                    1 -> "Format (Key)"
                                                    2 -> "Format (Raw)"
                                                    else -> "Format"
                                                },
                                            )
                                        },
                                    )
                                    FilterChip(
                                        selected = searchFuzzy,
                                        onClick = { sourcePreferences.searchFuzzy().set(!searchFuzzy) },
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
                            }
                            HorizontalDivider()
                        }
                    }
                },
                bottomBar = {
                    LibraryBottomActionMenu(
                        visible = state.selection.isNotEmpty(),
                        onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                        onMarkAsReadClicked = { screenModel.markSeenSelection(true) },
                        onMarkAsUnreadClicked = { screenModel.markSeenSelection(false) },
                        onDownloadClicked = null,
                        onDeleteClicked = screenModel::openDeleteAnimeDialog,
                        onMigrateClicked = {},
                        onMergeClicked = {},
                        onSelectionUpdateClicked = {
                            val started = AnimeLibraryUpdateJob.startNow(context)
                            scope.launch {
                                val msgRes = if (started) KMR.strings.updating else MR.strings.update_already_running
                                if (started) screenModel.clearSelection()
                                snackbarHostState.showSnackbar(context.stringResource(msgRes))
                            }
                        },
                        onDuplicateCheckClicked = {
                            val selection = state.selection.map { it.id }
                            screenModel.clearSelection()
                            navigator.push(DuplicateAnimeScreen(selection))
                        },
                        onClickCleanTitles = null,
                        onClickCollectRecommendations = null,
                        onClickAddToMangaDex = null,
                        onClickResetInfo = null,
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { contentPadding ->
                val bottomPadding = contentPadding.calculateBottomPadding() + (if (floatingBottomBar) 72.dp else 0.dp)
                val adjustedContentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = bottomPadding,
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                )

                when {
                    state.isLoading -> LoadingScreen(Modifier.padding(adjustedContentPadding))
                    state.searchQuery.isNullOrEmpty() &&
                        !state.hasActiveFilters &&
                        state.hasLoaded &&
                        state.isLibraryEmpty -> {
                        EmptyScreen(
                            stringRes = MR.strings.information_empty_library,
                            modifier = Modifier.padding(adjustedContentPadding),
                        )
                    }
                    else -> {
                        AnimeLibraryContent(
                            categories = state.categories,
                            searchQuery = state.searchQuery,
                            selection = state.selection,
                            contentPadding = adjustedContentPadding,
                            currentPage = {
                                if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                    val activeParentId = state.categories.getOrNull(screenModel.activeCategoryIndex)?.let {
                                        if (it.parentId == null) it.id else it.parentId
                                    }
                                    parentCategories.indexOfFirst { it.id == activeParentId }.coerceAtLeast(0)
                                } else {
                                    screenModel.activeCategoryIndex.coerceIn(0, state.categories.lastIndex.coerceAtLeast(0))
                                }
                            },
                            hasActiveFilters = state.hasActiveFilters,
                            showPageTabs = (showTopTabBar || !state.searchQuery.isNullOrEmpty()) && topBarVisible,
                            onClearFilters = screenModel::resetFilters,
                            onChangeCurrentPage = { page ->
                                if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                    val parentCat = parentCategories.getOrNull(page)
                                    if (parentCat != null) {
                                        val dbIndex = state.categories.indexOfFirst { it.id == parentCat.id }
                                        if (dbIndex != -1) {
                                            screenModel.activeCategoryIndex = dbIndex
                                        }
                                    }
                                } else {
                                    screenModel.activeCategoryIndex = page
                                }
                            },
                            onCategoryLongSelected = screenModel::selectAll,
                            onAnimeClicked = { item ->
                                navigator.push(AnimeScreen(item.id))
                            },
                            onToggleSelection = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                screenModel.toggleSelection(item)
                            },
                            onToggleRangeSelection = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                screenModel.toggleRangeSelection(item)
                            },
                            onRefresh = onClickRefresh,
                            onGlobalSearchClicked = {
                                navigator.push(GlobalAnimeSearchScreen(state.searchQuery ?: ""))
                            },
                            getNumberOfAnimeForCategory = { category ->
                                state.getAnimeCountForCategory(category)
                            },
                            displayMode = displayMode,
                            columns = columns,
                            showDownloadBadge = showDownloadBadge,
                            downloadedAnimeIds = emptySet(),
                            showUnseenBadge = showUnseenBadge,
                            showLanguageBadge = showLanguageBadge,
                            sourceLanguageByAnimeId = sourceLanguageByAnimeId,
                            getItemsForCategory = { category ->
                                state.library[category] ?: emptyList()
                            },
                            showParentFilters = showParentFilters,
                            showSubcategories = showSubcategoriesAtTop && (showSubcategoryTabs || showParentFilters) && topBarVisible,
                            activeSubcategoryId = activeSubcategoryId,
                            onSubcategorySelected = { activeSubcategoryId = it },
                            sort = state.sort,
                        )
                    }
                }
            }

            // Floating Horizontally Scrollable Category Bar (True Carousel with Auto-Centering)
            val parentCategoryRowState = rememberLazyListState()
            val subcategoryRowState = rememberLazyListState()

            LaunchedEffect(screenModel.activeCategoryIndex, tabCategories.size, categoryBarCarouselStyle) {
                if (categoryBarCarouselStyle && tabCategories.isNotEmpty()) {
                    val index = screenModel.activeCategoryIndex.coerceIn(0, tabCategories.lastIndex)
                    val layoutInfo = parentCategoryRowState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val targetItem = visibleItems.firstOrNull { it.index == index }
                    if (targetItem != null && viewportWidth > 0) {
                        val offset = (viewportWidth - targetItem.size) / 2
                        parentCategoryRowState.animateScrollToItem(index, -offset)
                    } else {
                        parentCategoryRowState.animateScrollToItem(index)
                    }
                }
            }

            LaunchedEffect(activeSubcategoryIdOfActivePage, subcategories.size, categoryBarCarouselStyle) {
                if (categoryBarCarouselStyle && subcategories.isNotEmpty()) {
                    val activeIndex = if (activeSubcategoryIdOfActivePage == null) {
                        0
                    } else {
                        val idx = subcategories.indexOfFirst { it.id == activeSubcategoryIdOfActivePage }
                        if (idx != -1) idx + 1 else 0
                    }
                    val layoutInfo = subcategoryRowState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val targetItem = visibleItems.firstOrNull { it.index == activeIndex }
                    if (targetItem != null && viewportWidth > 0) {
                        val offset = (viewportWidth - targetItem.size) / 2
                        subcategoryRowState.animateScrollToItem(activeIndex, -offset)
                    } else {
                        subcategoryRowState.animateScrollToItem(activeIndex)
                    }
                }
            }

            val fabBottomPadding = ((if (floatingBottomBar) (bottomBarHeight + bottomBarBottomMargin + 12) else (standardBottomBarHeight + standardBottomBarBottomMargin + 8)) + subTabsBottomMargin.coerceAtLeast(0)).dp

            val activeSubTabPopup = LocalActiveSubTabPopup.current
            val categoryBarVisible = !floatingBottomBar && showCategoryTabs && activeSubTabPopup == null && (
                (alwaysShowSubTabsLibrary || showCategoryBar || isCategoryBarPinned) &&
                    state.searchQuery == null && state.selection.isEmpty() && !state.isLoading && !state.isLibraryEmpty
                )

            val categoryBarTranslationY by animateFloatAsState(
                targetValue = if (categoryBarVisible) 0f else 150f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "categoryBarTranslationY",
            )
            val categoryBarAlpha by animateFloatAsState(
                targetValue = if (categoryBarVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 150),
                label = "categoryBarAlpha",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = fabBottomPadding)
                    .wrapContentWidth()
                    .graphicsLayer {
                        translationY = categoryBarTranslationY
                        alpha = categoryBarAlpha
                    },
            ) {
                if (categoryBarAlpha > 0.01f) {
                    val editCategory = LocalEditCategory.current
                    val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsState()
                    val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()
                    val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsState()
                    val effectiveCornerRadius = if (syncControlsWithDockRadius) bottomBarCornerRadius.dp else bottomControlsCornerRadius.dp
                    GlassSurface(
                        shape = RoundedCornerShape(effectiveCornerRadius),
                        style = GlassDefaults.regularStyle(),
                        isCategoryBar = true,
                    ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (subcategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
                            LazyRow(
                                state = subcategoryRowState,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                item(key = "anime_sub_all") {
                                    val allText = "All"
                                    SubTabButton(
                                        text = allText,
                                        selected = activeSubcategoryIdOfActivePage == null,
                                        carouselStyle = categoryBarCarouselStyle,
                                        onLongClick = { activeParent?.let { editCategory(it) } },
                                    ) {
                                        activeSubcategoryId = null
                                    }
                                }
                                items(subcategories, key = { it.id }) { sub ->
                                    val subCount = state.getAnimeCountForCategory(sub) ?: 0
                                    val subText = if (kisaraShowItemCountInTabs) "${sub.visualName} ($subCount)" else sub.visualName
                                    SubTabButton(
                                        text = subText,
                                        selected = activeSubcategoryIdOfActivePage == sub.id,
                                        carouselStyle = categoryBarCarouselStyle,
                                        onLongClick = { editCategory(sub) },
                                    ) {
                                        activeSubcategoryId = if (activeSubcategoryId == sub.id) null else sub.id
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LazyRow(
                                state = parentCategoryRowState,
                                modifier = Modifier.weight(1f, fill = false),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                itemsIndexed(tabCategories, key = { _, category -> category.id }) { index, category ->
                                    val count = state.getAnimeCountForCategory(category) ?: 0
                                    val text = if (kisaraShowItemCountInTabs) "${category.visualName} ($count)" else category.visualName
                                    SubTabButton(
                                        text = text,
                                        selected = screenModel.activeCategoryIndex == index,
                                        carouselStyle = categoryBarCarouselStyle,
                                        onLongClick = { editCategory(category) },
                                    ) {
                                        screenModel.activeCategoryIndex = index
                                        if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                            activeSubcategoryId = null
                                        }
                                    }
                                }
                            }

                            Icon(
                                imageVector = if (isCategoryBarPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin category bar",
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp)
                                    .clickable {
                                        scope.launch {
                                            categoryBarPinnedPref.set(!isCategoryBarPinned)
                                        }
                                    },
                                tint = if (isCategoryBarPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

        if (showSearchSheet) {
            SearchBottomSheet(
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                onSearch = { query ->
                    screenModel.search(query)
                    showSearchSheet = false
                },
                onDismissRequest = { showSearchSheet = false },
                title = stringResource(MR.strings.action_search),
                placeholderText = stringResource(MR.strings.action_search_hint),
            )
        }

        when (val dialog = state.dialog) {
            is AnimeLibraryScreenModel.Dialog.Settings -> {
                AnimeLibrarySettingsDialog(
                    onDismissRequest = screenModel::closeDialog,
                    screenModel = screenModel,
                )
            }
            is AnimeLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection.toImmutableList(),
                    onDismissRequest = screenModel::closeDialog,
                    onEditCategories = { navigator.push(AnimeCategoryScreen()) },
                    onConfirm = { include, exclude ->
                        screenModel.updateAnimeCategories(dialog.animes, include, exclude)
                    },
                    onDuplicateCheck = {
                        screenModel.closeDialog()
                        navigator.push(DuplicateAnimeScreen(dialog.animes.map { it.id }))
                    },
                    onDelete = {
                        screenModel.removeAnime(dialog.animes, deleteFromLibrary = true, deleteEpisodes = false)
                    },
                    anime = dialog.animes.singleOrNull(),
                    onCreateCategory = screenModel::createCategory,
                )
            }
            is AnimeLibraryScreenModel.Dialog.DeleteAnime -> {
                eu.kanade.presentation.library.DeleteLibraryMangaDialog(
                    containsLocalManga = false,
                    onDismissRequest = screenModel::closeDialog,
                    onConfirm = { deleteFromLibrary, deleteEpisodes ->
                        screenModel.removeAnime(dialog.animes, deleteFromLibrary, deleteEpisodes)
                    },
                )
            }
            null -> Unit
        }

        BackHandler(enabled = state.selection.isNotEmpty() || state.searchQuery != null) {
            when {
                state.selection.isNotEmpty() -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(Unit) {
            HomeScreen.showBottomNav(true)
            androidx.compose.runtime.snapshotFlow { state.selection.size }.collect { size ->
                HomeScreen.showBottomNav(size == 0)
            }
        }
    }
}

