package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaExternalMetadata

interface MangaExternalMetadataRepository {
    suspend fun getByMangaId(mangaId: Long): MangaExternalMetadata?
    fun subscribeByMangaId(mangaId: Long): Flow<MangaExternalMetadata?>
    suspend fun upsert(metadata: MangaExternalMetadata)
    suspend fun deleteByMangaId(mangaId: Long)
}
