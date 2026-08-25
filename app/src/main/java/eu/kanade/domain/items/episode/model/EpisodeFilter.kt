package eu.kanade.domain.items.episode.model

import eu.kanade.domain.entries.anime.model.effectiveDownloadedFilter
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.isLocal

private inline fun applyFilter(filter: TriState, predicate: () -> Boolean): Boolean = when (filter) {
    TriState.DISABLED -> true
    TriState.ENABLED_IS -> predicate()
    TriState.ENABLED_NOT -> !predicate()
}

private fun getEpisodeSort(
    anime: Anime,
    sortDescending: Boolean = anime.sortDescending(),
): (Episode, Episode) -> Int {
    return when (anime.sorting) {
        Anime.EPISODE_SORTING_SOURCE -> when (sortDescending) {
            true -> { e1, e2 -> e1.sourceOrder.compareTo(e2.sourceOrder) }
            false -> { e1, e2 -> e2.sourceOrder.compareTo(e1.sourceOrder) }
        }
        Anime.EPISODE_SORTING_NUMBER -> when (sortDescending) {
            true -> { e1, e2 -> e2.episodeNumber.compareTo(e1.episodeNumber) }
            false -> { e1, e2 -> e1.episodeNumber.compareTo(e2.episodeNumber) }
        }
        Anime.EPISODE_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { e1, e2 -> e2.dateUpload.compareTo(e1.dateUpload) }
            false -> { e1, e2 -> e1.dateUpload.compareTo(e2.dateUpload) }
        }
        Anime.EPISODE_SORTING_ALPHABET -> when (sortDescending) {
            true -> { e1, e2 -> e2.name.compareToWithCollator(e1.name) }
            false -> { e1, e2 -> e1.name.compareToWithCollator(e2.name) }
        }
        else -> throw NotImplementedError("Invalid episode sorting method: ${anime.sorting}")
    }
}

/**
 * Applies the view filters to the list of episodes obtained from the database.
 * @return an observable of the list of episodes filtered and sorted.
 */
fun List<Episode>.applyFilters(
    anime: Anime,
    downloadManager: AnimeDownloadManager,
    downloadedOnly: Boolean,
): List<Episode> {
    val isLocalAnime = anime.isLocal()
    val unseenFilter = anime.unseenFilter
    val downloadedFilter = anime.effectiveDownloadedFilter(downloadedOnly)
    val bookmarkedFilter = anime.bookmarkedFilter
    val fillermarkedFilter = anime.fillermarkedFilter

    return asSequence().filter { episode -> applyFilter(unseenFilter) { !episode.seen } }
        .filter { episode -> applyFilter(bookmarkedFilter) { episode.bookmark } }
        .filter { episode -> applyFilter(fillermarkedFilter) { episode.fillermark } }
        .filter { episode ->
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    anime.title,
                    anime.source,
                )
                downloaded || isLocalAnime
            }
        }
        .sortedWith(getEpisodeSort(anime)).toList()
}

/**
 * Applies the view filters to the list of episodes obtained from the database.
 * @return an observable of the list of episodes filtered and sorted.
 */
fun List<EpisodeList.Item>.applyFilters(
    anime: Anime,
    downloadedOnly: Boolean,
): Sequence<EpisodeList.Item> {
    val isLocalAnime = anime.isLocal()
    val unseenFilter = anime.unseenFilter
    val downloadedFilter = anime.effectiveDownloadedFilter(downloadedOnly)
    val bookmarkedFilter = anime.bookmarkedFilter
    val fillermarkedFilter = anime.fillermarkedFilter
    return asSequence()
        .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
        .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
        .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
        .sortedWith { (episode1), (episode2) -> getEpisodeSort(anime).invoke(episode1, episode2) }
}
