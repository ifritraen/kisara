package mihon.domain.migration.usecases

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
// KMK -->
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.util.ThrottleManager
import tachiyomi.domain.chapter.model.NoChaptersException
// KMK <--
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val coverCache: CoverCache,
    // KMK -->
    private val getHistory: GetHistory,
    private val upsertHistory: UpsertHistory,
    private val getMangaExternalMetadata: tachiyomi.domain.manga.interactor.GetMangaExternalMetadata,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    // KMK <--
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }
    // KMK -->
    private val throttleManager = ThrottleManager()
    // KMK <--

    suspend operator fun invoke(
        current: Manga,
        target: Manga,
        replace: Boolean,
        // KMK -->
        presetFlags: Set<MigrationFlag>? = null,
        // KMK <--
    ): Manga {
        val targetSource = sourceManager.getOrStub(target.source)
        val currentSource = sourceManager.get(current.source)
        val flags = /* KMK --> */ presetFlags ?: /* KMK <-- */ sourcePreferences.migrationFlags().get()

        val localTarget = if (target.id > 0) target else networkToLocalManga(target)
        val initializedTarget = if (!localTarget.initialized) {
            try {
                val sManga = targetSource.getMangaDetails(localTarget.toSManga())
                updateManga.awaitUpdateFromSource(localTarget, sManga, manualFetch = true)
                localTarget.copyFrom(sManga).copy(initialized = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
                localTarget
            }
        } else {
            localTarget
        }

        // Try syncing chapters with source
        val rawChapters = try {
            // SY -->
            if (targetSource is EHentai) {
                targetSource.getChapterList(initializedTarget.toSManga(), throttleManager::throttle)
            } else {
                targetSource.getChapterList(initializedTarget.toSManga())
            }
            // SY <--
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, throwable = e)
            emptyList()
        }

        if (rawChapters.isNotEmpty()) {
            try {
                syncChaptersWithSource.await(rawChapters, initializedTarget, targetSource)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        val targetChapters = getChaptersByMangaId.await(initializedTarget.id)
        if (targetChapters.isEmpty()) {
            throw NoChaptersException()
        }

        // Update chapters read, bookmark and dateFetch
        if (MigrationFlag.CHAPTER in flags) {
            val prevMangaChapters = getChaptersByMangaId.await(current.id)
            val mangaChapters = targetChapters

            val maxChapterRead = prevMangaChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            // SY -->
            val historyUpdates = mutableListOf<HistoryUpdate>()
            val prevHistoryList = getHistory.await(current.id)
                // SY <--
                // KMK -->
                .associateBy { it.chapterId }
            // KMK <--

            val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                var updatedChapter = mangaChapter
                val prevChapter = if (updatedChapter.isRecognizedNumber) {
                    prevMangaChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }
                } else {
                    val cleanName = updatedChapter.name.trim().lowercase()
                    prevMangaChapters.find { it.name.trim().lowercase() == cleanName }
                }

                if (prevChapter != null) {
                    updatedChapter = updatedChapter.copy(
                        // SY -->
                        // If chapters match then mark new manga's chapters read/unread as old one
                        read = prevChapter.read,
                        // SY <--
                        dateFetch = prevChapter.dateFetch,
                        bookmark = prevChapter.bookmark,
                        lastPageRead = prevChapter.lastPageRead,
                    )
                    // SY -->
                    // KMK -->
                    prevHistoryList[prevChapter.id]?.let { prevHistory ->
                        // KMK <--
                        historyUpdates += HistoryUpdate(
                            mangaChapter.id,
                            prevHistory.readAt ?: return@let,
                            prevHistory.readDuration,
                        )
                    }
                    // SY <--
                }
                // KMK -->
                // If chapters which only present on new manga then mark read up to latest read chapter number
                else /* KMK <-- */ if (updatedChapter.isRecognizedNumber && maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                    updatedChapter = updatedChapter.copy(read = true)
                }

                updatedChapter
            }

            val chapterUpdates = updatedMangaChapters.map {
                ChapterUpdate(
                    id = it.id,
                    mangaId = it.mangaId,
                    read = it.read,
                    bookmark = it.bookmark,
                    lastPageRead = it.lastPageRead,
                    dateFetch = it.dateFetch,
                    sourceOrder = it.sourceOrder,
                    url = it.url,
                    name = it.name,
                    dateUpload = it.dateUpload,
                    chapterNumber = it.chapterNumber,
                    scanlator = it.scanlator,
                    version = it.version,
                )
            }
            try {
                updateChapter.awaitAll(chapterUpdates)
                // SY -->
                upsertHistory.awaitAll(historyUpdates)
                // SY <--
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        // Update categories
        if (MigrationFlag.CATEGORY in flags) {
            try {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setMangaCategories.await(initializedTarget.id, categoryIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        // Update track
        // SY -->
        if (MigrationFlag.TRACK in flags) {
            try {
                // SY <--
                getTracks.await(current.id).mapNotNull { track ->
                    val updatedTrack = track.copy(mangaId = initializedTarget.id)

                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                    if (service != null) {
                        try {
                            service.migrateTrack(updatedTrack, initializedTarget, targetSource)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            logcat(LogPriority.WARN, throwable = e)
                            updatedTrack
                        }
                    } else {
                        updatedTrack
                    }
                }
                    .takeIf { it.isNotEmpty() }
                    ?.let { insertTrack.awaitAll(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        // Delete downloaded
        if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
            try {
                downloadManager.deleteManga(current, currentSource)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        // Update custom cover (recheck if custom cover exists)
        if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover(coverCache)) {
            try {
                coverCache.setCustomCoverToCache(initializedTarget, coverCache.getCustomCoverFile(current.id).inputStream())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, throwable = e)
            }
        }

        // Migrate external metadata
        try {
            val currentMeta = getMangaExternalMetadata.await(current.id)
            if (currentMeta != null) {
                getMangaExternalMetadata.upsert(currentMeta.copy(mangaId = initializedTarget.id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, throwable = e)
        }

        val currentMangaUpdate = MangaUpdate(
            id = current.id,
            favorite = false,
            dateAdded = 0,
        )
            .takeIf { replace }
        val targetMangaUpdate = MangaUpdate(
            id = initializedTarget.id,
            favorite = true,
            chapterFlags = current.chapterFlags
                // KMK -->
                .takeIf { MigrationFlag.EXTRA in flags },
            // KMK <--
            viewerFlags = current.viewerFlags
                // KMK -->
                .takeIf { MigrationFlag.EXTRA in flags },
            // KMK <--
            dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
            notes = if (MigrationFlag.NOTES in flags) current.notes else null,
        )

        updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))

        return initializedTarget
    }
}
