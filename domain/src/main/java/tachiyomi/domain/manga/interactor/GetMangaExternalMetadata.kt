package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaExternalMetadata
import tachiyomi.domain.manga.repository.MangaExternalMetadataRepository

class GetMangaExternalMetadata(
    private val repository: MangaExternalMetadataRepository,
) {
    suspend fun await(mangaId: Long): MangaExternalMetadata? {
        return repository.getByMangaId(mangaId)
    }

    fun subscribe(mangaId: Long): Flow<MangaExternalMetadata?> {
        return repository.subscribeByMangaId(mangaId)
    }

    suspend fun upsert(metadata: MangaExternalMetadata) {
        repository.upsert(metadata)
    }

    suspend fun delete(mangaId: Long) {
        repository.deleteByMangaId(mangaId)
    }
}
