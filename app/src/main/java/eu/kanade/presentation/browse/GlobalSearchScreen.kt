package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableMap
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.domain.source.model.Source as DomainSource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.components.GlobalSearchFilterChipsContent
import eu.kanade.presentation.browse.components.MinimalGlobalSearchToolbar
import eu.kanade.presentation.components.SearchBottomSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun GlobalSearchScreen(
    state: SearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    // KMK -->
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    hasPinnedSources: Boolean,
    onToggleClean: () -> Unit = {},
    onToggleFormat: () -> Unit = {},
    onToggleFuzzy: () -> Unit = {},
    customGroups: List<tachiyomi.domain.source.model.CustomSearchGroup> = emptyList(),
    activeCustomGroupId: String = "",
    onSelectCustomGroup: (String) -> Unit = {},
    onSaveCustomGroup: (tachiyomi.domain.source.model.CustomSearchGroup) -> Unit = {},
    onDeleteCustomGroup: (String) -> Unit = {},
    openSearchOnStart: Boolean = false,
    // KMK <--
) {
    // KMK -->
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    var showGroupManagerDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val uiPreferences = androidx.compose.runtime.remember { Injekt.get<UiPreferences>() }
    val globalSearchStyle by uiPreferences.globalSearchStyle().collectAsState()
    val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
    val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
    val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsState()
    val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()
    val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsState()
    val effectiveCornerRadius = if (syncControlsWithDockRadius) bottomBarCornerRadius.dp else bottomControlsCornerRadius.dp

    var showSearchSheet by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(openSearchOnStart)
    }

    if (showGroupManagerDialog) {
        eu.kanade.presentation.browse.components.CustomGroupManagerDialog(
            groups = customGroups,
            activeGroupId = activeCustomGroupId,
            onSelectGroup = { groupId ->
                onSelectCustomGroup(groupId)
                showGroupManagerDialog = false
            },
            onSaveGroup = onSaveCustomGroup,
            onDeleteGroup = onDeleteCustomGroup,
            onDismissRequest = { showGroupManagerDialog = false },
        )
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            searchQuery = state.searchQuery,
            onChangeSearchQuery = onChangeSearchQuery,
            onSearch = { query ->
                onSearch(query)
                showSearchSheet = false
            },
            onDismissRequest = { showSearchSheet = false },
            title = "Global Search",
            placeholderText = stringResource(MR.strings.action_global_search_hint),
            searchClean = state.searchClean,
            onToggleClean = onToggleClean,
            searchFormat = state.searchFormat,
            onToggleFormat = onToggleFormat,
            searchFuzzy = state.searchFuzzy,
            onToggleFuzzy = onToggleFuzzy,
            onToggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
            isSelectionMode = bulkFavoriteState.selectionMode,
            filterContent = {
                GlobalSearchFilterChipsContent(
                    hideSourceFilter = false,
                    sourceFilter = state.sourceFilter,
                    onChangeSearchFilter = onChangeSearchFilter,
                    onlyShowHasResults = state.onlyShowHasResults,
                    onToggleResults = onToggleResults,
                    hasPinnedSources = hasPinnedSources,
                    customGroups = customGroups,
                    activeCustomGroupId = activeCustomGroupId,
                    onSelectCustomGroup = onSelectCustomGroup,
                    onOpenGroupManager = { showGroupManagerDialog = true },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onChangeSearchQuery,
                )
            },
        )
    }
    // KMK <--

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { scrollBehavior ->
                // KMK -->
                if (globalSearchStyle == UiPreferences.GlobalSearchStyle.BOTTOM_SHEET) {
                    // When in bottom sheet mode, top bar is completely removed
                } else if (bulkFavoriteState.selectionMode) {
                    BulkSelectionToolbar(
                        selectedCount = bulkFavoriteState.selection.size,
                        isRunning = bulkFavoriteState.isRunning,
                        onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                        onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                        onSelectAll = {
                            state.filteredItems.values
                                .filterIsInstance<SearchItemResult.Success>()
                                .flatMap { it.result }
                                .forEach { bulkFavoriteScreenModel.select(it) }
                        },
                        onReverseSelection = {
                            state.filteredItems.values
                                .filterIsInstance<SearchItemResult.Success>()
                                .flatMap { it.result }
                                .let { bulkFavoriteScreenModel.reverseSelection(it) }
                        },
                    )
                } else {
                    // KMK <--
                    GlobalSearchToolbar(
                        searchQuery = state.searchQuery,
                        progress = state.progress,
                        total = state.total,
                        navigateUp = navigateUp,
                        onChangeSearchQuery = onChangeSearchQuery,
                        onSearch = onSearch,
                        hideSourceFilter = false,
                        sourceFilter = state.sourceFilter,
                        onChangeSearchFilter = onChangeSearchFilter,
                        onlyShowHasResults = state.onlyShowHasResults,
                        onToggleResults = onToggleResults,
                        scrollBehavior = scrollBehavior,
                        // KMK -->
                        toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                        isRunning = bulkFavoriteState.isRunning,
                        hasPinnedSources = hasPinnedSources,
                        searchClean = state.searchClean,
                        onToggleClean = onToggleClean,
                        searchFormat = state.searchFormat,
                        onToggleFormat = onToggleFormat,
                        searchFuzzy = state.searchFuzzy,
                        onToggleFuzzy = onToggleFuzzy,
                        customGroups = customGroups,
                        activeCustomGroupId = activeCustomGroupId,
                        onSelectCustomGroup = onSelectCustomGroup,
                        onOpenGroupManager = { showGroupManagerDialog = true },
                        // KMK <--
                    )
                }
            },
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            val effectivePadding = if (globalSearchStyle == UiPreferences.GlobalSearchStyle.BOTTOM_SHEET) {
                PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding() + (bottomBarHeight + bottomBarBottomMargin.coerceAtLeast(0) + 16).dp,
                )
            } else {
                paddingValues
            }

            GlobalSearchContent(
                items = state.filteredItems,
                contentPadding = effectivePadding,
                getManga = getManga,
                onClickSource = onClickSource,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                // KMK -->
                selection = bulkFavoriteState.selection,
                libraryResults = state.libraryResults,
                // KMK <--
            )
        }

        if (globalSearchStyle == UiPreferences.GlobalSearchStyle.BOTTOM_SHEET) {
            if (bulkFavoriteState.selectionMode) {
                // Compact bulk selection floating buttons matching dock style and exact alignment
                GlassSurface(
                    shape = RoundedCornerShape(effectiveCornerRadius),
                    style = GlassDefaults.prominentStyle(),
                    isStandardSurface = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            end = 16.dp,
                            bottom = bottomBarBottomMargin.coerceAtLeast(0).dp,
                        )
                        .height(bottomBarHeight.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = (bottomBarHeight * 0.15f).coerceAtLeast(8f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (bulkFavoriteState.selection.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size((bottomBarHeight * 0.42f).coerceAtLeast(24f).dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${bulkFavoriteState.selection.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                state.filteredItems.values
                                    .filterIsInstance<SearchItemResult.Success>()
                                    .flatMap { it.result }
                                    .forEach { bulkFavoriteScreenModel.select(it) }
                            },
                            modifier = Modifier.size((bottomBarHeight * 0.65f).coerceAtLeast(36f).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = "Select All",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size((bottomBarHeight * 0.38f).coerceAtLeast(20f).dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                state.filteredItems.values
                                    .filterIsInstance<SearchItemResult.Success>()
                                    .flatMap { it.result }
                                    .let { bulkFavoriteScreenModel.reverseSelection(it) }
                            },
                            modifier = Modifier.size((bottomBarHeight * 0.65f).coerceAtLeast(36f).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FlipToBack,
                                contentDescription = "Select Inverse",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size((bottomBarHeight * 0.38f).coerceAtLeast(20f).dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                bulkFavoriteScreenModel.addFavorite()
                            },
                            modifier = Modifier.size((bottomBarHeight * 0.65f).coerceAtLeast(36f).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkAdd,
                                contentDescription = "Add to Library",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size((bottomBarHeight * 0.38f).coerceAtLeast(20f).dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                bulkFavoriteScreenModel.toggleSelectionMode()
                            },
                            modifier = Modifier.size((bottomBarHeight * 0.65f).coerceAtLeast(36f).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Exit Selection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size((bottomBarHeight * 0.38f).coerceAtLeast(20f).dp),
                            )
                        }
                    }
                }
            } else {
                // Search floating button matching media mode change button style & exact alignment
                GlassSurface(
                    shape = RoundedCornerShape(effectiveCornerRadius),
                    style = GlassDefaults.prominentStyle(),
                    isStandardSurface = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            end = 16.dp,
                            bottom = bottomBarBottomMargin.coerceAtLeast(0).dp,
                        )
                        .size(bottomBarHeight.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(effectiveCornerRadius))
                            .clickable { showSearchSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((bottomBarHeight * 0.4f).coerceAtLeast(20f).dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlobalSearchContent(
    items: ImmutableMap<CatalogueSource, SearchItemResult>,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (CatalogueSource) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    fromSourceId: Long? = null,
    // KMK -->
    selection: List<Manga>,
    libraryResults: List<SearchScreenModel.LibrarySearchResult> = emptyList(),
    // KMK <--
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        // KMK -->
        if (libraryResults.isNotEmpty()) {
            item(key = "global-search-library-results") {
                GlobalSearchResultItem(
                    title = "In Library (${libraryResults.size})",
                    subtitle = "From your library",
                    onClick = {},
                    modifier = Modifier.animateItem(),
                ) {
                    eu.kanade.presentation.browse.components.GlobalSearchLibraryCardRow(
                        items = libraryResults,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                        selection = selection,
                    )
                }
            }
        }
        // KMK <--

        items.forEach { (source, result) ->
            item(key = "global-search-${source.id}") {
                // KMK -->
                val domainSource = DomainSource(
                    source.id,
                    "",
                    "",
                    supportsLatest = false,
                    isStub = false,
                )
                // KMK <--

                GlobalSearchResultItem(
                    title = (
                        fromSourceId?.let {
                            "▶ ${source.name}".takeIf { source.id == fromSourceId }
                        } ?: source.name
                        ) +
                        // KMK -->
                        (
                            domainSource.installedExtension?.let { extension ->
                                " (${extension.name})".takeIf { extension.name != source.name }
                            } ?: ""
                            ),
                    // KMK <--
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        SearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is SearchItemResult.Success -> {
                            GlobalSearchCardRow(
                                titles = result.result,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                                // KMK -->
                                selection = selection,
                                // KMK <--
                            )
                        }
                        is SearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
