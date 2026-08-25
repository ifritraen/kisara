package eu.kanade.tachiyomi.util.episode

import eu.kanade.domain.items.episode.model.applyFilters
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

private fun Episode.isEpisodeFinished(): Boolean {
    if (seen) return true
    if (totalSeconds > 0 && lastSecondSeen > 0) {
        val progress = lastSecondSeen.toDouble() / totalSeconds.toDouble()
        if (progress >= 0.90) return true
    }
    return false
}

private fun Episode.isEpisodeInProgress(): Boolean {
    if (seen) return false
    if (lastSecondSeen > 0) {
        if (totalSeconds > 0) {
            val progress = lastSecondSeen.toDouble() / totalSeconds.toDouble()
            return progress < 0.90
        }
        return true
    }
    return false
}

/**
 * Gets next unseen episode with filters and sorting applied
 */
fun List<Episode>.getNextUnseen(
    anime: Anime,
    downloadManager: AnimeDownloadManager,
    downloadedOnly: Boolean,
): Episode? {
    return applyFilters(anime, downloadManager, downloadedOnly).let { episodes ->
        val chronological = if (anime.sortDescending()) episodes.reversed() else episodes
        // 1. In-progress episode (user currently watching, not finished < 90%)
        chronological.findLast { it.isEpisodeInProgress() }
            // 2. Next unseen / unfinished episode
            ?: chronological.firstOrNull { !it.isEpisodeFinished() }
            // 3. Fallback to first episode in order
            ?: chronological.firstOrNull()
    }
}

/**
 * Gets next unseen episode with filters and sorting applied
 */
fun List<EpisodeList.Item>.getNextUnseen(
    anime: Anime,
    downloadedOnly: Boolean,
): Episode? {
    return applyFilters(anime, downloadedOnly).toList().let { episodes ->
        val chronological = if (anime.sortDescending()) episodes.reversed() else episodes
        // 1. In-progress episode (user currently watching, not finished < 90%)
        chronological.findLast { it.episode.isEpisodeInProgress() }
            // 2. Next unseen / unfinished episode
            ?: chronological.firstOrNull { !it.episode.isEpisodeFinished() }
            // 3. Fallback to first episode in order
            ?: chronological.firstOrNull()
    }?.episode
}

