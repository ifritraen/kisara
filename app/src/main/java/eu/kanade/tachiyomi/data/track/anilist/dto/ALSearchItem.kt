package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String?,
    val status: String?,
    val startDate: ALFuzzyDate? = null,
    val chapters: Long?,
    val averageScore: Int?,
    val staff: ALStaff? = null,
) {
    fun toALManga(): ALManga = ALManga(
        remoteId = id,
        title = title.userPreferred,
        imageUrl = coverImage.large,
        description = description,
        format = format?.replace("_", "-") ?: "",
        publishingStatus = status ?: "",
        startDateFuzzy = startDate?.toEpochMilli() ?: 0L,
        totalChapters = chapters ?: 0,
        averageScore = averageScore ?: -1,
        staff = staff ?: ALStaff(emptyList()),
    )
}

@Serializable
data class ALItemTitle(
    val userPreferred: String,
)

@Serializable
data class ItemCover(
    val large: String,
)

@Serializable
data class ALStaff(
    val edges: List<ALEdge>,
)

@Serializable
data class ALEdge(
    val role: String,
    val node: ALStaffNode,
)

@Serializable
data class ALStaffNode(
    val name: ALStaffName,
)

@Serializable
data class ALStaffName(
    val userPreferred: String?,
    val native: String?,
    val full: String?,
) {
    operator fun invoke(): String? {
        return userPreferred ?: full ?: native
    }
}
