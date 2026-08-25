package eu.kanade.tachiyomi.ui.player.aniskip

import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences

/**
 * Coordinates subtitle synchronization, delay adjustments, speed compensation,
 * and audio/subtitle timing offsets for video playback.
 */
class SubtitleSyncCoordinator(
    private val subtitlePreferences: SubtitlePreferences,
) {

    /**
     * Calculates the adjusted delay based on voice heard time and text seen time.
     */
    fun calculateVoiceSyncDelay(voiceHeardPositionMs: Long, textSeenPositionMs: Long): Int {
        val delta = (voiceHeardPositionMs - textSeenPositionMs).toInt()
        return delta
    }

    /**
     * Applies the computed delay to the subtitle track settings.
     */
    fun applyDelayOffset(currentDelayMs: Int, offsetMs: Int): Int {
        val newDelay = currentDelayMs + offsetMs
        subtitlePreferences.subtitlesDelay().set(newDelay)
        return newDelay
    }

    /**
     * Resets subtitle delay to default (0 ms).
     */
    fun resetDelay() {
        subtitlePreferences.subtitlesDelay().set(0)
    }
}
