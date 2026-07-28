package tachiyomi.domain.pagebookmark.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.pagebookmark.model.PageBookmark

interface PageBookmarkRepository {
    fun getByMangaId(mangaId: Long): Flow<List<PageBookmark>>
    fun getByChapterId(chapterId: Long): Flow<List<PageBookmark>>
    suspend fun getByChapterAndPage(chapterId: Long, pageNumber: Int): PageBookmark?
    suspend fun insert(bookmark: PageBookmark): Long
    suspend fun deleteById(id: Long)
    suspend fun deleteByChapterAndPage(chapterId: Long, pageNumber: Int)
}
