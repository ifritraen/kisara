package eu.kanade.domain.track.interactor

import android.app.Application
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackOnCategorySet(
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) {

    suspend fun execute(mangaId: Long) = withIOContext {
        val manga = getManga.await(mangaId) ?: return@withIOContext
        execute(manga)
    }

    suspend fun execute(manga: Manga) = withIOContext {
        val primaryTracker = trackPreferences.getPrimaryTracker(trackerManager) ?: return@withIOContext
        if (!primaryTracker.isLoggedIn) return@withIOContext

        try {
            val searchResults = primaryTracker.search(manga.title)
            if (searchResults.isEmpty()) return@withIOContext

            val targetResult = if (searchResults.size == 1) {
                searchResults.first()
            } else {
                // If single exact title match among multiple search results
                searchResults.firstOrNull { it.title.equals(manga.title, ignoreCase = true) }
            }

            if (targetResult != null) {
                targetResult.manga_id = manga.id
                addTracks.bind(primaryTracker, targetResult, manga.id)

                val context = Injekt.get<Application>()
                withUIContext {
                    context.toast("Added to ${primaryTracker.name}: ${targetResult.title}")
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "TrackOnCategorySet failed for ${primaryTracker.name} on ${manga.title}" }
        }
    }
}
