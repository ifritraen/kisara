package tachiyomi.domain.suggestions.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class GetSuggestions(
    private val repository: SuggestionRepository,
) {
    fun subscribe(): Flow<List<Suggestion>> {
        return repository.observeAll()
    }
}
