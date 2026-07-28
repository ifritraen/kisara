package tachiyomi.domain.pagebookmark.interactor

import tachiyomi.domain.pagebookmark.model.PageBookmark
import tachiyomi.domain.pagebookmark.repository.PageBookmarkRepository

class TogglePageBookmark(
    private val repository: PageBookmarkRepository,
) {
    suspend fun await(mangaId: Long, chapterId: Long, pageNumber: Int, comment: String? = null): Boolean {
        val existing = repository.getByChapterAndPage(chapterId, pageNumber)
        return if (existing != null) {
            repository.deleteById(existing.id)
            false
        } else {
            repository.insert(
                PageBookmark(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageNumber = pageNumber,
                    comment = comment,
                ),
            )
            true
        }
    }
}
