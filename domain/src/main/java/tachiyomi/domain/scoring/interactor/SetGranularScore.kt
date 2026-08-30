package tachiyomi.domain.scoring.interactor

import tachiyomi.domain.scoring.model.GranularScoreEntry
import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class SetGranularScore(
    private val repository: GranularScoreRepository,
) {
    suspend fun await(entry: GranularScoreEntry) {
        repository.upsertEntry(entry)
    }
}
// KMK <--
