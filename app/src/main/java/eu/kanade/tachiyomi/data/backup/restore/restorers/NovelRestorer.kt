package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelChapter
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.entries.novel.NovelMapper
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.novel.data.NovelDatabase
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class NovelRestorer(
    private var isSync: Boolean = false,

    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
) {

    suspend fun sortByNew(backupNovels: List<BackupNovel>): List<BackupNovel> {
        val urlsBySource = handler.awaitList { db -> db.novelsQueries.getAllNovelSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupNovels
            .sortedWith(
                compareBy<BackupNovel> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupNovel: BackupNovel,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) { db ->
            val dbNovel = findExistingNovel(backupNovel)
            val novel = backupNovel.getNovelImpl()
            val restoredNovel = if (dbNovel == null) {
                restoreNewNovel(db, novel)
            } else {
                restoreExistingNovel(db, novel, dbNovel)
            }

            restoreNovelDetails(
                db = db,
                novel = restoredNovel,
                chapters = backupNovel.chapters,
                categories = backupNovel.categories,
                backupCategories = backupCategories,
                history = backupNovel.history,
                tracks = backupNovel.tracking,
            )

            if (isSync) {
                db.novelsQueries.resetIsSyncing()
                db.novel_chaptersQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingNovel(backupNovel: BackupNovel): Novel? {
        return handler.awaitOneOrNull { db ->
            db.novelsQueries.getNovelByUrlAndSource(backupNovel.url, backupNovel.source, NovelMapper::mapNovel)
        }
    }

    private fun restoreExistingNovel(db: NovelDatabase, novel: Novel, dbNovel: Novel): Novel {
        return if (novel.version > dbNovel.version) {
            updateNovel(db, dbNovel.copyFrom(novel).copy(id = dbNovel.id))
        } else {
            updateNovel(db, novel.copyFrom(dbNovel).copy(id = dbNovel.id))
        }
    }

    private fun Novel.copyFrom(newer: Novel): Novel {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
            customTitle = newer.customTitle ?: this.customTitle,
            customAuthor = newer.customAuthor ?: this.customAuthor,
            customDescription = newer.customDescription ?: this.customDescription,
            customGenre = newer.customGenre ?: this.customGenre,
            customStatus = newer.customStatus ?: this.customStatus,
        )
    }

    private fun updateNovel(db: NovelDatabase, novel: Novel): Novel {
        db.novelsQueries.update(
            source = novel.source,
            url = novel.url,
            author = novel.author,
            description = novel.description,
            notes = novel.notes,
            genre = novel.genre,
            title = novel.title,
            status = novel.status,
            thumbnailUrl = novel.thumbnailUrl,
            favorite = novel.favorite,
            lastUpdate = novel.lastUpdate,
            nextUpdate = null,
            calculateInterval = null,
            initialized = novel.initialized,
            viewer = novel.viewerFlags,
            chapterFlags = novel.chapterFlags,
            coverLastModified = novel.coverLastModified,
            dateAdded = novel.dateAdded,
            novelId = novel.id,
            updateStrategy = novel.updateStrategy,
            version = novel.version,
            isSyncing = 1,
            pinned = novel.pinned,
        )
        if (novel.customTitle != null || novel.customAuthor != null ||
            novel.customDescription != null || novel.customGenre != null || novel.customStatus != null
        ) {
            db.novelsQueries.updateMetadata(
                customTitle = novel.customTitle,
                customAuthor = novel.customAuthor,
                customDescription = novel.customDescription,
                customGenre = novel.customGenre,
                customStatus = novel.customStatus,
                novelId = novel.id,
            )
        }
        return novel
    }

    private fun restoreNewNovel(db: NovelDatabase, novel: Novel): Novel {
        db.novelsQueries.insert(
            source = novel.source,
            url = novel.url,
            author = novel.author,
            description = novel.description,
            notes = novel.notes,
            genre = novel.genre,
            title = novel.title,
            status = novel.status,
            thumbnailUrl = novel.thumbnailUrl,
            favorite = novel.favorite,
            pinned = novel.pinned,
            lastUpdate = novel.lastUpdate,
            nextUpdate = novel.nextUpdate,
            calculateInterval = novel.fetchInterval.toLong(),
            initialized = novel.initialized,
            viewerFlags = novel.viewerFlags,
            chapterFlags = novel.chapterFlags,
            coverLastModified = novel.coverLastModified,
            dateAdded = novel.dateAdded,
            updateStrategy = novel.updateStrategy,
            version = novel.version,
        )
        val id = db.novelsQueries.selectLastInsertedRowId().executeAsOne()
        if (novel.customTitle != null || novel.customAuthor != null ||
            novel.customDescription != null || novel.customGenre != null || novel.customStatus != null
        ) {
            db.novelsQueries.updateMetadata(
                customTitle = novel.customTitle,
                customAuthor = novel.customAuthor,
                customDescription = novel.customDescription,
                customGenre = novel.customGenre,
                customStatus = novel.customStatus,
                novelId = id,
            )
        }
        return novel.copy(id = id)
    }

    private fun restoreNovelDetails(
        db: NovelDatabase,
        novel: Novel,
        chapters: List<BackupNovelChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
    ) {
        restoreChapters(db, novel, chapters)
        restoreNovelCategories(db, novel, categories, backupCategories)
        restoreNovelTracking(db, novel, tracks)
        restoreNovelHistory(db, history, novel.id)
    }

    private fun restoreChapters(db: NovelDatabase, novel: Novel, backupChapters: List<BackupNovelChapter>) {
        val dbChapters = db.novel_chaptersQueries.getChaptersByNovelId(
            novelId = novel.id,
            applyScanlatorFilter = 0L,
            mapper = ::mapNovelChapter,
        ).executeAsList()
        val dbChaptersByUrl = dbChapters.associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.toNovelChapterImpl().copy(novelId = novel.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter
                    chapter.forComparison() == dbChapter.forComparison() -> null
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter)
                }
            }
            .partition { it.id > 0 }

        for (chapter in newChapters) {
            db.novel_chaptersQueries.insert(
                novelId = chapter.novelId,
                url = chapter.url,
                name = chapter.name,
                scanlator = chapter.scanlator,
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.lastPageRead,
                chapterNumber = chapter.chapterNumber,
                sourceOrder = chapter.sourceOrder,
                dateFetch = chapter.dateFetch,
                dateUpload = chapter.dateUpload,
                dateUploadRaw = chapter.dateUploadRaw,
                version = chapter.version,
            )
        }

        for (chapter in existingChapters) {
            db.novel_chaptersQueries.update(
                chapterId = chapter.id,
                novelId = null,
                url = null,
                name = null,
                scanlator = null,
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.lastPageRead,
                chapterNumber = null,
                sourceOrder = null,
                dateFetch = null,
                dateUpload = null,
                dateUploadRaw = null,
                version = chapter.version,
                isSyncing = if (isSync) 1 else 0,
            )
        }
    }

    private fun updateChapterBasedOnSyncState(chapter: NovelChapter, dbChapter: NovelChapter): NovelChapter {
        return if (isSync) {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
                sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
            )
        } else {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
            ).let {
                when {
                    dbChapter.read && !it.read -> it.copy(read = true, lastPageRead = dbChapter.lastPageRead)
                    it.lastPageRead == 0L && dbChapter.lastPageRead != 0L -> it.copy(lastPageRead = dbChapter.lastPageRead)
                    else -> it
                }
            }
        }
    }

    private fun NovelChapter.forComparison() = copy(
        id = 0L,
        novelId = 0L,
        dateFetch = 0L,
        lastModifiedAt = 0L,
        version = 0L,
    )

    private fun restoreNovelCategories(
        db: NovelDatabase,
        novel: Novel,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        db.novels_categoriesQueries.deleteNovelCategoryByNovelId(novel.id)
        if (categories.isNotEmpty()) {
            val dbCategories = db.novel_categoriesQueries.getCategories { id, name, order, flags, hidden, _, parentId ->
                tachiyomi.domain.category.model.Category(id, name, order, flags, parentId, hidden == 1L)
            }.executeAsList()
            val dbCategoriesByName = dbCategories.associateBy { it.name }

            val novelCategories = categories.mapNotNull { categoryOrder ->
                val backupCategory = backupCategories.find { it.order == categoryOrder } ?: return@mapNotNull null
                dbCategoriesByName[backupCategory.name]?.id
            }

            for (categoryId in novelCategories) {
                db.novels_categoriesQueries.insert(novel.id, categoryId)
            }
        }
    }

    private fun restoreNovelTracking(db: NovelDatabase, novel: Novel, tracks: List<BackupTracking>) {
        if (tracks.isNotEmpty()) {
            val existingTracks = db.novel_syncQueries.getTracksByNovelId(novel.id) { id, novelId, trackerId, remoteId, libraryId, title, lastChapterRead, totalChapters, status, score, remoteUrl, startDate, finishDate, private ->
                tachiyomi.domain.track.novel.model.NovelTrack(id, novelId, trackerId, remoteId, libraryId, title, lastChapterRead, totalChapters, status, score, remoteUrl, startDate, finishDate, private)
            }.executeAsList()
            val existingTracksBySyncId = existingTracks.associateBy { it.trackerId }

            for (track in tracks) {
                val dbTrack = existingTracksBySyncId[track.syncId.toLong()]
                if (dbTrack != null) {
                    db.novel_syncQueries.update(
                        id = dbTrack.id,
                        novelId = novel.id,
                        syncId = track.syncId.toLong(),
                        mediaId = if (track.mediaIdInt != 0) track.mediaIdInt.toLong() else track.mediaId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead.toDouble()),
                        totalChapter = track.totalChapters.toLong(),
                        status = track.status.toLong(),
                        score = track.score.toDouble(),
                        trackingUrl = track.trackingUrl,
                        startDate = track.startedReadingDate,
                        finishDate = track.finishedReadingDate,
                        private = track.private,
                    )
                } else {
                    db.novel_syncQueries.insert(
                        novelId = novel.id,
                        syncId = track.syncId.toLong(),
                        remoteId = if (track.mediaIdInt != 0) track.mediaIdInt.toLong() else track.mediaId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastChapterRead = track.lastChapterRead.toDouble(),
                        totalChapters = track.totalChapters.toLong(),
                        status = track.status.toLong(),
                        score = track.score.toDouble(),
                        remoteUrl = track.trackingUrl,
                        startDate = track.startedReadingDate,
                        finishDate = track.finishedReadingDate,
                        private = track.private,
                    )
                }
            }
        }
    }

    private fun restoreNovelHistory(db: NovelDatabase, history: List<BackupHistory>, novelId: Long) {
        if (history.isNotEmpty()) {
            val chapters = db.novel_chaptersQueries.getChaptersByNovelId(
                novelId = novelId,
                applyScanlatorFilter = 0L,
                mapper = ::mapNovelChapter,
            ).executeAsList()
            val chaptersByUrl = chapters.associateBy { it.url }

            for (hist in history) {
                val chapter = chaptersByUrl[hist.url] ?: continue
                if (hist.lastRead > 0) {
                    db.novel_historyQueries.upsert(
                        chapterId = chapter.id,
                        readAt = Date(hist.lastRead),
                        time_read = hist.readDuration,
                    )
                }
            }
        }
    }

    private fun mapNovelChapter(
        id: Long,
        novelId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastPageRead: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        dateUploadRaw: String?,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        memo: JsonObject,
    ) = NovelChapter(
        id = id,
        novelId = novelId,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
        dateUploadRaw = dateUploadRaw,
        memo = memo,
    )
}
