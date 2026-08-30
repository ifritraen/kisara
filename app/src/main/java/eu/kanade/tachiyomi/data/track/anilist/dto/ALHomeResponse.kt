package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Represents a single section's worth of media items from the batched home query
data class ALHomeSection(
    val key: String, // e.g. "trendingMedia", "prev1TopRated"
    val title: String, // display title
    val items: List<ALSearchItem>,
)

// Wraps the complete home page data after parsing
data class ALHomePage(
    val sections: List<ALHomeSection>,
)

// For user list entries (used in My List subtab)
@Serializable
data class ALUserListEntry(
    val id: Long,
    val status: String? = null,
    val score: Double? = null,
    val progress: Int = 0,
    val progressVolumes: Int? = null,
    val private: Boolean = false,
    val startedAt: ALFuzzyDate? = null,
    val completedAt: ALFuzzyDate? = null,
    val updatedAt: Long? = null,
    val notes: String? = null,
    val media: ALSearchItem,
)

// MediaListCollection response shape
@Serializable
data class ALUserListGroup(
    val name: String? = null,
    val isCustomList: Boolean = false,
    val isSplitCompletedList: Boolean = false,
    val status: String? = null,
    val entries: List<ALUserListEntry> = emptyList(),
)

@Serializable
data class ALMediaListCollection(
    val lists: List<ALUserListGroup> = emptyList(),
)

@Serializable
data class ALUserListCollectionWrapper(
    @SerialName("MediaListCollection")
    val collection: ALMediaListCollection,
)

@Serializable
data class ALUserListResult(
    val data: ALUserListCollectionWrapper,
)

// For paginated search results with hasNextPage info
@Serializable 
data class ALPageInfo(
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
)

@Serializable
data class ALPaginatedMedia(
    val media: List<ALSearchItem> = emptyList(),
    val pageInfo: ALPageInfo = ALPageInfo(),
)

@Serializable
data class ALPaginatedSearchPage(
    @SerialName("Page")
    val page: ALPaginatedMedia,
)

@Serializable
data class ALPaginatedSearchResult(
    val data: ALPaginatedSearchPage,
)

// For user statistics
@Serializable
data class ALUserStatsResult(
    val data: ALUserStatsData,
)

@Serializable
data class ALUserStatsData(
    @SerialName("Viewer")
    val viewer: ALUserStatsViewer,
)

@Serializable
data class ALUserStatsViewer(
    val id: Int,
    val name: String,
    val avatar: ALAvatar? = null,
    val bannerImage: String? = null,
    val statistics: ALStatistics? = null,
    val favourites: ALFavourites? = null,
    val options: ALViewerOptions? = null,
    val mediaListOptions: ALMediaListOptions? = null,
)

@Serializable
data class ALAvatar(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class ALStatistics(
    val anime: ALMediaStats? = null,
    val manga: ALMediaStats? = null,
)

@Serializable
data class ALMediaStats(
    val count: Int = 0,
    val episodesWatched: Int? = null,
    val chaptersRead: Int? = null,
    val meanScore: Double = 0.0,
    val minutesWatched: Int? = null,
    val volumesRead: Int? = null,
    val scores: List<ALScoreDistribution>? = null,
    val genres: List<ALGenreStat>? = null,
    val tags: List<ALTagStat>? = null,
    val formats: List<ALFormatStat>? = null,
)

@Serializable
data class ALScoreDistribution(
    val score: Int = 0,
    val count: Int = 0,
)

@Serializable
data class ALGenreStat(
    val genre: String = "",
    val count: Int = 0,
    val meanScore: Double = 0.0,
    val minutesWatched: Int? = null,
    val chaptersRead: Int? = null,
)

@Serializable
data class ALTagStat(
    val tag: ALTagInfo = ALTagInfo(),
    val count: Int = 0,
    val meanScore: Double = 0.0,
)

@Serializable
data class ALTagInfo(
    val name: String = "",
    val isAdult: Boolean = false,
)

@Serializable
data class ALFormatStat(
    val format: String = "",
    val count: Int = 0,
)

@Serializable
data class ALFavourites(
    val anime: ALFavouriteConnection? = null,
    val manga: ALFavouriteConnection? = null,
    val characters: ALFavouriteConnection? = null,
    val staff: ALFavouriteConnection? = null,
)

@Serializable
data class ALFavouriteConnection(
    val nodes: List<ALFavouriteNode> = emptyList(),
)

@Serializable
data class ALFavouriteNode(
    val id: Long = 0,
    val name: ALFavName? = null,
    val title: ALItemTitle? = null,
    val coverImage: ItemCover? = null,
    val image: ALAvatar? = null,
)

@Serializable
data class ALFavName(
    val userPreferred: String? = null,
)

@Serializable
data class ALViewerOptions(
    val displayAdultContent: Boolean = false,
)

@Serializable
data class ALMediaListOptions(
    val scoreFormat: String = "POINT_100",
    val animeList: ALListTypeOptions? = null,
    val mangaList: ALListTypeOptions? = null,
)

@Serializable
data class ALListTypeOptions(
    val sectionOrder: List<String>? = null,
    val splitCompletedSectionByFormat: Boolean = false,
)

// For filter metadata (genres + tags)
@Serializable
data class ALFilterMetadataResult(
    val data: ALFilterMetadataData,
)

@Serializable
data class ALFilterMetadataData(
    @SerialName("GenreCollection")
    val genres: List<String> = emptyList(),
    @SerialName("MediaTagCollection")
    val tags: List<ALTagInfo> = emptyList(),
)
