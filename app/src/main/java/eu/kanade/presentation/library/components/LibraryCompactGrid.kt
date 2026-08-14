package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryCompactGrid(
    items: List<LibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            key = {
                try {
                    it.libraryManga.manga.id
                } catch (e: Throwable) {
                    it.hashCode()
                }
            },
            contentType = { "library_compact_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            val parsed = remember(manga) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(manga, manga.title) }
            MangaCompactGridItem(
                isSelected = manga.id in selection,
                title = manga.title.takeIf { showTitle },
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    ogUrl = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnreadBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    val lang = parsed.languageCode ?: libraryItem.sourceLanguage
                    val hasLang = !libraryItem.isLocal && lang.isNotEmpty()
                    val hasSource = libraryItem.source != null
                    val hasColor = parsed.isColorized || eu.kanade.tachiyomi.util.MangaTitleParser.isColorized(manga, manga.title)
                    val hasUncensored = parsed.isUncensored || eu.kanade.tachiyomi.util.MangaTitleParser.isUncensored(manga, manga.title)

                    // Column 1: Language (top) + Color (bottom)
                    if (hasLang || hasColor) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp),
                        ) {
                            if (hasLang) {
                                LanguageBadge(
                                    isLocal = libraryItem.isLocal,
                                    sourceLanguage = lang,
                                    useLangIcon = libraryItem.useLangIcon,
                                )
                            }
                            if (hasColor) {
                                ColorizedBadge()
                            }
                        }
                    }

                    // Column 2: Extension/Source (top) + UN (bottom)
                    if (hasSource || hasUncensored) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp),
                        ) {
                            if (hasSource) {
                                SourceIconBadge(source = libraryItem.source)
                            }
                            if (hasUncensored) {
                                UncensoredBadge()
                            }
                        }
                    }
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryItem.libraryManga) }
                } else {
                    null
                },
                manga = manga,
            )
        }
    }
}
