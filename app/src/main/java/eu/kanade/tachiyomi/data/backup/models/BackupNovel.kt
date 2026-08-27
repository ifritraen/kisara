package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.novel.model.Novel

@Suppress("DEPRECATION")
@Serializable
data class BackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<BackupNovelChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,

    // Custom metadata values
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    @ProtoNumber(804) var customDescription: String? = null,
    @ProtoNumber(805) var customGenre: List<String>? = null,
    @ProtoNumber(806) var customStatus: Int = 0,
) {
    fun getNovelImpl(): Novel {
        return Novel.create().copy(
            url = this@BackupNovel.url,
            title = this@BackupNovel.title,
            author = this@BackupNovel.author,
            thumbnailUrl = this@BackupNovel.thumbnailUrl,
            description = this@BackupNovel.description,
            genre = this@BackupNovel.genre,
            status = this@BackupNovel.status.toLong(),
            favorite = this@BackupNovel.favorite,
            source = this@BackupNovel.source,
            dateAdded = this@BackupNovel.dateAdded,
            viewerFlags = (this@BackupNovel.viewer_flags ?: this@BackupNovel.viewer).toLong(),
            chapterFlags = this@BackupNovel.chapterFlags.toLong(),
            updateStrategy = this@BackupNovel.updateStrategy,
            lastModifiedAt = this@BackupNovel.lastModifiedAt,
            favoriteModifiedAt = this@BackupNovel.favoriteModifiedAt,
            version = this@BackupNovel.version,
            notes = this@BackupNovel.notes,
            initialized = this@BackupNovel.initialized,
            customTitle = this@BackupNovel.customTitle,
            customAuthor = this@BackupNovel.customAuthor,
            customDescription = this@BackupNovel.customDescription,
            customGenre = this@BackupNovel.customGenre,
            customStatus = if (this@BackupNovel.customStatus != 0) this@BackupNovel.customStatus.toLong() else null,
        )
    }
}
