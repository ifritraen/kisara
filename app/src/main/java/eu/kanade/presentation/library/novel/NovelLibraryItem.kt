package eu.kanade.presentation.library.novel

import androidx.compose.runtime.Immutable
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import tachiyomi.domain.source.novel.service.NovelSourceManager

sealed interface NovelLibraryItem {
    val id: Long
    val category: Long
    val pinned: Boolean
    val unreadCount: Long
    val readCount: Long
    val lastRead: Long
    val totalChapters: Long
    val hasStarted: Boolean
    val hasBookmarks: Boolean
    val dateAdded: Long
    val title: String
    val isDownloaded: Boolean
    val sourceLanguage: String

    /** Returns the underlying [Novel] for cover display, or null for Series. */
    val coverNovel: Novel?

    fun matches(query: String, sourceManager: NovelSourceManager): Boolean

    @Immutable
    data class Single(
        val libraryNovel: LibraryNovel,
        override val isDownloaded: Boolean = false,
        override val sourceLanguage: String = "",
    ) : NovelLibraryItem {
        override val id = libraryNovel.id
        override val category = libraryNovel.category
        override val pinned = libraryNovel.pinned
        override val unreadCount = libraryNovel.unreadCount
        override val readCount = libraryNovel.readCount
        override val lastRead = libraryNovel.lastRead
        override val totalChapters = libraryNovel.totalChapters
        override val hasStarted = libraryNovel.hasStarted
        override val hasBookmarks = libraryNovel.hasBookmarks
        override val dateAdded = libraryNovel.novel.dateAdded
        override val title = libraryNovel.novel.displayTitle
        override val coverNovel = libraryNovel.novel

        override fun matches(query: String, sourceManager: NovelSourceManager): Boolean {
            return libraryNovel.novel.matches(query, sourceManager)
        }
    }

    @Immutable
    data class Series(
        val librarySeries: LibraryNovelSeries,
        override val isDownloaded: Boolean = false,
        override val sourceLanguage: String = "",
    ) : NovelLibraryItem {
        override val id = librarySeries.id
        override val category = librarySeries.categoryId
        override val pinned = librarySeries.pinned
        override val unreadCount = librarySeries.unreadCount
        override val readCount = librarySeries.readCount
        override val lastRead = librarySeries.lastRead
        override val totalChapters = librarySeries.totalChapters
        override val hasStarted = librarySeries.hasStarted
        override val hasBookmarks = librarySeries.entries.any { it.hasBookmarks }
        override val dateAdded = librarySeries.entries.maxOfOrNull { it.novel.dateAdded } ?: 0L
        override val title = librarySeries.title
        override val coverNovel = librarySeries.selectedCoverNovel ?: librarySeries.coverNovels.firstOrNull()

        val covers: List<tachiyomi.domain.entries.novel.model.NovelCover>
            get() = librarySeries.coverNovels.map { it.asNovelCover() }

        override fun matches(query: String, sourceManager: NovelSourceManager): Boolean {
            return title.contains(query, ignoreCase = true) ||
                librarySeries.entries.any { it.novel.matches(query, sourceManager) }
        }
    }

    companion object {
        private fun Novel.matches(
            query: String,
            sourceManager: NovelSourceManager,
        ): Boolean {
            val sourceName by lazy { sourceManager.getOrStub(source).name }
            return title.contains(query, ignoreCase = true) ||
                displayTitle.contains(query, ignoreCase = true) ||
                (author?.contains(query, ignoreCase = true) ?: false) ||
                (description?.contains(query, ignoreCase = true) ?: false) ||
                sourceName.contains(query, ignoreCase = true) ||
                (genre?.any { genre -> genre.contains(query, ignoreCase = true) } ?: false)
        }
    }
}
