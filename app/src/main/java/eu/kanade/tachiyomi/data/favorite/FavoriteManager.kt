package eu.kanade.tachiyomi.data.favorite

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
data class FavoritesData(
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

class FavoriteManager(
    private val context: Context,
    private val json: Json = Injekt.get(),
) {
    private val file = File(context.filesDir, "favorites.json")
    private var cache: FavoritesData = load()

    @Synchronized
    private fun load(): FavoritesData {
        return try {
            if (file.exists()) {
                json.decodeFromString<FavoritesData>(file.readText())
            } else {
                FavoritesData()
            }
        } catch (e: Exception) {
            FavoritesData()
        }
    }

    @Synchronized
    private fun save() {
        try {
            file.writeText(json.encodeToString(cache))
        } catch (e: Exception) {
            // Log/ignore
        }
    }

    fun getAuthors(): List<String> = cache.authors
    fun getArtists(): List<String> = cache.artists
    fun getTags(): List<String> = cache.tags

    @Synchronized
    fun isFavoriteAuthor(author: String): Boolean {
        return cache.authors.contains(author.trim().lowercase())
    }

    @Synchronized
    fun toggleFavoriteAuthor(author: String): Boolean {
        val clean = author.trim().lowercase()
        if (clean.isEmpty()) return false
        val current = cache.authors.toMutableList()
        val added = if (current.contains(clean)) {
            current.remove(clean)
            false
        } else {
            current.add(clean)
            true
        }
        cache = cache.copy(authors = current)
        save()
        return added
    }

    @Synchronized
    fun isFavoriteArtist(artist: String): Boolean {
        return cache.artists.contains(artist.trim().lowercase())
    }

    @Synchronized
    fun toggleFavoriteArtist(artist: String): Boolean {
        val clean = artist.trim().lowercase()
        if (clean.isEmpty()) return false
        val current = cache.artists.toMutableList()
        val added = if (current.contains(clean)) {
            current.remove(clean)
            false
        } else {
            current.add(clean)
            true
        }
        cache = cache.copy(artists = current)
        save()
        return added
    }

    @Synchronized
    fun isFavoriteTag(tag: String): Boolean {
        return cache.tags.contains(tag.trim().lowercase())
    }

    @Synchronized
    fun toggleFavoriteTag(tag: String): Boolean {
        val clean = tag.trim().lowercase()
        if (clean.isEmpty()) return false
        val current = cache.tags.toMutableList()
        val added = if (current.contains(clean)) {
            current.remove(clean)
            false
        } else {
            current.add(clean)
            true
        }
        cache = cache.copy(tags = current)
        save()
        return added
    }
}
