package tachiyomi.domain.pagebookmark.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.pagebookmark.model.PageBookmark
import tachiyomi.domain.pagebookmark.repository.PageBookmarkRepository

class GetPageBookmarks(
    private val repository: PageBookmarkRepository,
) {
    fun subscribeByMangaId(mangaId: Long): Flow<List<PageBookmark>> {
        return repository.getByMangaId(mangaId)
    }

    fun subscribeByChapterId(chapterId: Long): Flow<List<PageBookmark>> {
        return repository.getByChapterId(chapterId)
    }
}
