package tachiyomi.domain.pagebookmark.model

data class PageBookmark(
    val id: Long = 0,
    val mangaId: Long,
    val chapterId: Long,
    val pageNumber: Int,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
