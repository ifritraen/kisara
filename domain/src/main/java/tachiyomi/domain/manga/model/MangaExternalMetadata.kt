package tachiyomi.domain.manga.model

data class MangaExternalMetadata(
    val mangaId: Long,
    val score: Double?,
    val status: String?,
    val startDate: String?,
    val totalChapters: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val licensor: String?,
    val demographic: String?,
    val synopsis: String?,
    val sourceName: String,
    val fetchedAt: Long,
)
