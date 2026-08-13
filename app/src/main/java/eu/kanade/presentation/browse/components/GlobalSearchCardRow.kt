package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    // KMK <--
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
            val title by getManga(it)
            MangaItem(
                title = title.title,
                cover = title.asMangaCover(),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
                // KMK -->
                isSelected = selection.fastAny { selected -> selected.id == title.id },
                // KMK <--
                manga = title,
            )
        }
    }
}

// KMK -->
@Composable
fun GlobalSearchLibraryCardRow(
    items: List<eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel.LibrarySearchResult>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    selection: List<Manga> = emptyList(),
) {
    if (items.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(
            items = items,
            key = { "lib-search-${it.manga.id}" },
        ) { item ->
            val manga = item.manga
            val isSelected = selection.fastAny { it.id == manga.id }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier.width(108.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                MangaItem(
                    title = manga.title,
                    cover = manga.asMangaCover(),
                    isFavorite = true,
                    onClick = { onClick(manga) },
                    onLongClick = { onLongClick(manga) },
                    isSelected = isSelected,
                    manga = manga,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.categoryNames,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
// KMK <--

@Composable
internal fun MangaItem(
    title: String,
    cover: MangaCover,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    // KMK -->
    isSelected: Boolean = false,
    usePanoramaCover: Boolean? = null,
    // KMK <--
    manga: Manga? = null,
) {
    Box(
        modifier = Modifier.width(108.dp),
    ) {
        MangaCompactGridItem(
            title = title,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            isSelected = isSelected,
            manga = manga,
            coverAlpha = if (isFavorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
internal fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        modifier = Modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    )
}
