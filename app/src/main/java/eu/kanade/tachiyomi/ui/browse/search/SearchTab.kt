package eu.kanade.tachiyomi.ui.browse.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.bulk.BulkSearchScreen
import eu.kanade.tachiyomi.ui.browse.bulk.BulkSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.search.components.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.browse.search.components.GlobalSearchLandingView
import eu.kanade.tachiyomi.ui.browse.search.components.SearchSourceFilterSheet
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
fun Screen.searchTab(
    mediaType: UiPreferences.MediaType = UiPreferences.MediaType.MANGA,
): TabContent {
    return TabContent(
        titleRes = KMR.strings.bulk_search,
        searchEnabled = false,
        content = { contentPadding, _ ->
            SearchTabContent(
                mediaType = mediaType,
                contentPadding = contentPadding,
            )
        },
    )
}

@Composable
private fun SearchTabContent(
    mediaType: UiPreferences.MediaType,
    contentPadding: PaddingValues,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val screenModel = rememberScreenModel { SearchScreenModel(mediaType = mediaType) }
    val state by screenModel.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        eu.kanade.tachiyomi.ui.browse.search.model.TagDictionary.initialize(context)
    }

    val tabTitles = persistentListOf(
        stringResource(KMR.strings.tab_global_search),
        stringResource(KMR.strings.tab_bulk_search),
        stringResource(KMR.strings.tab_advanced_search),
    )
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    var showSourceFilterSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Subsubtab Header Row
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageIndex ->
                when (pageIndex) {
                    // Page 0: Global Search
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            GlobalSearchToolbar(
                                searchQuery = state.searchQuery,
                                onChangeSearchQuery = screenModel::updateSearchQuery,
                                onSearch = screenModel::search,
                                onClickCloseSearch = { screenModel.updateSearchQuery("") },
                                onToggleFilter = { showSourceFilterSheet = true },
                            )

                            if (state.searchQuery.isBlank() && state.results.isEmpty()) {
                                GlobalSearchLandingView(
                                    recentSearches = state.recentSearches,
                                    onSelectQuery = { query ->
                                        screenModel.updateSearchQuery(query)
                                        screenModel.search(query)
                                    },
                                    onOpenCategory = { category ->
                                        scope.launch { pagerState.animateScrollToPage(2) } // Jump to Advanced
                                    },
                                    onClearRecentSearches = screenModel::clearRecentSearches,
                                )
                            } else {
                                // Multi-Source Results
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                ) {
                                    items(state.results.entries.toList(), key = { it.key.id }) { (source, result) ->
                                        when (result) {
                                            is SearchItemResult.Loading -> {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(text = source.name, style = MaterialTheme.typography.titleMedium)
                                                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                                                }
                                            }
                                            is SearchItemResult.Success -> {
                                                GlobalSearchCardRow(
                                                    titles = result.list,
                                                    source = source,
                                                    onClick = { navigator.push(MangaScreen(it.id, true)) },
                                                    onLongClick = { navigator.push(MangaScreen(it.id, true)) },
                                                )
                                            }
                                            is SearchItemResult.Error -> {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(text = source.name, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        text = "Error: ${result.throwable.message ?: "Failed"}",
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Page 1: Bulk Search
                    1 -> {
                        val bulkScreenModel = rememberScreenModel {
                            val enabledSourceIds = state.availableSources.filter { it.isEnabled }.map { it.id }
                            BulkSearchScreenModel(sourceIds = enabledSourceIds, queries = emptyList())
                        }
                        BulkSearchScreen(
                            sourceIds = state.availableSources.filter { it.isEnabled }.map { it.id },
                            queries = emptyList(),
                        ).Content()
                    }

                    // Page 2: Advanced Search
                    2 -> {
                        AdvancedSearchScreen(
                            state = state.advancedState,
                            onStateChange = screenModel::updateAdvancedState,
                            onExecuteSearch = { compiledQuery ->
                                screenModel.updateSearchQuery(compiledQuery)
                                screenModel.search(compiledQuery)
                                scope.launch { pagerState.animateScrollToPage(0) } // Jump to Global results
                            },
                            onResetFilters = screenModel::resetAdvancedFilters,
                        )
                    }
                }
            }
        }

        // Floating 3-Dot Options Button (Toggle Sources)
        FloatingActionButton(
            onClick = { showSourceFilterSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = "Search Sources")
        }
    }

    // Source Filter Bottom Sheet
    if (showSourceFilterSheet) {
        SearchSourceFilterSheet(
            availableSources = state.availableSources,
            onToggleSource = screenModel::toggleSource,
            onSelectAll = screenModel::selectAllSources,
            onDeselectAll = screenModel::deselectAllSources,
            onDismissRequest = { showSourceFilterSheet = false },
        )
    }
}
// KMK <--
