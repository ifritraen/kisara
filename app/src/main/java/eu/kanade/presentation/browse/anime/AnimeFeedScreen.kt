package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedItemUI
import eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedScreenState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnimeFeedScreen(
    state: AnimeFeedScreenState,
    contentPadding: PaddingValues,
    onClickSource: (AnimeCatalogueSource, AnimeFeedItemUI) -> Unit,
    onClickAnime: (Anime) -> Unit,
    onLongClickAnime: ((Anime) -> Unit)? = null,
    getAnimeState: @Composable (Anime) -> State<Anime>,
    onRefresh: () -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen()
        state.isEmpty -> EmptyScreen(stringRes = KMR.strings.feed_empty)
        else -> {
            PullRefresh(
                refreshing = state.isLoadingItems,
                enabled = true,
                onRefresh = onRefresh,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items ?: emptyList(), key = { it.feed.id }) { feedItem ->
                        AnimeFeedRow(
                            feedItem = feedItem,
                            onClickSource = { onClickSource(feedItem.source, feedItem) },
                            onClickAnime = onClickAnime,
                            onLongClickAnime = onLongClickAnime,
                            getAnimeState = getAnimeState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeFeedRow(
    feedItem: AnimeFeedItemUI,
    onClickSource: () -> Unit,
    onClickAnime: (Anime) -> Unit,
    onLongClickAnime: ((Anime) -> Unit)? = null,
    getAnimeState: @Composable (Anime) -> State<Anime>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClickSource)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = feedItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = feedItem.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val results = feedItem.results
        if (results == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.id }) { rawAnime ->
                    val animeState = getAnimeState(rawAnime)
                    val anime = animeState.value
                    Column(
                        modifier = Modifier
                            .width(100.dp)
                            .combinedClickable(
                                onClick = { onClickAnime(anime) },
                                onLongClick = onLongClickAnime?.let { { it(anime) } },
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MangaCover.Book(
                            data = anime.asAnimeCover(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
