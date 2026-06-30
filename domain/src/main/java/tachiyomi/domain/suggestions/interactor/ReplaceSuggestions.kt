package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ReplaceSuggestions(
    private val repository: SuggestionRepository,
) {
    suspend fun invoke(suggestions: List<Pair<Manga, Double>>) {
        repository.replace(suggestions)
    }
}
