package tachiyomi.domain.manga.interactor

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaExternalMetadata
import tachiyomi.domain.manga.repository.MangaExternalMetadataRepository

class GetMangaExternalMetadata(
    private val repository: MangaExternalMetadataRepository,
) {
    private val memoryCache = ConcurrentHashMap<Long, MangaExternalMetadata>()

    fun getFromCache(mangaId: Long): MangaExternalMetadata? {
        return memoryCache[mangaId]
    }

    suspend fun await(mangaId: Long): MangaExternalMetadata? {
        memoryCache[mangaId]?.let { return it }
        val result = repository.getByMangaId(mangaId)
        if (result != null) {
            memoryCache[mangaId] = result
        }
        return result
    }

    fun subscribe(mangaId: Long): Flow<MangaExternalMetadata?> {
        return repository.subscribeByMangaId(mangaId)
    }

    suspend fun upsert(metadata: MangaExternalMetadata) {
        memoryCache[metadata.mangaId] = metadata
        repository.upsert(metadata)
    }

    suspend fun delete(mangaId: Long) {
        memoryCache.remove(mangaId)
        repository.deleteByMangaId(mangaId)
    }
}

