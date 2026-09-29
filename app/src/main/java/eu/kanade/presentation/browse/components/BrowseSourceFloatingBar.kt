package eu.kanade.presentation.browse.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewCompact
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.KisaraBottomSheet
import tachiyomi.domain.library.model.LibraryDisplayMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Main floating dock for source browsing screen.
 * Consists of:
 * - Left: Source Switcher button
 * - Center: Dynamic horizontally-scrollable Filter/Popular/Latest/Saved Searches bar
 * - Right: 3-Dot Action Button
 */
@Composable
fun BrowseSourceFloatingDock(
    sourceName: String,
    isPopularSelected: Boolean,
    isLatestSelected: Boolean,
    isFilterSelected: Boolean,
    supportsLatest: Boolean,
    filterable: Boolean,
    filtersCount: Int,
    savedSearches: List<Pair<Long, String>>,
    activeSavedSearchId: Long?,
    onPopularClick: () -> Unit,
    onLatestClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSavedSearchClick: (Long) -> Unit,
    onSavedSearchLongClick: (Long, String) -> Unit,
    onSourceSwitchClick: () -> Unit,
    onActionsMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceIcon: @Composable () -> Unit = {
        Text(
            text = sourceName.take(2).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    },
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
    val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()
    val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsState()
    val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsState()

    val effectiveCornerRadius = if (syncControlsWithDockRadius) {
        bottomBarCornerRadius.dp
    } else {
        bottomControlsCornerRadius.dp
    }

    val dockHeight = bottomBarHeight.coerceIn(40, 56)
    val listState = rememberLazyListState()

    LaunchedEffect(isPopularSelected, isLatestSelected, isFilterSelected, activeSavedSearchId) {
        val targetIndex = when {
            isFilterSelected -> 0
            isPopularSelected -> if (filterable) 1 else 0
            isLatestSelected -> if (filterable) 2 else 1
            activeSavedSearchId != null -> {
                val idx = savedSearches.indexOfFirst { it.first == activeSavedSearchId }
                val base = (if (filterable) 1 else 0) + (if (supportsLatest) 2 else 1)
                if (idx != -1) base + idx else 0
            }
            else -> 0
        }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left: Source Switcher Button
        GlassSurface(
            shape = RoundedCornerShape(effectiveCornerRadius),
            style = GlassDefaults.prominentStyle(),
            modifier = Modifier.size(dockHeight.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(effectiveCornerRadius))
                    .clickable(onClick = onSourceSwitchClick),
                contentAlignment = Alignment.Center,
            ) {
                sourceIcon()
            }
        }

        // 2. Middle: Dynamic Filter / Popular / Latest / Saved Searches Bar
        GlassSurface(
            shape = RoundedCornerShape(bottomBarCornerRadius.dp),
            style = GlassDefaults.prominentStyle(),
            modifier = Modifier
                .weight(1f)
                .height(dockHeight.dp),
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Filter Tab
                if (filterable) {
                    item(key = "filter_tab") {
                        SourceDockButton(
                            text = if (filtersCount > 0) "Filter ($filtersCount)" else "Filter",
                            icon = Icons.Outlined.FilterList,
                            selected = isFilterSelected && activeSavedSearchId == null,
                            onClick = onFilterClick,
                        )
                    }
                }

                // Popular Tab
                item(key = "popular_tab") {
                    SourceDockButton(
                        text = "Popular",
                        icon = Icons.Outlined.Favorite,
                        selected = isPopularSelected,
                        onClick = onPopularClick,
                    )
                }

                // Latest Tab
                if (supportsLatest) {
                    item(key = "latest_tab") {
                        SourceDockButton(
                            text = "Latest",
                            icon = Icons.Outlined.NewReleases,
                            selected = isLatestSelected,
                            onClick = onLatestClick,
                        )
                    }
                }

                // Dynamic Saved Searches
                items(savedSearches, key = { "saved_${it.first}" }) { (id, name) ->
                    SourceDockButton(
                        text = name,
                        selected = activeSavedSearchId == id,
                        onClick = { onSavedSearchClick(id) },
                        onLongClick = { onSavedSearchLongClick(id, name) },
                    )
                }
            }
        }

        // 3. Right: 3-Dot Floating Action Button
        GlassSurface(
            shape = RoundedCornerShape(effectiveCornerRadius),
            style = GlassDefaults.prominentStyle(),
            modifier = Modifier.size(dockHeight.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(effectiveCornerRadius))
                    .clickable(onClick = onActionsMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Source Actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Individual button inside the dynamic floating source dock.
 */
@Composable
private fun SourceDockButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val subBarHeight by uiPreferences.subBarHeight().collectAsState()
    val fontSize = (subBarHeight * 0.35f).coerceIn(9f, 13f).sp

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "sourceDockScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.76f,
        animationSpec = tween(150),
        label = "sourceDockAlpha",
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Floating actions bottom sheet / menu opened by the 3-dot FAB.
 */
@Composable
fun BrowseSourceActionsSheet(
    sourceName: String,
    displayMode: LibraryDisplayMode,
    isHttpSource: Boolean,
    isConfigurableSource: Boolean,
    isIncognito: Boolean,
    onDismissRequest: () -> Unit,
    onSearchClick: () -> Unit,
    onDisplayModeClick: () -> Unit,
    onToggleBulkSelection: () -> Unit,
    onWebViewClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleIncognito: () -> Unit,
    onHelpClick: () -> Unit,
) {
    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = sourceName,
        subtitle = "Source Options & Tools",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Search in source
            ActionMenuTile(
                title = "Search",
                subtitle = "Search titles and authors in this source",
                icon = Icons.Outlined.Search,
                onClick = {
                    onDismissRequest()
                    onSearchClick()
                },
            )

            // Display Mode
            val (displayIcon, displayLabel) = when (displayMode) {
                LibraryDisplayMode.CompactGrid -> Icons.Outlined.ViewCompact to "Compact Grid"
                LibraryDisplayMode.ComfortableGrid -> Icons.Outlined.GridView to "Comfortable Grid"
                LibraryDisplayMode.List -> Icons.Outlined.ViewList to "List View"
                else -> Icons.Outlined.GridView to "Grid View"
            }
            ActionMenuTile(
                title = "Display Mode",
                subtitle = "Current: $displayLabel",
                icon = displayIcon,
                onClick = {
                    onDismissRequest()
                    onDisplayModeClick()
                },
            )

            // Bulk Selection
            ActionMenuTile(
                title = "Bulk Selection",
                subtitle = "Select multiple items to favorite or manage",
                icon = Icons.Outlined.Checklist,
                onClick = {
                    onDismissRequest()
                    onToggleBulkSelection()
                },
            )

            // WebView
            if (isHttpSource) {
                ActionMenuTile(
                    title = "Open in WebView",
                    subtitle = "View original source website",
                    icon = Icons.Outlined.Public,
                    onClick = {
                        onDismissRequest()
                        onWebViewClick()
                    },
                )
            }

            // Source Settings
            if (isConfigurableSource) {
                ActionMenuTile(
                    title = "Source Settings",
                    subtitle = "Configure extension preferences and logins",
                    icon = Icons.Outlined.Settings,
                    onClick = {
                        onDismissRequest()
                        onSettingsClick()
                    },
                )
            }

            // Incognito Mode
            ActionMenuTile(
                title = "Incognito Mode",
                subtitle = if (isIncognito) "Incognito is active (history disabled)" else "Normal mode",
                icon = Icons.Outlined.VisibilityOff,
                trailing = {
                    Switch(
                        checked = isIncognito,
                        onCheckedChange = { onToggleIncognito() },
                    )
                },
                onClick = onToggleIncognito,
            )

            // Help
            ActionMenuTile(
                title = "Help & Guides",
                subtitle = "Learn about sources and troubleshooting",
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = {
                    onDismissRequest()
                    onHelpClick()
                },
            )
        }
    }
}

/**
 * Clickable tile row for actions inside the bottom sheet.
 */
@Composable
private fun ActionMenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * Quick search bottom sheet with query text input and instant search execution.
 */
@Composable
fun BrowseSourceSearchSheet(
    sourceName: String,
    initialQuery: String?,
    savedSearches: List<Pair<Long, String>>,
    onDismissRequest: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectSavedSearch: (Long) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery.orEmpty()) }
    val focusManager = LocalFocusManager.current

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = "Search $sourceName",
        subtitle = "Enter keywords or pick a saved search",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search titles, authors, genres...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onDismissRequest()
                        onSearch(query.trim())
                    },
                ),
            )

            // Saved searches quick tags
            if (savedSearches.isNotEmpty()) {
                Text(
                    text = "Saved Searches",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(savedSearches, key = { it.first }) { (id, name) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onDismissRequest()
                                onSelectSavedSearch(id)
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // Search execution button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onDismissRequest()
                    onSearch(query.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search")
            }
        }
    }
}

