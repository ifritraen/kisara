package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
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
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.library.LibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.SyncFavoritesConfirmDialog
import eu.kanade.presentation.library.components.SyncFavoritesProgressDialog
import eu.kanade.presentation.library.components.SyncFavoritesWarningDialog
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.home.LocalActiveSubTabPopup
import eu.kanade.tachiyomi.ui.home.LocalEditCategory
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import exh.favorites.FavoritesSyncStatus
import exh.recs.RecommendsScreen
import exh.recs.batch.RecommendationSearchBottomSheetDialog
import exh.recs.batch.RecommendationSearchProgressDialog
import exh.recs.batch.SearchStatus
import exh.source.MERGED_SOURCE_ID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object LibraryTab : Tab {
    val activeCategoryFlow = MutableStateFlow<Pair<Long?, Long?>>(Pair(null, null))
    val toggleCategoryBarEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val selectCategoryEvent = Channel<Int>(1, BufferOverflow.DROP_OLDEST)
    val nextCategoryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val prevCategoryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val selectSubcategoryEvent = Channel<Long?>(1, BufferOverflow.DROP_OLDEST)
    val nextSubcategoryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val prevSubcategoryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val searchEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val filterSettingsEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val syncEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val randomMangaEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val globalUpdateEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val categoryUpdateEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val reindexDownloadEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val syncFavoritesEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    @Suppress("unused")
    private fun readResolve(): Any = LibraryTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
        val activeMediaType by uiPreferences.activeMediaType().collectAsStateWithLifecycle()

        if (activeMediaType == eu.kanade.domain.ui.model.MediaType.NOVEL) {
            eu.kanade.tachiyomi.ui.library.novel.NovelLibraryTab.Content()
            return
        }

        if (activeMediaType == eu.kanade.domain.ui.model.MediaType.ANIME) {
            eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab.Content()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { LibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val useNewCategorySubbar = true

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            // SY -->
            val started = LibraryUpdateJob.startNow(
                context = context,
                category = if (state.groupType == LibraryGroup.BY_DEFAULT && category?.isLocalCategory != true) category else null,
                group = state.groupType,
                groupExtra = when (state.groupType) {
                    LibraryGroup.BY_DEFAULT -> null
                    LibraryGroup.BY_SOURCE, LibraryGroup.BY_TRACK_STATUS -> category?.id?.toString()
                    LibraryGroup.BY_STATUS -> category?.id?.minus(1)?.toString()
                    else -> null
                },
            )
            // SY <--
            scope.launch {
                val msgRes = when {
                    !started -> MR.strings.update_already_running
                    category != null -> MR.strings.updating_category
                    else -> MR.strings.updating_library
                }
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        // KMK -->
        var activeSubcategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
        var previousParentId by rememberSaveable { mutableStateOf<Long?>(null) }
        var showSearchSheet by rememberSaveable { mutableStateOf(false) }
        val showSubcategoriesAtTop by uiPreferences.showSubcategoriesAtTop().collectAsStateWithLifecycle()
        // KMK <--

        val kisaraShowSubcategoriesInMainBar = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.kisaraShowSubcategoriesInMainBar().collectAsStateWithLifecycle().value
        val parentCategories = remember(state.categories) {
            state.categories.filter { it.parentId == null }.sortedWith { c1, c2 ->
                when {
                    c1.isSystemCategory && !c2.isSystemCategory -> -1
                    c2.isSystemCategory && !c1.isSystemCategory -> 1
                    c1.isLocalCategory && !c2.isLocalCategory -> -1
                    c2.isLocalCategory && !c1.isLocalCategory -> 1
                    else -> c1.order.compareTo(c2.order)
                }
            }
        }
        val childrenByParent = remember(state.categories) {
            state.categories.filter { it.parentId != null }
                .groupBy { it.parentId }
                .mapValues { entry -> entry.value.sortedBy { it.order } }
        }
        val showParentFilters = useNewCategorySubbar || (state.showParentFilters && state.categories.any { it.parentId == null && !it.isSystemCategory && !it.isLocalCategory })
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
            parentCategories
        } else {
            state.categories
        }
        val activeCategory = state.categories.getOrNull(state.activeCategoryIndex)
        val activeParent = remember(activeCategory, parentCategories) {
            if (activeCategory == null) {
                null
            } else if (activeCategory.parentId == null) {
                activeCategory
            } else {
                parentCategories.find { it.id == activeCategory.parentId }
            }
        }
        val activeParentIndexInTabCategories = if (activeParent != null) tabCategories.indexOf(activeParent).coerceAtLeast(0) else 0
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
            activeCategoryFlow.value = Pair(activeParent?.id, activeSubcategoryId)
        }

        var showCategoryBar by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch {
                toggleCategoryBarEvent.receiveAsFlow().collectLatest {
                    showCategoryBar = !showCategoryBar
                }
            }
            launch {
                selectCategoryEvent.receiveAsFlow().collectLatest { index ->
                    screenModel.updateActiveCategoryIndex(index)
                }
            }
            launch {
                nextCategoryEvent.receiveAsFlow().collectLatest {
                    val totalCategories = tabCategories.size
                    if (totalCategories > 1) {
                        val next = (state.activeCategoryIndex + 1).coerceAtMost(totalCategories - 1)
                        screenModel.updateActiveCategoryIndex(next)
                    }
                }
            }
            launch {
                prevCategoryEvent.receiveAsFlow().collectLatest {
                    val totalCategories = tabCategories.size
                    if (totalCategories > 1) {
                        val prev = (state.activeCategoryIndex - 1).coerceAtLeast(0)
                        screenModel.updateActiveCategoryIndex(prev)
                    }
                }
            }
            launch {
                selectSubcategoryEvent.receiveAsFlow().collectLatest { subId ->
                    if (subId != null) {
                        val sub = state.categories.find { it.id == subId }
                        if (sub?.parentId != null) {
                            val pIndex = state.categories.indexOfFirst { it.id == sub.parentId }
                            if (pIndex != -1) {
                                previousParentId = sub.parentId
                                screenModel.updateActiveCategoryIndex(pIndex)
                            }
                        }
                    }
                    activeSubcategoryId = subId
                }
            }
            launch {
                nextSubcategoryEvent.receiveAsFlow().collectLatest {
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
                prevSubcategoryEvent.receiveAsFlow().collectLatest {
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
            launch {
                searchEvent.receiveAsFlow().collectLatest {
                    showSearchSheet = true
                }
            }
            launch {
                filterSettingsEvent.receiveAsFlow().collectLatest {
                    screenModel.showSettingsDialog()
                }
            }
            launch {
                syncEvent.receiveAsFlow().collectLatest {
                    if (!SyncDataJob.isRunning(context)) {
                        SyncDataJob.startNow(context, manual = true)
                    }
                }
            }
            launch {
                randomMangaEvent.receiveAsFlow().collectLatest {
                    val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                    if (randomItem != null) {
                        navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                    }
                }
            }
            launch {
                globalUpdateEvent.receiveAsFlow().collectLatest {
                    onClickRefresh(null)
                }
            }
            launch {
                categoryUpdateEvent.receiveAsFlow().collectLatest {
                    onClickRefresh(state.activeCategory)
                }
            }
            launch {
                reindexDownloadEvent.receiveAsFlow().collectLatest {
                    Injekt.get<DownloadCache>().invalidateCache()
                    context.toast(MR.strings.download_cache_invalidated)
                }
            }
            launch {
                syncFavoritesEvent.receiveAsFlow().collectLatest {
                    screenModel.openFavoritesSyncDialog()
                }
            }
        }

        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val categoryBarPinnedPref = libraryPreferences.categoryBarPinned()
        val isCategoryBarPinned by categoryBarPinnedPref.collectAsStateWithLifecycle()

        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsStateWithLifecycle()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsStateWithLifecycle()
        val showCategoryTabs by uiPreferences.showCategoryTabs().collectAsStateWithLifecycle()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsStateWithLifecycle()
        val categoryBarCarouselStyle by uiPreferences.categoryBarCarouselStyle().collectAsStateWithLifecycle()
        val alwaysShowSubTabsLibrary by uiPreferences.alwaysShowSubTabsLibrary().collectAsStateWithLifecycle()
        val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsStateWithLifecycle()
        val kisaraShowItemCountInTabs by uiPreferences.kisaraShowItemCountInTabs().collectAsStateWithLifecycle()
        val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsStateWithLifecycle()
        val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsStateWithLifecycle()
        val standardBottomBarHeight by uiPreferences.standardBottomBarHeight().collectAsStateWithLifecycle()
        val standardBottomBarBottomMargin by uiPreferences.standardBottomBarBottomMargin().collectAsStateWithLifecycle()

        val categoryBarSelectedFontColorType by uiPreferences.categoryBarSelectedFontColorType().collectAsStateWithLifecycle()
        val categoryBarSelectedFontCustomColor by uiPreferences.categoryBarSelectedFontCustomColor().collectAsStateWithLifecycle()

        val categorySelectedLabelColor = when (categoryBarSelectedFontColorType) {
            0 -> MaterialTheme.colorScheme.onSurface
            1 -> MaterialTheme.colorScheme.primary
            else -> Color(categoryBarSelectedFontCustomColor)
        }

        var topBarVisible by remember { mutableStateOf(true) }
        val bottomBarVisible by HomeScreen.showBottomNavFlow.collectAsStateWithLifecycle()
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
        // KMK <--

        LaunchedEffect(hideTopBarOnScroll) {
            if (!hideTopBarOnScroll) {
                topBarVisible = true
            }
        }

        val localHazeState = remember { dev.chrisbanes.haze.HazeState() }

        CompositionLocalProvider(LocalHazeState provides localHazeState) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsStateWithLifecycle()
                val showSubcategoryTabs by libraryPreferences.subcategoryTabs().collectAsStateWithLifecycle()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (frostedGlass) Modifier.hazeSource(state = localHazeState) else Modifier),
                ) {
                    Scaffold(
                        modifier = Modifier.nestedScroll(nestedScrollConnection),
                        topBar = { scrollBehavior ->
                            if (state.searchQuery != null || state.selectionMode) {
                                val title = state.getToolbarTitle(
                                    defaultTitle = stringResource(MR.strings.label_library),
                                    defaultCategoryTitle = stringResource(MR.strings.label_default),
                                    page = state.activeCategoryIndex,
                                )
                                // KMK -->
                                val sourcePrefs = remember { Injekt.get<SourcePreferences>() }
                                val searchClean by sourcePrefs.searchClean().collectAsStateWithLifecycle()
                                val searchFormat by sourcePrefs.searchFormat().collectAsStateWithLifecycle()
                                val searchFuzzy by sourcePrefs.searchFuzzy().collectAsStateWithLifecycle()
                                Column {
                                    // KMK <--
                                    LibraryToolbar(
                                        hasActiveFilters = state.hasActiveFilters,
                                        selectedCount = state.selection.size,
                                        title = title,
                                        onClickUnselectAll = screenModel::clearSelection,
                                        onClickSelectAll = { screenModel.selectAll(activeSubcategoryId, activeParent?.id ?: state.activeCategory?.id) },
                                        onClickInvertSelection = { screenModel.invertSelection(activeSubcategoryId, activeParent?.id ?: state.activeCategory?.id) },
                                        onClickFilter = screenModel::showSettingsDialog,
                                        onClickRefresh = { onClickRefresh(state.activeCategory) },
                                        onClickGlobalUpdate = { onClickRefresh(null) },
                                        onClickOpenRandomManga = {
                                            scope.launch {
                                                val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                                                if (randomItem != null) {
                                                    navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                                                } else {
                                                    snackbarHostState.showSnackbar(
                                                        context.stringResource(MR.strings.information_no_entries_found),
                                                    )
                                                }
                                            }
                                        },
                                        onClickSyncNow = {
                                            if (!SyncDataJob.isRunning(context)) {
                                                SyncDataJob.startNow(context, manual = true)
                                            } else {
                                                context.toast(SYMR.strings.sync_in_progress)
                                            }
                                        },
                                        // SY -->
                                        onClickSyncExh = screenModel::openFavoritesSyncDialog.takeIf { state.showSyncExh },
                                        isSyncEnabled = state.isSyncEnabled,
                                        // SY <--
                                        searchQuery = state.searchQuery,
                                        onSearchQueryChange = screenModel::search,
                                        onOpenSearchSheet = { showSearchSheet = true },
                                        onInvalidateDownloadCache = { context ->
                                            Injekt.get<DownloadCache>().invalidateCache()
                                            context.toast(MR.strings.download_cache_invalidated)
                                        },
                                        // For scroll overlay when no tab
                                        scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs },
                                    )
                                    // KMK -->
                                    if (state.searchQuery != null) {
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            FilterChip(
                                                selected = searchClean,
                                                onClick = { sourcePrefs.searchClean().set(!searchClean) },
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
                                                onClick = { sourcePrefs.searchFormat().set((searchFormat + 1) % 3) },
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
                                                onClick = { sourcePrefs.searchFuzzy().set(!searchFuzzy) },
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
                                        HorizontalDivider()
                                    }
                                } // end Column
                                // KMK <--
                            }
                        },
                        bottomBar = {
                            LibraryBottomActionMenu(
                                visible = state.selectionMode,
                                onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                                onMarkAsReadClicked = { screenModel.markReadSelection(true) },
                                onMarkAsUnreadClicked = { screenModel.markReadSelection(false) },
                                onDownloadClicked = screenModel::performDownloadAction
                                    .takeIf { state.selectedManga.fastAll { !it.isLocal() } },
                                onDeleteClicked = screenModel::openDeleteMangaDialog,
                                onMigrateClicked = {
                                    val selection = state
                                        // KMK -->
                                        .selectedManga
                                        .filterNot { it.source == MERGED_SOURCE_ID }
                                        .map { it.id }
                                    // KMK <--
                                    screenModel.clearSelection()
                                    // KMK -->
                                    if (selection.isEmpty()) {
                                        context.toast(SYMR.strings.no_valid_entry)
                                    } else {
                                        // KMK <--
                                        navigator.push(MigrationConfigScreen(selection))
                                    }
                                },
                                // KMK -->
                                onMergeClicked = {
                                    if (state.selection.size == 1) {
                                        val manga = state.selectedManga.first()
                                        // Invoke merging for this manga
                                        screenModel.clearSelection()
                                        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)
                                        navigator.push(SourcesScreen(smartSearchConfig))
                                    } else if (state.selection.isNotEmpty()) {
                                        // Invoke multiple merge
                                        val selectedManga = state.selectedManga
                                        screenModel.clearSelection()
                                        scope.launchIO {
                                            val mergingMangas = selectedManga.filterNot { it.source == MERGED_SOURCE_ID }
                                            val mergedMangaId = screenModel.smartSearchMerge(selectedManga.toPersistentList())
                                            snackbarHostState.showSnackbar(context.stringResource(SYMR.strings.entry_merged))
                                            if (mergedMangaId != null) {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = context.stringResource(KMR.strings.action_remove_merged),
                                                    actionLabel = context.stringResource(MR.strings.action_remove),
                                                    withDismissAction = true,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    screenModel.removeMangas(
                                                        mangas = mergingMangas,
                                                        deleteFromLibrary = true,
                                                        deleteChapters = false,
                                                    )
                                                }
                                                navigator.push(MangaScreen(mergedMangaId))
                                            } else {
                                                snackbarHostState.showSnackbar(context.stringResource(SYMR.strings.merged_references_invalid))
                                            }
                                        }
                                    } else {
                                        screenModel.clearSelection()
                                        context.toast(SYMR.strings.no_valid_entry)
                                    }
                                },
                                onSelectionUpdateClicked = {
                                    val started = screenModel.updateSelectedManga()
                                    scope.launch {
                                        val msgRes = if (started) {
                                            KMR.strings.updating
                                        } else {
                                            MR.strings.update_already_running
                                        }
                                        if (started) {
                                            screenModel.clearSelection()
                                        }
                                        snackbarHostState.showSnackbar(context.stringResource(msgRes))
                                    }
                                },
                                // KMK <--
                                // SY -->
                                onClickCleanTitles = screenModel::cleanTitles.takeIf { state.showCleanTitles },
                                onClickCollectRecommendations = screenModel::showRecommendationSearchDialog.takeIf { state.selection.size > 1 },
                                onClickAddToMangaDex = screenModel::syncMangaToDex.takeIf { state.showAddToMangadex },
                                onClickResetInfo = screenModel::resetInfo.takeIf { state.showResetInfo },
                                // SY <--
                                // KMK -->
                                onDuplicateCheckClicked = {
                                    val selection = state.selectedManga.map { it.id }
                                    screenModel.clearSelection()
                                    navigator.push(DuplicateMangaScreen(selection))
                                },
                                // KMK <--
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
                            state.isLoading -> {
                                LoadingScreen(Modifier.padding(adjustedContentPadding))
                            }
                            state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                                val handler = LocalUriHandler.current
                                EmptyScreen(
                                    stringRes = MR.strings.information_empty_library,
                                    modifier = Modifier.padding(adjustedContentPadding),
                                    actions = persistentListOf(
                                        EmptyScreenAction(
                                            stringRes = MR.strings.getting_started_guide,
                                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                            onClick = { handler.openUri(GETTING_STARTED_URL) },
                                        ),
                                    ),
                                )
                            }
                            else -> {
                                LibraryContent(
                                    categories = state.categories,
                                    activeCategoryIndex = if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                        val activeParentId = state.categories.getOrNull(state.activeCategoryIndex)?.let {
                                            if (it.parentId == null) it.id else it.parentId
                                        }
                                        parentCategories.indexOfFirst { it.id == activeParentId }.coerceAtLeast(0)
                                    } else {
                                        state.activeCategoryIndex
                                    },
                                    searchQuery = state.searchQuery,
                                    selection = state.selection,
                                    contentPadding = adjustedContentPadding,
                                    currentPage = if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                        val activeParentId = state.categories.getOrNull(state.activeCategoryIndex)?.let {
                                            if (it.parentId == null) it.id else it.parentId
                                        }
                                        parentCategories.indexOfFirst { it.id == activeParentId }.coerceAtLeast(0)
                                    } else {
                                        state.activeCategoryIndex.coerceIn(0, state.categories.lastIndex.coerceAtLeast(0))
                                    },
                                    hasActiveFilters = state.hasActiveFilters,
                                    showPageTabs = (showTopTabBar || !state.searchQuery.isNullOrEmpty()) && topBarVisible,
                                    showParentFilters = showParentFilters,
                                    showSubcategories = showSubcategoriesAtTop && (showSubcategoryTabs || useNewCategorySubbar) && topBarVisible,
                                    onChangeCurrentPage = { page ->
                                        if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                            val parentCat = parentCategories.getOrNull(page)
                                            if (parentCat != null) {
                                                val dbIndex = state.categories.indexOfFirst { it.id == parentCat.id }
                                                if (dbIndex != -1) {
                                                    screenModel.updateActiveCategoryIndex(dbIndex)
                                                }
                                            }
                                        } else {
                                            screenModel.updateActiveCategoryIndex(page)
                                        }
                                    },
                                    onClickManga = { navigator.push(MangaScreen(it)) },
                                    onContinueReadingClicked = { it: LibraryManga ->
                                        scope.launchIO {
                                            val chapter = screenModel.getNextUnreadChapter(it.manga)
                                            if (chapter != null) {
                                                context.startActivity(
                                                    ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                                            }
                                        }
                                        Unit
                                    }.takeIf { state.showMangaContinueButton },
                                    onToggleSelection = screenModel::toggleSelection,
                                    onToggleRangeSelection = { category, manga ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        screenModel.toggleRangeSelection(category, manga)
                                    },
                                    onRefresh = { onClickRefresh(state.activeCategory) },
                                    onGlobalSearchClicked = {
                                        navigator.push(GlobalSearchScreen(screenModel.state.value.searchQuery ?: ""))
                                    },
                                    getItemCountForCategory = { state.getItemCountForCategory(it) },
                                    getDisplayMode = { screenModel.getDisplayMode() },
                                    getColumnsForOrientation = { screenModel.getColumnsForOrientation(it) },
                                    getItemsForCategory = { state.getItemsForCategory(it) },
                                    // KMK -->
                                    activeSubcategoryId = activeSubcategoryId,
                                    onSubcategorySelected = { activeSubcategoryId = it },
                                    scrollPositions = screenModel.scrollPositions,
                                    // KMK <--
                                )
                            }
                        }
                    }
                }

                // Floating Horizontally Scrollable Category Bar (True Carousel with Auto-Centering)
                val bottomBarOpacity by uiPreferences.bottomBarOpacity().collectAsStateWithLifecycle()
                val parentCategoryRowState = rememberLazyListState()
                val subcategoryRowState = rememberLazyListState()

                // KMK --> ponytail: auto-center active parent category in carousel
                LaunchedEffect(state.activeCategoryIndex, tabCategories.size, categoryBarCarouselStyle) {
                    if (categoryBarCarouselStyle && tabCategories.isNotEmpty()) {
                        val activeParentId = state.categories.getOrNull(state.activeCategoryIndex)?.let {
                            if (it.parentId == null) it.id else it.parentId
                        }
                        val activeIndex = tabCategories.indexOfFirst { it.id == activeParentId }.coerceAtLeast(0)
                        val layoutInfo = parentCategoryRowState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                        val targetItem = visibleItems.firstOrNull { it.index == activeIndex }
                        if (targetItem != null && viewportWidth > 0) {
                            val offset = (viewportWidth - targetItem.size) / 2
                            parentCategoryRowState.animateScrollToItem(activeIndex, -offset)
                        } else {
                            parentCategoryRowState.animateScrollToItem(activeIndex)
                        }
                    }
                }

                // Auto-center active subcategory in carousel
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
                // KMK <--

                val fabBottomPadding = ((if (floatingBottomBar) (bottomBarHeight + bottomBarBottomMargin + 12) else (standardBottomBarHeight + standardBottomBarBottomMargin + 8)) + subTabsBottomMargin.coerceAtLeast(0)).dp

                val activeSubTabPopup = LocalActiveSubTabPopup.current
                val categoryBarVisible = !floatingBottomBar && showCategoryTabs && activeSubTabPopup == null && (
                    (alwaysShowSubTabsLibrary || showCategoryBar || isCategoryBarPinned) &&
                        state.searchQuery == null && !state.selectionMode && !state.isLoading && !state.isLibraryEmpty
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
                        val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsStateWithLifecycle()
                        val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsStateWithLifecycle()
                        val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsStateWithLifecycle()
                        val effectiveCornerRadius = if (syncControlsWithDockRadius) bottomBarCornerRadius.dp else bottomControlsCornerRadius.dp
                        // Main Category Bar Surface
                        GlassSurface(
                            shape = RoundedCornerShape(effectiveCornerRadius),
                            style = GlassDefaults.regularStyle(),
                            isCategoryBar = true,
                        ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Subcategories Row (if present) - Rendered ABOVE Parent Categories (True Carousel)
                            if (subcategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
                                LazyRow(
                                    state = subcategoryRowState,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    // "All" button
                                    item(key = "sub_all") {
                                        val allCount = remember(activeParent, state.libraryData.favorites, childrenByParent, kisaraShowItemCountInTabs) {
                                            if (kisaraShowItemCountInTabs && activeParent != null) {
                                                val childCategories = childrenByParent[activeParent.id].orEmpty()
                                                val allCategoryIds = listOf(activeParent.id) + childCategories.map { it.id }
                                                state.libraryData.favorites.count { item ->
                                                    item.libraryManga.categories.any { it in allCategoryIds }
                                                }
                                            } else {
                                                0
                                            }
                                        }
                                        val allText = if (kisaraShowItemCountInTabs) "All ($allCount)" else "All"
                                        SubTabButton(
                                            text = allText,
                                            selected = activeSubcategoryIdOfActivePage == null,
                                            carouselStyle = categoryBarCarouselStyle,
                                            onLongClick = { activeParent?.let { editCategory(it) } },
                                        ) {
                                            if (showParentFilters) {
                                                activeSubcategoryId = null
                                            } else {
                                                activeParent?.let { parent ->
                                                    val actualIndex = state.categories.indexOfFirst { it.id == parent.id }
                                                    if (actualIndex != -1) {
                                                        LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    items(subcategories, key = { it.id }) { sub ->
                                        val subCount = remember(sub, state, kisaraShowItemCountInTabs) {
                                            if (kisaraShowItemCountInTabs) {
                                                state.getItemCountForCategory(sub, force = true)
                                            } else {
                                                0
                                            }
                                        }
                                        val subText = if (kisaraShowItemCountInTabs) "${sub.visualName} ($subCount)" else sub.visualName
                                        SubTabButton(
                                            text = subText,
                                            selected = activeSubcategoryIdOfActivePage == sub.id,
                                            carouselStyle = categoryBarCarouselStyle,
                                            onLongClick = { editCategory(sub) },
                                        ) {
                                            if (showParentFilters) {
                                                activeSubcategoryId = if (activeSubcategoryId == sub.id) null else sub.id
                                            } else {
                                                val actualIndex = state.categories.indexOfFirst { it.id == sub.id }
                                                if (actualIndex != -1) {
                                                    LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Parent Categories Row (with Pin on the right) (True Carousel)
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
                                        val count = remember(category, state.libraryData.favorites, childrenByParent, kisaraShowItemCountInTabs) {
                                            if (kisaraShowItemCountInTabs) {
                                                val childCategories = childrenByParent[category.id].orEmpty()
                                                if (childCategories.isEmpty()) {
                                                    state.getItemCountForCategory(category, force = true)?.toInt() ?: 0
                                                } else {
                                                    val allCategoryIds = listOf(category.id) + childCategories.map { it.id }
                                                    state.libraryData.favorites.count { item ->
                                                        item.libraryManga.categories.any { it in allCategoryIds }
                                                    }
                                                }
                                            } else {
                                                0
                                            }
                                        }
                                        val text = if (kisaraShowItemCountInTabs) "${category.visualName} ($count)" else category.visualName
                                        SubTabButton(
                                            text = text,
                                            selected = if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                                val activeParentId = state.categories.getOrNull(state.activeCategoryIndex)?.let {
                                                    if (it.parentId == null) it.id else it.parentId
                                                }
                                                category.id == activeParentId
                                            } else {
                                                state.activeCategoryIndex == index
                                            },
                                            carouselStyle = categoryBarCarouselStyle,
                                            onLongClick = { if (!category.isLocalCategory && !category.isSystemCategory) editCategory(category) },
                                        ) {
                                            val actualIndex = state.categories.indexOfFirst { it.id == category.id }
                                            if (actualIndex != -1) {
                                                LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                if (showParentFilters && !kisaraShowSubcategoriesInMainBar) {
                                                    activeSubcategoryId = null
                                                }
                                            }
                                        }
                                    }
                                }

                                // Pin button on the right (tiny & zero padding space)
                                androidx.compose.material3.Icon(
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

            val onDismissRequest = screenModel::closeDialog
            when (val dialog = state.dialog) {
                is LibraryScreenModel.Dialog.SettingsSheet -> run {
                    val activeCategoryForSettings = if (activeSubcategoryId != null) {
                        state.libraryData.categories.find { it.id == activeSubcategoryId } ?: activeParent ?: state.activeCategory
                    } else {
                        activeParent ?: state.activeCategory
                    }
                    LibrarySettingsDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        category = activeCategoryForSettings,
                        // SY -->
                        hasCategories = state.libraryData.categories.fastAny { !it.isSystemCategory && !it.isLocalCategory },
                        // SY <--
                        // KMK -->
                        categories = state.libraryData.categories.filterNot { it.isSystemCategory || it.isLocalCategory },
                        // KMK <--
                    )
                }
                is LibraryScreenModel.Dialog.ChangeCategory -> {
                    val targetManga = dialog.manga.firstOrNull()
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = {
                            // KMK -->
                            // screenModel.clearSelection()
                            // KMK <--
                            navigator.push(CategoryScreen())
                        },
                        onConfirm = { include, exclude ->
                            screenModel.clearSelection()
                            screenModel.setMangaCategories(dialog.manga, include, exclude)
                        },
                        onDuplicateCheck = {
                            onDismissRequest()
                            navigator.push(DuplicateMangaScreen(dialog.manga.map { it.id }))
                        },
                        onDeleteManga = {
                            screenModel.removeMangas(dialog.manga, deleteFromLibrary = true, deleteChapters = false)
                        },
                        manga = targetManga,
                        onOpenTrackerSearch = {
                            if (targetManga != null) {
                                onDismissRequest()
                                navigator.push(eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen(mangaId = targetManga.id, mangaTitle = targetManga.title, sourceId = targetManga.source))
                            }
                        },
                    )
                }
                is LibraryScreenModel.Dialog.DeleteManga -> {
                    DeleteLibraryMangaDialog(
                        containsLocalManga = dialog.manga.any(Manga::isLocal),
                        onDismissRequest = onDismissRequest,
                        onConfirm = { deleteManga, deleteChapter ->
                            screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                            screenModel.clearSelection()
                        },
                    )
                }
                // SY -->
                LibraryScreenModel.Dialog.SyncFavoritesWarning -> {
                    SyncFavoritesWarningDialog(
                        onDismissRequest = onDismissRequest,
                        onAccept = {
                            onDismissRequest()
                            screenModel.onAcceptSyncWarning()
                        },
                    )
                }
                LibraryScreenModel.Dialog.SyncFavoritesConfirm -> {
                    SyncFavoritesConfirmDialog(
                        onDismissRequest = onDismissRequest,
                        onAccept = {
                            onDismissRequest()
                            screenModel.runSync()
                        },
                    )
                }
                is LibraryScreenModel.Dialog.RecommendationSearchSheet -> {
                    RecommendationSearchBottomSheetDialog(
                        onDismissRequest = onDismissRequest,
                        onSearchRequest = {
                            onDismissRequest()
                            screenModel.clearSelection()
                            screenModel.runRecommendationSearch(dialog.manga)
                        },
                    )
                }
                // SY <--
                null -> {}
            }

            // SY -->
            SyncFavoritesProgressDialog(
                status = screenModel.favoritesSync.status.collectAsStateWithLifecycle().value,
                setStatusIdle = { screenModel.favoritesSync.status.value = FavoritesSyncStatus.Idle },
                openManga = { navigator.push(MangaScreen(it)) },
            )

            RecommendationSearchProgressDialog(
                status = screenModel.recommendationSearch.status.collectAsStateWithLifecycle().value,
                setStatusIdle = { screenModel.recommendationSearch.status.value = SearchStatus.Idle },
                setStatusCancelling = { screenModel.recommendationSearch.status.value = SearchStatus.Cancelling },
            )
            // SY <--

            BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
                when {
                    state.selectionMode -> screenModel.clearSelection()
                    state.searchQuery != null -> screenModel.search(null)
                }
            }

            LaunchedEffect(Unit) {
                HomeScreen.showBottomNav(true)
                androidx.compose.runtime.snapshotFlow { state.selectionMode to state.selection.size }.collect { (selectionMode, size) ->
                    HomeScreen.showBottomNav(!selectionMode && size == 0)
                }
            }

            LaunchedEffect(state.isLoading) {
                if (!state.isLoading) {
                    (context as? MainActivity)?.ready = true

                    // AM (DISCORD) -->
                    with(DiscordRPCService) {
                        discordScope.launchIO { setScreen(context, DiscordScreen.LIBRARY) }
                    }
                    // <-- AM (DISCORD)
                }
            }

            // SY -->
            val recSearchState by screenModel.recommendationSearch.status.collectAsStateWithLifecycle()
            LaunchedEffect(recSearchState) {
                when (val current = recSearchState) {
                    is SearchStatus.Finished.WithResults -> {
                        RecommendsScreen.Args.MergedSourceMangas(current.results)
                            .let(::RecommendsScreen)
                            .let(navigator::push)

                        screenModel.recommendationSearch.status.value = SearchStatus.Idle
                    }
                    is SearchStatus.Finished.WithoutResults -> {
                        context.toast(SYMR.strings.rec_no_results)
                        screenModel.recommendationSearch.status.value = SearchStatus.Idle
                    }
                    is SearchStatus.Cancelling -> {
                        screenModel.cancelRecommendationSearch()
                        screenModel.recommendationSearch.status.value = SearchStatus.Idle
                    }
                    else -> {}
                }
            }
            // SY <--

            LaunchedEffect(Unit) {
                launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
                launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
            }
        }
    }
}

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)

    var selectedCategoryIndex: Int = 0
}

@Composable
private fun SubTabButton(
    text: String,
    selected: Boolean,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    carouselStyle: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val subBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.subBarHeight().collectAsStateWithLifecycle().value
    val fontSize = (subBarHeight * 0.35f).coerceIn(6f, 14f).sp

    val scale by animateFloatAsState(
        targetValue = if (selected && carouselStyle) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "subTabScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected || !carouselStyle) 1.0f else 0.72f,
        animationSpec = tween(150),
        label = "subTabAlpha",
    )

    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(subBarHeight.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(shape)
            .background(backgroundColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
        )
    }
}
