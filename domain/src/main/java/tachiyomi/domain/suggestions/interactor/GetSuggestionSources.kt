package tachiyomi.domain.suggestions.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class GetSuggestionSources(
    private val repository: SuggestionRepository,
) {
    fun subscribe(): Flow<List<SuggestionSource>> {
        return repository.observeSources()
    }

    suspend fun await(): List<SuggestionSource> {
        return repository.getSources()
    }
}
