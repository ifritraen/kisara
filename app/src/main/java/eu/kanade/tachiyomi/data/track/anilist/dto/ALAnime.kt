package eu.kanade.tachiyomi.data.track.anilist.dto

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALStudios(
    val edges: List<ALStudioEdge> = emptyList(),
)

@Serializable
data class ALStudioEdge(
    val isMain: Boolean = false,
    val node: ALStudioNode,
)

@Serializable
data class ALStudioNode(
    val id: Long? = null,
    val name: String,
)

@Serializable
data class ALStudioSearchResult(
    val data: ALStudioPage,
)

@Serializable
data class ALStudioPage(
    @SerialName("Page")
    val page: ALStudioContainer,
)

@Serializable
data class ALStudioContainer(
    val studios: List<ALStudioNode> = emptyList(),
)

@Serializable
data class ALGenreCollectionResult(
    val data: ALGenreData,
)

@Serializable
data class ALGenreData(
    @SerialName("GenreCollection")
    val genres: List<String> = emptyList(),
)

data class ALAnime(
    val remoteId: Long,
    val title: String,
    val imageUrl: String,
    val description: String?,
    val format: String,
    val publishingStatus: String,
    val startDateFuzzy: Long,
    val totalEpisodes: Long,
    val averageScore: Int,
    val studios: ALStudios = ALStudios(),
    val genres: List<String> = emptyList(),
) {
    /**
     * Convert medium cover URL to large for better quality.
     * Anilist API returns medium by default, but large is available by changing the path.
     * Example: .../cover/medium/bx200556-TezRfCQCFLbA.png -> .../cover/large/bx200556-TezRfCQCFLbA.png
     */
    val largeImageUrl: String
        get() = imageUrl.replace("/medium/", "/large/")

    fun toTrack() = AnimeTrackSearch.create(TrackerManager.ANILIST).apply {
        remote_id = remoteId
        title = this@ALAnime.title
        total_episodes = totalEpisodes
        cover_url = largeImageUrl
        summary = description?.htmlDecode() ?: ""
        score = averageScore.toDouble()
        tracking_url = AnilistApi.animeUrl(remote_id)
        publishing_status = publishingStatus
        publishing_type = format
        genres = this@ALAnime.genres
        if (startDateFuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(startDateFuzzy)
            } catch (e: IllegalArgumentException) {
                ""
            }
        }

        val mainStudios = studios.edges.filter { it.isMain }
        val effectiveStudios = if (mainStudios.isNotEmpty()) mainStudios else studios.edges
        authors = effectiveStudios.take(3).map { it.node.name }
    }
}

data class ALUserAnime(
    val libraryId: Long,
    val listStatus: String,
    val scoreRaw: Int,
    val episodesSeen: Int,
    val startDateFuzzy: Long,
    val completedDateFuzzy: Long,
    val anime: ALAnime,
    val private: Boolean,
) {
    fun toTrack() = AnimeTrack.create(TrackerManager.ANILIST).apply {
        remote_id = anime.remoteId
        title = anime.title
        status = toTrackStatus()
        score = scoreRaw.toDouble()
        started_watching_date = startDateFuzzy
        finished_watching_date = completedDateFuzzy
        last_episode_seen = episodesSeen.toDouble()
        library_id = libraryId
        total_episodes = anime.totalEpisodes
        private = this@ALUserAnime.private
    }

    private fun toTrackStatus() = when (listStatus) {
        "CURRENT" -> Anilist.WATCHING
        "COMPLETED" -> Anilist.COMPLETED
        "PAUSED" -> Anilist.ON_HOLD
        "DROPPED" -> Anilist.DROPPED
        "PLANNING" -> Anilist.PLAN_TO_WATCH
        "REPEATING" -> Anilist.REWATCHING
        else -> Anilist.PLAN_TO_WATCH
    }
}
