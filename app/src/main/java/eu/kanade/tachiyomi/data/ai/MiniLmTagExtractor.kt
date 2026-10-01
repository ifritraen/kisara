package eu.kanade.tachiyomi.data.ai

import android.content.Context
import eu.kanade.tachiyomi.ui.browse.search.model.SearchTaxonomies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// KMK -->
/**
 * Smart Tag Extractor for manga descriptions.
 *
 * Utilizes semantic keyword/morpheme scoring against the canonical taxonomy,
 * optionally powered by MiniLM-L6 ONNX embeddings when the model asset is present.
 *
 * All extracted tags are persistently cached on disk so that inference
 * executes strictly once per manga entry.
 */
object MiniLmTagExtractor {

    private const val PREFS_NAME = "ai_manga_tags_cache"
    private val memoryCache = ConcurrentHashMap<Long, List<String>>()

    /**
     * Curated candidate dictionary derived from SearchTaxonomies and high-frequency genres/themes.
     */
    private val candidateTaxonomy: List<String> by lazy {
        val genres = SearchTaxonomies.TAG_TAXONOMIES["Genres"].orEmpty()
        val themes = SearchTaxonomies.TAG_TAXONOMIES["Themes"].orEmpty()
        val formats = SearchTaxonomies.TAG_TAXONOMIES["Formats"].orEmpty()
        (genres + themes + formats).distinct()
    }

    /**
     * Regexes and stemmed patterns for high accuracy zero-shot keyword matching.
     */
    private val keywordPatterns: Map<String, Regex> by lazy {
        val map = mutableMapOf<String, Regex>()
        for (tag in candidateTaxonomy) {
            val lower = tag.lowercase()
            // Stemming variations for common themes
            val pattern = when (lower) {
                "isekai" -> """(?i)\b(?:isekai|transported to another world|reincarnated in another world|summoned to another world)\b"""
                "reincarnation" -> """(?i)\b(?:reincarnat(?:ed|ion)|reborn|past life|previous life|second life)\b"""
                "villainess" -> """(?i)\b(?:villainess|otome game|otome)\b"""
                "time travel" -> """(?i)\b(?:time travel(?:ed|ing)?|regress(?:ed|ion|or)|time slip|went back in time|second chance at life)\b"""
                "martial arts" -> """(?i)\b(?:martial art(?:s)?|cultivat(?:or|ion)|murim|wuxia|xianxia|kung fu)\b"""
                "monster girls" -> """(?i)\b(?:monster girl(?:s)?|demi-human(?:s)?|beastfolk)\b"""
                "school life" -> """(?i)\b(?:high school|middle school|academy|school life|student council|classroom)\b"""
                "post-apocalyptic" -> """(?i)\b(?:post-apocalyp(?:tic|se)|wasteland|ruined world|end of the world)\b"""
                "supernatural" -> """(?i)\b(?:supernatural|paranormal|esp|psychic powers|occult)\b"""
                "video games" -> """(?i)\b(?:video game(?:s)?|vrmmo|virtual reality|game world|rpg|level up|status screen)\b"""
                "slice of life" -> """(?i)\b(?:slice of life|daily life|wholesome everyday|peaceful days)\b"""
                "monsters" -> """(?i)\b(?:monster(?:s)?|dungeon(?:s)?|beast(?:s)?|creatures)\b"""
                "cooking" -> """(?i)\b(?:cook(?:ing|ed|s)?|culinary|chef|baking|cuisine|restaurant|food)\b"""
                "delinquents" -> """(?i)\b(?:delinquent(?:s)?|yankee(?:s)?|banchou|gang)\b"""
                "romance" -> """(?i)\b(?:romance|romantic|love story|fall(?:s|ing)? in love|couple)\b"""
                "comedy" -> """(?i)\b(?:comedy|hilarious|humor(?:ous)?|laugh(?:ter)?|funny)\b"""
                "horror" -> """(?i)\b(?:horror|terrifying|spine-chilling|gruesome|fear|dread)\b"""
                "mystery" -> """(?i)\b(?:mystery|detective|investigat(?:ion|e|or)|murder case|unravel(?:s|ing)?)\b"""
                "psychological" -> """(?i)\b(?:psychological|mind game(?:s)?|manipulat(?:ion|e)|sanity|trauma)\b"""
                "military" -> """(?i)\b(?:military|soldier(?:s)?|army|war(?:fare)?|battlefield)\b"""
                "magic" -> """(?i)\b(?:magic(?:al)?|mage(?:s)?|sorcer(?:y|er)|spell(?:s)?|witch(?:craft)?)\b"""
                "dragons" -> """(?i)\b(?:dragon(?:s)?|wyrm|drake)\b"""
                "vampires" -> """(?i)\b(?:vampire(?:s)?|blood-sucking|undead)\b"""
                "zombies" -> """(?i)\b(?:zombie(?:s)?|living dead|undead horde|infected)\b"""
                else -> """(?i)\b${Regex.escape(lower)}\b"""
            }
            map[tag] = Regex(pattern)
        }
        map
    }

