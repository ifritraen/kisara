package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import tachiyomi.core.common.preference.Preference

/**
 * Persists default stream fingerprints globally and per anime.
 */
class DefaultStreamPreferenceStore(
    private val playerPreferences: PlayerPreferences,
) {
    fun perAnimeEnabled(): Preference<Boolean> = playerPreferences.perAnimeDefaultStream()

    fun autoScrollEnabled(): Preference<Boolean> = playerPreferences.autoScrollDefaultStream()

    fun highlightEnabled(): Preference<Boolean> = playerPreferences.showDefaultStreamHighlight()

    fun isFeatureEnabled(): Boolean = playerPreferences.perAnimeDefaultStream().get()

    fun getEffectiveSelector(animeId: Long?): String {
        if (!isFeatureEnabled()) return ""
        if (animeId == null) return ""
        return getPerAnimeMap()[animeId].orEmpty()
    }

    fun setSelector(animeId: Long?, selector: String) {
        if (!isFeatureEnabled() || animeId == null) return
        val map = getPerAnimeMap().toMutableMap()
        if (selector.isBlank()) {
            map.remove(animeId)
        } else {
            map[animeId] = selector
        }
        savePerAnimeMap(map)
    }

    fun clearAll() {
        playerPreferences.defaultStreamSelector().set("")
        playerPreferences.perAnimeDefaultStreamData().set("")
    }

    fun savedAnimeCount(): Int = getPerAnimeMap().size

    private fun getPerAnimeMap(): Map<Long, String> = decode(playerPreferences.perAnimeDefaultStreamData().get())

    private fun savePerAnimeMap(map: Map<Long, String>) {
        playerPreferences.perAnimeDefaultStreamData().set(encode(map))
    }

    companion object {
        private const val ENTRY_SEP = '\u001E'
        private const val KV_SEP = '\u001F'

        fun encode(map: Map<Long, String>): String {
            return map.entries.joinToString(ENTRY_SEP.toString()) { (id, selector) ->
                "$id$KV_SEP$selector"
            }
        }

        fun decode(raw: String): Map<Long, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.split(ENTRY_SEP).mapNotNull { entry ->
                val sep = entry.indexOf(KV_SEP)
                if (sep <= 0) return@mapNotNull null
                val id = entry.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
                val selector = entry.substring(sep + 1)
                if (selector.isBlank()) null else id to selector
            }.toMap()
        }
    }
}
