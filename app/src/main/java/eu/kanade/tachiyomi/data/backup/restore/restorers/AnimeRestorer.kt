package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.AnimeMapper
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.track.anime.AnimeTrackMapper
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.mi.data.AnimeDatabase
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class AnimeRestorer(
    private var isSync: Boolean = false,

    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
) {

    suspend fun sortByNew(backupAnimes: List<BackupAnime>): List<BackupAnime> {
        val urlsBySource = handler.awaitList { db -> db.animesQueries.getAllAnimeSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupAnimes
            .sortedWith(
                compareBy<BackupAnime> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupAnime: BackupAnime,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) { db ->
            val dbAnime = findExistingAnime(backupAnime)
            val anime = backupAnime.getAnimeImpl()
            val restoredAnime = if (dbAnime == null) {
                restoreNewAnime(db, anime)
            } else {
                restoreExistingAnime(db, anime, dbAnime)
            }

            restoreAnimeDetails(
                db = db,
                anime = restoredAnime,
                episodes = backupAnime.episodes,
                categories = backupAnime.categories,
                backupCategories = backupCategories,
                history = backupAnime.history,
                tracks = backupAnime.tracking,
            )

            if (isSync) {
                db.animesQueries.resetIsSyncing()
                db.episodesQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingAnime(backupAnime: BackupAnime): Anime? {
        return getAnime.await(backupAnime.url, backupAnime.source)
    }

    private fun restoreExistingAnime(db: AnimeDatabase, anime: Anime, dbAnime: Anime): Anime {
        return if (anime.version > dbAnime.version) {
            updateAnime(db, dbAnime.copyFrom(anime).copy(id = dbAnime.id))
        } else {
            updateAnime(db, anime.copyFrom(dbAnime).copy(id = dbAnime.id))
        }
    }

    private fun Anime.copyFrom(newer: Anime): Anime {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            artist = newer.artist,
            author = newer.author,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            backgroundUrl = newer.backgroundUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
            customTitle = newer.customTitle ?: this.customTitle,
            customArtist = newer.customArtist ?: this.customArtist,
            customAuthor = newer.customAuthor ?: this.customAuthor,
            customDescription = newer.customDescription ?: this.customDescription,
            customGenre = newer.customGenre ?: this.customGenre,
            customStatus = newer.customStatus ?: this.customStatus,
        )
    }

    private fun updateAnime(db: AnimeDatabase, anime: Anime): Anime {
        db.animesQueries.update(
            source = anime.source,
            url = anime.url,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            notes = anime.notes,
            genre = anime.genre,
            title = anime.title,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            backgroundUrl = anime.backgroundUrl,
            favorite = anime.favorite,
            lastUpdate = anime.lastUpdate,
            nextUpdate = null,
            calculateInterval = null,
            initialized = anime.initialized,
            viewer = anime.viewerFlags,
            episodeFlags = anime.episodeFlags,
            coverLastModified = anime.coverLastModified,
            backgroundLastModified = anime.backgroundLastModified,
            dateAdded = anime.dateAdded,
            animeId = anime.id,
            updateStrategy = anime.updateStrategy.let(AnimeUpdateStrategyColumnAdapter::encode),
            version = anime.version,
            isSyncing = 1,
            fetchType = anime.fetchType.let(FetchTypeColumnAdapter::encode),
            parentId = anime.parentId,
            seasonFlags = anime.seasonFlags,
            seasonNumber = anime.seasonNumber,
            seasonSourceOrder = anime.seasonSourceOrder,
            pinned = anime.pinned,
        )
        if (anime.customTitle != null || anime.customArtist != null || anime.customAuthor != null ||
            anime.customDescription != null || anime.customGenre != null || anime.customStatus != null
        ) {
            db.animesQueries.updateMetadata(
                customTitle = anime.customTitle,
                customArtist = anime.customArtist,
                customAuthor = anime.customAuthor,
                customDescription = anime.customDescription,
                customGenre = anime.customGenre,
                customStatus = anime.customStatus,
                animeId = anime.id,
            )
        }
        return anime
    }

    private fun restoreNewAnime(db: AnimeDatabase, anime: Anime): Anime {
        db.animesQueries.insert(
            source = anime.source,
            url = anime.url,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            notes = anime.notes,
            genre = anime.genre,
            title = anime.title,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            backgroundUrl = anime.backgroundUrl,
            favorite = anime.favorite,
            pinned = anime.pinned,
            lastUpdate = anime.lastUpdate,
            nextUpdate = anime.nextUpdate,
            calculateInterval = anime.fetchInterval.toLong(),
            initialized = anime.initialized,
            viewerFlags = anime.viewerFlags,
            episodeFlags = anime.episodeFlags,
            coverLastModified = anime.coverLastModified,
            backgroundLastModified = anime.backgroundLastModified,
            dateAdded = anime.dateAdded,
            updateStrategy = anime.updateStrategy,
            version = anime.version,
            fetchType = anime.fetchType,
            parentId = anime.parentId,
            seasonFlags = anime.seasonFlags,
            seasonNumber = anime.seasonNumber,
            seasonSourceOrder = anime.seasonSourceOrder,
        )
        val id = db.animesQueries.selectLastInsertedRowId().executeAsOne()
        if (anime.customTitle != null || anime.customArtist != null || anime.customAuthor != null ||
            anime.customDescription != null || anime.customGenre != null || anime.customStatus != null
        ) {
            db.animesQueries.updateMetadata(
                customTitle = anime.customTitle,
                customArtist = anime.customArtist,
                customAuthor = anime.customAuthor,
                customDescription = anime.customDescription,
                customGenre = anime.customGenre,
                customStatus = anime.customStatus,
                animeId = id,
            )
        }
        return anime.copy(id = id)
    }

    private fun restoreAnimeDetails(
        db: AnimeDatabase,
        anime: Anime,
        episodes: List<BackupEpisode>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
    ) {
        restoreEpisodes(db, anime, episodes)
        restoreAnimeCategories(db, anime, categories, backupCategories)
        restoreAnimeTracking(db, anime, tracks)
        restoreAnimeHistory(db, history, anime.id)
    }

    private fun restoreEpisodes(db: AnimeDatabase, anime: Anime, backupEpisodes: List<BackupEpisode>) {
        val dbEpisodes = db.episodesQueries.getEpisodesByAnimeId(anime.id, ::mapEpisode).executeAsList()
        val dbEpisodesByUrl = dbEpisodes.associateBy { it.url }

        val (existingEpisodes, newEpisodes) = backupEpisodes
            .mapNotNull { backupEpisode ->
                val episode = backupEpisode.toEpisodeImpl().copy(animeId = anime.id)
                val dbEpisode = dbEpisodesByUrl[episode.url]

                when {
                    dbEpisode == null -> episode
                    episode.forComparison() == dbEpisode.forComparison() -> null
                    else -> updateEpisodeBasedOnSyncState(episode, dbEpisode)
                }
            }
            .partition { it.id > 0 }

        for (episode in newEpisodes) {
            db.episodesQueries.insert(
                animeId = episode.animeId,
                url = episode.url,
                name = episode.name,
                scanlator = episode.scanlator,
                seen = episode.seen,
                bookmark = episode.bookmark,
                fillermark = episode.fillermark,
                lastSecondSeen = episode.lastSecondSeen,
                totalSeconds = episode.totalSeconds,
                episodeNumber = episode.episodeNumber,
                sourceOrder = episode.sourceOrder,
                dateFetch = episode.dateFetch,
                dateUpload = episode.dateUpload,
                version = episode.version,
                summary = episode.summary,
                previewUrl = episode.previewUrl,
            )
        }

        for (episode in existingEpisodes) {
            db.episodesQueries.update(
                episodeId = episode.id,
                animeId = null,
                url = null,
                name = null,
                scanlator = null,
                seen = episode.seen,
                bookmark = episode.bookmark,
                fillermark = episode.fillermark,
                lastSecondSeen = episode.lastSecondSeen,
                totalSeconds = episode.totalSeconds,
                episodeNumber = null,
                sourceOrder = null,
                dateFetch = null,
                dateUpload = null,
                version = episode.version,
                isSyncing = if (isSync) 1 else 0,
                summary = null,
                previewUrl = null,
            )
        }
    }

    private fun updateEpisodeBasedOnSyncState(episode: Episode, dbEpisode: Episode): Episode {
        return if (isSync) {
            episode.copy(
                id = dbEpisode.id,
                bookmark = episode.bookmark || dbEpisode.bookmark,
                fillermark = episode.fillermark || dbEpisode.fillermark,
                seen = episode.seen,
                lastSecondSeen = episode.lastSecondSeen,
                totalSeconds = max(episode.totalSeconds, dbEpisode.totalSeconds),
                sourceOrder = max(episode.sourceOrder, dbEpisode.sourceOrder),
                dateUpload = min(episode.dateUpload, dbEpisode.dateUpload),
            )
        } else {
            episode.copy(
                id = dbEpisode.id,
                bookmark = episode.bookmark || dbEpisode.bookmark,
                fillermark = episode.fillermark || dbEpisode.fillermark,
                sourceOrder = max(episode.sourceOrder, dbEpisode.sourceOrder),
                dateUpload = min(episode.dateUpload, dbEpisode.dateUpload),
            ).let {
                when {
                    dbEpisode.seen && !it.seen -> it.copy(seen = true, lastSecondSeen = dbEpisode.lastSecondSeen)
                    it.lastSecondSeen == 0L && dbEpisode.lastSecondSeen != 0L -> it.copy(lastSecondSeen = dbEpisode.lastSecondSeen)
                    else -> it
                }
            }
        }
    }

    private fun Episode.forComparison() = copy(
        id = 0L,
        animeId = 0L,
        dateFetch = 0L,
        lastModifiedAt = 0L,
        version = 0L,
    )

    private fun restoreAnimeCategories(
        db: AnimeDatabase,
        anime: Anime,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        db.animes_categoriesQueries.deleteAnimeCategoryByAnimeId(anime.id)
        if (categories.isNotEmpty()) {
            val dbCategories = db.categoriesQueries.getCategories(AnimeMapper::mapCategory).executeAsList()
            val dbCategoriesByName = dbCategories.associateBy { it.name }

            val animeCategories = categories.mapNotNull { categoryOrder ->
                val backupCategory = backupCategories.find { it.order == categoryOrder } ?: return@mapNotNull null
                dbCategoriesByName[backupCategory.name]?.id
            }

            for (categoryId in animeCategories) {
                db.animes_categoriesQueries.insert(anime.id, categoryId)
            }
        }
    }

    private fun restoreAnimeTracking(db: AnimeDatabase, anime: Anime, tracks: List<BackupTracking>) {
        if (tracks.isNotEmpty()) {
            val existingTracks = db.anime_syncQueries.getTracksByAnimeId(anime.id, AnimeTrackMapper::mapTrack).executeAsList()
            val existingTracksBySyncId = existingTracks.associateBy { it.trackerId }

            for (track in tracks) {
                val dbTrack = existingTracksBySyncId[track.syncId.toLong()]
                if (dbTrack != null) {
                    db.anime_syncQueries.update(
                        id = dbTrack.id,
                        animeId = anime.id,
                        syncId = track.syncId.toLong(),
                        mediaId = if (track.mediaIdInt != 0) track.mediaIdInt.toLong() else track.mediaId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastEpisodeSeen = max(dbTrack.lastEpisodeSeen, track.lastChapterRead.toDouble()),
                        totalEpisodes = track.totalChapters.toLong(),
                        status = track.status.toLong(),
                        score = track.score.toDouble(),
                        trackingUrl = track.trackingUrl,
                        startDate = track.startedReadingDate,
                        finishDate = track.finishedReadingDate,
                        private = track.private,
                    )
                } else {
                    db.anime_syncQueries.insert(
                        animeId = anime.id,
                        syncId = track.syncId.toLong(),
                        remoteId = if (track.mediaIdInt != 0) track.mediaIdInt.toLong() else track.mediaId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastEpisodeSeen = track.lastChapterRead.toDouble(),
                        totalEpisodes = track.totalChapters.toLong(),
                        status = track.status.toLong(),
                        score = track.score.toDouble(),
                        remoteUrl = track.trackingUrl,
                        startDate = track.startedReadingDate,
                        finishDate = track.finishedReadingDate,
                        private = track.private,
                    )
                }
            }
        }
    }

    private fun restoreAnimeHistory(db: AnimeDatabase, history: List<BackupHistory>, animeId: Long) {
        if (history.isNotEmpty()) {
            val episodes = db.episodesQueries.getEpisodesByAnimeId(animeId, ::mapEpisode).executeAsList()
            val episodesByUrl = episodes.associateBy { it.url }

            for (hist in history) {
                val episode = episodesByUrl[hist.url] ?: continue
                if (hist.lastRead > 0) {
                    db.animehistoryQueries.upsert(
                        episodeId = episode.id,
                        seenAt = Date(hist.lastRead),
                    )
                }
            }
        }
    }

    private fun mapEpisode(
        id: Long,
        animeId: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        summary: String?,
        previewUrl: String?,
        fillermark: Boolean,
        memo: JsonObject,
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        summary = summary,
        previewUrl = previewUrl,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = memo,
    )
}
