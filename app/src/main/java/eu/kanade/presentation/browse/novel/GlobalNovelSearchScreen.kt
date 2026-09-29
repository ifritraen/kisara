package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.novel.components.GlobalNovelSearchCardRow
import eu.kanade.presentation.browse.novel.components.GlobalNovelSearchLibraryCardRow
import eu.kanade.presentation.browse.novel.components.GlobalNovelSearchToolbar
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchItemResult
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.SearchBottomSheet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.padding
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalNovelSearchScreen(
    state: NovelSearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (NovelSourceFilter) -> Unit,
    onChangeLanguageFilter: (Set<String>) -> Unit,
    onToggleResults: () -> Unit,
    getNovel: @Composable (Novel) -> State<Novel>,
    onClickSource: (NovelCatalogueSource) -> Unit,
    onClickItem: (Novel) -> Unit,
    onLongClickItem: (Novel) -> Unit,
    onToggleClean: () -> Unit = {},
    onToggleFormat: () -> Unit = {},
    onToggleFuzzy: () -> Unit = {},
    openSearchOnStart: Boolean = false,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val globalSearchStyle by uiPreferences.globalSearchStyle().collectAsState()
    val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
    val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
    val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsState()
    val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()
    val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsState()
    val effectiveCornerRadius = if (syncControlsWithDockRadius) bottomBarCornerRadius.dp else bottomControlsCornerRadius.dp

    var showSearchSheet by remember {
        mutableStateOf(openSearchOnStart)
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
            title = "Global Novel Search",
            placeholderText = stringResource(MR.strings.action_global_search_hint),
            searchClean = state.searchClean,
            onToggleClean = onToggleClean,
            searchFormat = state.searchFormat,
            onToggleFormat = onToggleFormat,
            searchFuzzy = state.searchFuzzy,
            onToggleFuzzy = onToggleFuzzy,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { scrollBehavior ->
                if (globalSearchStyle == UiPreferences.GlobalSearchStyle.BOTTOM_SHEET) {
                    // When in bottom sheet mode, top bar is completely removed
                } else {
                    GlobalNovelSearchToolbar(
                        searchQuery = state.searchQuery,
                        progress = state.progress,
                        total = state.total,
                        navigateUp = navigateUp,
                        onChangeSearchQuery = onChangeSearchQuery,
                        onSearch = onSearch,
                        sourceFilter = state.sourceFilter,
                        onChangeSearchFilter = onChangeSearchFilter,
                        languageFilter = state.languageFilter,
                        availableLanguages = state.availableLanguages,
                        onChangeLanguageFilter = onChangeLanguageFilter,
                        onlyShowHasResults = state.onlyShowHasResults,
                        onToggleResults = onToggleResults,
                        scrollBehavior = scrollBehavior,
                        searchClean = state.searchClean,
                        onToggleClean = onToggleClean,
                        searchFormat = state.searchFormat,
                        onToggleFormat = onToggleFormat,
                        searchFuzzy = state.searchFuzzy,
                        onToggleFuzzy = onToggleFuzzy,
                    )
                }
            },
        ) { paddingValues ->
            if (state.items.isEmpty() && state.searchQuery.isNullOrBlank()) {
                EmptyScreen(
                    stringRes = MR.strings.source_empty_screen,
                    modifier = Modifier,
                )
                return@Scaffold
            }

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
                getNovel = getNovel,
                onClickSource = onClickSource,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                libraryResults = state.libraryResults,
            )
        }

        if (globalSearchStyle == UiPreferences.GlobalSearchStyle.BOTTOM_SHEET) {
            GlassSurface(
                shape = RoundedCornerShape(effectiveCornerRadius),
                style = GlassDefaults.prominentStyle(),
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

@Composable
internal fun GlobalSearchContent(
    items: Map<NovelCatalogueSource, NovelSearchItemResult>,
    contentPadding: PaddingValues,
    getNovel: @Composable (Novel) -> State<Novel>,
    onClickSource: (NovelCatalogueSource) -> Unit,
    onClickItem: (Novel) -> Unit,
    onLongClickItem: (Novel) -> Unit,
    libraryResults: List<NovelSearchScreenModel.LibrarySearchResult> = emptyList(),
) {
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyColumn(
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = contentPadding,
    ) {
        if (libraryResults.isNotEmpty()) {
            item(key = "novel-library-matches") {
                GlobalSearchResultItem(
                    title = stringResource(MR.strings.in_library),
                    subtitle = "${libraryResults.size} matches",
                    onClick = {},
                ) {
                    GlobalNovelSearchLibraryCardRow(
                        items = libraryResults,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                    )
                }
            }
        }

        items.forEach { (source, result) ->
            item(key = "global-novel-search-${source.id}") {
                GlobalSearchResultItem(
                    title = source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier
                        .animateItem()
                        .auroraCenteredMaxWidth(
                            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
                        ),
                ) {
                    when (result) {
                        NovelSearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is NovelSearchItemResult.Success -> {
                            GlobalNovelSearchCardRow(
                                titles = result.result,
                                getNovel = getNovel,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is NovelSearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
