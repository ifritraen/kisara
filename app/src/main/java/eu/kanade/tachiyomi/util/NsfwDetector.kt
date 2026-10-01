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
        // KMK -->
        return eu.kanade.tachiyomi.data.ai.NsfwTagClassifier.is18PlusManga(manga, title)
        // KMK <--
    }
}
