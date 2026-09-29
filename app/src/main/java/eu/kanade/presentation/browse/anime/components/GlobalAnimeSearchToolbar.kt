package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchBottomSheet
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalAnimeSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    sourceFilter: AnimeSourceFilter,
    onChangeSearchFilter: (AnimeSourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    searchClean: Boolean = false,
    onToggleClean: () -> Unit = {},
    searchFormat: Int = 0,
    onToggleFormat: () -> Unit = {},
    searchFuzzy: Boolean = false,
    onToggleFuzzy: () -> Unit = {},
) {
    var showSearchSheet by remember { mutableStateOf(false) }

    val filterChipsRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            FilterChip(
                selected = sourceFilter == AnimeSourceFilter.PinnedOnly,
                onClick = {
                    val newFilter = if (sourceFilter == AnimeSourceFilter.PinnedOnly) {
                        AnimeSourceFilter.All
                    } else {
                        AnimeSourceFilter.PinnedOnly
                    }
                    onChangeSearchFilter(newFilter)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.pinned_sources))
                },
            )
            FilterChip(
                selected = onlyShowHasResults,
                onClick = onToggleResults,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DoneAll,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )
            FilterChip(
                selected = searchClean,
                onClick = onToggleClean,
                label = {
                    Text(text = "Clean")
                },
            )
            FilterChip(
                selected = searchFormat > 0,
                onClick = onToggleFormat,
                label = {
                    val formatLabel = when (searchFormat) {
                        1 -> "Fmt: S1"
                        2 -> "Fmt: None"
                        else -> "Format"
                    }
                    Text(text = formatLabel)
                },
            )
            FilterChip(
                selected = searchFuzzy,
                onClick = onToggleFuzzy,
                label = {
                    Text(text = "Fuzzy")
                },
            )
        }
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            searchQuery = searchQuery,
            onChangeSearchQuery = onChangeSearchQuery,
            onSearch = onSearch,
            onDismissRequest = { showSearchSheet = false },
            title = "Global Anime Search",
            placeholderText = stringResource(MR.strings.action_global_search_hint),
            searchClean = searchClean,
            onToggleClean = onToggleClean,
            searchFormat = searchFormat,
            onToggleFormat = onToggleFormat,
            searchFuzzy = searchFuzzy,
            onToggleFuzzy = onToggleFuzzy,
            filterContent = filterChipsRow,
        )
    }

    Column {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                scrollBehavior = scrollBehavior,
                placeholderText = stringResource(MR.strings.action_global_search_hint),
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = "Search Sheet",
                                icon = Icons.Outlined.Layers,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = { showSearchSheet = true },
                            ),
                        ),
                    )
                },
            )
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() / total },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            FilterChip(
                selected = sourceFilter == AnimeSourceFilter.PinnedOnly,
                onClick = {
                    val newFilter = if (sourceFilter == AnimeSourceFilter.PinnedOnly) {
                        AnimeSourceFilter.All
                    } else {
                        AnimeSourceFilter.PinnedOnly
                    }
                    onChangeSearchFilter(newFilter)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.pinned_sources))
                },
            )
            FilterChip(
                selected = onlyShowHasResults,
                onClick = onToggleResults,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DoneAll,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )
            FilterChip(
                selected = searchClean,
                onClick = onToggleClean,
                label = {
                    Text(text = "Clean")
                },
            )
            FilterChip(
                selected = searchFormat > 0,
                onClick = onToggleFormat,
                label = {
                    val formatLabel = when (searchFormat) {
                        1 -> "Fmt: S1"
                        2 -> "Fmt: None"
                        else -> "Format"
                    }
                    Text(text = formatLabel)
                },
            )
            FilterChip(
                selected = searchFuzzy,
                onClick = onToggleFuzzy,
                label = {
                    Text(text = "Fuzzy")
                },
            )
        }
        HorizontalDivider()
    }
}

@Composable
fun MinimalGlobalAnimeSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenSearchSheet: () -> Unit,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            AppBar(
                title = if (searchQuery.isNullOrBlank()) "Global Anime Search" else searchQuery,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = "Search Sheet",
                                icon = Icons.Outlined.Layers,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = onOpenSearchSheet,
                            ),
                        ),
                    )
                },
            )
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() / total },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