/**
 * Source Switcher Bottom Sheet:
 * Allows user to quickly jump to any other pinned or installed source directly.
 */
data class SourcePickerItem(
    val id: Long,
    val name: String,
    val lang: String,
    val isPinned: Boolean,
)

@Composable
fun SourcePickerBottomSheet(
    currentSourceId: Long,
    sources: List<SourcePickerItem>,
    onDismissRequest: () -> Unit,
    onSelectSource: (Long) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSources = remember(sources, searchQuery) {
        if (searchQuery.isBlank()) {
            sources
        } else {
            sources.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.lang.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val pinned = remember(filteredSources) { filteredSources.filter { it.isPinned } }
    val unpinned = remember(filteredSources) { filteredSources.filter { !it.isPinned } }

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = "Switch Source",
        subtitle = "Select an installed source to browse",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter sources...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (pinned.isNotEmpty()) {
                    item {
                        Text(
                            text = "Pinned Sources",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(pinned, key = { "pinned_${it.id}" }) { item ->
                        SourcePickerTile(
                            item = item,
                            isSelected = item.id == currentSourceId,
                            onClick = {
                                onDismissRequest()
                                onSelectSource(item.id)
                            },
                        )
                    }
                }

                if (unpinned.isNotEmpty()) {
                    item {
                        Text(
                            text = if (pinned.isNotEmpty()) "All Sources" else "Sources",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(unpinned, key = { "all_${it.id}" }) { item ->
                        SourcePickerTile(
                            item = item,
                            isSelected = item.id == currentSourceId,
                            onClick = {
                                onDismissRequest()
                                onSelectSource(item.id)
                            },
                        )
                    }
                }

                if (filteredSources.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matching sources found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerTile(
    item: SourcePickerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Floating bulk selection dock shown when bulk selection mode is enabled in source browser.
 */
@Composable
fun BrowseSourceBulkActionDock(
    selectedCount: Int,
    isRunning: Boolean,
    onSelectAll: () -> Unit,
    onReverseSelection: () -> Unit,
    onChangeCategoryClick: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()

    GlassSurface(
        shape = RoundedCornerShape(bottomBarCornerRadius.dp),
        style = GlassDefaults.prominentStyle(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSelectAll) {
                    Text("All", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onReverseSelection) {
                    Text("Invert", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onChangeCategoryClick,
                    enabled = selectedCount > 0 && !isRunning,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