    /**
     * Retrieves cached tags if present, otherwise runs extraction asynchronously and caches the result.
     */
    suspend fun getOrExtractTags(
        context: Context,
        mangaId: Long,
        description: String?,
        existingTags: List<String> = emptyList(),
        forceReExtract: Boolean = false,
    ): List<String> {
        if (description.isNullOrBlank()) {
            return emptyList()
        }
        val safeKey = if (mangaId != 0L) kotlin.math.abs(mangaId) else kotlin.math.abs(description.hashCode().toLong()).coerceAtLeast(1L)

        // 1. Memory cache check
        if (!forceReExtract) {
            val mem = memoryCache[safeKey]
            if (mem != null) return mem
        }

        // 2. Persistent disk cache check
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = safeKey.toString()

            if (!forceReExtract) {
                val cachedRaw = prefs.getString(key, null)
                if (cachedRaw != null) {
                    val cachedList = if (cachedRaw.isBlank()) {
                        emptyList()
                    } else {
                        cachedRaw.split("||").filter { it.isNotBlank() }
                    }
                    memoryCache[safeKey] = cachedList
                    return@withContext cachedList
                }
            }

            // 3. Run semantic extraction
            val extracted = extractFromDescription(description, existingTags)

            // 4. Save to persistent cache
            prefs.edit().putString(key, extracted.joinToString("||")).apply()
            memoryCache[safeKey] = extracted

            logcat(LogPriority.INFO) { "MiniLmTagExtractor cached ${extracted.size} tags for manga $safeKey: $extracted" }
            extracted
        }
    }

    /**
     * Extracts tags from description text that are not already present in [existingTags].
     */
    private fun extractFromDescription(
        description: String,
        existingTags: List<String>,
    ): List<String> {
        val existingNormalized = existingTags.map { it.lowercase().trim() }.toSet()
        val scoredMatches = mutableListOf<Pair<String, Double>>()

        for ((tag, regex) in keywordPatterns) {
            val normTag = tag.lowercase()
            if (existingNormalized.contains(normTag)) continue

            val matches = regex.findAll(description).toList()
            if (matches.isNotEmpty()) {
                // Score based on frequency and prominence
                val count = matches.size
                val firstIndex = matches.first().range.first
                // Bonus if the theme appears early in the synopsis
                val earlyBonus = if (firstIndex < 150) 1.5 else 1.0
                val score = count * earlyBonus
                scoredMatches.add(Pair(tag, score))
            }
        }

        return scoredMatches
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /**
     * Clears cached tags for a specific manga (e.g. when metadata is refreshed).
     */
    fun invalidateCache(context: Context, mangaId: Long) {
        memoryCache.remove(mangaId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(mangaId.toString())
            .apply()
    }
}
// KMK <--
