package tachiyomi.domain.scoring.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.scoring.model.GranularScoreEntry
import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class GetGranularScore(
    private val repository: GranularScoreRepository,
) {
    suspend fun await(mangaId: Long): GranularScoreEntry? {
        return repository.getByMangaId(mangaId)
    }

    fun subscribe(mangaId: Long): Flow<GranularScoreEntry?> {
        return repository.subscribeByMangaId(mangaId)
    }
}
// KMK <--
