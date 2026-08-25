package eu.kanade.presentation.updates.anime

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesItem
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate

sealed interface AnimeUpdatesUiModel {
    data class Header(val date: LocalDate) : AnimeUpdatesUiModel
    data class Item(val item: AnimeUpdatesItem) : AnimeUpdatesUiModel
}

@Composable
fun AnimeUpdatesScreen(
    state: AnimeUpdatesScreenModel.State,
    contentPadding: PaddingValues = PaddingValues(),
    onAnimeClick: (Long) -> Unit,
    onPlayClick: (animeId: Long, episodeId: Long) -> Unit,
    onToggleSelection: (AnimeUpdatesItem, Boolean) -> Unit,
) {
    Scaffold { _ ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> FastScrollLazyColumn(contentPadding = contentPadding) {
                animeUpdatesUiItems(
                    uiModels = state.getUiModel(),
                    selectionMode = state.selectionMode,
                    onAnimeClick = onAnimeClick,
                    onPlayClick = onPlayClick,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
}

private fun LazyListScope.animeUpdatesUiItems(
    uiModels: List<AnimeUpdatesUiModel>,
    selectionMode: Boolean,
    onAnimeClick: (Long) -> Unit,
    onPlayClick: (animeId: Long, episodeId: Long) -> Unit,
    onToggleSelection: (AnimeUpdatesItem, Boolean) -> Unit,
) {
    items(
        items = uiModels,
        key = { "anime-updates-${it.hashCode()}" },
        contentType = {
            when (it) {
                is AnimeUpdatesUiModel.Header -> "header"
                is AnimeUpdatesUiModel.Item -> "item"
            }
        },
    ) { item ->
        when (item) {
            is AnimeUpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.animateItemFastScroll(),
                    text = relativeDateText(item.date),
                )
            }
            is AnimeUpdatesUiModel.Item -> {
                val updatesItem = item.item
                AnimeUpdatesListItem(
                    modifier = Modifier.animateItemFastScroll(),
                    item = updatesItem,
                    onClickAnime = { onAnimeClick(updatesItem.update.animeId) },
                    onClickPlay = { onPlayClick(updatesItem.update.animeId, updatesItem.update.episodeId) },
                    onLongClick = { onToggleSelection(updatesItem, !updatesItem.selected) },
                )
            }
        }
    }
}

@Composable
private fun AnimeUpdatesListItem(
    modifier: Modifier = Modifier,
    item: AnimeUpdatesItem,
    onClickAnime: () -> Unit,
    onClickPlay: () -> Unit,
    onLongClick: () -> Unit,
) {
    val update = item.update
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClickAnime,
                onLongClick = onLongClick,
            )
            .height(72.dp)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = update.coverData,
            onClick = onClickAnime,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            Text(
                text = update.animeTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = update.episodeName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (update.seen) {
                        LocalContentColor.current.copy(alpha = 0.38f)
                    } else {
                        LocalContentColor.current
                    },
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onClickPlay) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
