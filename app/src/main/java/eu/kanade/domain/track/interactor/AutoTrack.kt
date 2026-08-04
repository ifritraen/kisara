package eu.kanade.domain.track.interactor

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
) {
    data class AutoTrackResult(
        val trackerName: String,
        val trackTitle: String,
        val mangaId: Long,
    )

    suspend fun execute(manga: Manga): List<AutoTrackResult> = withIOContext {
        val loggedInTrackers = trackerManager.loggedInTrackers().filterNot { it is EnhancedTracker }
        if (loggedInTrackers.isEmpty()) return@withIOContext emptyList()

        val existingTracks = try {
            getTracks.await(manga.id).map { it.trackerId }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        val resultsList = mutableListOf<AutoTrackResult>()

        for (tracker in loggedInTrackers) {
            if (tracker.id in existingTracks) continue

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
