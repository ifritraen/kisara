package eu.kanade.tachiyomi.ui.browse.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.browse.search.model.AdvancedSearchState
import eu.kanade.tachiyomi.ui.browse.search.model.CanonicalTag
import eu.kanade.tachiyomi.ui.browse.search.model.SearchTaxonomies
import eu.kanade.tachiyomi.ui.browse.search.model.TagDictionary
import eu.kanade.tachiyomi.ui.browse.search.model.TagSelectionState

// KMK -->
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchScreen(
    state: AdvancedSearchState,
    onStateChange: (AdvancedSearchState) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTaxonomyTab by remember { mutableIntStateOf(0) }
    val taxonomyCategories = remember { SearchTaxonomies.TAG_TAXONOMIES.keys.toList() }
    val currentCategory = taxonomyCategories.getOrElse(selectedTaxonomyTab) { "Genres" }
    val currentTags = SearchTaxonomies.TAG_TAXONOMIES[currentCategory].orEmpty()

    var showSortMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDemographicMenu by remember { mutableStateOf(false) }
    var showOriginMenu by remember { mutableStateOf(false) }

    // Live Autocomplete States for 46,000+ Dictionary
    var authorSuggestions by remember { mutableStateOf<List<CanonicalTag>>(emptyList()) }
    var artistSuggestions by remember { mutableStateOf<List<CanonicalTag>>(emptyList()) }
    var tagSearchQuery by remember { mutableStateOf("") }
    var tagSuggestions by remember { mutableStateOf<List<CanonicalTag>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Query Text Inputs
        OutlinedTextField(
            value = state.query,
            onValueChange = { onStateChange(state.copy(query = it)) },
            label = { Text("Title Keywords") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Author & Artist Fields with Live Autocomplete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.author,
                    onValueChange = { input ->
                        onStateChange(state.copy(author = input))
                        authorSuggestions = if (input.length >= 2) TagDictionary.searchAuthors(input, limit = 8) else emptyList()
                    },
                    label = { Text("Author / Circle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (authorSuggestions.isNotEmpty()) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { authorSuggestions = emptyList() },
                    ) {
                        authorSuggestions.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text("${tag.name} (${tag.namespace.label})") },
                                onClick = {
                                    onStateChange(state.copy(author = tag.name))
                                    authorSuggestions = emptyList()
                                },
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.artist,
                    onValueChange = { input ->
                        onStateChange(state.copy(artist = input))
                        artistSuggestions = if (input.length >= 2) TagDictionary.searchArtists(input, limit = 8) else emptyList()
                    },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (artistSuggestions.isNotEmpty()) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { artistSuggestions = emptyList() },
                    ) {
                        artistSuggestions.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.name) },
                                onClick = {
                                    onStateChange(state.copy(artist = tag.name))
                                    artistSuggestions = emptyList()
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Dynamic Tag Search Across 46,000+ Dictionary Entries
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = tagSearchQuery,
                onValueChange = { input ->
                    tagSearchQuery = input
                    tagSuggestions = if (input.length >= 2) TagDictionary.autocomplete(input, limit = 12) else emptyList()
                },
                label = { Text("Search 46,000+ Tags, Parodies, Characters...") },
                leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tagSuggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { tagSuggestions = emptyList() },
                ) {
                    tagSuggestions.forEach { tag ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(tag.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = tag.namespace.prefix,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                val updatedMap = state.tagStates.toMutableMap()
                                updatedMap[tag.name] = TagSelectionState.INCLUDED
                                onStateChange(state.copy(tagStates = updatedMap))
                                tagSearchQuery = ""
                                tagSuggestions = emptyList()
                            },
                        )
                    }
                }
            }
        }

        // Active Custom/Searched Tags Bar
        val customSelectedTags = state.tagStates.keys.filter { tag ->
            !SearchTaxonomies.TAG_TAXONOMIES.values.any { it.contains(tag) }
        }
        if (customSelectedTags.isNotEmpty()) {
            Text(
                text = "Custom Selected Tags (${customSelectedTags.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                customSelectedTags.forEach { tag ->
                    val tagState = state.tagStates[tag] ?: TagSelectionState.UNSELECTED
                    ThreeStateTagChip(
                        tag = tag,
                        state = tagState,
                        onClick = {
                            val nextState = when (tagState) {
                                TagSelectionState.UNSELECTED -> TagSelectionState.INCLUDED
                                TagSelectionState.INCLUDED -> TagSelectionState.EXCLUDED
                                TagSelectionState.EXCLUDED -> TagSelectionState.UNSELECTED
                            }
                            val updatedMap = state.tagStates.toMutableMap()
                            if (nextState == TagSelectionState.UNSELECTED) {
                                updatedMap.remove(tag)
                            } else {
                                updatedMap[tag] = nextState
                            }
                            onStateChange(state.copy(tagStates = updatedMap))
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Taxonomy Section Header & Logic Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Taxonomies (${state.includedTags.size + state.excludedTags.size} selected)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // AND / OR mode switch
            FilledTonalButton(
                onClick = { onStateChange(state.copy(isIncludeAndMode = !state.isIncludeAndMode)) },
            ) {
                Text(
                    text = if (state.isIncludeAndMode) "Mode: AND" else "Mode: OR",
                    fontSize = 12.sp,
                )
            }
        }

        // Category Subtabs
        ScrollableTabRow(
            selectedTabIndex = selectedTaxonomyTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            taxonomyCategories.forEachIndexed { index, cat ->
                val includedInCat = SearchTaxonomies.TAG_TAXONOMIES[cat]?.count { state.tagStates[it] == TagSelectionState.INCLUDED } ?: 0
                val excludedInCat = SearchTaxonomies.TAG_TAXONOMIES[cat]?.count { state.tagStates[it] == TagSelectionState.EXCLUDED } ?: 0
                val badge = if (includedInCat > 0 || excludedInCat > 0) " (${includedInCat + excludedInCat})" else ""

                Tab(
                    selected = selectedTaxonomyTab == index,
                    onClick = { selectedTaxonomyTab = index },
                    text = { Text("$cat$badge") },
                )
            }
        }

        // 3-State Tag Chips (Default -> Include + -> Exclude -)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            currentTags.forEach { tag ->
                val tagState = state.tagStates[tag] ?: TagSelectionState.UNSELECTED
                ThreeStateTagChip(
                    tag = tag,
                    state = tagState,
                    onClick = {
                        val nextState = when (tagState) {
                            TagSelectionState.UNSELECTED -> TagSelectionState.INCLUDED
                            TagSelectionState.INCLUDED -> TagSelectionState.EXCLUDED
                            TagSelectionState.EXCLUDED -> TagSelectionState.UNSELECTED
                        }
                        val updatedMap = state.tagStates.toMutableMap()
                        if (nextState == TagSelectionState.UNSELECTED) {
                            updatedMap.remove(tag)
                        } else {
                            updatedMap[tag] = nextState
                        }
                        onStateChange(state.copy(tagStates = updatedMap))
                    },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Demographics, Status, Origin, Sort Dropdown Controls
        Text(
            text = "Attributes & Sort",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Sort Dropdown
            Box {
                OutlinedButton(onClick = { showSortMenu = true }) {
                    Text("Sort: ${state.sort}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    SearchTaxonomies.SORT_OPTIONS.forEach { sortOpt ->
                        DropdownMenuItem(
                            text = { Text(sortOpt) },
                            onClick = {
                                onStateChange(state.copy(sort = sortOpt))
                                showSortMenu = false
                            },
                        )
                    }
                }
            }

            // Demographics Dropdown
            Box {
                OutlinedButton(onClick = { showDemographicMenu = true }) {
                    Text("Demographic: ${state.demographic}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showDemographicMenu, onDismissRequest = { showDemographicMenu = false }) {
                    SearchTaxonomies.DEMOGRAPHICS.forEach { demo ->
                        DropdownMenuItem(
                            text = { Text(demo) },
                            onClick = {
                                onStateChange(state.copy(demographic = demo))
                                showDemographicMenu = false
                            },
                        )
                    }
                }
            }

            // Status Dropdown
            Box {
                OutlinedButton(onClick = { showStatusMenu = true }) {
                    Text("Status: ${state.status}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    SearchTaxonomies.STATUS_OPTIONS.forEach { statusOpt ->
                        DropdownMenuItem(
                            text = { Text(statusOpt) },
                            onClick = {
                                onStateChange(state.copy(status = statusOpt))
                                showStatusMenu = false
                            },
                        )
                    }
                }
            }

            // Origin Dropdown
            Box {
                OutlinedButton(onClick = { showOriginMenu = true }) {
                    Text("Origin: ${state.origin}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showOriginMenu, onDismissRequest = { showOriginMenu = false }) {
                    SearchTaxonomies.ORIGIN_OPTIONS.forEach { originOpt ->
                        DropdownMenuItem(
                            text = { Text(originOpt) },
                            onClick = {
                                onStateChange(state.copy(origin = originOpt))
                                showOriginMenu = false
                            },
                        )
                    }
                }
            }
        }

        // Year Range & Chapter Range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.fromYear,
                onValueChange = { onStateChange(state.copy(fromYear = it.filter { c -> c.isDigit() }.take(4))) },
                label = { Text("From Year") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.toYear,
                onValueChange = { onStateChange(state.copy(toYear = it.filter { c -> c.isDigit() }.take(4))) },
                label = { Text("To Year") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onResetFilters,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset")
            }

            Button(
                onClick = {
                    val compiled = state.compileQuery()
                    onExecuteSearch(compiled)
                },
                modifier = Modifier.weight(1.5f),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search (${state.activeFilterCount})")
            }
        }
    }
}

@Composable
private fun ThreeStateTagChip(
    tag: String,
    state: TagSelectionState,
    onClick: () -> Unit,
) {
    val (backgroundColor, textColor, prefixIcon) = when (state) {
        TagSelectionState.UNSELECTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            null,
        )
        TagSelectionState.INCLUDED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Outlined.Check,
        )
        TagSelectionState.EXCLUDED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Outlined.Close,
        )
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (prefixIcon != null) {
                Icon(
                    prefixIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (state != TagSelectionState.UNSELECTED) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}
// KMK <--
