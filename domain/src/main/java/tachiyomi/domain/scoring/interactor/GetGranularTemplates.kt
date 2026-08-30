package tachiyomi.domain.scoring.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.scoring.model.GranularScorePresets
import tachiyomi.domain.scoring.model.GranularScoreTemplate
import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class GetGranularTemplates(
    private val repository: GranularScoreRepository,
) {
    fun subscribe(): Flow<List<GranularScoreTemplate>> {
        return repository.subscribeAllTemplates().map { userTemplates ->
            GranularScorePresets.ALL_PRESETS + userTemplates
        }
    }

    suspend fun await(mediaType: String? = null): List<GranularScoreTemplate> {
        val userTemplates = if (mediaType != null) {
            repository.getTemplatesByMediaType(mediaType)
        } else {
            repository.getAllTemplates()
        }
        val presets = if (mediaType != null) {
            GranularScorePresets.ALL_PRESETS.filter { it.mediaType == "ALL" || it.mediaType.equals(mediaType, ignoreCase = true) }
        } else {
            GranularScorePresets.ALL_PRESETS
        }
        return presets + userTemplates
    }
}
// KMK <--
