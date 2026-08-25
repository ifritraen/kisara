package eu.kanade.presentation.entries.anime.components.aurora

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

@Immutable
data class AnimeProgressSnapshot(
    val watchedCount: Int,
    val totalEpisodes: Int,
    val percent: Float?,
) {
    val progressText: String
        get() {
            val percentText = percent?.let { "${(it * 100f).toInt().coerceIn(0, 100)}%" }
            return buildString {
                append(watchedCount.coerceAtLeast(0))
                append('/')
                append(totalEpisodes.coerceAtLeast(0))
                if (percentText != null) {
                    append(" (")
                    append(percentText)
                    append(')')
                }
            }
        }
}

@Immutable
data class AnimeDetailsSnapshot(
    val sourceText: String,
    val dubbingText: String?,
    val ratingValue: Float?,
    val ratingText: String,
    val formatText: String?,
    val statusText: String,
    val statusBadgeText: String,
    val progress: AnimeProgressSnapshot?,
    val episodesText: String,
    val updateText: String,
    val isCompleted: Boolean,
)

fun resolveAnimeDetailsSnapshot(
    anime: Anime,
    watchedCount: Int,
    totalEpisodes: Int,
    sourceName: String,
    selectedDubbing: String?,
    nextUpdate: Instant?,
    sourceRating: Float?,
    animeMetadata: Any? = null,
    isMetadataLoading: Boolean = false,
    metadataError: Any? = null,
    context: Context? = null,
): AnimeDetailsSnapshot {
    val ratingValue = resolveAnimeRatingValue(
        anime = anime,
        sourceRating = sourceRating,
    )
    val ratingText = when {
        sourceRating != null -> String.format(Locale.US, "%.1f", sourceRating)
        isMetadataLoading -> "..."
        else -> ratingValue?.let { String.format(Locale.US, "%.1f", it) } ?: "N/D"
    }
    val formatText: String? = null
    val statusText = context?.let { AnimeStatusFormatter.formatStatus(it, anime.status) }
        ?: AnimeStatusFormatter.formatStatus(anime.status)
    val statusBadgeText = statusText
    val progress = resolveAnimeProgressSnapshot(
        watchedCount = watchedCount,
        totalEpisodes = totalEpisodes,
    )
    val updateText = resolveAnimeUpdateText(
        nextUpdate = nextUpdate,
        isCompleted = anime.status.isAnimeCompleted(),
    )

    return AnimeDetailsSnapshot(
        sourceText = sourceName.trim().ifBlank { "N/D" },
        dubbingText = selectedDubbing.trimOrNull(),
        ratingValue = ratingValue,
        ratingText = ratingText,
        formatText = formatText,
        statusText = statusText,
        statusBadgeText = statusBadgeText,
        progress = progress,
        episodesText = totalEpisodes.toString(),
        updateText = updateText,
        isCompleted = anime.status.isAnimeCompleted(),
    )
}

private fun resolveAnimeRatingValue(
    anime: Anime,
    sourceRating: Float?,
): Float? {
    sourceRating
        ?.takeIf { it >= 0.0f }
        ?.also {
            debugLog(
                "resolveAnimeRatingValue: sourceRating=${it.previewFloat()} title=${anime.title}",
            )
        }
        ?.let { return it }

    return null
}

private fun resolveAnimeProgressSnapshot(
    watchedCount: Int,
    totalEpisodes: Int,
): AnimeProgressSnapshot? {
    if (totalEpisodes <= 0) return null

    val clampedWatchedCount = watchedCount.coerceAtLeast(0).coerceAtMost(totalEpisodes)
    val percent = clampedWatchedCount.toFloat() / totalEpisodes.toFloat()

    return AnimeProgressSnapshot(
        watchedCount = clampedWatchedCount,
        totalEpisodes = totalEpisodes,
        percent = percent,
    )
}

private fun resolveAnimeUpdateText(
    nextUpdate: Instant?,
    isCompleted: Boolean,
): String {
    if (isCompleted) return "N/D"

    val days = nextUpdate?.let {
        Instant.now().until(it, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
    } ?: return "N/D"

    return when (days) {
        0 -> "0d"
        else -> "${days}d"
    }
}

private fun String?.trimOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}

private fun Long.isAnimeCompleted(): Boolean {
    return when (this.toInt()) {
        SAnime.COMPLETED,
        SAnime.PUBLISHING_FINISHED,
        SAnime.CANCELLED,
        -> true

        else -> false
    }
}

private fun Float.previewFloat(): String = String.format(Locale.US, "%.3f", this)

private fun debugLog(message: String) {
    runCatching { Log.d("AnimeDetailsSnapshot", message) }
}

object AnimeStatusFormatter {

    /**
     * Converts anime status code to readable localized text.
     */
    fun formatStatus(context: Context, status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> MR.strings.ongoing.getString(context)
            SAnime.COMPLETED -> MR.strings.completed.getString(context)
            SAnime.LICENSED -> MR.strings.licensed.getString(context)
            SAnime.PUBLISHING_FINISHED -> MR.strings.publishing_finished.getString(context)
            SAnime.CANCELLED -> MR.strings.cancelled.getString(context)
            SAnime.ON_HIATUS -> MR.strings.on_hiatus.getString(context)
            else -> MR.strings.unknown.getString(context)
        }
    }

    fun formatStatus(status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> "ongoing"
            SAnime.COMPLETED -> "completed"
            SAnime.LICENSED -> "licensed"
            SAnime.PUBLISHING_FINISHED -> "publishing finished"
            SAnime.CANCELLED -> "cancelled"
            SAnime.ON_HIATUS -> "on hiatus"
            else -> "unknown"
        }
    }
}
