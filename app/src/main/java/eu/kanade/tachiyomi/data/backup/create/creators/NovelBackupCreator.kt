package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.backupNovelChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
) {

    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        return novels.map {
            backupNovel(it, options)
        }
    }

    private suspend fun backupNovel(novel: Novel, options: BackupOptions): BackupNovel {
        val novelObject = novel.toBackupNovel()

        if (options.chapters) {
            val chapters = handler.awaitList { db ->
                db.novel_chaptersQueries.getChaptersByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = 0L,
                    mapper = backupNovelChapterMapper,
                )
            }
            if (chapters.isNotEmpty()) {
                novelObject.chapters = chapters
            }
        }

        if (options.categories) {
            val categoriesForNovel = getCategories.await(novel.id)
            if (categoriesForNovel.isNotEmpty()) {
                novelObject.categories = categoriesForNovel.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { db ->
                db.novel_syncQueries.getTracksByNovelId(novel.id, backupTrackMapper)
            }
            if (tracks.isNotEmpty()) {
                novelObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByNovelId = historyRepository.getHistoryByNovelId(novel.id)
            if (historyByNovelId.isNotEmpty()) {
                val history = historyByNovelId.mapNotNull { hist ->
                    try {
                        val chapter = handler.awaitOne { db -> db.novel_chaptersQueries.getChapterById(hist.chapterId) }
                        BackupHistory(chapter.url, hist.readAt?.time ?: 0L, hist.readDuration)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (history.isNotEmpty()) {
                    novelObject.history = history
                }
            }
        }

        return novelObject
    }
}

private fun Novel.toBackupNovel() = BackupNovel(
    source = this.source,
    url = this.url,
    title = this.title,
    author = this.author,
    description = this.description,
    genre = this.genre.orEmpty(),
    status = this.status.toInt(),
    thumbnailUrl = this.thumbnailUrl,
    dateAdded = this.dateAdded,
    viewer = this.viewerFlags.toInt(),
    viewer_flags = this.viewerFlags.toInt(),
    chapterFlags = this.chapterFlags.toInt(),
    favorite = this.favorite,
    updateStrategy = this.updateStrategy,
    lastModifiedAt = this.lastModifiedAt,
    favoriteModifiedAt = this.favoriteModifiedAt,
    version = this.version,
    notes = this.notes,
    initialized = this.initialized,
    customTitle = this.customTitle,
    customAuthor = this.customAuthor,
    customDescription = this.customDescription,
    customGenre = this.customGenre,
    customStatus = this.customStatus?.toInt() ?: 0,
)
