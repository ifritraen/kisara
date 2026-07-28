package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ModifySuggestionArtist(
    private val repository: SuggestionRepository,
) {
    suspend fun addArtist(artistText: String) {
        val cleanArtist = artistText.trim().lowercase()
        if (cleanArtist.isEmpty()) return
        val existing = repository.getArtists()
        val match = existing.find { it.artist == cleanArtist }
        if (match != null) {
            repository.insertArtist(match.copy(isBlocked = false, isUserAdded = true))
        } else {
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0L
            repository.insertArtist(
                SuggestionArtist(
                    artist = cleanArtist,
                    count = 1,
                    isBlocked = false,
                    isUserAdded = true,
                    sortOrder = maxSort + 1,
                ),
            )
        }
    }

    suspend fun toggleBlock(artistText: String) {
        val existing = repository.getArtists()
        val match = existing.find { it.artist == artistText } ?: return
        repository.insertArtist(match.copy(isBlocked = !match.isBlocked))
    }

    suspend fun reorder(artists: List<SuggestionArtist>) {
        artists.forEachIndexed { index, artist ->
            repository.insertArtist(artist.copy(sortOrder = index.toLong()))
        }
    }

    suspend fun delete(artistText: String) {
        repository.deleteArtist(artistText)
    }

    suspend fun clear() {
        repository.clearArtists()
    }
}
