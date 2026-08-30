package tachiyomi.data.scoring

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.scoring.model.GranularScoreCriterion
import tachiyomi.domain.scoring.model.GranularScoreEntry
import tachiyomi.domain.scoring.model.GranularScoreTemplate
import tachiyomi.domain.scoring.model.GranularTemplateCriterion
import tachiyomi.domain.scoring.repository.GranularScoreRepository

// KMK -->
class GranularScoreRepositoryImpl(
    private val handler: DatabaseHandler,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GranularScoreRepository {

    override suspend fun getByMangaId(mangaId: Long): GranularScoreEntry? {
        return handler.awaitOneOrNull {
            granular_scoringQueries.selectByMangaId(mangaId, ::mapGranularScoreEntry)
        }
    }

    override fun subscribeByMangaId(mangaId: Long): Flow<GranularScoreEntry?> {
        return handler.subscribeToOneOrNull {
            granular_scoringQueries.selectByMangaId(mangaId, ::mapGranularScoreEntry)
        }
    }

    override suspend fun getAllEntries(): List<GranularScoreEntry> {
        return handler.awaitList {
            granular_scoringQueries.selectAllEntries(::mapGranularScoreEntry)
        }
    }

    override suspend fun upsertEntry(entry: GranularScoreEntry) {
        handler.await {
            granular_scoringQueries.upsertEntry(
                mangaId = entry.mangaId,
                templateName = entry.templateName,
                criteriaJson = json.encodeToString(entry.criteria),
                totalScore = entry.totalScore,
                scale10Score = entry.scale10Score,
                ignoreUnrated = if (entry.ignoreUnrated) 1L else 0L,
                autoSyncTracker = if (entry.autoSyncTracker) 1L else 0L,
                updatedAt = entry.updatedAt,
            )
        }
    }

    override suspend fun deleteEntryByMangaId(mangaId: Long) {
        handler.await {
            granular_scoringQueries.deleteEntryByMangaId(mangaId)
        }
    }

    override fun subscribeAllTemplates(): Flow<List<GranularScoreTemplate>> {
        return handler.subscribeToList {
            granular_scoringQueries.selectAllTemplates(::mapGranularScoreTemplate)
        }
    }

    override suspend fun getAllTemplates(): List<GranularScoreTemplate> {
        return handler.awaitList {
            granular_scoringQueries.selectAllTemplates(::mapGranularScoreTemplate)
        }
    }

    override suspend fun getTemplatesByMediaType(mediaType: String): List<GranularScoreTemplate> {
        return handler.awaitList {
            granular_scoringQueries.selectTemplatesByMediaType(mediaType, ::mapGranularScoreTemplate)
        }
    }

    override suspend fun getTemplateById(id: Long): GranularScoreTemplate? {
        return handler.awaitOneOrNull {
            granular_scoringQueries.selectTemplateById(id, ::mapGranularScoreTemplate)
        }
    }

    override suspend fun insertTemplate(template: GranularScoreTemplate): Long {
        return handler.awaitOneExecutable {
            granular_scoringQueries.insertTemplate(
                name = template.name,
                mediaType = template.mediaType,
                criteriaJson = json.encodeToString(template.criteria),
                isDefault = if (template.isDefault) 1L else 0L,
            )
            granular_scoringQueries.selectTemplateById(-1L, ::mapGranularScoreTemplate) // fallback
        }.id
    }

    override suspend fun updateTemplate(template: GranularScoreTemplate) {
        handler.await {
            granular_scoringQueries.updateTemplate(
                id = template.id,
                name = template.name,
                mediaType = template.mediaType,
                criteriaJson = json.encodeToString(template.criteria),
                isDefault = if (template.isDefault) 1L else 0L,
            )
        }
    }

    override suspend fun deleteTemplateById(id: Long) {
        handler.await {
            granular_scoringQueries.deleteTemplateById(id)
        }
    }

    private fun mapGranularScoreEntry(
        mangaId: Long,
        templateName: String,
        criteriaJson: String,
        totalScore: Double,
        scale10Score: Double,
        ignoreUnrated: Long,
        autoSyncTracker: Long,
        updatedAt: Long,
    ): GranularScoreEntry {
        val criteriaList = try {
            json.decodeFromString<List<GranularScoreCriterion>>(criteriaJson)
        } catch (_: Exception) {
            emptyList()
        }
        return GranularScoreEntry(
            mangaId = mangaId,
            templateName = templateName,
            criteria = criteriaList,
            totalScore = totalScore,
            scale10Score = scale10Score,
            ignoreUnrated = ignoreUnrated != 0L,
            autoSyncTracker = autoSyncTracker != 0L,
            updatedAt = updatedAt,
        )
    }

    private fun mapGranularScoreTemplate(
        id: Long,
        name: String,
        mediaType: String,
        criteriaJson: String,
        isDefault: Long,
    ): GranularScoreTemplate {
        val criteriaList = try {
            json.decodeFromString<List<GranularTemplateCriterion>>(criteriaJson)
        } catch (_: Exception) {
            emptyList()
        }
        return GranularScoreTemplate(
            id = id,
            name = name,
            mediaType = mediaType,
            criteria = criteriaList,
            isDefault = isDefault != 0L,
        )
    }
}
// KMK <--
