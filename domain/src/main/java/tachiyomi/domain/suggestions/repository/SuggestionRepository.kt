package tachiyomi.domain.suggestions.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag

interface SuggestionRepository {

    fun observeAll(): Flow<List<Suggestion>>

    suspend fun count(): Long

    fun observeCount(): Flow<Long>

    suspend fun clear()

    suspend fun replace(suggestions: List<Pair<Manga, Double>>)

    fun observeTags(): Flow<List<SuggestionTag>>

    suspend fun getTags(): List<SuggestionTag>

    suspend fun insertTag(tag: SuggestionTag)

    suspend fun deleteTag(tag: String)

    suspend fun clearTags()

    fun observeSources(): Flow<List<SuggestionSource>>

    suspend fun getSources(): List<SuggestionSource>

    suspend fun insertSource(source: SuggestionSource)

    suspend fun deleteSource(sourceId: Long)

    suspend fun clearSources()

    fun observeAuthors(): Flow<List<SuggestionAuthor>>

    suspend fun getAuthors(): List<SuggestionAuthor>

    suspend fun insertAuthor(author: SuggestionAuthor)

    suspend fun deleteAuthor(author: String)

    suspend fun clearAuthors()

    fun observeArtists(): Flow<List<SuggestionArtist>>

    suspend fun getArtists(): List<SuggestionArtist>

    suspend fun insertArtist(artist: SuggestionArtist)

    suspend fun deleteArtist(artist: String)

    suspend fun clearArtists()

    fun observeDismissed(): Flow<List<String>>

    suspend fun dismiss(mangaUrl: String, title: String)

    suspend fun getDismissed(): List<Pair<String, String>>

    suspend fun deleteDismissed(mangaUrl: String)

    suspend fun clearDismissed()
}
