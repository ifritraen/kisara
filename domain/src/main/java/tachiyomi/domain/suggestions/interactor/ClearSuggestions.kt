package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ClearSuggestions(
    private val repository: SuggestionRepository,
) {
    suspend fun invoke() {
        repository.clear()
    }
}
