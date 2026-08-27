package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.backupEpisodeMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBackupCreator(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getHistory: GetAnimeHistory = Injekt.get(),
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return animes.map {
            backupAnime(it, options)
        }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        val animeObject = anime.toBackupAnime()

        if (options.chapters) {
            handler.awaitList { db ->
                db.episodesQueries.getEpisodesByAnimeId(
                    animeId = anime.id,
                    mapper = backupEpisodeMapper,
                )
            }
                .takeUnless(List<BackupEpisode>::isEmpty)
                ?.let { animeObject.episodes = it }
        }

        if (options.categories) {
            val categoriesForAnime = getCategories.await(anime.id)
            if (categoriesForAnime.isNotEmpty()) {
                animeObject.categories = categoriesForAnime.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { db ->
                db.anime_syncQueries.getTracksByAnimeId(anime.id, backupTrackMapper)
            }
            if (tracks.isNotEmpty()) {
                animeObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByAnimeId = getHistory.await(anime.id)
            if (historyByAnimeId.isNotEmpty()) {
                val history = historyByAnimeId.mapNotNull { hist ->
                    try {
                        val episode = handler.awaitOne { db -> db.episodesQueries.getEpisodeById(hist.episodeId) }
                        BackupHistory(episode.url, hist.seenAt?.time ?: 0L, 0L)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (history.isNotEmpty()) {
                    animeObject.history = history
                }
            }
        }

        return animeObject
    }
}

private fun Anime.toBackupAnime() = BackupAnime(
    source = this.source,
    url = this.url,
    title = this.title,
    artist = this.artist,
    author = this.author,
    description = this.description,
    genre = this.genre.orEmpty(),
    status = this.status.toInt(),
    thumbnailUrl = this.thumbnailUrl,
    backgroundUrl = this.backgroundUrl,
    dateAdded = this.dateAdded,
    viewer = this.viewerFlags.toInt(),
    viewer_flags = this.viewerFlags.toInt(),
    episodeFlags = this.episodeFlags.toInt(),
    favorite = this.favorite,
    updateStrategy = this.updateStrategy,
    lastModifiedAt = this.lastModifiedAt,
    favoriteModifiedAt = this.favoriteModifiedAt,
    version = this.version,
    notes = this.notes,
    initialized = this.initialized,
    parentId = this.parentId,
    seasonFlags = this.seasonFlags,
    seasonNumber = this.seasonNumber,
    seasonSourceOrder = this.seasonSourceOrder,
    customTitle = this.customTitle,
    customArtist = this.customArtist,
    customAuthor = this.customAuthor,
    customDescription = this.customDescription,
    customGenre = this.customGenre,
    customStatus = this.customStatus?.toInt() ?: 0,
)
