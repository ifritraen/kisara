package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.presentation.browse.anime.components.GlobalAnimeSearchCardRow
import eu.kanade.presentation.browse.anime.components.GlobalAnimeSearchLibraryCardRow
import eu.kanade.presentation.browse.anime.components.GlobalAnimeSearchToolbar
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchItemResult
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableMap
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.SearchBottomSheet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalAnimeSearchScreen(
    state: AnimeSearchScreenModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (AnimeSourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getAnime: @Composable (Anime) -> State<Anime>,
    onClickSource: (AnimeCatalogueSource) -> Unit,
    onClickItem: (Anime) -> Unit,
    onLongClickItem: (Anime) -> Unit,
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
            title = "Global Anime Search",
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
                    GlobalAnimeSearchToolbar(
                        searchQuery = state.searchQuery,
                        progress = state.progress,
                        total = state.total,
                        navigateUp = navigateUp,
                        onChangeSearchQuery = onChangeSearchQuery,
                        onSearch = onSearch,
                        sourceFilter = state.sourceFilter,
                        onChangeSearchFilter = onChangeSearchFilter,
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

            GlobalAnimeSearchContent(
                items = state.filteredItems,
                contentPadding = effectivePadding,
                getAnime = getAnime,
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

@Composable
internal fun GlobalAnimeSearchContent(
    items: ImmutableMap<AnimeCatalogueSource, AnimeSearchItemResult>,
    contentPadding: PaddingValues,
    getAnime: @Composable (Anime) -> State<Anime>,
    onClickSource: (AnimeCatalogueSource) -> Unit,
    onClickItem: (Anime) -> Unit,
    onLongClickItem: (Anime) -> Unit,
    libraryResults: List<AnimeSearchScreenModel.LibrarySearchResult> = emptyList(),
) {
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyColumn(
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = contentPadding.calculateBottomPadding() + MaterialTheme.padding.medium,
        ),
    ) {
        if (libraryResults.isNotEmpty()) {
            item(key = "anime-library-matches") {
                GlobalSearchResultItem(
                    title = stringResource(MR.strings.in_library),
                    subtitle = "${libraryResults.size} matches",
                    onClick = {},
                ) {
                    GlobalAnimeSearchLibraryCardRow(
                        items = libraryResults,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                    )
                }
            }
        }

        items.forEach { (source, result) ->
            item(key = "global-anime-search-${source.id}") {
                GlobalSearchResultItem(
                    title = source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        AnimeSearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is AnimeSearchItemResult.Success -> {
                            GlobalAnimeSearchCardRow(
                                titles = result.result,
                                getAnime = getAnime,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is AnimeSearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                        AnimeSearchItemResult.Empty -> {}
                    }
                }
            }
        }
    }
}
