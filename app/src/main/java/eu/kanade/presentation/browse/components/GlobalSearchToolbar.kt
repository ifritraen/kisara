package eu.kanade.presentation.browse.components

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
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderSpecial
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collect
import tachiyomi.domain.suggestions.interactor.GetSuggestionArtists
import tachiyomi.domain.suggestions.interactor.GetSuggestionAuthors
import tachiyomi.domain.suggestions.interactor.GetSuggestionTags
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    isRunning: Boolean,
    hasPinnedSources: Boolean,
    searchClean: Boolean = false,
    onToggleClean: () -> Unit = {},
    searchFormat: Int = 0,
    onToggleFormat: () -> Unit = {},
    searchFuzzy: Boolean = false,
    onToggleFuzzy: () -> Unit = {},
    customGroups: List<tachiyomi.domain.source.model.CustomSearchGroup> = emptyList(),
    activeCustomGroupId: String = "",
    onSelectCustomGroup: (String) -> Unit = {},
    onOpenGroupManager: () -> Unit = {},
    // KMK <--
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                // KMK -->
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.Action(
                                title = if (searchClean) "Clean (ON)" else "Clean",
                                icon = Icons.Outlined.CleaningServices,
                                iconTint = if (searchClean) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onToggleClean,
                            ),
                            AppBar.Action(
                                title = when (searchFormat) {
                                    1 -> "Format: Key"
                                    2 -> "Format: Raw"
                                    else -> "Format"
                                },
                                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                iconTint = if (searchFormat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onToggleFormat,
                            ),
                            bulkSelectionButton(isRunning, toggleSelectionMode),
                        ),
                    )
                },
                // KMK <--
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
            // TODO: make this UX better; it only applies when triggering a new search
            if (!hideSourceFilter) {
                // KMK -->
                if (hasPinnedSources) {
                    // KMK <--
                    FilterChip(
                        selected = sourceFilter == SourceFilter.PinnedOnly,
                        onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(MR.strings.pinned_sources))
                        },
                    )
                }
                FilterChip(
                    selected = sourceFilter == SourceFilter.All,
                    onClick = { onChangeSearchFilter(SourceFilter.All) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.all))
                    },
                )

                // KMK -->
                var customGroupsExpanded by remember { mutableStateOf(false) }
                val sourcePreferences = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }
                val allSourceTags = remember { sourcePreferences.customSourceTags().get() }
                val activeGroup = remember(customGroups, activeCustomGroupId) {
                    customGroups.firstOrNull { it.id == activeCustomGroupId }
                }
                val activeTagName = remember(activeCustomGroupId) {
                    if (activeCustomGroupId.startsWith("tag_")) activeCustomGroupId.removePrefix("tag_") else null
                }
                Box {
                    FilterChip(
                        selected = sourceFilter == SourceFilter.Custom,
                        onClick = {
                            if (customGroups.isEmpty() && allSourceTags.isEmpty()) {
                                onOpenGroupManager()
                            } else {
                                customGroupsExpanded = true
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (activeTagName != null) Icons.Outlined.FilterList else Icons.Outlined.FolderSpecial,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(
                                text = if (sourceFilter == SourceFilter.Custom) {
                                    activeGroup?.name ?: (if (activeTagName != null) "Tag: $activeTagName" else stringResource(KMR.strings.custom_groups))
                                } else {
                                    stringResource(KMR.strings.custom_groups)
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
                        expanded = customGroupsExpanded,
                        onDismissRequest = { customGroupsExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        customGroups.forEach { group ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = group.name,
                                        fontWeight = if (sourceFilter == SourceFilter.Custom && group.id == activeCustomGroupId) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                                onClick = {
                                    onSelectCustomGroup(group.id)
                                    customGroupsExpanded = false
                                },
                            )
                        }
                        if (customGroups.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        if (allSourceTags.isNotEmpty()) {
                            allSourceTags.forEach { tag ->
                                val tagId = "tag_$tag"
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "🏷️ $tag",
                                            fontWeight = if (sourceFilter == SourceFilter.Custom && activeCustomGroupId == tagId) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
                                    onClick = {
                                        onSelectCustomGroup(tagId)
                                        customGroupsExpanded = false
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(KMR.strings.action_manage_custom_groups),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                customGroupsExpanded = false
                                onOpenGroupManager()
                            },
                        )
                    }
                }
                // KMK <--

                VerticalDivider()
            }

            FilterChip(
                selected = onlyShowHasResults,
                onClick = { onToggleResults() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )

            VerticalDivider()

            val favoriteManager = remember { Injekt.get<eu.kanade.tachiyomi.data.favorite.FavoriteManager>() }
            val appendToQuery: (String) -> Unit = { term ->
                val current = searchQuery ?: ""
                val newQuery = if (current.isEmpty()) term else "$current $term"
                onChangeSearchQuery(newQuery)
            }

            // Author & Artist Favorite Chip
            val authors = remember { favoriteManager.getAuthors() }
            val artists = remember { favoriteManager.getArtists() }
            val combinedAuthorsArtists = remember(authors, artists) { (authors + artists).distinct() }
            var authorsArtistsExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { authorsArtistsExpanded = true },
                    label = { Text("Author & Artist") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                DropdownMenu(
                    expanded = authorsArtistsExpanded,
                    onDismissRequest = { authorsArtistsExpanded = false },
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    if (combinedAuthorsArtists.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No favorite author/artist") },
                            onClick = { authorsArtistsExpanded = false },
                            enabled = false,
                        )
                    } else {
                        combinedAuthorsArtists.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    appendToQuery(name)
                                    authorsArtistsExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Tags Favorite Chip
            val favTags = remember { favoriteManager.getTags() }
            var tagsExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { tagsExpanded = true },
                    label = { Text("Tags") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                DropdownMenu(
                    expanded = tagsExpanded,
                    onDismissRequest = { tagsExpanded = false },
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    if (favTags.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No favorite tags") },
                            onClick = { tagsExpanded = false },
                            enabled = false,
                        )
                    } else {
                        favTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    appendToQuery(tag)
                                    tagsExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Language Chip
            val languagesList = remember {
                listOf(
                    "english", "spanish", "korean", "japanese", "chinese", "french", "german", "italian", "russian", "vietnamese", "portuguese",
                )
            }
            var languagesExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { languagesExpanded = true },
                    label = { Text("Language") },
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
                    languagesList.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                appendToQuery(lang)
                                languagesExpanded = false
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider()
    }
}
