package tachiyomi.domain.suggestions.interactor

import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FilterMangaByBlockedContent(
    private val getSuggestionTags: GetSuggestionTags = Injekt.get(),
    private val getSuggestionSources: GetSuggestionSources = Injekt.get(),
    private val getSuggestionAuthors: GetSuggestionAuthors = Injekt.get(),
    private val getSuggestionArtists: GetSuggestionArtists = Injekt.get(),
) {
    data class BlockedFilters(
        val blockedTags: Set<String>,
        val blockedSources: Set<Long>,
        val blockedAuthors: Set<String>,
        val blockedArtists: Set<String>,
    )

    suspend fun getBlockedFilters(): BlockedFilters = withIOContext {
        val tags = getSuggestionTags.await().filter { it.isBlocked }.map { it.tag.lowercase().trim() }.toSet()
        val sources = getSuggestionSources.await().filter { it.isBlocked }.map { it.sourceId }.toSet()
        val authors = getSuggestionAuthors.await().filter { it.isBlocked }.map { it.author.lowercase().trim() }.toSet()
        val artists = getSuggestionArtists.await().filter { it.isBlocked }.map { it.artist.lowercase().trim() }.toSet()
        BlockedFilters(tags, sources, authors, artists)
    }

    fun isMangaBlocked(manga: Manga, filters: BlockedFilters): Boolean {
        if (filters.blockedSources.contains(manga.source)) return true

        val authorClean = manga.author?.lowercase()?.trim().orEmpty()
        if (authorClean.isNotEmpty() && filters.blockedAuthors.any { authorClean.contains(it) }) return true

        val artistClean = manga.artist?.lowercase()?.trim().orEmpty()
        if (artistClean.isNotEmpty() && filters.blockedArtists.any { artistClean.contains(it) }) return true

        val genres = manga.genre.orEmpty().map { it.lowercase().trim() }
        if (genres.any { genre -> filters.blockedTags.contains(genre) }) return true

        return false
    }

    suspend fun filterList(list: List<Manga>): List<Manga> {
        val filters = getBlockedFilters()
        if (filters.blockedTags.isEmpty() && filters.blockedSources.isEmpty() && filters.blockedAuthors.isEmpty() && filters.blockedArtists.isEmpty()) {
            return list
        }
        return list.filterNot { isMangaBlocked(it, filters) }
    }
}
