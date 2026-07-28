package tachiyomi.domain.suggestions.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class GetSuggestionAuthors(
    private val repository: SuggestionRepository,
) {
    fun subscribe(): Flow<List<SuggestionAuthor>> {
        return repository.observeAuthors()
    }

    suspend fun await(): List<SuggestionAuthor> {
        return repository.getAuthors()
    }
}
