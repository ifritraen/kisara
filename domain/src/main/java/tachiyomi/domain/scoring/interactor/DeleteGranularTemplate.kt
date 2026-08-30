package tachiyomi.domain.scoring.interactor

import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class DeleteGranularTemplate(
    private val repository: GranularScoreRepository,
) {
    suspend fun await(id: Long) {
        if (id > 0) {
            repository.deleteTemplateById(id)
        }
    }
}
// KMK <--
