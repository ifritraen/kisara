package tachiyomi.domain.suggestions.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class GetSuggestionArtists(
    private val repository: SuggestionRepository,
) {
    fun subscribe(): Flow<List<SuggestionArtist>> {
        return repository.observeArtists()
    }

    suspend fun await(): List<SuggestionArtist> {
        return repository.getArtists()
    }
}
