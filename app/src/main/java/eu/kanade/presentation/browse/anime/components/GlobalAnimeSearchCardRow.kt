package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.browse.components.EmptyResultItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.components.cards.KisaraNormalCard
import eu.kanade.presentation.components.cards.NormalCardStyle
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.components.material.padding

@Composable
fun GlobalAnimeSearchCardRow(
    titles: List<Anime>,
    getAnime: @Composable (Anime) -> State<Anime>,
    onClick: (Anime) -> Unit,
    onLongClick: (Anime) -> Unit,
    selection: List<Anime> = emptyList(),
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(titles) {
            val title by getAnime(it)
            AnimeItem(
                title = title.title,
                cover = AnimeCover(title.id, title.source, title.favorite, title.thumbnailUrl, title.coverLastModified),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
                isSelected = selection.fastAny { selected -> selected.id == title.id },
            )
        }
    }
}

@Composable
fun GlobalAnimeSearchLibraryCardRow(
    items: List<AnimeSearchScreenModel.LibrarySearchResult>,
    onClick: (Anime) -> Unit,
    onLongClick: (Anime) -> Unit,
    selection: List<Anime> = emptyList(),
) {
    if (items.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(
            items = items,
            key = { "anime-lib-search-${it.anime.id}" },
        ) { item ->
            val anime = item.anime
            val isSelected = selection.fastAny { it.id == anime.id }

            Column(
                modifier = Modifier.width(108.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimeItem(
                    title = anime.title,
                    cover = AnimeCover(anime.id, anime.source, anime.favorite, anime.thumbnailUrl, anime.coverLastModified),
                    isFavorite = true,
                    onClick = { onClick(anime) },
                    onLongClick = { onLongClick(anime) },
                    isSelected = isSelected,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.categoryNames,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun AnimeItem(
    title: String,
    cover: AnimeCover,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
) {
    Box(
        modifier = Modifier.width(108.dp),
    ) {
        KisaraNormalCard(
            style = NormalCardStyle.DEFAULT,
            title = title,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            badgeText = if (isSelected) "✓" else null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}
