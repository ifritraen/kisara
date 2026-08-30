package eu.kanade.tachiyomi.data.track.anilist

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import uy.kohesive.injekt.injectLazy

// KMK -->
/**
 * Persistent cache for AniList media details, stored as JSON in SharedPreferences.
 * ponytail: avoids redundant API calls when reopening previously viewed titles.
 */
class AnilistMediaCache(context: Context) {
    private val json: Json by injectLazy()
    private val prefs = context.getSharedPreferences("anilist_media_cache", Context.MODE_PRIVATE)

    fun getCachedMedia(id: Long): ALSearchItem? {
        val raw = prefs.getString("media_$id", null) ?: return null
        return try {
            json.decodeFromString<ALSearchItem>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun cacheMedia(item: ALSearchItem) {
        try {
            prefs.edit()
                .putString("media_${item.id}", json.encodeToString(item))
                .apply()
        } catch (_: Exception) {
            // ponytail: silently ignore serialization failures
        }
    }

    fun cacheMediaList(items: List<ALSearchItem>) {
        val editor = prefs.edit()
        items.forEach { item ->
            try {
                editor.putString("media_${item.id}", json.encodeToString(item))
            } catch (_: Exception) {
                // skip
            }
        }
        editor.apply()
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
// KMK <--
