package tachiyomi.domain.scoring.interactor

import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class DeleteGranularScore(
    private val repository: GranularScoreRepository,
) {
    suspend fun await(mangaId: Long) {
        repository.deleteEntryByMangaId(mangaId)
    }
}
// KMK <--
