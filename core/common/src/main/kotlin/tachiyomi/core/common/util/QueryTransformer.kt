package tachiyomi.core.common.util

// KMK -->
object QueryTransformer {

    /**
     * Fix missing opening brackets at the beginning of a query string (e.g. from OCR/Google Lens copy).
     * e.g. "abc] title" -> "[abc] title", "author (artist)] title" -> "[author (artist)] title"
     */
    fun fixMissingLeadingBracket(input: String): String {
        var result = input.trim()
        if (result.isEmpty()) return result

        var squareDepth = 0
        for (char in result) {
            if (char == '[') squareDepth++
            else if (char == ']') {
                squareDepth--
                if (squareDepth < 0) {
                    result = "[$result"
                    break
                }
            }
        }

        var parenDepth = 0
        for (char in result) {
            if (char == '(') parenDepth++
            else if (char == ')') {
                parenDepth--
                if (parenDepth < 0) {
                    result = "($result"
                    break
                }
            }
        }

        return result
    }

    /**
     * Clean mode: strip bracket-enclosed content, remove non-letter/space/digit chars, lowercase.
     * e.g. "[x (y)] Abc dEf - 2? [English] [Uncensored] [DCscan]" → "abc def 2"
     */
    fun clean(query: String): String {
        val fixed = fixMissingLeadingBracket(query)
        return fixed
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\{.*?\\}"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun format(query: String, mode: Int = 1): String {
        if (mode == 0) return query
        val fixed = fixMissingLeadingBracket(query)
        val normalized = fixed
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
            if (mode == 1) {
                if (detectedAuthor != null) append(" author:${detectedAuthor.lowercase()}")
                if (detectedArtist != null) append(" artist:${detectedArtist.lowercase()}")
                if (detectedLanguage != null) append(" language:$detectedLanguage")
            } else if (mode == 2) {
                if (detectedAuthor != null) append(" ${detectedAuthor.lowercase()}")
                if (detectedArtist != null) append(" ${detectedArtist.lowercase()}")
                if (detectedLanguage != null) append(" $detectedLanguage")
            }
        }
    }

    /**
     * Apply transformations.
     * If both are true: clean the title part, but format and append metadata.
     */
    fun transform(query: String, clean: Boolean, formatMode: Int): String {
        if (!clean && formatMode == 0) return query
        if (clean && formatMode == 0) return clean(query)
        if (formatMode != 0 && !clean) return format(query, formatMode)

        // Both clean and format are active
        val formatted = format(query, formatMode)
        if (formatted == query) {
            // Nothing formatted, just clean
            return clean(query)
        }

        if (formatMode == 1) {
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
        } else {
            // formatMode == 2: we appended author, artist, language as raw words at the end.
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
            val cleanTitlePart = clean(query)
            return buildString {
                append(cleanTitlePart)
                if (detectedAuthor != null) append(" ${detectedAuthor.lowercase()}")
                if (detectedArtist != null) append(" ${detectedArtist.lowercase()}")
                if (detectedLanguage != null) append(" $detectedLanguage")
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
