package eu.kanade.tachiyomi.ui.browse.search.model

import tachiyomi.domain.manga.model.Manga

// KMK -->
/**
 * Utility to extract optional tags from search queries and rank results
 * so that entries with higher numbers of optional tag matches receive higher priority.
 */
object SearchQueryTagMatcher {

    /**
     * Extracts optional tags from an advanced search query that contains OR clauses.
     * E.g. `tag:"Action" (tag:"Isekai" OR tag:"Fantasy")` -> `["Isekai", "Fantasy"]`
     */
    fun extractOptionalTags(query: String?): List<String> {
        if (query.isNullOrBlank() || !query.contains(" OR ")) return emptyList()

        val optionalTags = mutableListOf<String>()
        val tokens = query.split(Regex("""\s+"""))
        for (i in tokens.indices) {
            val token = tokens[i].trim().removeSurrounding("(", ")")
            if (token.startsWith("tag:\"") && token.endsWith("\"")) {
                val tag = token.removePrefix("tag:\"").removeSuffix("\"")
                val isPrecededByOr = i > 0 && tokens[i - 1].equals("OR", ignoreCase = true)
                val isFollowedByOr = i < tokens.size - 1 && tokens[i + 1].equals("OR", ignoreCase = true)
                if (isPrecededByOr || isFollowedByOr) {
                    optionalTags.add(tag)
                }
            }
        }
        return optionalTags.distinct()
    }

    /**
     * Ranks a list of manga based on the count of matching optional tags.
     * Titles matching more optional tags are placed earlier in the list.
     */
    fun rankTitlesByOptionalTagMatches(titles: List<Manga>, optionalTags: List<String>): List<Manga> {
        if (optionalTags.isEmpty() || titles.size <= 1) return titles

        val optSet = optionalTags.map { it.lowercase().trim() }.toSet()
        return titles.sortedWith(
            compareByDescending<Manga> { manga ->
                val genres = manga.genre.orEmpty().map { it.lowercase().trim() }
                val tagMatchCount = genres.count { optSet.contains(it) }
                val desc = manga.description
                val descMatchBonus = if (desc != null && optSet.any { desc.contains(it, ignoreCase = true) }) 1 else 0
                tagMatchCount * 2 + descMatchBonus
            }.thenBy { it.title.lowercase() },
        )
    }

    /**
     * Ranks an anime list based on the count of matching optional tags.
     */
    fun rankAnimesByOptionalTagMatches(
        animes: List<tachiyomi.domain.entries.anime.model.Anime>,
        optionalTags: List<String>,
    ): List<tachiyomi.domain.entries.anime.model.Anime> {
        if (optionalTags.isEmpty() || animes.size <= 1) return animes

        val optSet = optionalTags.map { it.lowercase().trim() }.toSet()
        return animes.sortedWith(
            compareByDescending<tachiyomi.domain.entries.anime.model.Anime> { anime ->
                val genres = anime.genre.orEmpty().map { it.lowercase().trim() }
                val tagMatchCount = genres.count { optSet.contains(it) }
                val desc = anime.description
                val descMatchBonus = if (desc != null && optSet.any { desc.contains(it, ignoreCase = true) }) 1 else 0
                tagMatchCount * 2 + descMatchBonus
            }.thenBy { it.title.lowercase() },
        )
    }

    /**
     * Ranks a novel list based on the count of matching optional tags.
     */
    fun rankNovelsByOptionalTagMatches(
        novels: List<tachiyomi.domain.entries.novel.model.Novel>,
        optionalTags: List<String>,
    ): List<tachiyomi.domain.entries.novel.model.Novel> {
        if (optionalTags.isEmpty() || novels.size <= 1) return novels

        val optSet = optionalTags.map { it.lowercase().trim() }.toSet()
        return novels.sortedWith(
            compareByDescending<tachiyomi.domain.entries.novel.model.Novel> { novel ->
                val genres = novel.genre.orEmpty().map { it.lowercase().trim() }
                val tagMatchCount = genres.count { optSet.contains(it) }
                val desc = novel.description
                val descMatchBonus = if (desc != null && optSet.any { desc.contains(it, ignoreCase = true) }) 1 else 0
                tagMatchCount * 2 + descMatchBonus
            }.thenBy { it.title.lowercase() },
        )
    }
}
// KMK <--
