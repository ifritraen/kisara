package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.AnimeTracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack

class SyncEpisodeProgressWithTrack(
    private val updateEpisode: UpdateEpisode,
    private val insertTrack: InsertAnimeTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val trackPreferences: TrackPreferences,
    private val resolveTrackProgressSync: ResolveTrackProgressSync,
) {

    suspend fun await(
        animeId: Long,
        remoteTrack: AnimeTrack,
        service: AnimeTracker,
    ) {
        val sortedEpisodes = getEpisodesByAnimeId.await(animeId)
            .sortedBy { it.episodeNumber }
            .filter { it.isRecognizedNumber }

        val localLastSeen = sortedEpisodes.takeWhile { it.seen }
            .lastOrNull()
            ?.episodeNumber
            ?: 0.0
        val action = resolveTrackProgressSync.resolve(
            local = localLastSeen,
            remote = remoteTrack.lastEpisodeSeen,
            pullEnabled = trackPreferences.autoSyncProgressFromTrackers().get(),
            trigger = ResolveTrackProgressSync.Trigger.OPEN_REFRESH,
        )

        when (action) {
            ResolveTrackProgressSync.SyncAction.NoOp -> Unit
            is ResolveTrackProgressSync.SyncAction.MarkLocalUntil -> {
                val episodeUpdates = sortedEpisodes
                    .filter { episode -> episode.episodeNumber <= action.value && !episode.seen }
                    .map {
                        EpisodeUpdate(
                            id = it.id,
                            animeId = it.animeId,
                            seen = true,
                            bookmark = it.bookmark,
                            lastSecondSeen = it.lastSecondSeen,
                            totalSeconds = it.totalSeconds,
                            dateFetch = it.dateFetch,
                            sourceOrder = it.sourceOrder,
                            url = it.url,
                            name = it.name,
                            dateUpload = it.dateUpload,
                            episodeNumber = it.episodeNumber,
                            scanlator = it.scanlator,
                            version = it.version,
                        )
                    }
                updateEpisode.awaitAll(episodeUpdates)
            }
            is ResolveTrackProgressSync.SyncAction.PushRemoteTo -> {
                val updatedTrack = remoteTrack.copy(lastEpisodeSeen = action.value)
                try {
                    service.update(updatedTrack.toDbTrack())
                    insertTrack.await(updatedTrack)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e)
                }
            }
        }
    }
}
