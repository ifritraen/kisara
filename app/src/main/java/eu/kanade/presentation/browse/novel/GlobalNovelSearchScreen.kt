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
) {
    Scaffold(
        topBar = { scrollBehavior ->
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
        },
    ) { paddingValues ->
        if (state.items.isEmpty() && state.searchQuery.isNullOrBlank()) {
            EmptyScreen(
                stringRes = MR.strings.source_empty_screen,
                modifier = Modifier,
            )
            return@Scaffold
        }

        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            getNovel = getNovel,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            libraryResults = state.libraryResults,
        )
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
            item(key = source.id) {
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
