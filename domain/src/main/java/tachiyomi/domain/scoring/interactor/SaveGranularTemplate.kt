package tachiyomi.domain.scoring.interactor

import tachiyomi.domain.scoring.model.GranularScoreTemplate
import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class SaveGranularTemplate(
    private val repository: GranularScoreRepository,
) {
    suspend fun await(template: GranularScoreTemplate): Long {
        return if (template.id > 0) {
            repository.updateTemplate(template)
            template.id
        } else {
            repository.insertTemplate(template)
        }
    }
}
// KMK <--
