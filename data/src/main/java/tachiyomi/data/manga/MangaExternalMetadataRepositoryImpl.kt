package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.MangaExternalMetadata
import tachiyomi.domain.manga.repository.MangaExternalMetadataRepository

class MangaExternalMetadataRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaExternalMetadataRepository {

    override suspend fun getByMangaId(mangaId: Long): MangaExternalMetadata? {
        return handler.awaitOneOrNull {
            manga_external_metadataQueries.selectByMangaId(mangaId, ::mapMangaExternalMetadata)
        }
    }

    override fun subscribeByMangaId(mangaId: Long): Flow<MangaExternalMetadata?> {
        return handler.subscribeToOneOrNull {
            manga_external_metadataQueries.selectByMangaId(mangaId, ::mapMangaExternalMetadata)
        }
    }

    override suspend fun upsert(metadata: MangaExternalMetadata) {
        handler.await {
            manga_external_metadataQueries.upsert(
                mangaId = metadata.mangaId,
                score = metadata.score,
                status = metadata.status,
                startDate = metadata.startDate,
                totalChapters = metadata.totalChapters?.toLong(),
                genres = metadata.genres,
                tags = metadata.tags,
                licensor = metadata.licensor,
                demographic = metadata.demographic,
                synopsis = metadata.synopsis,
                sourceName = metadata.sourceName,
                fetchedAt = metadata.fetchedAt,
            )
        }
    }

    override suspend fun deleteByMangaId(mangaId: Long) {
        handler.await {
            manga_external_metadataQueries.deleteByMangaId(mangaId)
        }
    }

    private fun mapMangaExternalMetadata(
        mangaId: Long,
        score: Double?,
        status: String?,
        startDate: String?,
        totalChapters: Long?,
        genres: List<String>?,
        tags: List<String>?,
        licensor: String?,
        demographic: String?,
        synopsis: String?,
        sourceName: String,
        fetchedAt: Long,
    ): MangaExternalMetadata {
        return MangaExternalMetadata(
            mangaId = mangaId,
            score = score,
            status = status,
            startDate = startDate,
            totalChapters = totalChapters?.toInt(),
            genres = genres.orEmpty(),
            tags = tags.orEmpty(),
            licensor = licensor,
            demographic = demographic,
            synopsis = synopsis,
            sourceName = sourceName,
            fetchedAt = fetchedAt,
        )
    }
}
