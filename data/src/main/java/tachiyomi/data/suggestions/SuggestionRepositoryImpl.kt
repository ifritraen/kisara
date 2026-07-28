package tachiyomi.data.suggestions

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class SuggestionRepositoryImpl(
    private val handler: DatabaseHandler,
) : SuggestionRepository {

    override fun observeAll(): Flow<List<Suggestion>> {
        return handler.subscribeToList {
            suggestionsQueries.getSuggestions {
                    _id,
                    source,
                    url,
                    artist,
                    author,
                    description,
                    genre,
                    title,
                    status,
                    thumbnailUrl,
                    favorite,
                    lastUpdate,
                    nextUpdate,
                    initialized,
                    viewerFlags,
                    chapterFlags,
                    coverLastModified,
                    dateAdded,
                    filteredScanlators,
                    updateStrategy,
                    calculateInterval,
                    lastModifiedAt,
                    favoriteModifiedAt,
                    version,
                    isSyncing,
                    notes,
                    relevance,
                    createdAt,
                ->
                val manga = MangaMapper.mapManga(
                    _id, source, url, artist, author, description, genre, title, status, thumbnailUrl,
                    favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags,
                    coverLastModified, dateAdded, filteredScanlators, updateStrategy,
                    calculateInterval, lastModifiedAt, favoriteModifiedAt, version, isSyncing, notes,
                )
                Suggestion(manga, relevance, createdAt)
            }
        }
    }

    override suspend fun count(): Long {
        return handler.awaitOne { suggestionsQueries.countSuggestions() }
    }

    override fun observeCount(): Flow<Long> {
        return handler.subscribeToOne { suggestionsQueries.countSuggestions() }
    }

    override suspend fun clear() {
        handler.await { suggestionsQueries.deleteAllSuggestions() }
    }

    override suspend fun replace(suggestions: List<Pair<Manga, Double>>) {
        handler.await(inTransaction = true) {
            suggestionsQueries.deleteAllSuggestions()
            val now = System.currentTimeMillis()
            suggestions.forEach { (manga, relevance) ->
                suggestionsQueries.insertSuggestion(
                    mangaId = manga.id,
                    relevance = relevance,
                    createdAt = now,
                )
            }
        }
    }

    override fun observeTags(): Flow<List<SuggestionTag>> {
        return handler.subscribeToList {
            suggestionsQueries.getSuggestionTags { tag, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionTag(
                    tag = tag,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun getTags(): List<SuggestionTag> {
        return handler.awaitList {
            suggestionsQueries.getSuggestionTags { tag, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionTag(
                    tag = tag,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun insertTag(tag: SuggestionTag) {
        handler.await {
            suggestionsQueries.insertSuggestionTag(
                tag = tag.tag,
                count = tag.count,
                isBlocked = if (tag.isBlocked) 1L else 0L,
                isUserAdded = if (tag.isUserAdded) 1L else 0L,
                sortOrder = tag.sortOrder,
            )
        }
    }

    override suspend fun deleteTag(tag: String) {
        handler.await {
            suggestionsQueries.deleteSuggestionTag(tag)
        }
    }

    override suspend fun clearTags() {
        handler.await {
            suggestionsQueries.deleteAllSuggestionTags()
        }
    }

    override fun observeSources(): Flow<List<SuggestionSource>> {
        return handler.subscribeToList {
            suggestionsQueries.getSuggestionSources { sourceId, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionSource(
                    sourceId = sourceId,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun getSources(): List<SuggestionSource> {
        return handler.awaitList {
            suggestionsQueries.getSuggestionSources { sourceId, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionSource(
                    sourceId = sourceId,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun insertSource(source: SuggestionSource) {
        handler.await {
            suggestionsQueries.insertSuggestionSource(
                sourceId = source.sourceId,
                count = source.count,
                isBlocked = if (source.isBlocked) 1L else 0L,
                isUserAdded = if (source.isUserAdded) 1L else 0L,
                sortOrder = source.sortOrder,
            )
        }
    }

    override suspend fun deleteSource(sourceId: Long) {
        handler.await {
            suggestionsQueries.deleteSuggestionSource(sourceId)
        }
    }

    override suspend fun clearSources() {
        handler.await {
            suggestionsQueries.deleteAllSuggestionSources()
        }
    }

    override fun observeDismissed(): Flow<List<String>> {
        return handler.subscribeToList {
            suggestionsQueries.getDismissedSuggestions { mangaUrl, _, _ ->
                mangaUrl
            }
        }
    }

    override suspend fun dismiss(mangaUrl: String, title: String) {
        handler.await {
            suggestionsQueries.insertDismissedSuggestion(
                mangaUrl = mangaUrl,
                title = title,
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun getDismissed(): List<Pair<String, String>> {
        return handler.awaitList {
            suggestionsQueries.getDismissedSuggestions { mangaUrl, title, _ ->
                Pair(mangaUrl, title)
            }
        }
    }

    override suspend fun deleteDismissed(mangaUrl: String) {
        handler.await {
            suggestionsQueries.deleteDismissedSuggestion(mangaUrl)
        }
    }

    override suspend fun clearDismissed() {
        handler.await {
            suggestionsQueries.deleteAllDismissedSuggestions()
        }
    }

    override fun observeAuthors(): Flow<List<SuggestionAuthor>> {
        return handler.subscribeToList {
            suggestionsQueries.getSuggestionAuthors { author, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionAuthor(
                    author = author,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun getAuthors(): List<SuggestionAuthor> {
        return handler.awaitList {
            suggestionsQueries.getSuggestionAuthors { author, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionAuthor(
                    author = author,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun insertAuthor(author: SuggestionAuthor) {
        handler.await {
            suggestionsQueries.insertSuggestionAuthor(
                author = author.author,
                count = author.count,
                isBlocked = if (author.isBlocked) 1L else 0L,
                isUserAdded = if (author.isUserAdded) 1L else 0L,
                sortOrder = author.sortOrder,
            )
        }
    }

    override suspend fun deleteAuthor(author: String) {
        handler.await {
            suggestionsQueries.deleteSuggestionAuthor(author)
        }
    }

    override suspend fun clearAuthors() {
        handler.await {
            suggestionsQueries.deleteAllSuggestionAuthors()
        }
    }

    override fun observeArtists(): Flow<List<SuggestionArtist>> {
        return handler.subscribeToList {
            suggestionsQueries.getSuggestionArtists { artist, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionArtist(
                    artist = artist,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun getArtists(): List<SuggestionArtist> {
        return handler.awaitList {
            suggestionsQueries.getSuggestionArtists { artist, count, isBlocked, isUserAdded, sortOrder ->
                SuggestionArtist(
                    artist = artist,
                    count = count,
                    isBlocked = isBlocked == 1L,
                    isUserAdded = isUserAdded == 1L,
                    sortOrder = sortOrder,
                )
            }
        }
    }

    override suspend fun insertArtist(artist: SuggestionArtist) {
        handler.await {
            suggestionsQueries.insertSuggestionArtist(
                artist = artist.artist,
                count = artist.count,
                isBlocked = if (artist.isBlocked) 1L else 0L,
                isUserAdded = if (artist.isUserAdded) 1L else 0L,
                sortOrder = artist.sortOrder,
            )
        }
    }

    override suspend fun deleteArtist(artist: String) {
        handler.await {
            suggestionsQueries.deleteSuggestionArtist(artist)
        }
    }

    override suspend fun clearArtists() {
        handler.await {
            suggestionsQueries.deleteAllSuggestionArtists()
        }
    }
}
