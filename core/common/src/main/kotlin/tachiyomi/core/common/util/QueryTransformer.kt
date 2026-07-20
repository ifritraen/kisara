package tachiyomi.core.common.util

// KMK -->
object QueryTransformer {

    /**
     * Clean mode: strip bracket-enclosed content, remove non-letter/space/digit chars, lowercase.
     * e.g. "[x (y)] Abc dEf - 2? [English] [Uncensored] [DCscan]" → "abc def 2"
     */
    fun clean(query: String): String {
        return query
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\{.*?\\}"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * Format mode: parse metadata hints from a raw title string and append them.
     * e.g. "[x (y)] Abc dEf - 2? [English] [Uncensored] [DCscan]" → "Abc dEf - 2? author:x artist:y language:english"
     */
    fun format(query: String): String {
        val normalized = query
            .replace("\u201c", "\"").replace("\u201d", "\"")
            .replace("\u2018", "'").replace("\u2019", "'")

        val squareBrackets = Regex("\\[(.*?)]").findAll(normalized).map { it.groupValues[1].trim() }.toList()
        val parens = Regex("\\((.*?)\\)").findAll(normalized).map { it.groupValues[1].trim() }.toList()
        val allBrackets = squareBrackets + parens

        val languageCodes = setOf(
            "english", "en", "spanish", "es", "korean", "kr", "japanese", "jp", "raw",
            "chinese", "zh", "french", "fr", "german", "de", "italian", "it",
            "russian", "ru", "vietnamese", "vi", "portuguese", "pt",
        )

        var detectedLanguage: String? = null
        var detectedAuthor: String? = null
        var detectedArtist: String? = null

        val parenInsideBracketRegex = Regex("^([^()]+)\\s*\\(([^()]+)\\)$")

        for (bracket in allBrackets) {
            val lower = bracket.lowercase()
            val parenMatch = parenInsideBracketRegex.matchEntire(bracket)
            when {
                lower in languageCodes -> detectedLanguage = lower
                lower.startsWith("author:") -> detectedAuthor = bracket.substring(7).trim()
                lower.startsWith("artist:") -> detectedArtist = bracket.substring(7).trim()
                lower.startsWith("by:") -> detectedAuthor = bracket.substring(3).trim()
                parenMatch != null -> {
                    detectedAuthor = parenMatch.groupValues[1].trim()
                    detectedArtist = parenMatch.groupValues[2].trim()
                }
            }
        }

        val cleanTitle = normalized
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (detectedLanguage == null && detectedAuthor == null && detectedArtist == null) {
            return query
        }

        return buildString {
            append(cleanTitle)
            if (detectedAuthor != null) append(" author:${detectedAuthor.lowercase()}")
            if (detectedArtist != null) append(" artist:${detectedArtist.lowercase()}")
            if (detectedLanguage != null) append(" language:$detectedLanguage")
        }
    }

    /**
     * Apply transformations.
     * If both are true: clean the title part, but format and append metadata.
     */
    fun transform(query: String, clean: Boolean, format: Boolean): String {
        if (!clean && !format) return query
        if (clean && !format) return clean(query)
        if (format && !clean) return format(query)

        // Both clean and format are active
        val formatted = format(query)
        if (formatted == query) {
            // Nothing formatted, just clean
            return clean(query)
        }

        // Parse title and key-value attributes from the formatted string
        val parts = formatted.split(" ")
        val attrPrefixes = listOf("author:", "artist:", "language:")
        val attrs = parts.filter { part -> attrPrefixes.any { part.startsWith(it) } }
        val titlePart = parts.filter { part -> attrPrefixes.none { part.startsWith(it) } }.joinToString(" ")

        val cleanedTitle = clean(titlePart)
        return buildString {
            append(cleanedTitle)
            if (attrs.isNotEmpty()) {
                append(" ")
                append(attrs.joinToString(" "))
            }
        }
    }

    // --- Fuzzy matching for local library search ---

    fun fuzzyMatch(queryWord: String, candidate: String, threshold: Int): Boolean {
        if (threshold == 0) return candidate.contains(queryWord, ignoreCase = true)
        val maxDist = when {
            threshold <= 25 -> 1
            threshold <= 60 -> 2
            threshold <= 85 -> 3
            else -> 4
        }
        val q = queryWord.lowercase()
        val words = candidate.lowercase().split(Regex("\\s+"))
        return words.any { editDistance(q, it) <= maxDist } ||
            candidate.lowercase().contains(q)
    }

    fun fuzzyContains(query: String, candidate: String, threshold: Int): Boolean {
        if (query.isBlank()) return true
        val words = query.lowercase().trim().split(Regex("\\s+"))
        return words.all { word -> fuzzyMatch(word, candidate, threshold) }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val m = a.length
        val n = b.length
        if (kotlin.math.abs(m - n) > 4) return 5
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }
}
// KMK <--
