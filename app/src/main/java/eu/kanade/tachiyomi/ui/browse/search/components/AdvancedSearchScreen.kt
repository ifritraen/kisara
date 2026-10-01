package eu.kanade.tachiyomi.ui.browse.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tag.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = tag.namespace.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                val updatedMap = state.tagStates.toMutableMap()
                                                updatedMap[tag.name] = TagSelectionState.MUST_HAVE
                                                onStateChange(state.copy(tagStates = updatedMap))
                                                tagSearchQuery = ""
                                                tagSuggestions = emptyList()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("+ AND", fontSize = 11.sp)
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                val updatedMap = state.tagStates.toMutableMap()
                                                updatedMap[tag.name] = TagSelectionState.OPTIONAL
                                                onStateChange(state.copy(tagStates = updatedMap))
                                                tagSearchQuery = ""
                                                tagSuggestions = emptyList()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("~ OR", fontSize = 11.sp)
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                val updatedMap = state.tagStates.toMutableMap()
                                                updatedMap[tag.name] = TagSelectionState.EXCLUDED
                                                onStateChange(state.copy(tagStates = updatedMap))
                                                tagSearchQuery = ""
                                                tagSuggestions = emptyList()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Text("- NOT", fontSize = 11.sp)
                                        }
                                    }
                                }
                            },
                            onClick = {
                                val updatedMap = state.tagStates.toMutableMap()
                                updatedMap[tag.name] = TagSelectionState.MUST_HAVE
                                onStateChange(state.copy(tagStates = updatedMap))
                                tagSearchQuery = ""
                                tagSuggestions = emptyList()
                            },
                        )
                    }
                }
            }
        }

        // =========================================================================
        // BOX 1: Must-Have Tags (AND) - All selected tags must be present
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Must-Have Tags (AND)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "(${state.mustHaveTags.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "All required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (state.mustHaveTags.isEmpty()) {
                    Text(
                        text = "No must-have tags selected. Tap '+ AND' above or tap tags below to require all.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.mustHaveTags.forEach { tag ->
                            SelectedTagChip(
                                tag = tag,
                                badgeText = "AND",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = Icons.Outlined.Check,
                                onToggle = {
                                    // Toggle to OPTIONAL
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap[tag] = TagSelectionState.OPTIONAL
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                                onRemove = {
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap.remove(tag)
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // BOX 2: Optional Tags (OR) - Matches any; more matches rank higher
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Optional Tags (OR)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "(${state.optionalTags.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "More matches = higher rank",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                if (state.optionalTags.isEmpty()) {
                    Text(
                        text = "No optional tags selected. Tap '~ OR' above or tap tags below for bonus match priority.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.optionalTags.forEach { tag ->
                            SelectedTagChip(
                                tag = tag,
                                badgeText = "OR",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = Icons.Filled.AutoAwesome,
                                onToggle = {
                                    // Toggle to MUST_HAVE
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap[tag] = TagSelectionState.MUST_HAVE
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                                onRemove = {
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap.remove(tag)
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // BOX 3: Excluded Tags (NOT) - If any are excluded
        // =========================================================================
        if (state.excludedTags.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Excluded Tags (NOT)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "(${state.excludedTags.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Excluded from results",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.excludedTags.forEach { tag ->
                            SelectedTagChip(
                                tag = tag,
                                badgeText = "NOT",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                icon = Icons.Outlined.Close,
                                onToggle = {
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap.remove(tag)
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                                onRemove = {
                                    val updatedMap = state.tagStates.toMutableMap()
                                    updatedMap.remove(tag)
                                    onStateChange(state.copy(tagStates = updatedMap))
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Taxonomy Browser Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Taxonomy Explorer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tap: [AND] -> [OR] -> [NOT] -> Off",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Category Subtabs
        ScrollableTabRow(
            selectedTabIndex = selectedTaxonomyTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            taxonomyCategories.forEachIndexed { index, cat ->
                val activeCount = SearchTaxonomies.TAG_TAXONOMIES[cat]?.count {
                    state.tagStates[it] != null && state.tagStates[it] != TagSelectionState.UNSELECTED
                } ?: 0
                val badge = if (activeCount > 0) " ($activeCount)" else ""

                Tab(
                    selected = selectedTaxonomyTab == index,
                    onClick = { selectedTaxonomyTab = index },
                    text = { Text("$cat$badge") },
                )
            }
        }

        // 4-State Tag Chips (Default -> Must-Have AND -> Optional OR -> Excluded NOT -> Off)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            currentTags.forEach { tag ->
                val tagState = state.tagStates[tag] ?: TagSelectionState.UNSELECTED
                FourStateTagChip(
                    tag = tag,
                    state = tagState,
                    onClick = {
                        val nextState = when (tagState) {
                            TagSelectionState.UNSELECTED -> TagSelectionState.MUST_HAVE
                            TagSelectionState.MUST_HAVE -> TagSelectionState.OPTIONAL
                            TagSelectionState.OPTIONAL -> TagSelectionState.EXCLUDED
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
private fun FourStateTagChip(
    tag: String,
    state: TagSelectionState,
    onClick: () -> Unit,
) {
    val (backgroundColor, textColor, prefixIcon, badgeText) = when (state) {
        TagSelectionState.UNSELECTED -> Quad(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            null,
            null,
        )
        TagSelectionState.MUST_HAVE -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Outlined.Check,
            "AND",
        )
        TagSelectionState.OPTIONAL -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.AutoAwesome,
            "OR",
        )
        TagSelectionState.EXCLUDED -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Outlined.Close,
            "NOT",
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
                    modifier = Modifier.size(13.dp),
                )
            }
            if (badgeText != null) {
                Text(
                    text = "[$badgeText]",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
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

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun SelectedTagChip(
    tag: String,
    badgeText: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() },
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "[$badgeText] $tag",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRemove() }
                    .padding(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove tag",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
// KMK <--
