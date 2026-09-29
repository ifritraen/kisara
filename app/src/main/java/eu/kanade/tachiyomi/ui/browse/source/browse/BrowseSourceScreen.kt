package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.components.BrowseSourceActionsSheet
import eu.kanade.presentation.browse.components.BrowseSourceBulkActionDock
import eu.kanade.presentation.browse.components.BrowseSourceFloatingDock
import eu.kanade.presentation.browse.components.BrowseSourceSearchSheet
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.browse.components.SavedSearchCreateDialog
import eu.kanade.presentation.browse.components.SavedSearchDeleteDialog
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.browse.components.SourcePickerBottomSheet
import eu.kanade.presentation.browse.components.SourcePickerItem
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.more.settings.screen.SettingsEhScreen
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.source.ExhPreferences
import exh.source.anyIs
import exh.source.isEhBasedSource
import exh.source.isMdBasedSource
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    // SY -->
    private val filtersJson: String? = null,
    private val savedSearch: Long? = null,
    /** being set when called from [SmartSearchScreen] or when click on a manga from this screen
     * which was previously opened from `SmartSearchScreen` */
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
    // SY <--
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel {
            BrowseSourceScreenModel(
                sourceId = sourceId,
                listingQuery = listingQuery,
                // SY -->
                filtersJson = filtersJson,
                savedSearch = savedSearch,
                // SY <--
            )
        }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        val hazeState = remember { HazeState() }
        val frostedGlass by Injekt.get<UiPreferences>().kisaraFrostedGlass().collectAsState()

        // SY -->
        val context = LocalContext.current
        // SY <--

        // KMK -->
        screenModel.source.let {
            // KMK <--
            if (it is StubSource) {
                MissingSourceScreen(
                    source = it,
                    navigateUp = navigateUp,
                )
                return
            }
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        var showActionsSheet by remember { mutableStateOf(false) }
        var showSearchSheet by remember { mutableStateOf(false) }
        var showSourcePickerSheet by remember { mutableStateOf(false) }

        val onHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.baseUrl
        }

        // KMK -->
        val mangaList = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()

        val isHentaiEnabled: Boolean = Injekt.get<ExhPreferences>().isHentaiEnabled().get()
        val isConfigurableSource = screenModel.source.anyIs<ConfigurableSource>() ||
            (screenModel.source.isEhBasedSource() && isHentaiEnabled)
        // KMK <--

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Scaffold(
                modifier = Modifier.then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { paddingValues ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BrowseSourceContent(
                        source = screenModel.source,
                        mangaList = mangaList,
                        columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                        // SY -->
                        ehentaiBrowseDisplayMode = screenModel.ehentaiBrowseDisplayMode,
                        // SY <--
                        displayMode = screenModel.displayMode,
                        snackbarHostState = snackbarHostState,
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 84.dp,
                        ),
                        onWebViewClick = onWebViewClick,
                        onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                        onLocalSourceHelpClick = onHelpClick,
                        onMangaClick = { manga ->
                            // KMK -->
                            if (bulkFavoriteState.selectionMode) {
                                bulkFavoriteScreenModel.toggleSelection(manga)
                            } else {
                                // KMK <--
                                navigator.push(
                                    MangaScreen(
                                        mangaId = manga.id,
                                        // KMK -->
                                        // Finding the entry to be merged to, so we don't want to expand description
                                        // so that user can see the `Merge to another` button
                                        fromSource = smartSearchConfig == null,
                                        // KMK <--
                                        smartSearchConfig = smartSearchConfig,
                                    ),
                                )
                            }
                        },
                        onMangaLongClick = { manga ->
                            // KMK -->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (bulkFavoriteState.selectionMode) {
                                navigator.push(MangaScreen(manga.id, true))
                            } else {
                                // KMK <--
                                scope.launchIO {
                                    val duplicates = screenModel.getDuplicateLibraryManga(manga)
                                    when {
                                        manga.favorite -> {
                                            val categories = screenModel.getCategories()
                                            val preselectedIds = screenModel.getCategories.await(manga.id).map { it.id }
                                            screenModel.setDialog(
                                                BrowseSourceScreenModel.Dialog.ChangeMangaCategory(
                                                    manga,
                                                    categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                                                ),
                                            )
                                        }
                                        duplicates.isNotEmpty() -> screenModel.setDialog(
                                            BrowseSourceScreenModel.Dialog.AddDuplicateManga(manga, duplicates),
                                        )
                                        else -> screenModel.addFavorite(manga)
                                    }
                                }
                            }
                        },
                        // KMK -->
                        selection = bulkFavoriteState.selection,
                        // KMK <--
                    )

                    // Floating Dock Overlay at bottom
                    if (bulkFavoriteState.selectionMode) {
                        BrowseSourceBulkActionDock(
                            selectedCount = bulkFavoriteState.selection.size,
                            isRunning = bulkFavoriteState.isRunning,
                            onSelectAll = {
                                mangaList.itemSnapshotList.items
                                    .map { it.value.first }
                                    .forEach { bulkFavoriteScreenModel.select(it) }
                            },
                            onReverseSelection = {
                                mangaList.itemSnapshotList.items
                                    .map { it.value.first }
                                    .let { bulkFavoriteScreenModel.reverseSelection(it) }
                            },
                            onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                            onClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                        )
                    } else {
                        val savedSearchesPairs = remember(state.savedSearches) {
                            state.savedSearches.map { it.id to it.name }
                        }
                        val activeSavedSearchId = (state.listing as? Listing.Search)?.savedSearchId

                        BrowseSourceFloatingDock(
                            sourceName = screenModel.source.name,
                            isPopularSelected = state.listing == Listing.Popular,
                            isLatestSelected = state.listing == Listing.Latest,
                            isFilterSelected = state.listing is Listing.Search && activeSavedSearchId == null,
                            supportsLatest = (screenModel.source as? CatalogueSource)?.supportsLatest == true,
                            filterable = state.filterable,
                            filtersCount = state.filters.size,
                            savedSearches = savedSearchesPairs,
                            activeSavedSearchId = activeSavedSearchId,
                            onPopularClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            onLatestClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Latest)
                            },
                            onFilterClick = screenModel::openFilterSheet,
                            onSavedSearchClick = { id ->
                                val saved = state.savedSearches.find { it.id == id }
                                if (saved != null) {
                                    screenModel.onSavedSearch(saved) { context.toast(it) }
                                }
                            },
                            onSavedSearchLongClick = { id, _ ->
                                val saved = state.savedSearches.find { it.id == id }
                                if (saved != null) {
                                    screenModel.onSavedSearchPress(saved)
                                }
                            },
                            onSourceSwitchClick = { showSourcePickerSheet = true },
                            onActionsMenuClick = { showActionsSheet = true },
                            sourceIcon = {
                                SourceIcon(
                                    source = tachiyomi.domain.source.model.Source(
                                        id = screenModel.source.id,
                                        lang = screenModel.source.lang,
                                        name = screenModel.source.name,
                                        supportsLatest = (screenModel.source as? CatalogueSource)?.supportsLatest == true,
                                        isStub = false,
                                    ),
                                    modifier = Modifier.size(28.dp),
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                        )
                    }
                }
            }

            // Bottom Sheets
            if (showActionsSheet) {
                BrowseSourceActionsSheet(
                    sourceName = screenModel.source.name,
                    displayMode = screenModel.displayMode,
                    isHttpSource = screenModel.source is HttpSource,
                    isConfigurableSource = isConfigurableSource,
                    isIncognito = screenModel.incognitoMode.value,
                    onDismissRequest = { showActionsSheet = false },
                    onSearchClick = { showSearchSheet = true },
                    onDisplayModeClick = {
                        screenModel.displayMode = when (screenModel.displayMode) {
                            LibraryDisplayMode.CompactGrid -> LibraryDisplayMode.ComfortableGrid
                            LibraryDisplayMode.ComfortableGrid -> LibraryDisplayMode.List
                            LibraryDisplayMode.List -> LibraryDisplayMode.CoverOnlyGrid
                            else -> LibraryDisplayMode.CompactGrid
                        }
                    },
                    onToggleBulkSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onWebViewClick = onWebViewClick,
                    onSettingsClick = {
                        when {
                            screenModel.source.isEhBasedSource() && isHentaiEnabled -> navigator.push(SettingsEhScreen)
                            screenModel.source.anyIs<ConfigurableSource>() -> navigator.push(SourcePreferencesScreen(sourceId))
                            else -> {}
                        }
                    },
                    onToggleIncognito = screenModel::toggleIncognitoMode,
                    onHelpClick = onHelpClick,
                )
            }

            if (showSearchSheet) {
                val savedSearchesPairs = remember(state.savedSearches) {
                    state.savedSearches.map { it.id to it.name }
                }
                BrowseSourceSearchSheet(
                    sourceName = screenModel.source.name,
                    initialQuery = state.toolbarQuery,
                    savedSearches = savedSearchesPairs,
                    onDismissRequest = { showSearchSheet = false },
                    onSearch = { query ->
                        screenModel.setToolbarQuery(query)
                        screenModel.search(query = query)
                    },
                    onSelectSavedSearch = { id ->
                        val saved = state.savedSearches.find { it.id == id }
                        if (saved != null) {
                            screenModel.onSavedSearch(saved) { context.toast(it) }
                        }
                    },
                )
            }

            if (showSourcePickerSheet) {
                val sourceManager = remember { Injekt.get<SourceManager>() }
                val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
                val pinnedSources by sourcePreferences.pinnedSources().collectAsState()
                val availableSources = remember(sourceManager, pinnedSources) {
                    sourceManager.getCatalogueSources().map { s ->
                        SourcePickerItem(
                            id = s.id,
                            name = s.name,
                            lang = s.lang,
                            isPinned = s.id.toString() in pinnedSources,
                        )
                    }
                }
                SourcePickerBottomSheet(
                    currentSourceId = sourceId,
                    sources = availableSources,
                    onDismissRequest = { showSourcePickerSheet = false },
                    onSelectSource = { newSourceId ->
                        navigator.replace(
                            BrowseSourceScreen(
                                sourceId = newSourceId,
                                listingQuery = null,
                                smartSearchConfig = smartSearchConfig,
                            ),
                        )
                    },
                )
            }

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is BrowseSourceScreenModel.Dialog.Filter -> {
                    SourceFilterDialog(
                        onDismissRequest = onDismissRequest,
                        filters = state.filters,
                        onReset = screenModel::resetFilters,
                        onFilter = { screenModel.search(filters = state.filters) },
                        onUpdate = screenModel::setFilters,
                        // SY -->
                        startExpanded = screenModel.startExpanded,
                        onSave = screenModel::onSaveSearch,
                        savedSearches = state.savedSearches,
                        onSavedSearch = { search ->
                            screenModel.onSavedSearch(search) {
                                context.toast(it)
                            }
                        },
                        onSavedSearchPress = screenModel::onSavedSearchPress,
                        // KMK -->
                        onSavedSearchPressDesc = stringResource(KMR.strings.saved_searches_delete),
                        // KMK <--
                        openMangaDexRandom = if (screenModel.source.isMdBasedSource()) {
                            {
                                screenModel.onMangaDexRandom {
                                    navigator.replace(
                                        BrowseSourceScreen(
                                            sourceId,
                                            "id:$it",
                                        ),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        openMangaDexFollows = if (screenModel.source.isMdBasedSource()) {
                            {
                                // KMK -->
                                // navigator.replace(MangaDexFollowsScreen(sourceId))
                                navigator.push(MangaDexFollowsScreen(sourceId))
                                // KMK <--
                            }
                        } else {
                            null
                        },
                        // SY <--
                    )
                }
                is BrowseSourceScreenModel.Dialog.AddDuplicateManga -> {
                    DuplicateMangaDialog(
                        duplicates = dialog.duplicates,
                        onDismissRequest = onDismissRequest,
                        onConfirm = { screenModel.addFavorite(dialog.manga) },
                        onOpenManga = { navigator.push(MangaScreen(it.id)) },
                        onMigrate = { screenModel.setDialog(BrowseSourceScreenModel.Dialog.Migrate(dialog.manga, it)) },
                        // KMK -->
                        targetManga = dialog.manga,
                        // KMK <--
                    )
                }

                is BrowseSourceScreenModel.Dialog.Migrate -> {
                    MigrateMangaDialog(
                        current = dialog.current,
                        target = dialog.target,
                        // Initiated from the context of [dialog.target] so we show [dialog.current].
                        onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                        onDismissRequest = onDismissRequest,
                    )
                }
                is BrowseSourceScreenModel.Dialog.RemoveManga -> {
                    RemoveMangaDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = {
                            screenModel.changeMangaFavorite(dialog.manga)
                        },
                        mangaToRemove = dialog.manga,
                    )
                }
                is BrowseSourceScreenModel.Dialog.ChangeMangaCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = { navigator.push(CategoryScreen()) },
                        onConfirm = { include, _ ->
                            screenModel.changeMangaFavorite(dialog.manga)
                            screenModel.moveMangaToCategories(dialog.manga, include)
                        },
                        onDuplicateCheck = {
                            onDismissRequest()
                            navigator.push(DuplicateMangaScreen(dialog.manga.id))
                        },
                        onDeleteManga = {
                            screenModel.changeMangaFavorite(dialog.manga)
                        },
                        manga = dialog.manga,
                    )
                }
                is BrowseSourceScreenModel.Dialog.CreateSavedSearch -> SavedSearchCreateDialog(
                    onDismissRequest = onDismissRequest,
                    currentSavedSearches = dialog.currentSavedSearches,
                    saveSearch = screenModel::saveSearch,
                )
                is BrowseSourceScreenModel.Dialog.DeleteSavedSearch -> SavedSearchDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    name = dialog.name,
                    deleteSavedSearch = {
                        screenModel.deleteSearch(dialog.idToDelete)
                    },
                )
                else -> {}
            }

            // KMK -->
            // Bulk-favorite actions only
            BulkFavoriteDialogs(
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                dialog = bulkFavoriteState.dialog,
            )
            // KMK <--

            LaunchedEffect(Unit) {
                queryEvent.receiveAsFlow()
                    .collectLatest {
                        when (it) {
                            is SearchType.Genre -> screenModel.searchGenre(it.txt)
                            is SearchType.Text -> screenModel.search(it.txt)
                        }
                    }
            }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
