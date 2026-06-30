package tachiyomi.domain.suggestions.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.suggestions.model.Suggestion

interface SuggestionRepository {

    fun observeAll(): Flow<List<Suggestion>>

    suspend fun count(): Long

    fun observeCount(): Flow<Long>

    suspend fun clear()

    suspend fun replace(suggestions: List<Pair<Manga, Double>>)
}
