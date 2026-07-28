package tachiyomi.data.pagebookmark

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.pagebookmark.model.PageBookmark
import tachiyomi.domain.pagebookmark.repository.PageBookmarkRepository

class PageBookmarkRepositoryImpl(
    private val handler: DatabaseHandler,
) : PageBookmarkRepository {

    override fun getByMangaId(mangaId: Long): Flow<List<PageBookmark>> {
        return handler.subscribeToList {
            page_bookmarkQueries.getByMangaId(mangaId, ::mapPageBookmark)
        }
    }

    override fun getByChapterId(chapterId: Long): Flow<List<PageBookmark>> {
        return handler.subscribeToList {
            page_bookmarkQueries.getByChapterId(chapterId, ::mapPageBookmark)
        }
    }

    override suspend fun getByChapterAndPage(chapterId: Long, pageNumber: Int): PageBookmark? {
        return handler.awaitOneOrNull {
            page_bookmarkQueries.getByChapterAndPage(chapterId, pageNumber.toLong(), ::mapPageBookmark)
        }
    }

    override suspend fun insert(bookmark: PageBookmark): Long {
        return handler.awaitOneExecutable(true) {
            page_bookmarkQueries.insert(
                mangaId = bookmark.mangaId,
                chapterId = bookmark.chapterId,
                pageNumber = bookmark.pageNumber.toLong(),
                comment = bookmark.comment,
                createdAt = bookmark.createdAt,
            )
            page_bookmarkQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await {
            page_bookmarkQueries.deleteById(id)
        }
    }

    override suspend fun deleteByChapterAndPage(chapterId: Long, pageNumber: Int) {
        handler.await {
            page_bookmarkQueries.deleteByChapterAndPage(chapterId, pageNumber.toLong())
        }
    }

    private fun mapPageBookmark(
        id: Long,
        mangaId: Long,
        chapterId: Long,
        pageNumber: Long,
        comment: String?,
        createdAt: Long,
    ): PageBookmark {
        return PageBookmark(
            id = id,
            mangaId = mangaId,
            chapterId = chapterId,
            pageNumber = pageNumber.toInt(),
            comment = comment,
            createdAt = createdAt,
        )
    }
}
