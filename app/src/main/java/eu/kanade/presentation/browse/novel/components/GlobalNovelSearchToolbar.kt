package eu.kanade.presentation.browse.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSourceFilter
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalNovelSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    sourceFilter: NovelSourceFilter,
    onChangeSearchFilter: (NovelSourceFilter) -> Unit,
    languageFilter: ImmutableSet<String>,
    availableLanguages: ImmutableSet<String>,
    onChangeLanguageFilter: (Set<String>) -> Unit,
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
                selected = sourceFilter == NovelSourceFilter.PinnedOnly,
                onClick = {
                    val newFilter = if (sourceFilter == NovelSourceFilter.PinnedOnly) {
                        NovelSourceFilter.All
                    } else {
                        NovelSourceFilter.PinnedOnly
                    }
                    onChangeSearchFilter(newFilter)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
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
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
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

            // Language Filter Chip
            if (availableLanguages.isNotEmpty()) {
                var languagesExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = languageFilter.isNotEmpty(),
                        onClick = { languagesExpanded = true },
                        label = {
                            Text(
                                if (languageFilter.isEmpty()) {
                                    "Languages (All)"
                                } else {
                                    "Languages (${languageFilter.size})"
                                },
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = languagesExpanded,
                        onDismissRequest = { languagesExpanded = false },
                        modifier = Modifier.heightIn(max = 240.dp),
                    ) {
                        availableLanguages.forEach { lang ->
                            val isSelected = languageFilter.contains(lang)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${if (isSelected) "✓ " else ""}$lang",
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    val newSet = if (isSelected) {
                                        languageFilter - lang
                                    } else {
                                        languageFilter + lang
                                    }
                                    onChangeLanguageFilter(newSet)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            searchQuery = searchQuery,
            onChangeSearchQuery = onChangeSearchQuery,
            onSearch = onSearch,
            onDismissRequest = { showSearchSheet = false },
            title = "Global Novel Search",
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

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                navigateUp = navigateUp,
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
                    progress = { progress / total.toFloat() },
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
                selected = sourceFilter == NovelSourceFilter.PinnedOnly,
                onClick = {
                    val newFilter = if (sourceFilter == NovelSourceFilter.PinnedOnly) {
                        NovelSourceFilter.All
                    } else {
                        NovelSourceFilter.PinnedOnly
                    }
                    onChangeSearchFilter(newFilter)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
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
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
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
