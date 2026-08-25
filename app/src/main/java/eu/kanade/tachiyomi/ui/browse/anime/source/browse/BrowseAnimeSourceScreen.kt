// KMK -->
package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.browse.anime.MissingSourceScreen
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceToolbar
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
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

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
            topBar = {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseAnimeSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = {},
                        onSettingsClick = { navigator.push(AnimeSourcePreferencesScreen(sourceId)) },
                        onSearch = screenModel::search,
                        useAuroraAppBarActions = false,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == BrowseAnimeSourceScreenModel.Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(BrowseAnimeSourceScreenModel.Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as AnimeCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == BrowseAnimeSourceScreenModel.Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(BrowseAnimeSourceScreenModel.Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is BrowseAnimeSourceScreenModel.Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                        state.savedSearches.forEach { (search, isActive) ->
                            FilterChip(
                                selected = isActive,
                                onClick = { screenModel.openSavedSearch(search) },
                                label = { Text(text = search.name) },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingAnime = screenModel.animePagerFlowFlow.collectAsLazyPagingItems()
            BrowseAnimeSourceContent(
                source = screenModel.source,
                animeList = pagingAnime,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                entries = screenModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
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
                                BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime(
                                    anime,
                                    duplicateAnime,
                                ),
                            )
                            else -> screenModel.addFavorite(anime)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
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
