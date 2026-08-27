package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.items.episode.model.Episode

@Serializable
data class BackupEpisode(
    // url is called key in 1.x
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var seen: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastSecondSeen is called progress in 1.x
    @ProtoNumber(6) var lastSecondSeen: Long = 0,
    @ProtoNumber(7) var totalSeconds: Long = 0,
    @ProtoNumber(8) var dateFetch: Long = 0,
    @ProtoNumber(9) var dateUpload: Long = 0,
    // episodeNumber is called number in 1.x
    @ProtoNumber(10) var episodeNumber: Float = 0F,
    @ProtoNumber(11) var sourceOrder: Long = 0,
    @ProtoNumber(12) var lastModifiedAt: Long = 0,
    @ProtoNumber(13) var version: Long = 0,
    @ProtoNumber(14) var fillermark: Boolean = false,
    @ProtoNumber(15) var summary: String? = null,
    @ProtoNumber(16) var previewUrl: String? = null,
) {
    fun toEpisodeImpl(): Episode {
        return Episode.create().copy(
            url = this@BackupEpisode.url,
            name = this@BackupEpisode.name,
            episodeNumber = this@BackupEpisode.episodeNumber.toDouble(),
            scanlator = this@BackupEpisode.scanlator,
            seen = this@BackupEpisode.seen,
            bookmark = this@BackupEpisode.bookmark,
            fillermark = this@BackupEpisode.fillermark,
            lastSecondSeen = this@BackupEpisode.lastSecondSeen,
            totalSeconds = this@BackupEpisode.totalSeconds,
            dateFetch = this@BackupEpisode.dateFetch,
            dateUpload = this@BackupEpisode.dateUpload,
            sourceOrder = this@BackupEpisode.sourceOrder,
            lastModifiedAt = this@BackupEpisode.lastModifiedAt,
            version = this@BackupEpisode.version,
            summary = this@BackupEpisode.summary,
            previewUrl = this@BackupEpisode.previewUrl,
        )
    }
}

val backupEpisodeMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        fillermark: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        summary: String?,
        previewUrl: String?,
    ->
    BackupEpisode(
        url = url,
        name = name,
        episodeNumber = episodeNumber.toFloat(),
        scanlator = scanlator,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
        summary = summary,
        previewUrl = previewUrl,
    )
}
