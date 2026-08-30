package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import kotlin.math.roundToInt

// KMK -->
class SyncGranularScoreWithTrack(
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val trackerManager: TrackerManager,
) {
    suspend fun await(
        mangaId: Long,
        totalScore: Double,
        scale10Score: Double,
    ) {
        val tracks = getTracks.await(mangaId)
        if (tracks.isEmpty()) return

        tracks.forEach { track ->
            val tracker = trackerManager.getTracker(track.trackerId)
            if (tracker != null && tracker.isLoggedIn) {
                try {
                    val dbTrack = track.toDbTrack()
                    val scoreList = tracker.getScoreList()

                    val targetScoreString = when (tracker) {
                        is Anilist -> {
                            // Find closest score item in AniList scoreList
                            scoreList.minByOrNull { scoreItem ->
                                val numericVal = scoreItem.toDoubleOrNull() ?: 0.0
                                if (scoreList.size > 15) {
                                    // 100-point scale
                                    kotlin.math.abs(numericVal - totalScore)
                                } else {
                                    // 10-point scale
                                    kotlin.math.abs(numericVal - scale10Score)
                                }
                            } ?: scoreList.firstOrNull() ?: "0"
                        }
                        is MyAnimeList -> {
                            val malScore = (totalScore / 10.0).roundToInt().coerceIn(0, 10)
                            malScore.toString()
                        }
                        else -> {
                            scoreList.minByOrNull { scoreItem ->
                                val numericVal = scoreItem.toDoubleOrNull() ?: 0.0
                                kotlin.math.abs(numericVal - scale10Score)
                            } ?: scoreList.firstOrNull() ?: "0"
                        }
                    }

                    tracker.setRemoteScore(dbTrack, targetScoreString)
                    dbTrack.toDomainTrack()?.let { insertTrack.await(it) }
                    logcat(LogPriority.INFO) { "Synced granular score (${totalScore}%) to ${tracker.name}: $targetScoreString" }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to sync granular score to tracker ${tracker.name}" }
                }
            }
        }
    }
}
// KMK <--
