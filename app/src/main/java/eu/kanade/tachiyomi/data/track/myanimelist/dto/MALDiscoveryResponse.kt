package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// KMK -->
@Serializable
data class MALAnimeRankingResult(
    val data: List<MALAnimeRankingNode> = emptyList(),
    val paging: MALSearchPaging? = null,
)

@Serializable
data class MALAnimeRankingNode(
    val node: MALAnime,
    val ranking: MALRankingInfo? = null,
)

@Serializable
data class MALMangaRankingResult(
    val data: List<MALMangaRankingNode> = emptyList(),
    val paging: MALSearchPaging? = null,
)

@Serializable
data class MALMangaRankingNode(
    val node: MALManga,
    val ranking: MALRankingInfo? = null,
)

@Serializable
data class MALRankingInfo(
    val rank: Int = 0,
    @SerialName("previous_rank")
    val previousRank: Int? = null,
)

@Serializable
data class MALUserAnimeListResult(
    val data: List<MALUserAnimeNode> = emptyList(),
    val paging: MALSearchPaging? = null,
)

@Serializable
data class MALUserAnimeNode(
    val node: MALAnime,
    @SerialName("list_status")
    val listStatus: MALUserAnimeListStatus? = null,
)

@Serializable
data class MALUserAnimeListStatus(
    val status: String? = null,
    val score: Int = 0,
    @SerialName("num_episodes_watched")
    val numEpisodesWatched: Int = 0,
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)

@Serializable
data class MALUserMangaListResult(
    val data: List<MALUserMangaNode> = emptyList(),
    val paging: MALSearchPaging? = null,
)

@Serializable
data class MALUserMangaNode(
    val node: MALManga,
    @SerialName("list_status")
    val listStatus: MALUserMangaListStatus? = null,
)

@Serializable
data class MALUserMangaListStatus(
    val status: String? = null,
    val score: Int = 0,
    @SerialName("num_chapters_read")
    val numChaptersRead: Int = 0,
    @SerialName("num_volumes_read")
    val numVolumesRead: Int = 0,
    @SerialName("is_rereading")
    val isRereading: Boolean = false,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)

@Serializable
data class MALAnimeSearchResult(
    val data: List<MALAnimeSearchResultNode> = emptyList(),
    val paging: MALSearchPaging? = null,
)

@Serializable
data class MALAnimeSearchResultNode(
    val node: MALAnime,
)

// Generic section wrapper for MAL Home
data class MALHomeSection(
    val key: String,
    val title: String,
    val items: List<MALMediaItem>,
)

// Unified media item model for MAL Anime/Manga discovery
data class MALMediaItem(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val score: Double?,
    val format: String?,
    val status: String?,
    val numEpisodesOrChapters: Long?,
    val isAnime: Boolean,
    val synopsis: String? = null,
    val startDate: String? = null,
    val authors: String? = null,
    val genres: List<String> = emptyList(),
)
// KMK <--
