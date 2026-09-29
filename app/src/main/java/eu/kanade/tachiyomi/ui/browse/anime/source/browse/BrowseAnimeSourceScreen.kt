// KMK -->
package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.browse.anime.MissingSourceScreen
import eu.kanade.presentation.browse.anime.components.AnimeSourceIcon
import eu.kanade.presentation.browse.components.BrowseSourceActionsSheet
import eu.kanade.presentation.browse.components.BrowseSourceFloatingDock
import eu.kanade.presentation.browse.components.BrowseSourceSearchSheet
import eu.kanade.presentation.browse.components.SourcePickerBottomSheet
import eu.kanade.presentation.browse.components.SourcePickerItem
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseAnimeSourceScreen(
    val sourceId: Long,
    private val listingQuery: String? = null,
    private val savedSearchId: Long? = null,
    private val parentScreen: cafe.adriel.voyager.core.screen.Screen? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = if (parentScreen != null) {
            parentScreen.rememberScreenModel(tag = sourceId.toString()) {
                BrowseAnimeSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        } else {
            rememberScreenModel {
                BrowseAnimeSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val favoriteAnimeUrls by screenModel.favoriteAnimeUrls.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubAnimeSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val snackbarHostState = remember { SnackbarHostState() }

        var showActionsSheet by remember { mutableStateOf(false) }
        var showSearchSheet by remember { mutableStateOf(false) }
        var showSourcePickerSheet by remember { mutableStateOf(false) }

        val onWebViewClick = f@{
            val source = screenModel.source as? AnimeHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

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

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingAnime = screenModel.animePagerFlowFlow.collectAsLazyPagingItems()
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                BrowseAnimeSourceContent(
                    source = screenModel.source,
                    animeList = pagingAnime,
                    favoriteAnimeUrls = favoriteAnimeUrls,
                    columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                    entries = screenModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
                    displayMode = screenModel.displayMode,
                    snackbarHostState = snackbarHostState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 84.dp,
                    ),
                    onWebViewClick = onWebViewClick,
                    onHelpClick = {},
                    onLocalAnimeSourceHelpClick = {},
                    onAnimeClick = { anime ->
                        navigator.push(AnimeScreen(anime.id, true))
                    },
                    onAnimeLongClick = { anime ->
                        scope.launchIO {
                            val duplicateAnime = screenModel.getDuplicateAnimelibAnime(anime)
                            when {
                                anime.favorite -> {
                                    val categories = screenModel.getCategories()
                                    val preselectedIds = screenModel.getAnimeCategoryIds(anime.id)
                                    screenModel.setDialog(
                                        BrowseAnimeSourceScreenModel.Dialog.ChangeAnimeCategory(
                                            anime,
                                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                                        ),
                                    )
                                }
                                duplicateAnime != null -> screenModel.setDialog(
                                    BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime(anime, duplicateAnime),
                                )
                                else -> screenModel.addFavorite(anime)
                            }
                        }
                    },
                )

                val savedSearchesPairs = remember(state.savedSearches) {
                    state.savedSearches.map { it.first.id to it.first.name }
                }
                val activeSavedSearchId = state.savedSearches.find { it.second }?.first?.id

                BrowseSourceFloatingDock(
                    sourceName = screenModel.source.name,
                    isPopularSelected = state.listing == BrowseAnimeSourceScreenModel.Listing.Popular,
                    isLatestSelected = state.listing == BrowseAnimeSourceScreenModel.Listing.Latest,
                    isFilterSelected = state.listing is BrowseAnimeSourceScreenModel.Listing.Search && activeSavedSearchId == null,
                    supportsLatest = (screenModel.source as? AnimeCatalogueSource)?.supportsLatest == true,
                    filterable = state.filters.isNotEmpty(),
                    filtersCount = state.filters.size,
                    savedSearches = savedSearchesPairs,
                    activeSavedSearchId = activeSavedSearchId,
                    onPopularClick = {
                        screenModel.resetFilters()
                        screenModel.setListing(BrowseAnimeSourceScreenModel.Listing.Popular)
                    },
                    onLatestClick = {
                        screenModel.resetFilters()
                        screenModel.setListing(BrowseAnimeSourceScreenModel.Listing.Latest)
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
                            screenModel.setDialog(BrowseAnimeSourceScreenModel.Dialog.DeleteSavedSearch(saved))
                        }
                    },
                    onSourceSwitchClick = { showSourcePickerSheet = true },
                    onActionsMenuClick = { showActionsSheet = true },
                    sourceIcon = {
                        AnimeSourceIcon(
                            source = tachiyomi.domain.source.anime.model.AnimeSource(
                                id = screenModel.source.id,
                                lang = screenModel.source.lang,
                                name = screenModel.source.name,
                                supportsLatest = (screenModel.source as? eu.kanade.tachiyomi.animesource.AnimeCatalogueSource)?.supportsLatest ?: false,
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
            BrowseSourceActionsSheet(
                sourceName = screenModel.source.name,
                displayMode = screenModel.displayMode,
                isHttpSource = screenModel.source is AnimeHttpSource,
                isConfigurableSource = true,
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
                onWebViewClick = onWebViewClick,
                onSettingsClick = { navigator.push(AnimeSourcePreferencesScreen(sourceId)) },
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
            val sourceManager = remember { Injekt.get<AnimeSourceManager>() }
            val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
            val pinnedSources by sourcePreferences.pinnedAnimeSources().collectAsState()
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
                        BrowseAnimeSourceScreen(
                            sourceId = newSourceId,
                            listingQuery = null,
                        ),
                    )
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseAnimeSourceScreenModel.Dialog.Filter -> {
                SourceFilterAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    savedSearches = screenModel.state.value.savedSearches,
                    onSaveSearch = screenModel::openSaveSearchDialog,
                    onOpenSavedSearch = screenModel::openSavedSearch,
                    onDeleteSavedSearch = {
                        screenModel.setDialog(BrowseAnimeSourceScreenModel.Dialog.DeleteSavedSearch(it))
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime -> {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    title = { Text(text = stringResource(MR.strings.are_you_sure)) },
                    text = { Text(text = stringResource(KMR.strings.confirm_add_duplicate_manga, dialog.anime.title)) },
                    dismissButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDismissRequest()
                                screenModel.addFavorite(dialog.anime)
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_add))
                        }
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.RemoveAnime -> {
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
                                screenModel.changeAnimeFavorite(dialog.anime)
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_remove))
                        }
                    },
                    title = {
                        Text(text = stringResource(MR.strings.are_you_sure))
                    },
                    text = {
                        Text(text = stringResource(MR.strings.remove_manga, dialog.anime.title))
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.CreateSavedSearch -> {
                CreateSavedSearchDialog(
                    onDismiss = onDismissRequest,
                    onSave = { name -> screenModel.saveSearch(name) },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.DeleteSavedSearch -> {
                DeleteSavedSearchDialog(
                    savedSearch = dialog.savedSearch,
                    onDismiss = onDismissRequest,
                    onConfirm = { screenModel.deleteSearch(dialog.savedSearch) },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.ChangeAnimeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen())
                    },
                    onConfirm = { include, _ ->
                        screenModel.changeAnimeFavorite(dialog.anime)
                        screenModel.moveAnimeToCategories(dialog.anime, include)
                    },
                    onDuplicateCheck = {
                        onDismissRequest()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen(dialog.anime.id))
                    },
                    onDelete = {
                        screenModel.changeAnimeFavorite(dialog.anime)
                    }.takeIf { dialog.anime.favorite },
                    anime = dialog.anime,
                )
            }
            // Migrate dialog not yet available in Kisara — skipped
            is BrowseAnimeSourceScreenModel.Dialog.Migrate -> {}
            else -> {}
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
// KMK <--
