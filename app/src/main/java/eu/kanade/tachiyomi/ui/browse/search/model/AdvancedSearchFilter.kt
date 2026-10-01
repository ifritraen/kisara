package eu.kanade.tachiyomi.ui.browse.search.model

// KMK -->
enum class TagSelectionState {
    UNSELECTED,
    MUST_HAVE, // All must match (AND)
    OPTIONAL,  // Matches any (OR), higher matches rank higher
    EXCLUDED,  // Must not match (NOT / -)
    ;

    companion object {
        val INCLUDED = MUST_HAVE
    }
}

data class AdvancedSearchState(
    val query: String = "",
    val author: String = "",
    val artist: String = "",
    val isIncludeAndMode: Boolean = true, // Retained for backwards-compatibility
    val tagStates: Map<String, TagSelectionState> = emptyMap(),
    val demographic: String = "All",
    val status: String = "All",
    val origin: String = "All",
    val language: String = "All",
    val sort: String = "Popular",
    val minChapters: String = "",
    val maxChapters: String = "",
    val fromYear: String = "",
    val toYear: String = "",
) {
    val mustHaveTags: List<String>
        get() = tagStates.filterValues { it == TagSelectionState.MUST_HAVE }.keys.toList()

    val optionalTags: List<String>
        get() = tagStates.filterValues { it == TagSelectionState.OPTIONAL }.keys.toList()

    val excludedTags: List<String>
        get() = tagStates.filterValues { it == TagSelectionState.EXCLUDED }.keys.toList()

    val includedTags: List<String>
        get() = mustHaveTags + optionalTags

    val activeFilterCount: Int
        get() {
            var count = 0
            if (author.isNotBlank()) count++
            if (artist.isNotBlank()) count++
            count += mustHaveTags.size + optionalTags.size + excludedTags.size
            if (demographic != "All") count++
            if (status != "All") count++
            if (origin != "All") count++
            if (language != "All") count++
            if (sort != "Popular" && sort != "Latest Uploaded") count++
            if (fromYear.isNotBlank() || toYear.isNotBlank()) count++
            if (minChapters.isNotBlank() || maxChapters.isNotBlank()) count++
            return count
        }

    /**
     * Compiles the advanced filters into a search query string suitable for source search.
     * Must-have tags compile into individual AND tag clauses (tag:"...").
     * Optional tags compile into an OR clause ((tag:"..." OR tag:"...")).
     */
    fun compileQuery(): String {
        val parts = mutableListOf<String>()

        if (query.isNotBlank()) {
            parts.add(query.trim())
        }

        if (author.isNotBlank()) {
            parts.add("author:\"${author.trim()}\"")
        }

        if (artist.isNotBlank()) {
            parts.add("artist:\"${artist.trim()}\"")
        }

        // Must-have tags (AND)
        mustHaveTags.forEach { parts.add("tag:\"$it\"") }

        // Optional tags (OR)
        if (optionalTags.isNotEmpty()) {
            val orClause = optionalTags.joinToString(" OR ") { "tag:\"$it\"" }
            if (mustHaveTags.isNotEmpty() || query.isNotBlank() || author.isNotBlank() || artist.isNotBlank()) {
                parts.add("($orClause)")
            } else {
                parts.add(orClause)
            }
        }

        // Excluded tags (NOT)
        excludedTags.forEach { parts.add("-tag:\"$it\"") }

        if (demographic != "All" && demographic != "None") {
            parts.add("tag:\"$demographic\"")
        }

        if (status != "All") {
            parts.add("status:\"$status\"")
        }

        if (origin != "All") {
            parts.add("origin:\"$origin\"")
        }

        return parts.joinToString(" ").trim()
    }
}

object SearchTaxonomies {
    val TAG_TAXONOMIES = mapOf(
        "Genres" to listOf(
            "Action", "Adventure", "Comedy", "Crime", "Drama", "Fantasy",
            "Historical", "Horror", "Isekai", "Magical Girls", "Mecha", "Medical",
            "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
            "Sports", "Superhero", "Thriller", "Tragedy", "Wuxia", "Yaoi", "Yuri",
        ),
        "Themes" to listOf(
            "Aliens", "Animals", "Cooking", "Crossdressing", "Delinquents", "Demons",
            "Genderswap", "Ghosts", "Gyaru", "Harem", "Incest", "Mafia", "Magic",
            "Martial Arts", "Military", "Monster Girls", "Monsters", "Music", "Ninja",
            "Office Workers", "Police", "Post-Apocalyptic", "Reincarnation", "Reverse Harem",
            "Samurai", "School Life", "Supernatural", "Survival", "Time Travel", "Vampires",
            "Video Games", "Villainess", "Virtual Reality", "Zombies",
        ),
        "Formats" to listOf(
            "4-Koma", "Adaptation", "Anthology", "Award Winning", "Doujinshi",
            "Fan Colored", "Full Color", "Long Strip", "Official Colored", "Oneshot",
            "User Created", "Web Comic",
        ),
        "Doujin & Mature" to listOf(
            "Big Breasts", "Maid", "Stockings", "Schoolgirl Uniform", "Swimsuit",
            "Glasses", "Catgirl", "Elf", "Bunny Girl", "Milf", "Teacher", "Nurse",
            "Sole Female", "Sole Male", "Ahegao", "Anal", "BDSM", "Blowjob", "Bukkake",
            "Dark Skin", "Defloration", "Double Penetration", "Femdom", "FFM Threesome",
            "Footjob", "Group", "Handjob", "Hypnosis", "Impregnation", "Kimono", "Lactation",
            "Lingerie", "Mind Break", "Nakadashi", "Older Female", "Paizuri", "Pantyhose",
            "Public Sex", "Rape", "Sex Toys", "Shibari", "Sister", "Slave", "Succubus",
            "Tentacles", "Tomboy", "Tsundere", "Uncensored", "Vanilla", "Virgin", "X-Ray", "Yandere",
        ),
        "Content Warnings" to listOf(
            "Ecchi", "Gore", "Sexual Violence", "Smut",
        ),
    )

    val QUICK_SUGGESTIONS = listOf(
        "Isekai", "Romance", "Action", "Fantasy", "Comedy",
        "Slice of Life", "School Life", "Supernatural", "Adventure",
        "Sci-Fi", "Drama", "Mystery", "Harem", "Vanilla",
    )

    val DEMOGRAPHICS = listOf("All", "Shounen", "Shoujo", "Seinen", "Josei", "None")

    val STATUS_OPTIONS = listOf("All", "Ongoing", "Completed", "Hiatus", "Cancelled")

    val ORIGIN_OPTIONS = listOf("All", "Manga", "Manhwa", "Manhua")

    val SORT_OPTIONS = listOf(
        "Popular", "Latest Uploaded", "Highest Rating",
        "Recently Created", "Total Chapters", "Release Year", "Title (A-Z)", "Title (Z-A)",
    )
}
// KMK <--
