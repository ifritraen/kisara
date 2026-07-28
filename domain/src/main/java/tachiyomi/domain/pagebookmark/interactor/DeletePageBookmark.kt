package tachiyomi.domain.pagebookmark.interactor

import tachiyomi.domain.pagebookmark.repository.PageBookmarkRepository

class DeletePageBookmark(
    private val repository: PageBookmarkRepository,
) {
    suspend fun await(id: Long) {
        repository.deleteById(id)
    }
}
