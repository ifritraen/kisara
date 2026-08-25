// KMK -->
package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.components.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

private fun animeBrowseItemKey(url: String?, index: Int): String = "anime/${url.orEmpty()}#$index"

@Composable
fun BrowseAnimeSourceContent(
    source: AnimeSource?,
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    entries: Int,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onWebViewClick: (() -> Unit)?,
    onHelpClick: (() -> Unit)?,
    onLocalAnimeSourceHelpClick: (() -> Unit)?,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)? = null,
) {
    val context = LocalContext.current

    val appendErrorState = animeList.loadState.append.takeIf { it is LoadState.Error }
    val errorState = animeList.loadState.refresh.takeIf { it is LoadState.Error } ?: appendErrorState

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        with(context) { state.error.formattedMessage }
    }

    LaunchedEffect(errorState) {
        if (animeList.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> animeList.retry()
            }
        }
    }

    if (animeList.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = getErrorMessage(errorState),
            actions = persistentListOf(
                EmptyScreenAction(
                    stringRes = MR.strings.action_retry,
                    icon = Icons.Outlined.Refresh,
                    onClick = animeList::refresh,
                ),
            ),
        )
        return
    }

    if (animeList.itemCount == 0 && animeList.loadState.refresh is LoadState.Loading) {
        LoadingScreen(modifier = Modifier.padding(contentPadding))
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            AnimeListContent(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                contentPadding = contentPadding,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
        LibraryDisplayMode.ComfortableGrid, LibraryDisplayMode.ComfortableGridPanorama -> {
            AnimeComfortableGridContent(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = columns,
                contentPadding = contentPadding,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid -> {
            AnimeCompactGridContent(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = columns,
                contentPadding = contentPadding,
                showTitle = true,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
        LibraryDisplayMode.CoverOnlyGrid -> {
            AnimeCompactGridContent(
                animeList = animeList,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = columns,
                contentPadding = contentPadding,
                showTitle = false,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
            )
        }
    }
}

@Composable
private fun AnimeListContent(
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)?,
) {
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        items(
            count = animeList.itemCount,
            key = { index -> animeBrowseItemKey(animeList[index]?.url, index) },
        ) { index ->
            val anime = animeList[index] ?: return@items
            val isFavorite = remember(anime.url, favoriteAnimeUrls) { anime.url in favoriteAnimeUrls }
            val cover = anime.asBrowseAnimeCover(isFavorite)
            EntryListItem(
                title = anime.title,
                coverData = cover,
                coverAlpha = if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                badge = { InLibraryBadge(enabled = isFavorite) },
                onLongClick = onAnimeLongClick?.let { cb -> { cb(anime) } } ?: {},
                onClick = { onAnimeClick(anime) },
            )
        }
    }
}

@Composable
private fun AnimeComfortableGridContent(
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)?,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
        items(
            count = animeList.itemCount,
            key = { index -> animeBrowseItemKey(animeList[index]?.url, index) },
        ) { index ->
            val anime = animeList[index] ?: return@items
            val isFavorite = remember(anime.url, favoriteAnimeUrls) { anime.url in favoriteAnimeUrls }
            val cover = anime.asBrowseAnimeCover(isFavorite)
            EntryComfortableGridItem(
                title = anime.title,
                coverData = cover,
                coverAlpha = if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                coverBadgeStart = { InLibraryBadge(enabled = isFavorite) },
                onLongClick = onAnimeLongClick?.let { cb -> { cb(anime) } } ?: {},
                onClick = { onAnimeClick(anime) },
            )
        }
        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
    }
}

@Composable
private fun AnimeCompactGridContent(
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    showTitle: Boolean,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)?,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
        items(
            count = animeList.itemCount,
            key = { index -> animeBrowseItemKey(animeList[index]?.url, index) },
        ) { index ->
            val anime = animeList[index] ?: return@items
            val isFavorite = remember(anime.url, favoriteAnimeUrls) { anime.url in favoriteAnimeUrls }
            val cover = anime.asBrowseAnimeCover(isFavorite)
            EntryCompactGridItem(
                title = anime.title.takeIf { showTitle },
                coverData = cover,
                coverAlpha = if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                coverBadgeStart = { InLibraryBadge(enabled = isFavorite) },
                onLongClick = onAnimeLongClick?.let { cb -> { cb(anime) } } ?: {},
                onClick = { onAnimeClick(anime) },
            )
        }
        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
    }
}

private fun Anime.asBrowseAnimeCover(isFavorite: Boolean): AnimeCover {
    return AnimeCover(
        animeId = id,
        sourceId = source,
        isAnimeFavorite = isFavorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}

@Composable
internal fun MissingSourceScreen(
    source: StubAnimeSource,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = source.name,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        EmptyScreen(
            message = stringResource(MR.strings.source_not_installed, source.toString()),
            modifier = Modifier.padding(paddingValues),
        )
    }
}
// KMK <--
