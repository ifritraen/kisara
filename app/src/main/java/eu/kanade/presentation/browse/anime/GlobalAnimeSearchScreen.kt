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
) {
    Scaffold(
        topBar = { scrollBehavior ->
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
        },
    ) { paddingValues ->
        if (state.items.isEmpty() && state.searchQuery.isNullOrBlank()) {
            EmptyScreen(
                stringRes = MR.strings.source_empty_screen,
                modifier = Modifier,
            )
            return@Scaffold
        }

        GlobalAnimeSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getAnime = getAnime,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            libraryResults = state.libraryResults,
        )
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
            item(key = source.id) {
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
