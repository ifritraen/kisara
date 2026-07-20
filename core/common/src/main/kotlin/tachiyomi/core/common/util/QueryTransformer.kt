package tachiyomi.core.common.util

// KMK -->
object QueryTransformer {

    /**
     * Clean mode: strip bracket-enclosed content, remove non-letter/space chars, lowercase.
     * e.g. "One Piece (Manga) [English] Vol.1!" → "one piece"
     */
    fun clean(query: String): String {
        return query
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\{.*?}"), "")
            .replace(Regex("[^\\p{L} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * Format mode: parse metadata hints from a raw title string and emit structured tokens.
     * Detected fields: title, author, artist, language.
     * e.g. "One Piece [English] (Author: Oda)" → "title:one piece author:oda language:english"
     * Falls back to raw string if nothing structured is detected.
     */
    fun format(query: String): String {
        val normalised = query
            .replace("\u201c", "\"").replace("\u201d", "\"")
            .replace("\u2018", "'").replace("\u2019", "'")

        val squareBrackets = Regex("\\[(.*?)]").findAll(normalised).map { it.groupValues[1].trim() }.toList()
        val parens = Regex("\\((.*?)\\)").findAll(normalised).map { it.groupValues[1].trim() }.toList()

        val allBrackets = squareBrackets + parens

        val languageCodes = setOf(
            "english", "en", "spanish", "es", "korean", "kr", "japanese", "jp", "raw",
            "chinese", "zh", "french", "fr", "german", "de", "italian", "it",
            "russian", "ru", "vietnamese", "vi", "portuguese", "pt",
        )

        var detectedLanguage: String? = null
        var detectedAuthor: String? = null
        var detectedArtist: String? = null

        for (bracket in allBrackets) {
            val lower = bracket.lowercase()
            when {
                lower in languageCodes -> detectedLanguage = lower
                lower.startsWith("author:") -> detectedAuthor = bracket.substring(7).trim()
                lower.startsWith("artist:") -> detectedArtist = bracket.substring(7).trim()
                lower.startsWith("by:") -> detectedAuthor = bracket.substring(3).trim()
            }
        }

        val cleanTitle = normalised
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (detectedLanguage == null && detectedAuthor == null && detectedArtist == null) {
            // Nothing detected — return as-is (no false positives)
            return query
        }

        return buildString {
            if (cleanTitle.isNotEmpty()) append("title:${cleanTitle.lowercase()}")
            if (detectedAuthor != null) {
                if (isNotEmpty()) append(" ")
                append("author:${detectedAuthor.lowercase()}")
            }
            if (detectedArtist != null) {
                if (isNotEmpty()) append(" ")
                append("artist:${detectedArtist.lowercase()}")
            }
            if (detectedLanguage != null) {
                if (isNotEmpty()) append(" ")
                append("language:$detectedLanguage")
            }
        }
    }

    /**
     * Apply all enabled transformations to a query.
     * Order: clean first (if enabled), then format (if enabled).
     */
    fun transform(query: String, clean: Boolean, format: Boolean): String {
        var result = query
        if (clean) result = clean(result)
        if (format && !clean) result = format(result) // format on raw or post-clean
        if (format && clean) result = format(result)
        return result.ifBlank { query }
    }

    // --- Fuzzy matching for local library search ---

    /**
     * threshold 0 = exact match required, 100 = very loose (edit distance ≤ 4).
     * Maps: 0→0, 1-25→1, 26-60→2, 61-85→3, 86-100→4
     */
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

    /**
     * Returns true if the query matches the candidate text (title/author/etc.)
     * considering each query word independently.
     */
    fun fuzzyContains(query: String, candidate: String, threshold: Int): Boolean {
        if (query.isBlank()) return true
        val words = query.lowercase().trim().split(Regex("\\s+"))
        return words.all { word -> fuzzyMatch(word, candidate, threshold) }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        // ponytail: simple DP, O(n*m). Ceiling: long strings. Upgrade: use length guard.
        val m = a.length
        val n = b.length
        if (kotlin.math.abs(m - n) > 4) return 5 // short-circuit obviously far strings
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
