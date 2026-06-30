package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.LocalHazeState
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
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object LibraryTab : Tab {
    val toggleCategoryBarEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val selectCategoryEvent = Channel<Int>(1, BufferOverflow.DROP_OLDEST)
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
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { LibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { LibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        val useNewCategorySubbar = true

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            // SY -->
            val started = LibraryUpdateJob.startNow(
                context = context,
                category = if (state.groupType == LibraryGroup.BY_DEFAULT) category else null,
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
        var activeSubcategoryId by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(state.activeCategoryIndex) {
            activeSubcategoryId = null
        }

        val kisaraShowSubcategoriesInMainBar = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.kisaraShowSubcategoriesInMainBar().collectAsState().value
        val parentCategories = remember(state.categories) {
            state.categories.filter { it.parentId == null }.sortedBy { it.order }
        }
        val childrenByParent = remember(state.categories) {
            state.categories.filter { it.parentId != null }
                .groupBy { it.parentId }
                .mapValues { entry -> entry.value.sortedBy { it.order } }
        }
        val showParentFilters = useNewCategorySubbar || (state.showParentFilters && state.categories.any { it.parentId == null && !it.isSystemCategory })
        val tabCategories = if (showParentFilters && parentCategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
            parentCategories
        } else {
            state.categories
        }
        val activeCategory = tabCategories.getOrNull(state.activeCategoryIndex)
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
                searchEvent.receiveAsFlow().collectLatest {
                    screenModel.search("")
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
        val isCategoryBarPinned by categoryBarPinnedPref.collectAsState()

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsState()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsState()
        val showCategoryTabs by uiPreferences.showCategoryTabs().collectAsState()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()
        val categoryBarCarouselStyle by uiPreferences.categoryBarCarouselStyle().collectAsState()
        val alwaysShowSubTabsLibrary by uiPreferences.alwaysShowSubTabsLibrary().collectAsState()
        val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsState()
        val kisaraShowItemCountInTabs by uiPreferences.kisaraShowItemCountInTabs().collectAsState()
        val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()

        val categoryBarSelectedFontColorType by uiPreferences.categoryBarSelectedFontColorType().collectAsState()
        val categoryBarSelectedFontCustomColor by uiPreferences.categoryBarSelectedFontCustomColor().collectAsState()

        val categorySelectedLabelColor = when (categoryBarSelectedFontColorType) {
            0 -> MaterialTheme.colorScheme.onSurface
            1 -> MaterialTheme.colorScheme.primary
            else -> Color(categoryBarSelectedFontCustomColor)
        }

        var topBarVisible by remember { mutableStateOf(true) }
        var bottomBarVisible by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (delta < -10f) {
                        if (hideTopBarOnScroll) {
                            if (topBarVisible) topBarVisible = false
                        }
                        if (bottomBarVisible) {
                            bottomBarVisible = false
                            scope.launch { HomeScreen.showBottomNav(false) }
                        }
                    } else if (delta > 10f) {
                        if (hideTopBarOnScroll) {
                            if (!topBarVisible) topBarVisible = true
                        } else {
                            topBarVisible = true
                        }
                        if (!bottomBarVisible) {
                            bottomBarVisible = true
                            scope.launch { HomeScreen.showBottomNav(true) }
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
                val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
                val showSubcategoryTabs by libraryPreferences.subcategoryTabs().collectAsState()

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
                                LibraryToolbar(
                                    hasActiveFilters = state.hasActiveFilters,
                                    selectedCount = state.selection.size,
                                    title = title,
                                    onClickUnselectAll = screenModel::clearSelection,
                                    onClickSelectAll = screenModel::selectAll,
                                    onClickInvertSelection = screenModel::invertSelection,
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
                                    onInvalidateDownloadCache = { context ->
                                        Injekt.get<DownloadCache>().invalidateCache()
                                        context.toast(MR.strings.download_cache_invalidated)
                                    },
                                    // For scroll overlay when no tab
                                    scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs },
                                )
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
                                    showSubcategories = showSubcategoryTabs && topBarVisible,
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
                                    // KMK <--
                                )
                            }
                        }
                    }
                }

                // Floating Horizontally Scrollable Category Bar (with Scroll-to-Hide and Swapped Rows)
                val bottomBarOpacity by uiPreferences.bottomBarOpacity().collectAsState()
                val categoryRowState = rememberLazyListState()

                LaunchedEffect(state.activeCategoryIndex) {
                    if (categoryBarCarouselStyle && state.categories.isNotEmpty()) {
                        val index = state.activeCategoryIndex.coerceIn(0, state.categories.lastIndex)
                        val layoutInfo = categoryRowState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                        val targetItem = visibleItems.firstOrNull { it.index == index }
                        if (targetItem != null) {
                            val offset = (viewportWidth - targetItem.size) / 2
                            categoryRowState.animateScrollToItem(index, -offset)
                        } else {
                            categoryRowState.scrollToItem(index)
                            val targetItemNext = categoryRowState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (targetItemNext != null) {
                                val offset = (viewportWidth - targetItemNext.size) / 2
                                categoryRowState.animateScrollToItem(index, -offset)
                            }
                        }
                    }
                }

                val fabBottomPadding = if (bottomBarVisible) {
                    ((if (floatingBottomBar) (72 - 12) else (80 - 12)) + bottomBarBottomMargin + subTabsBottomMargin).coerceAtLeast(0).dp
                } else {
                    16.dp
                }

                val activeSubTabPopup = LocalActiveSubTabPopup.current
                val categoryBarVisible = showCategoryTabs && activeSubTabPopup == null && (
                    (((alwaysShowSubTabsLibrary || showCategoryBar) && bottomBarVisible) || isCategoryBarPinned) &&
                        state.searchQuery == null && !state.selectionMode && !state.isLoading && !state.isLibraryEmpty
                    )

                AnimatedVisibility(
                    visible = categoryBarVisible,
                    enter = expandVertically(expandFrom = Alignment.Bottom),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier = Modifier
                        .align(if (useNewCategorySubbar) Alignment.BottomCenter else Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = fabBottomPadding)
                        .let { if (useNewCategorySubbar) it.wrapContentWidth() else it.fillMaxWidth() },
                ) {
                    if (useNewCategorySubbar) {
                        val editCategory = LocalEditCategory.current
                        // Main Category Bar Surface (New Style)
                        GlassSurface(
                            shape = RoundedCornerShape(24.dp),
                            style = GlassDefaults.prominentStyle(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Subcategories Row (if present) - Rendered ABOVE Parent Categories
                                if (subcategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // "All" button
                                        val allCount = remember(activeParent, state, kisaraShowItemCountInTabs) {
                                            if (kisaraShowItemCountInTabs && activeParent != null) {
                                                childrenByParent[activeParent.id].orEmpty().sumOf { state.getItemCountForCategory(it, force = true)?.toInt() ?: 0 } + (state.getItemCountForCategory(activeParent, force = true)?.toInt() ?: 0)
                                            } else {
                                                0
                                            }
                                        }
                                        val allText = if (kisaraShowItemCountInTabs) "All ($allCount)" else "All"
                                        SubTabButton(
                                            text = allText,
                                            selected = activeSubcategoryIdOfActivePage == null,
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
                                        subcategories.forEach { sub ->
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
                                                onLongClick = { editCategory(sub) },
                                            ) {
                                                if (showParentFilters) {
                                                    activeSubcategoryId = sub.id
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

                                // Parent Categories Row (with Pin on the right)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    tabCategories.forEachIndexed { index, category ->
                                        val count = remember(category, state, kisaraShowItemCountInTabs) {
                                            if (kisaraShowItemCountInTabs) {
                                                val childCount = childrenByParent[category.id].orEmpty().sumOf { state.getItemCountForCategory(it, force = true)?.toInt() ?: 0 }
                                                val parentCount = state.getItemCountForCategory(category, force = true)?.toInt() ?: 0
                                                childCount + parentCount
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
                                            onLongClick = { editCategory(category) },
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

                                    // Pin button on the right (tiny & zero padding space)
                                    androidx.compose.material3.Icon(
                                        imageVector = if (isCategoryBarPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "Pin category bar",
                                        modifier = Modifier
                                            .size(12.dp)
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
                    } else {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Small attached Pin button above category bar
                            GlassSurface(
                                modifier = Modifier.padding(end = 12.dp),
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                style = GlassDefaults.subtleStyle(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                categoryBarPinnedPref.set(!isCategoryBarPinned)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = if (isCategoryBarPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "Pin category bar",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isCategoryBarPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Main Category Bar Surface
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                style = GlassDefaults.prominentStyle(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        val contentColor = LocalContentColor.current
                                        if (subcategories.isNotEmpty()) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                item {
                                                    FilterChip(
                                                        selected = activeSubcategoryIdOfActivePage == null,
                                                        onClick = {
                                                            if (showParentFilters) {
                                                                activeSubcategoryId = null
                                                            } else {
                                                                activeParent?.let { parent ->
                                                                    val actualIndex = state.categories.indexOfFirst { it.id == parent.id }
                                                                    if (actualIndex != -1) {
                                                                        screenModel.updateActiveCategoryIndex(actualIndex)
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        label = {
                                                            val contentColor = LocalContentColor.current
                                                            val allText = remember(activeParent, kisaraShowItemCountInTabs, state, contentColor) {
                                                                if (kisaraShowItemCountInTabs && activeParent != null) {
                                                                    val count = state.getItemCountForCategory(activeParent, force = true)
                                                                    if (count != null) {
                                                                        buildAnnotatedString {
                                                                            append("All")
                                                                            withStyle(style = SpanStyle(fontSize = 7.sp, color = contentColor.copy(alpha = 0.54f))) {
                                                                                append(" ($count)")
                                                                            }
                                                                        }
                                                                    } else {
                                                                        AnnotatedString("All")
                                                                    }
                                                                } else {
                                                                    AnnotatedString("All")
                                                                }
                                                            }
                                                            Text(text = allText, fontSize = 9.sp)
                                                        },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            containerColor = Color.Transparent,
                                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                                            selectedLabelColor = categorySelectedLabelColor,
                                                        ),
                                                        border = null,
                                                    )
                                                }
                                                items(subcategories) { sub ->
                                                    val isSelected = activeSubcategoryIdOfActivePage == sub.id
                                                    val visualName = sub.visualName
                                                    val subTitleText = remember(sub, visualName, kisaraShowItemCountInTabs, state, contentColor) {
                                                        if (kisaraShowItemCountInTabs) {
                                                            val count = state.getItemCountForCategory(sub, force = true)
                                                            if (count != null) {
                                                                buildAnnotatedString {
                                                                    append(visualName)
                                                                    withStyle(style = SpanStyle(fontSize = 7.sp, color = contentColor.copy(alpha = 0.54f))) {
                                                                        append(" ($count)")
                                                                    }
                                                                }
                                                            } else {
                                                                AnnotatedString(visualName)
                                                            }
                                                        } else {
                                                            AnnotatedString(visualName)
                                                        }
                                                    }
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            if (showParentFilters) {
                                                                activeSubcategoryId = sub.id
                                                            } else {
                                                                val actualIndex = state.categories.indexOfFirst { it.id == sub.id }
                                                                if (actualIndex != -1) {
                                                                    screenModel.updateActiveCategoryIndex(actualIndex)
                                                                }
                                                            }
                                                        },
                                                        label = {
                                                            Text(text = subTitleText, fontSize = 9.sp)
                                                        },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            containerColor = Color.Transparent,
                                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                                            selectedLabelColor = categorySelectedLabelColor,
                                                        ),
                                                        border = null,
                                                    )
                                                }
                                            }
                                        }

                                        // Main Categories
                                        LazyRow(
                                            state = categoryRowState,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            itemsIndexed(tabCategories) { idx, cat ->
                                                val isSelected = state.activeCategoryIndex == idx
                                                val scale by animateFloatAsState(
                                                    targetValue = if (categoryBarCarouselStyle && isSelected) 1.2f else 1.0f,
                                                    label = "scaleAnimation",
                                                )
                                                val contentColor = LocalContentColor.current
                                                val visualName = cat.visualName
                                                val catTitleText = remember(cat, visualName, kisaraShowItemCountInTabs, state, contentColor) {
                                                    if (kisaraShowItemCountInTabs) {
                                                        val count = state.getItemCountForCategory(cat, force = true)
                                                        if (count != null) {
                                                            buildAnnotatedString {
                                                                append(visualName)
                                                                withStyle(style = SpanStyle(fontSize = 7.sp, color = contentColor.copy(alpha = 0.54f))) {
                                                                    append(" ($count)")
                                                                }
                                                            }
                                                        } else {
                                                            AnnotatedString(visualName)
                                                        }
                                                    } else {
                                                        AnnotatedString(visualName)
                                                    }
                                                }
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        screenModel.updateActiveCategoryIndex(idx)
                                                        if (showParentFilters) {
                                                            activeSubcategoryId = null
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            text = catTitleText,
                                                            fontSize = 10.sp,
                                                            modifier = if (categoryBarCarouselStyle) Modifier.graphicsLayer(scaleX = scale, scaleY = scale) else Modifier,
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(24.dp)
                                                        .then(
                                                            if (categoryBarCarouselStyle && isSelected) {
                                                                Modifier.padding(horizontal = 6.dp)
                                                            } else {
                                                                Modifier
                                                            },
                                                        ),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        containerColor = Color.Transparent,
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                                        selectedLabelColor = categorySelectedLabelColor,
                                                    ),
                                                    border = null,
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

            val onDismissRequest = screenModel::closeDialog
            when (val dialog = state.dialog) {
                is LibraryScreenModel.Dialog.SettingsSheet -> run {
                    LibrarySettingsDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        category = state.activeCategory,
                        // SY -->
                        hasCategories = state.libraryData.categories.fastAny { !it.isSystemCategory },
                        // SY <--
                        // KMK -->
                        categories = state.libraryData.categories.filterNot(Category::isSystemCategory),
                        // KMK <--
                    )
                }
                is LibraryScreenModel.Dialog.ChangeCategory -> {
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
                status = screenModel.favoritesSync.status.collectAsState().value,
                setStatusIdle = { screenModel.favoritesSync.status.value = FavoritesSyncStatus.Idle },
                openManga = { navigator.push(MangaScreen(it)) },
            )

            RecommendationSearchProgressDialog(
                status = screenModel.recommendationSearch.status.collectAsState().value,
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

            LaunchedEffect(state.selectionMode, state.dialog) {
                HomeScreen.showBottomNav(!state.selectionMode)
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
            val recSearchState by screenModel.recommendationSearch.status.collectAsState()
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

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}

@Composable
private fun SubTabButton(
    text: String,
    selected: Boolean,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val subBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.subBarHeight().collectAsState().value
    val fontSize = (subBarHeight * 0.35f).coerceIn(6f, 14f).sp
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(subBarHeight.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
            )
        }
    }
}
