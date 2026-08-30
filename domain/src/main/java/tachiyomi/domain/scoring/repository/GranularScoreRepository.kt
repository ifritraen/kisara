package tachiyomi.domain.scoring.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.scoring.model.GranularScoreEntry
import tachiyomi.domain.scoring.model.GranularScoreTemplate

// KMK -->
interface GranularScoreRepository {
    suspend fun getByMangaId(mangaId: Long): GranularScoreEntry?
    fun subscribeByMangaId(mangaId: Long): Flow<GranularScoreEntry?>
    suspend fun getAllEntries(): List<GranularScoreEntry>
    suspend fun upsertEntry(entry: GranularScoreEntry)
    suspend fun deleteEntryByMangaId(mangaId: Long)

    fun subscribeAllTemplates(): Flow<List<GranularScoreTemplate>>
    suspend fun getAllTemplates(): List<GranularScoreTemplate>
    suspend fun getTemplatesByMediaType(mediaType: String): List<GranularScoreTemplate>
    suspend fun getTemplateById(id: Long): GranularScoreTemplate?
    suspend fun insertTemplate(template: GranularScoreTemplate): Long
    suspend fun updateTemplate(template: GranularScoreTemplate)
    suspend fun deleteTemplateById(id: Long)
}
// KMK <--
