package eu.kanade.tachiyomi.data.anischedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AniScheduleMedia(
    val id: Long,
    val idMal: Long? = null,
    val title: AniScheduleTitle? = null,
    val format: String? = null,
    val genres: List<String> = emptyList(),
    val duration: Int? = null,
    val seasonYear: Int? = null,
    val coverImage: AniScheduleCoverImage? = null,
    val isAdult: Boolean = false,
    val bannerImage: String? = null,
    val airingSchedule: AniScheduleNodes? = null,
    val zeroEpisode: Boolean = false,
)

@Serializable
data class AniScheduleTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null,
) {
    fun getBestTitle(): String {
        return userPreferred ?: english ?: romaji ?: native ?: "Unknown Title"
    }
}

@Serializable
data class AniScheduleCoverImage(
    val extraLarge: String? = null,
    val medium: String? = null,
    val color: String? = null,
) {
    fun getUrl(): String? = extraLarge ?: medium
}

@Serializable
data class AniScheduleNodes(
    val nodes: List<AniScheduleAiringEpisode> = emptyList(),
)

@Serializable
data class AniScheduleAiringEpisode(
    val episode: Int,
    val airingAt: Long,
)

@Serializable
data class AniScheduleFeedItem(
    val id: Long,
    val idMal: Long? = null,
    val format: String? = null,
    val duration: Int? = null,
    val episode: AniScheduleFeedEpisode? = null,
)

@Serializable
data class AniScheduleFeedEpisode(
    val aired: Int? = null,
    val airedAt: String? = null,
    val addedAt: String? = null,
)
