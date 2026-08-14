package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MUSearchResult(
    val results: List<MUSearchResultItem> = emptyList(),
)

@Serializable
data class MUSearchResultItem(
    val record: MURecord,
)

@Serializable
data class MUReleasesDaysResponse(
    val results: List<MUReleaseDayItem> = emptyList(),
)

@Serializable
data class MUReleaseDayItem(
    val record: MUReleaseRecord,
)

@Serializable
data class MUReleaseRecord(
    val id: Long? = null,
    val title: String? = null,
    val volume: String? = null,
    val chapter: String? = null,
    val groups: List<MUReleaseGroup>? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
)

@Serializable
data class MUReleaseGroup(
    val name: String? = null,
    @SerialName("group_id")
    val groupId: Long? = null,
    val url: String? = null,
)

@Serializable
data class MUGenreItem(
    val id: Long? = null,
    val genre: String? = null,
    val description: String? = null,
    val demographic: Boolean? = null,
    val stats: MUGenreStats? = null,
)

@Serializable
data class MUGenreStats(
    val series: Long? = null,
    val authors: Long? = null,
)

@Serializable
data class MUGroupsSearchResponse(
    val results: List<MUGroupSearchItem> = emptyList(),
)

@Serializable
data class MUGroupSearchItem(
    val record: MUGroupRecord,
)

@Serializable
data class MUGroupRecord(
    @SerialName("group_id")
    val groupId: Long? = null,
    val name: String? = null,
    val url: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class MUAuthorsSearchResponse(
    val results: List<MUAuthorSearchItem> = emptyList(),
)

@Serializable
data class MUAuthorSearchItem(
    val record: MUAuthorRecord,
)

@Serializable
data class MUAuthorRecord(
    val id: Long? = null,
    val name: String? = null,
    val gender: String? = null,
    val birthplace: String? = null,
)

@Serializable
data class MUPublishersSearchResponse(
    val results: List<MUPublisherSearchItem> = emptyList(),
)

@Serializable
data class MUPublisherSearchItem(
    val record: MUPublisherRecord,
)

@Serializable
data class MUPublisherRecord(
    val id: Long? = null,
    val name: String? = null,
    val type: String? = null,
    val url: String? = null,
)

@Serializable
data class MUReviewsSearchResponse(
    val results: List<MUReviewSearchItem> = emptyList(),
)

@Serializable
data class MUReviewSearchItem(
    val record: MUReviewRecord,
)

@Serializable
data class MUReviewRecord(
    val id: Long? = null,
    val title: String? = null,
    val body: String? = null,
    val score: Double? = null,
)

