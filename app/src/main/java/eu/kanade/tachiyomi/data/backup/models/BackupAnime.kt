package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.anime.model.Anime

@Suppress("DEPRECATION")
@Serializable
data class BackupAnime(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var episodeFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var backgroundUrl: String? = null,
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var parentId: Long? = null,
    @ProtoNumber(113) var seasonFlags: Long = 0,
    @ProtoNumber(114) var seasonNumber: Double = -1.0,
    @ProtoNumber(115) var seasonSourceOrder: Long = 0,

    // Custom metadata values
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(801) var customArtist: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    @ProtoNumber(804) var customDescription: String? = null,
    @ProtoNumber(805) var customGenre: List<String>? = null,
    @ProtoNumber(806) var customStatus: Int = 0,
) {
    fun getAnimeImpl(): Anime {
        return Anime.create().copy(
            url = this@BackupAnime.url,
            title = this@BackupAnime.title,
            artist = this@BackupAnime.artist,
            author = this@BackupAnime.author,
            thumbnailUrl = this@BackupAnime.thumbnailUrl,
            backgroundUrl = this@BackupAnime.backgroundUrl,
            description = this@BackupAnime.description,
            genre = this@BackupAnime.genre,
            status = this@BackupAnime.status.toLong(),
            favorite = this@BackupAnime.favorite,
            source = this@BackupAnime.source,
            dateAdded = this@BackupAnime.dateAdded,
            viewerFlags = (this@BackupAnime.viewer_flags ?: this@BackupAnime.viewer).toLong(),
            episodeFlags = this@BackupAnime.episodeFlags.toLong(),
            updateStrategy = this@BackupAnime.updateStrategy,
            lastModifiedAt = this@BackupAnime.lastModifiedAt,
            favoriteModifiedAt = this@BackupAnime.favoriteModifiedAt,
            version = this@BackupAnime.version,
            notes = this@BackupAnime.notes,
            initialized = this@BackupAnime.initialized,
            parentId = this@BackupAnime.parentId,
            seasonFlags = this@BackupAnime.seasonFlags,
            seasonNumber = this@BackupAnime.seasonNumber,
            seasonSourceOrder = this@BackupAnime.seasonSourceOrder,
            customTitle = this@BackupAnime.customTitle,
            customArtist = this@BackupAnime.customArtist,
            customAuthor = this@BackupAnime.customAuthor,
            customDescription = this@BackupAnime.customDescription,
            customGenre = this@BackupAnime.customGenre,
            customStatus = if (this@BackupAnime.customStatus != 0) this@BackupAnime.customStatus.toLong() else null,
        )
    }
}
