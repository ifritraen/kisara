package tachiyomi.domain.suggestions.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class GetSuggestionTags(
    private val repository: SuggestionRepository,
) {
    fun subscribe(): Flow<List<SuggestionTag>> {
        return repository.observeTags()
    }

    suspend fun await(): List<SuggestionTag> {
        return repository.getTags()
    }
}
