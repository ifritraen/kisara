package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AutoTrack(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val insertTrack: tachiyomi.domain.track.interactor.InsertTrack = Injekt.get(),
    private val trackPreferences: eu.kanade.domain.track.service.TrackPreferences = Injekt.get(),
) {
    data class AutoTrackResult(
        val trackerName: String,
        val trackTitle: String,
        val mangaId: Long,
    )

    suspend fun execute(manga: Manga, isReading: Boolean = false): List<AutoTrackResult> = withIOContext {
        if (isReading && !trackPreferences.trackOnStartReading().get()) return@withIOContext emptyList()
        val loggedInTrackers = trackerManager.loggedInTrackers().filterNot { it is EnhancedTracker }
        if (loggedInTrackers.isEmpty()) return@withIOContext emptyList()

        val existingTracks = try {
            getTracks.await(manga.id)
        } catch (e: Exception) {
            emptyList()
        }
        val existingMap = existingTracks.associateBy { it.trackerId }

        val resultsList = mutableListOf<AutoTrackResult>()

        for (tracker in loggedInTrackers) {
            val existingTrack = existingMap[tracker.id]
            if (existingTrack != null) {
                if (isReading && tracker.hasNotStartedReading(existingTrack.status)) {
                    try {
                        val dbTrack = existingTrack.toDbTrack()
                        tracker.setRemoteStatus(dbTrack, tracker.getReadingStatus())
                        insertTrack.await(existingTrack.copy(status = tracker.getReadingStatus()))
                        resultsList.add(
                            AutoTrackResult(
                                trackerName = tracker.name,
                                trackTitle = existingTrack.title.ifBlank { manga.title },
                                mangaId = manga.id,
                            ),
                        )
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to update READING status for ${tracker.name}" }
                    }
                }
                continue
            }

            val rawTitle = manga.ogTitle.ifBlank { manga.title }
            var searchResults = try {
                tracker.search(rawTitle)
            } catch (e: Exception) {
                emptyList()
            }

            if (searchResults.isEmpty()) {
                val cleanedTitle = cleanMangaTitle(rawTitle)
                if (cleanedTitle.isNotBlank() && cleanedTitle != rawTitle) {
                    searchResults = try {
                        tracker.search(cleanedTitle)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            val topResult = searchResults.firstOrNull() ?: continue

            try {
                topResult.manga_id = manga.id
                addTracks.bind(tracker, topResult, manga.id)

                if (isReading) {
                    val newTracks = getTracks.await(manga.id)
                    val newlyAdded = newTracks.find { it.trackerId == tracker.id }
                    if (newlyAdded != null && tracker.hasNotStartedReading(newlyAdded.status)) {
                        try {
                            val dbTrack = newlyAdded.toDbTrack()
                            tracker.setRemoteStatus(dbTrack, tracker.getReadingStatus())
                            insertTrack.await(newlyAdded.copy(status = tracker.getReadingStatus()))
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to set initial READING status for ${tracker.name}" }
                        }
                    }
                }

                resultsList.add(
                    AutoTrackResult(
                        trackerName = tracker.name,
                        trackTitle = topResult.title,
                        mangaId = manga.id,
                    ),
                )
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "AutoTrack failed for ${tracker.name} on ${manga.title}" }
            }
        }
        resultsList
    }

    fun cleanMangaTitle(title: String): String {
        return title
            .replace(Regex("""[\(\[\{【〔].*?[\)\]\}】〕]"""), "")
            .replace(Regex("""(?i)\b(season|vol|volume|chapter|ch|part)\.?\s*\d+"""), "")
            .replace(Regex("""[^\w\s\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
