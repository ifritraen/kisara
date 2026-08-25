package tachiyomi.domain.items.episode.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import kotlin.math.floor

fun List<Double>.missingEntriesCount(): Int {
    if (this.isEmpty()) return 0
    val items = filter { it != -1.0 }.map(Double::toInt).distinct().sorted()
    if (items.isEmpty()) return 0
    var missingEntriesCount = 0
    var previousEntry = 0
    for (i in items.indices) {
        val currentEntry = items[i]
        if (currentEntry > previousEntry + 1) {
            missingEntriesCount += currentEntry - previousEntry - 1
        }
        previousEntry = currentEntry
    }
    return missingEntriesCount
}

fun calculateEpisodeGap(higherEpisode: Episode?, lowerEpisode: Episode?): Int {
    if (higherEpisode == null || lowerEpisode == null) return 0
    if (!higherEpisode.isRecognizedNumber || !lowerEpisode.isRecognizedNumber) return 0
    return calculateEpisodeGap(higherEpisode.episodeNumber, lowerEpisode.episodeNumber)
}

fun calculateEpisodeGap(higherEpisodeNumber: Double, lowerEpisodeNumber: Double): Int {
    if (higherEpisodeNumber < 0.0 || lowerEpisodeNumber < 0.0) return 0
    return floor(higherEpisodeNumber).toInt() - floor(lowerEpisodeNumber).toInt() - 1
}

fun getEpisodeSort(anime: Anime, sortDescending: Boolean = anime.sortDescending()): (Episode, Episode) -> Int {
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
        else -> when (sortDescending) {
            true -> { e1, e2 -> e1.sourceOrder.compareTo(e2.sourceOrder) }
            false -> { e1, e2 -> e2.sourceOrder.compareTo(e1.sourceOrder) }
        }
    }
}
