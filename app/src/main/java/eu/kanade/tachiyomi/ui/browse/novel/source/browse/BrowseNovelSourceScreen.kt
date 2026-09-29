package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.BrowseSourceActionsSheet
import eu.kanade.presentation.browse.components.BrowseSourceFloatingDock
import eu.kanade.presentation.browse.components.BrowseSourceSearchSheet
import eu.kanade.presentation.browse.components.SourcePickerBottomSheet
import eu.kanade.presentation.browse.components.SourcePickerItem
import eu.kanade.presentation.browse.novel.BrowseNovelSourceContent
import eu.kanade.presentation.browse.novel.MissingNovelSourceScreen
import eu.kanade.presentation.browse.novel.components.NovelSourceIcon
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.novel.DuplicateNovelDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
data class BrowseNovelSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    private val savedSearchId: Long? = null,
    private val parentScreen: cafe.adriel.voyager.core.screen.Screen? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = if (parentScreen != null) {
            parentScreen.rememberScreenModel(tag = sourceId.toString()) {
                BrowseNovelSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        } else {
            rememberScreenModel {
                BrowseNovelSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val favoriteNovelUrls by screenModel.favoriteNovelUrls.collectAsStateWithLifecycle()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                        is SearchType.Genres -> screenModel.searchGenres(it.txts)
                    }
                }
        }

        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubNovelSource) {
            MissingNovelSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }
        val sourceWebUrl = resolveNovelSourceWebUrl(screenModel.source)

        var showActionsSheet by remember { mutableStateOf(false) }
        var showSearchSheet by remember { mutableStateOf(false) }
        var showSourcePickerSheet by remember { mutableStateOf(false) }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingNovels = screenModel.novelPagerFlowFlow.collectAsLazyPagingItems()
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                BrowseNovelSourceContent(
                    source = screenModel.source,
                    novels = pagingNovels,
                    favoriteNovelUrls = favoriteNovelUrls,
                    displayMode = screenModel.displayMode,
                    snackbarHostState = snackbarHostState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 84.dp,
                    ),
                    onNovelClick = { novel ->
                        navigator.push(NovelScreen(novel.id, true))
                    },
                    onNovelLongClick = { novel ->
                        scope.launchIO {
                            val duplicateNovel = screenModel.getDuplicateLibraryNovel(novel)
                            val isFavorite = novel.url in favoriteNovelUrls
                            when {
                                isFavorite -> {
                                    val categories = screenModel.getCategories()
                                    val preselectedIds = screenModel.getNovelCategoryIds(novel.id)
                                    screenModel.setDialog(
                                        BrowseNovelSourceScreenModel.Dialog.ChangeNovelCategory(
                                            novel = novel,
                                            initialSelection = categories
                                                .mapAsCheckboxState { it.id in preselectedIds }
                                                .toImmutableList(),
                                        ),
                                    )
                                }
                                duplicateNovel != null -> screenModel.setDialog(
                                    BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel(novel, duplicateNovel),
                                )
                                else -> screenModel.addFavorite(novel)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                )

                val savedSearchesPairs = remember(state.savedSearches) {
                    state.savedSearches.map { it.first.id to it.first.name }
                }
                val activeSavedSearchId = state.savedSearches.find { it.second }?.first?.id

                BrowseSourceFloatingDock(
                    sourceName = screenModel.source.name,
                    isPopularSelected = state.listing == BrowseNovelSourceScreenModel.Listing.Popular,
                    isLatestSelected = state.listing == BrowseNovelSourceScreenModel.Listing.Latest,
                    isFilterSelected = state.listing is BrowseNovelSourceScreenModel.Listing.Search && activeSavedSearchId == null,
                    supportsLatest = (screenModel.source as? NovelCatalogueSource)?.supportsLatest == true,
                    filterable = state.filters.isNotEmpty(),
                    filtersCount = state.filters.size,
                    savedSearches = savedSearchesPairs,
                    activeSavedSearchId = activeSavedSearchId,
                    onPopularClick = {
                        screenModel.resetFilters()
                        screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Popular)
                    },
                    onLatestClick = {
                        screenModel.resetFilters()
                        screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Latest)
                    },
                    onFilterClick = screenModel::openFilterSheet,
                    onSavedSearchClick = { id ->
                        val saved = state.savedSearches.find { it.first.id == id }?.first
                        if (saved != null) {
                            screenModel.openSavedSearch(saved)
                        }
                    },
                    onSavedSearchLongClick = { id, _ ->
                        val saved = state.savedSearches.find { it.first.id == id }?.first
                        if (saved != null) {
                            screenModel.setDialog(BrowseNovelSourceScreenModel.Dialog.DeleteSavedSearch(saved))
                        }
                    },
                    onSourceSwitchClick = { showSourcePickerSheet = true },
                    onActionsMenuClick = { showActionsSheet = true },
                    sourceIcon = {
                        NovelSourceIcon(
                            source = tachiyomi.domain.source.novel.model.Source(
                                id = screenModel.source.id,
                                lang = screenModel.source.lang,
                                name = screenModel.source.name,
                                supportsLatest = (screenModel.source as? eu.kanade.tachiyomi.novelsource.NovelCatalogueSource)?.supportsLatest ?: false,
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

        // Bottom Sheets
        if (showActionsSheet) {
            val onSettings = novelSourcePreferencesScreenOrNull(
                sourceId = sourceId,
                isSourceConfigurable = state.isSourceConfigurable,
            )?.let { screen ->
                { navigator.push(screen) }
            }
            val onWebView = sourceWebUrl?.let { url ->
                {
                    navigator.push(
                        WebViewScreen(
                            url = url,
                            initialTitle = screenModel.source.name,
                            sourceId = screenModel.source.id,
                        ),
                    )
                }
            }
            BrowseSourceActionsSheet(
                sourceName = screenModel.source.name,
                displayMode = screenModel.displayMode,
                isHttpSource = sourceWebUrl != null,
                isConfigurableSource = onSettings != null,
                isIncognito = false,
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
                onToggleBulkSelection = {},
                onWebViewClick = { onWebView?.invoke() },
                onSettingsClick = { onSettings?.invoke() },
                onToggleIncognito = {},
                onHelpClick = {},
            )
        }

        if (showSearchSheet) {
            val savedSearchesPairs = remember(state.savedSearches) {
                state.savedSearches.map { it.first.id to it.first.name }
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
                    val saved = state.savedSearches.find { it.first.id == id }?.first
                    if (saved != null) {
                        screenModel.openSavedSearch(saved)
                    }
                },
            )
        }

        if (showSourcePickerSheet) {
            val sourceManager = remember { Injekt.get<NovelSourceManager>() }
            val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
            val pinnedSources by sourcePreferences.pinnedNovelSources().collectAsState()
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
                        BrowseNovelSourceScreen(
                            sourceId = newSourceId,
                            listingQuery = null,
                        ),
                    )
                },
            )
        }

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                BrowseNovelSourceScreenModel.Dialog.Filter -> {
                    SourceFilterNovelDialog(
                        onDismissRequest = onDismissRequest,
                        filters = visibleNovelFiltersForListing(state.listing, state.filters),
                        onReset = screenModel::resetFilters,
                        onFilter = screenModel::applyFilters,
                        onUpdate = screenModel::setFilters,
                        savedSearches = screenModel.state.value.savedSearches,
                        onSaveSearch = screenModel::openSaveSearchDialog,
                        onOpenSavedSearch = screenModel::openSavedSearch,
                        onDeleteSavedSearch = {
                            screenModel.setDialog(BrowseNovelSourceScreenModel.Dialog.DeleteSavedSearch(it))
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel -> {
                    DuplicateNovelDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = { screenModel.addFavorite(dialog.novel) },
                        onOpenNovel = { navigator.push(NovelScreen(dialog.duplicate.id, true)) },
                        onMigrate = {
                            screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.Migrate(
                                    newNovel = dialog.novel,
                                    oldNovel = dialog.duplicate,
                                ),
                            )
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.Migrate -> {
                    MigrateNovelDialog(
                        oldNovel = dialog.oldNovel,
                        newNovel = dialog.newNovel,
                        screenModel = MigrateNovelDialogScreenModel(),
                        onDismissRequest = onDismissRequest,
                        onClickTitle = { navigator.push(NovelScreen(dialog.oldNovel.id)) },
                        onPopScreen = {
                            onDismissRequest()
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.RemoveNovel -> {
                    AlertDialog(
                        onDismissRequest = onDismissRequest,
                        dismissButton = {
                            TextButton(onClick = onDismissRequest) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    screenModel.changeNovelFavorite(dialog.novel)
                                },
                            ) {
                                Text(text = stringResource(MR.strings.action_remove))
                            }
                        },
                        title = {
                            Text(text = stringResource(MR.strings.are_you_sure))
                        },
                        text = {
                            Text(text = stringResource(MR.strings.remove_manga, dialog.novel.title))
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.CreateSavedSearch -> {
                    CreateSavedSearchDialog(
                        onDismiss = onDismissRequest,
                        onSave = { name -> screenModel.saveSearch(name) },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.DeleteSavedSearch -> {
                    DeleteSavedSearchDialog(
                        savedSearch = dialog.savedSearch,
                        onDismiss = onDismissRequest,
                        onConfirm = { screenModel.deleteSearch(dialog.savedSearch) },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.ChangeNovelCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = {
                            navigator.push(eu.kanade.tachiyomi.ui.category.novel.NovelCategoryScreen())
                        },
                        onConfirm = { include, _ ->
                            screenModel.changeNovelFavorite(dialog.novel)
                            screenModel.moveNovelToCategories(dialog.novel, include)
                        },
                        onDuplicateCheck = {
                            onDismissRequest()
                            navigator.push(eu.kanade.tachiyomi.ui.browse.novel.duplicate.DuplicateNovelScreen(dialog.novel.id))
                        },
                        onDelete = {
                            screenModel.changeNovelFavorite(dialog.novel)
                        }.takeIf { dialog.novel.favorite },
                        novel = dialog.novel,
                    )
                }
                null -> Unit
            }
        }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))
    suspend fun searchGenres(names: List<String>) {
        if (names.isNotEmpty()) {
            queryEvent.send(SearchType.Genres(names))
        }
    }

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed interface SearchType {
        data class Text(val txt: String) : SearchType
        data class Genre(val txt: String) : SearchType
        data class Genres(val txts: List<String>) : SearchType
    }
}

@Composable
private fun CreateSavedSearchDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.save_search)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(KMR.strings.saved_search_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(MR.strings.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun DeleteSavedSearchDialog(
    savedSearch: SavedSearch,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.saved_search_delete)) },
        text = { Text(stringResource(KMR.strings.saved_search_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(MR.strings.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

internal fun visibleNovelFiltersForListing(
    listing: BrowseNovelSourceScreenModel.Listing,
    filters: NovelFilterList,
): NovelFilterList {
    if (listing != BrowseNovelSourceScreenModel.Listing.Latest) return filters
    return NovelFilterList(filters.mapNotNull { it.withoutSortFiltersForLatest() })
}

private fun NovelFilter<*>.withoutSortFiltersForLatest(): NovelFilter<*>? {
    return when (this) {
        is NovelFilter.Sort -> null
        is NovelFilter.Group<*> -> {
            val visibleChildren = state
                .filterIsInstance<NovelFilter<*>>()
                .mapNotNull { it.withoutSortFiltersForLatest() }
            if (visibleChildren.isEmpty()) null else LatestVisibleGroupFilter(name, visibleChildren)
        }
        else -> this
    }
}

private class LatestVisibleGroupFilter(
    name: String,
    state: List<NovelFilter<*>>,
) : NovelFilter.Group<NovelFilter<*>>(name, state)

internal fun resolveNovelSourceWebUrl(source: NovelSource?): String? {
    val siteUrl = (source as? NovelSiteSource)?.siteUrl?.trim().orEmpty()
    if (siteUrl.isBlank()) return null

    val normalizedUrl = if (
        siteUrl.startsWith("http://", ignoreCase = true) ||
        siteUrl.startsWith("https://", ignoreCase = true)
    ) {
        siteUrl
    } else {
        "https://$siteUrl"
    }

    return normalizedUrl.toHttpUrlOrNull()?.toString()
}

internal fun novelSourcePreferencesScreenOrNull(
    sourceId: Long,
    isSourceConfigurable: Boolean,
): NovelSourcePreferencesScreen? {
    if (!isSourceConfigurable) return null
    return NovelSourcePreferencesScreen(sourceId)
}
