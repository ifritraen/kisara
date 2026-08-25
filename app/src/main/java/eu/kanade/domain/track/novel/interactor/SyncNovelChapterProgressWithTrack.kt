package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.Tracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack

class SyncNovelChapterProgressWithTrack(
    private val novelChapterRepository: NovelChapterRepository,
    private val insertTrack: InsertNovelTrack,
    private val getNovelChapters: GetNovelChapters,
    private val trackPreferences: TrackPreferences,
    private val resolveTrackProgressSync: ResolveTrackProgressSync,
) {

    suspend fun await(
        novelId: Long,
        remoteTrack: NovelTrack,
        tracker: Tracker,
    ) {
        val sortedChapters = getNovelChapters.await(novelId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val localLastRead = sortedChapters.takeWhile { it.read }
            .lastOrNull()
            ?.chapterNumber
            ?: 0.0
        val action = resolveTrackProgressSync.resolve(
            local = localLastRead,
            remote = remoteTrack.lastChapterRead,
            pullEnabled = trackPreferences.autoSyncProgressFromTrackers().get(),
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        )

        when (action) {
            ResolveTrackProgressSync.SyncAction.NoOp -> Unit
            is ResolveTrackProgressSync.SyncAction.MarkLocalUntil -> {
                val chapterUpdates = sortedChapters
                    .filter { chapter -> chapter.chapterNumber <= action.value && !chapter.read }
                    .map {
                        NovelChapterUpdate(
                            id = it.id,
                            novelId = it.novelId,
                            read = true,
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
                    novelChapterRepository.updateAllChapters(chapterUpdates)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e)
                }
            }
            is ResolveTrackProgressSync.SyncAction.PushRemoteTo -> {
                val updatedTrack = remoteTrack.copy(lastChapterRead = action.value)
                try {
                    tracker.update(updatedTrack.toDbTrack())
                    insertTrack.await(updatedTrack)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e)
                }
            }
        }
    }
}
