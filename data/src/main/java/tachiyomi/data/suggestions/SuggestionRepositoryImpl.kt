package tachiyomi.data.suggestions

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.suggestions.model.Suggestion
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
}
