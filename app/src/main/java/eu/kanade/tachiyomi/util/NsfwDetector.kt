package eu.kanade.tachiyomi.util

import eu.kanade.domain.ui.UiPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object NsfwDetector {

    private val defaultNsfwTags = setOf(
        "hentai", "ecchi", "doujinshi", "mature", "adult", "smut",
        "yaoi", "yuri", "erotica", "nsfw", "18+",
    )

    private val uiPreferences: UiPreferences by lazy { Injekt.get() }

    fun isNsfw(manga: Manga?, title: String? = null): Boolean {
        val checkTitle = title ?: manga?.title ?: ""
        // 1. Check title [Uncensored]
        if (checkTitle.contains("[Uncensored]", ignoreCase = true)) return true

        if (manga == null) return false

        // 2. Check source name (e.g. contains "nsfw")
        try {
            val sourceManager = Injekt.get<SourceManager>()
            val source = sourceManager.get(manga.source)
            if (source != null && source.name.contains("nsfw", ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {
            // Ignore Injekt or source errors in preview
        }

        // 3. Check tags
        val genres = manga.genre
        if (!genres.isNullOrEmpty()) {
            val customTags = uiPreferences.kisaraCustomNsfwTags().get().map { it.lowercase().trim() }.toSet()
            for (genre in genres) {
                val cleanedGenre = genre.lowercase().trim()
                if (defaultNsfwTags.contains(cleanedGenre) || customTags.contains(cleanedGenre)) {
                    return true
                }
            }
        }

        return false
    }
}
