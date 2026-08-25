package eu.kanade.presentation.library.anime

import androidx.compose.runtime.Immutable
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Immutable
data class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val isDownloaded: Boolean = false,
    val sourceLanguage: String = "",
) {
    val id: Long = libraryAnime.id
    val category: Long = libraryAnime.category
    val pinned: Boolean = libraryAnime.pinned
    val unseenCount: Long = libraryAnime.unseenCount
    val seenCount: Long = libraryAnime.seenCount
    val lastSeen: Long = libraryAnime.lastSeen
    val totalEpisodes: Long = libraryAnime.totalCount
    val hasStarted: Boolean = libraryAnime.hasStarted
    val hasBookmarks: Boolean = libraryAnime.hasBookmarks
    val dateAdded: Long = libraryAnime.anime.dateAdded
    val title: String = libraryAnime.anime.title
    val anime: Anime = libraryAnime.anime
    val coverAnime: Anime = libraryAnime.anime

    fun matches(query: String, sourceManager: AnimeSourceManager): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(anime.source).name }
        return anime.title.contains(query, ignoreCase = true) ||
            (anime.author?.contains(query, ignoreCase = true) ?: false) ||
            (anime.artist?.contains(query, ignoreCase = true) ?: false) ||
            (anime.description?.contains(query, ignoreCase = true) ?: false) ||
            sourceName.contains(query, ignoreCase = true) ||
            (anime.genre?.any { it.contains(query, ignoreCase = true) } ?: false)
    }
}
