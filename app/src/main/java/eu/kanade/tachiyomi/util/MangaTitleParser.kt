package eu.kanade.tachiyomi.util

import tachiyomi.domain.manga.model.Manga

object MangaTitleParser {

    private val LANGUAGE_TO_CODE = mapOf(
        "english" to "en",
        "japanese" to "ja",
        "korean" to "ko",
        "chinese" to "zh",
        "spanish" to "es",
        "italian" to "it",
        "indonesian" to "id",
        "french" to "fr",
        "russian" to "ru",
        "portuguese" to "pt",
        "arabic" to "ar",
    )

    private val COLORIZED_KEYWORDS = setOf("colorized", "color", "full color", "fullcolor", "colored")

    data class ParsedTitle(
        val cleanTitle: String,
        val author: String?,
        val artist: String?,
        val languageCode: String?,
        val isUncensored: Boolean,
        val isColorized: Boolean,
    )

    private val parseCache = java.util.concurrent.ConcurrentHashMap<String, ParsedTitle>(512)

    fun parse(rawTitle: String): ParsedTitle {
        return parseCache.getOrPut(rawTitle) {
            doParse(rawTitle)
        }
    }

    private fun doParse(rawTitle: String): ParsedTitle {
        var title = rawTitle.trim()
        val normalized = title.replace("“", "\"").replace("”", "\"").replace("‘", "'").replace("’", "'")
        title = normalized

        var author: String? = null
        var artist: String? = null
        var languageCode: String? = null
        var isUncensored = false
        var isColorized = false

        // 1. Check for leading volume/sequel in parentheses e.g. (pq)
        val leadingParenRegex = Regex("""^\(([^()]+)\)\s*(.*)$""")
        val leadingParenMatch = leadingParenRegex.find(title)
        if (leadingParenMatch != null) {
            val rest = leadingParenMatch.groupValues[2].trim()
            if (rest.startsWith("[") || rest.startsWith("(") || rest.isNotEmpty()) {
                title = rest
            }
        }

        // 2. Parse leading author/artist bracket: [abc (de)] or [abc] or (abc (de)) or (abc)
        val leadingBracketRegex = Regex("""^[\[({]([^\[\]()]+)\s*(?:\(([^()]+)\))?[\])}]""")
        val match = leadingBracketRegex.find(title)
        if (match != null) {
            val part1 = match.groupValues[1].trim()
            val part2 = match.groupValues.getOrNull(2)?.trim()
            if (!part2.isNullOrEmpty()) {
                author = part1
                artist = part2
            } else {
                author = part1
            }
            title = title.substring(match.range.last + 1).trim()
        }

        // 3. Collect flags from remaining bracketed metadata
        val simpleBracketRegex = Regex("""[\[({]([^\])}]+)[\])}]""")
        val matches = simpleBracketRegex.findAll(title).toList()
        for (m in matches) {
            val inside = m.groupValues[1].trim().lowercase()
            if (LANGUAGE_TO_CODE.containsKey(inside)) {
                languageCode = LANGUAGE_TO_CODE[inside]
            } else if (inside == "uncensored") {
                isUncensored = true
            } else if (COLORIZED_KEYWORDS.contains(inside)) {
                isColorized = true
            }
        }

        // 4. Repeatedly strip all simple bracket expressions to avoid dangling brackets from nested structures
        while (title.contains("(") || title.contains("[") || title.contains("{")) {
            val old = title
            title = title
                .replace(Regex("""\([^()]*\)"""), "")
                .replace(Regex("""\[[^\[\]]*\]"""), "")
                .replace(Regex("""\{[^{}]*\}"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (title == old) break
        }

        // Cleanup trailing non-alphanumeric chars
        title = title.replace(Regex("""\s+[-|/~]\s*$"""), "").trim()

        if (title.isEmpty()) {
            title = rawTitle
        }

        return ParsedTitle(
            cleanTitle = title,
            author = author.takeIf { !it.isNullOrBlank() },
            artist = artist.takeIf { !it.isNullOrBlank() },
            languageCode = languageCode,
            isUncensored = isUncensored,
            isColorized = isColorized,
        )
    }

    fun parseDescriptionTags(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        val results = mutableListOf<String>()
        val lines = description.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains(":")) {
                val parts = trimmed.split(",", ";").map { it.trim() }
                for (part in parts) {
                    if (part.contains(":")) {
                        val cleaned = part.replace(Regex("""[♀♂♀️♂️]"""), "").trim()
                        if (cleaned.isNotEmpty()) {
                            results.add(cleaned)
                        }
                    }
                }
            }
        }
        return results.distinct()
    }

    fun isColorized(manga: Manga?, title: String): Boolean {
        if (parse(title).isColorized) return true
        val genres = manga?.genre
        if (!genres.isNullOrEmpty()) {
            for (genre in genres) {
                val clean = genre.lowercase().trim()
                if (clean == "color" || clean == "full color" || clean == "colored" || clean == "colorized" || clean == "webtoon" || clean == "manhwa") {
                    return true
                }
            }
        }
        return false
    }

    fun isUncensored(manga: Manga?, title: String): Boolean {
        if (parse(title).isUncensored) return true
        val genres = manga?.genre
        if (!genres.isNullOrEmpty()) {
            for (genre in genres) {
                val clean = genre.lowercase().trim()
                if (clean == "uncensored" || clean == "decensored") {
                    return true
                }
            }
        }
        val description = manga?.description
        if (!description.isNullOrEmpty()) {
            val lower = description.lowercase()
            if (lower.contains("uncensored") || lower.contains("decensored")) {
                return true
            }
        }
        return false
    }

    fun getLanguageCode(manga: Manga?, title: String): String? {
        val parsed = parse(title)
        if (parsed.languageCode != null) return parsed.languageCode
        val genres = manga?.genre
        if (!genres.isNullOrEmpty()) {
            for (genre in genres) {
                val clean = genre.lowercase().trim()
                if (LANGUAGE_TO_CODE.containsKey(clean)) {
                    return LANGUAGE_TO_CODE[clean]
                }
            }
        }
        val description = manga?.description
        if (!description.isNullOrEmpty()) {
            val lower = description.lowercase()
            for ((key, code) in LANGUAGE_TO_CODE) {
                if (lower.contains(key)) {
                    return code
                }
            }
        }
        return null
    }
}
