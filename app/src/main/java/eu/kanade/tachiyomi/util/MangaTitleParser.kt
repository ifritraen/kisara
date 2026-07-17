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

    fun parse(rawTitle: String): ParsedTitle {
        var title = rawTitle.trim()
        var author: String? = null
        var artist: String? = null
        var languageCode: String? = null
        var isUncensored = false
        var isColorized = false

        // 1. Parse leading bracket: [Author] or [Artist (Author)] or (Author) or {Author}
        val leadingBracketRegex = Regex("""^[\[({]([^\]\(){}]+)\s*(?:\(([^\]\){}]+)\))?[\])}]""")
        val match = leadingBracketRegex.find(title)
        if (match != null) {
            val part1 = match.groupValues[1].trim()
            val part2 = match.groupValues.getOrNull(2)?.trim()
            if (part2 != null && part2.isNotEmpty()) {
                artist = part1
                author = part2
            } else {
                author = part1
            }
            title = title.substring(match.range.last + 1).trim()
        }

        // 2. Parse and strip all brackets at the end/inside the title
        val bracketsRegex = Regex("""[\[({]([^\])}]+)[\])}]""")
        val allMatches = bracketsRegex.findAll(title).toList()

        for (m in allMatches) {
            val inside = m.groupValues[1].trim().lowercase()
            if (LANGUAGE_TO_CODE.containsKey(inside)) {
                languageCode = LANGUAGE_TO_CODE[inside]
            } else if (inside == "uncensored") {
                isUncensored = true
            } else if (COLORIZED_KEYWORDS.contains(inside)) {
                isColorized = true
            }
        }

        title = bracketsRegex.replace(title, "").trim()
        // Cleanup trailing non-alphanumeric chars
        title = title.replace(Regex("""\s+[-|/~]\s*$"""), "").trim()

        if (title.isEmpty()) {
            title = rawTitle.replace(Regex("""[\[({][^\])}]+[\])}]"""), "").trim()
            if (title.isEmpty()) {
                title = rawTitle
            }
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
        return null
    }
}
