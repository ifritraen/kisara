package eu.kanade.domain.manga.interactor

import android.app.Application
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.ui.track.TrackSeriesItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
data class TrackerContinueItem(
    val id: Long = 0,
    val title: String,
    val coverUrl: String? = null,
    val lastReadChapter: Float? = null,
    val totalChapters: Int? = null,
    val lastWatchedEpisode: Float? = null,
    val totalEpisodes: Int? = null,
    val score: Double? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val sourceName: String = "AniList",
    val synopsis: String? = null,
    val trackingUrl: String? = null,
    val updatedAt: Long = 0,
)

fun TrackerContinueItem.toTrackSeriesItem(mediaType: MediaType): TrackSeriesItem {
    return TrackSeriesItem(
        title = title,
        coverUrl = coverUrl,
        type = when (mediaType) {
            MediaType.ANIME -> "ANIME"
            MediaType.NOVEL -> "NOVEL"
            MediaType.MANGA -> "MANGA"
        },
        status = status,
        rating = score?.let { if (it > 0) "$it" else null },
        score = score,
        description = synopsis,
        trackingUrl = trackingUrl,
        genres = genres,
        trackerId = id,
        mediaType = mediaType,
    )
}

class GetTrackerContinueReading(
    private val app: Application = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun getCacheFile(mediaType: MediaType): File {
        val suffix = when (mediaType) {
            MediaType.MANGA -> "manga"
            MediaType.ANIME -> "anime"
            MediaType.NOVEL -> "novel"
        }
        return File(app.cacheDir, "tracker_continue_${suffix}_cache.json")
    }

    fun getCached(mediaType: MediaType = MediaType.MANGA): List<TrackerContinueItem> {
        return try {
            val file = getCacheFile(mediaType)
            if (file.exists()) {
                json.decodeFromString<List<TrackerContinueItem>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetch(mediaType: MediaType = MediaType.MANGA, force: Boolean = false): List<TrackerContinueItem> = withIOContext {
        if (!force) {
            val cached = getCached(mediaType)
            if (cached.isNotEmpty()) return@withIOContext cached
        }

        val items = mutableListOf<TrackerContinueItem>()

        // 1. AniList
        if (trackerManager.aniList.isLoggedIn) {
            try {
                val (userId, _) = trackerManager.aniList.api.getCurrentUser()
                val type = when (mediaType) {
                    MediaType.ANIME -> "ANIME"
                    MediaType.NOVEL, MediaType.MANGA -> "MANGA"
                }
                val userListResult = trackerManager.aniList.api.getUserMediaList(userId, type)
                val currentEntries = userListResult.data.collection.lists
                    .filter { it.status == "CURRENT" }
                    .flatMap { it.entries }

                for (entry in currentEntries) {
                    val media = entry.media ?: continue
                    if (mediaType == MediaType.NOVEL && media.format != "NOVEL") continue
                    if (mediaType == MediaType.MANGA && media.format == "NOVEL") continue
                    items.add(
                        TrackerContinueItem(
                            id = media.id,
                            title = media.title?.userPreferred ?: "",
                            coverUrl = media.coverImage?.large,
                            lastReadChapter = entry.progress?.toFloat(),
                            totalChapters = media.chapters?.toInt(),
                            lastWatchedEpisode = entry.progress?.toFloat(),
                            totalEpisodes = media.episodes?.toInt(),
                            score = entry.score,
                            status = if (mediaType == MediaType.ANIME) "Watching" else "Reading",
                            genres = media.genres ?: emptyList(),
                            sourceName = "AniList",
                            synopsis = media.description,
                            trackingUrl = if (mediaType == MediaType.ANIME) AnilistApi.animeUrl(media.id) else AnilistApi.mangaUrl(media.id),
                            updatedAt = entry.updatedAt?.toLong() ?: 0L,
                        ),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch AniList continue reading for $mediaType" }
            }
        }

        // 2. MyAnimeList (MAL)
        if (items.isEmpty() && trackerManager.myAnimeList.isLoggedIn) {
            try {
                val isAnime = mediaType == MediaType.ANIME
                if (isAnime) {
                    val userAnime = trackerManager.myAnimeList.api.getUserAnimeList("watching")
                    for (item in userAnime) {
                        items.add(
                            TrackerContinueItem(
                                id = item.node.id,
                                title = item.node.title,
                                coverUrl = item.node.covers?.large,
                                lastWatchedEpisode = item.listStatus?.numEpisodesWatched?.toFloat(),
                                totalEpisodes = item.node.numEpisodes.toInt(),
                                score = item.listStatus?.score?.toDouble(),
                                status = "Watching",
                                genres = item.node.genres?.map { it.name } ?: emptyList(),
                                sourceName = "MyAnimeList",
                                synopsis = item.node.synopsis,
                                trackingUrl = "https://myanimelist.net/anime/${item.node.id}",
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                } else {
                    val userManga = trackerManager.myAnimeList.api.getUserMangaList("reading")
                    for (item in userManga) {
                        items.add(
                            TrackerContinueItem(
                                id = item.node.id,
                                title = item.node.title,
                                coverUrl = item.node.covers?.large ?: item.node.covers?.medium,
                                lastReadChapter = item.listStatus?.numChaptersRead?.toFloat(),
                                totalChapters = item.node.numChapters.toInt(),
                                score = item.listStatus?.score?.toDouble(),
                                status = "Reading",
                                genres = emptyList(),
                                sourceName = "MyAnimeList",
                                synopsis = item.node.synopsis,
                                trackingUrl = "https://myanimelist.net/manga/${item.node.id}",
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch MAL continue reading for $mediaType" }
            }
        }

        val sorted = items.distinctBy { it.id }.sortedByDescending { it.updatedAt }.take(30)
        if (sorted.isNotEmpty()) {
            saveCache(mediaType, sorted)
            return@withIOContext sorted
        }

        getCached(mediaType)
    }

    private fun saveCache(mediaType: MediaType, items: List<TrackerContinueItem>) {
        try {
            getCacheFile(mediaType).writeText(json.encodeToString(items))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to save tracker continue cache for $mediaType" }
        }
    }
}
