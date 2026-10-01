package eu.kanade.tachiyomi.data.ai

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.service.SuggestionsPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

// KMK -->
/**
 * Character N-gram and Taxonomy-derived classifier for detecting 18+ / NSFW
 * content across manga tags, titles, and extensions.
 *
 * Utilizes sub-word morphemes calibrated against the 50,000 Canonical Tag Database
 * and curated explicit taxonomy to detect exact, compound, romanized, and novel adult tags.
 */
object NsfwTagClassifier {

    private val scoreCache = ConcurrentHashMap<String, Double>(1024)

    /**
     * Exact adult root keywords derived from SearchTaxonomies ("Doujin & Mature", "Content Warnings")
     * and high-frequency 18+ canonical tags.
     */
    private val explicitAdultRoots = setOf(
        "hentai", "ecchi", "smut", "erotica", "eromanga", "adult", "mature", "18+", "r18", "r-18",
        "ahegao", "anal", "bdsm", "blowjob", "bukkake", "chikan", "crossdressing",
        "defloration", "dickgirl", "double penetration", "doujinshi", "drugs", "femdom",
        "ffm threesome", "footjob", "futanari", "gangbang", "gender bender", "genderswap",
        "gouto", "group", "guro", "handjob", "harem", "huge breasts", "hypnosis", "impregnation",
        "incest", "infidelity", "lactation", "lewd", "lingerie", "lolicon", "masturbation",
        "milf", "mind break", "mind control", "nakadashi", "netorare", "netorase", "netori",
        "ntr", "oppai", "orgasm", "paizuri", "pegging", "petplay", "public sex", "rape",
        "scat", "sex", "sex toys", "sexual violence", "shibari", "shotacon", "sister",
        "slave", "spanking", "squirt", "submissive", "succubus", "sweat", "swapping",
        "tentacles", "torture", "uncensored", "urination", "vanilla", "voyeur", "voyeurism",
        "x-ray", "yaoi", "yuri", "zero tolerance",
    )

    /**
     * Strongly indicative character 3-grams, 4-grams, and 5-grams for 18+ classification.
     * Captures Romanized Japanese roots, English slang, and compound combinations.
     */
    private val nGramWeights = mapOf(
        // High confidence (weight >= 0.70)
        "hent" to 0.95, "enta" to 0.90, "ntai" to 0.95,
        "ecch" to 0.85, "cchi" to 0.80,
        "smut" to 0.90,
        "erot" to 0.85, "roti" to 0.80, "otic" to 0.80,
        "aheg" to 0.95, "hega" to 0.95, "egao" to 0.95,
        "paiz" to 0.95, "aizu" to 0.90, "izur" to 0.90, "zuri" to 0.90,
        "naka" to 0.75, "akad" to 0.80, "kada" to 0.80, "dashi" to 0.90,
        "bukk" to 0.95, "ukka" to 0.90, "kake" to 0.85,
        "neto" to 0.80, "etor" to 0.80, "tora" to 0.80, "rare" to 0.75,
        "chika" to 0.90, "hikan" to 0.90,
        "shota" to 0.85, "hota" to 0.75, "taco" to 0.80, "acon" to 0.80,
        "lolic" to 0.95, "olico" to 0.95, "licon" to 0.95,
        "futan" to 0.95, "utan" to 0.80, "tana" to 0.80, "anari" to 0.90,
        "shiba" to 0.85, "hibar" to 0.85, "ibari" to 0.85,
        "defl" to 0.85, "eflo" to 0.85, "lora" to 0.75, "rati" to 0.60,
        "femdo" to 0.90, "emdom" to 0.90,
        "uncen" to 0.80, "ncens" to 0.80, "censo" to 0.70, "ensor" to 0.70,
        "lacta" to 0.85, "actat" to 0.85, "ctati" to 0.85,
        "mindb" to 0.90, "indbr" to 0.90, "ndbre" to 0.90, "break" to 0.50,
        "voyeu" to 0.85, "oyeur" to 0.85,
        "douji" to 0.75, "oujin" to 0.75, "ujin" to 0.70, "jinsh" to 0.75,
        "r-18" to 0.95, "r18" to 0.90, "18+" to 0.90,
        "milf" to 0.90,
        "bdsm" to 0.95,

        // Medium confidence (weight ~ 0.40 - 0.65)
        "harem" to 0.45,
        "adul" to 0.65, "dult" to 0.65,
        "matu" to 0.60, "atur" to 0.60, "ture" to 0.50,
        "sexe" to 0.60, "sexu" to 0.70, "exua" to 0.70, "xual" to 0.70,
        "ling" to 0.50, "geri" to 0.55, "erie" to 0.55,
        "brea" to 0.45, "reast" to 0.50,
    )

    /**
     * Evaluates a single tag string and returns a continuous NSFW risk score in [0.0, 1.0].
     */
    fun getTagScore(tag: String): Double {
        val clean = tag.lowercase().trim()
        if (clean.isBlank()) return 0.0

        return scoreCache.computeIfAbsent(clean) { calculateScore(clean) }
    }

    private fun calculateScore(cleanTag: String): Double {
        // Strip namespace prefixes like "female:", "male:", "tag:"
        val raw = if (cleanTag.contains(":")) {
            cleanTag.substringAfter(":").trim()
        } else {
            cleanTag
        }

        // 1. Exact match against known explicit roots
        if (explicitAdultRoots.contains(raw) || explicitAdultRoots.contains(cleanTag)) {
            return 1.0
        }

        // 2. Exact word boundaries inside multi-word tags (e.g. "big breasts", "sole female")
        val words = raw.split(" ", "-", "_").filter { it.isNotBlank() }
        for (word in words) {
            if (explicitAdultRoots.contains(word)) {
                return 0.95
            }
        }

        // 3. Sub-word character N-gram matching (3, 4, 5-grams)
        var maxWeight = 0.0
        var weightSum = 0.0
        var matchCount = 0

        val nGramLengths = intArrayOf(3, 4, 5)
        for (n in nGramLengths) {
            if (raw.length < n) continue
            for (i in 0..raw.length - n) {
                val sub = raw.substring(i, i + n)
                val w = nGramWeights[sub]
                if (w != null) {
                    if (w > maxWeight) maxWeight = w
                    weightSum += w
                    matchCount++
                }
            }
        }

        if (matchCount > 0) {
            val avgWeight = weightSum / matchCount
            // Blend max weight with average to reward multiple converging sub-word hits
            val combined = (maxWeight * 0.7) + (avgWeight * 0.3)
            return combined.coerceIn(0.0, 1.0)
        }

        return 0.0
    }

    /**
     * Determines whether an individual tag is classified as 18+ (NSFW).
     * @param threshold Cutoff probability (default 0.50).
     */
    fun is18PlusTag(tag: String, threshold: Double = 0.50): Boolean {
        // Check user custom NSFW tags first
        val uiPreferences = runCatching { Injekt.get<UiPreferences>() }.getOrNull()
        if (uiPreferences != null) {
            val customTags = uiPreferences.kisaraCustomNsfwTags().get().map { it.lowercase().trim() }.toSet()
            val clean = tag.lowercase().trim()
            val raw = clean.substringAfter(":")
            if (customTags.contains(clean) || customTags.contains(raw)) {
                return true
            }
        }

        return getTagScore(tag) >= threshold
    }

    /**
     * Determines whether an extension (by its package name/id and native status) is classified as 18+ (NSFW).
     * Honors user manual override preferences in [SourcePreferences].
     */
    fun is18PlusExtension(pkgName: String, nativeIsNsfw: Boolean): Boolean {
        val sourcePreferences = runCatching { Injekt.get<SourcePreferences>() }.getOrNull()
        val overrideSfw = sourcePreferences?.nsfwOverrideSfwExtensions()?.get().orEmpty()
        val overrideNsfw = sourcePreferences?.nsfwOverrideNsfwExtensions()?.get().orEmpty()

        if (overrideSfw.contains(pkgName)) return false
        if (overrideNsfw.contains(pkgName)) return true
        return nativeIsNsfw
    }

    /**
     * Toggles an extension's NSFW status between 18+ (ON) and SFW (OFF).
     */
    fun toggleExtensionNsfw(pkgName: String, nativeIsNsfw: Boolean) {
        val sourcePreferences = Injekt.get<SourcePreferences>()
        val overrideSfw = sourcePreferences.nsfwOverrideSfwExtensions().get().toMutableSet()
        val overrideNsfw = sourcePreferences.nsfwOverrideNsfwExtensions().get().toMutableSet()

        val currentStatus = when {
            overrideSfw.contains(pkgName) -> false
            overrideNsfw.contains(pkgName) -> true
            else -> nativeIsNsfw
        }

        if (currentStatus) {
            // Turning OFF 18+ -> override to SFW
            overrideNsfw.remove(pkgName)
            if (nativeIsNsfw) {
                overrideSfw.add(pkgName)
            } else {
                overrideSfw.remove(pkgName)
            }
        } else {
            // Turning ON 18+ -> override to NSFW
            overrideSfw.remove(pkgName)
            if (!nativeIsNsfw) {
                overrideNsfw.add(pkgName)
            } else {
                overrideNsfw.remove(pkgName)
            }
        }

        sourcePreferences.nsfwOverrideSfwExtensions().set(overrideSfw)
        sourcePreferences.nsfwOverrideNsfwExtensions().set(overrideNsfw)
    }

    /**
     * Determines whether a source/extension is classified as 18+ (NSFW).
     * Respects the user's manual whitelist/override preference in [SourcePreferences] and [SuggestionsPreferences].
     */
    fun is18PlusSource(sourceId: Long, sourceName: String? = null): Boolean {
        val suggestionsPreferences = runCatching { Injekt.get<SuggestionsPreferences>() }.getOrNull()
        if (suggestionsPreferences != null) {
            val manualSafe = suggestionsPreferences.manuallySafeSources().get()
            if (manualSafe.contains(sourceId.toString())) {
                // Manually overridden to be SAFE / non-18+
                return false
            }
        }

        val sourcePreferences = runCatching { Injekt.get<SourcePreferences>() }.getOrNull()
        val overrideSfw = sourcePreferences?.nsfwOverrideSfwExtensions()?.get().orEmpty()
        val overrideNsfw = sourcePreferences?.nsfwOverrideNsfwExtensions()?.get().orEmpty()

        if (overrideSfw.contains(sourceId.toString())) return false
        if (overrideNsfw.contains(sourceId.toString())) return true

        // 1. Check Manga extension
        val ext = runCatching {
            Injekt.get<eu.kanade.tachiyomi.extension.ExtensionManager>()
                .installedExtensionsFlow
                .value
                .find { extension -> extension.sources.any { it.id == sourceId } }
        }.getOrNull()

        if (ext != null) {
            if (overrideSfw.contains(ext.pkgName)) return false
            if (overrideNsfw.contains(ext.pkgName)) return true
            if (ext.isNsfw) return true
        }

        // 2. Check Anime extension
        val animeExt = runCatching {
            Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                .installedExtensionsFlow
                .value
                .find { extension -> extension.sources.any { it.id == sourceId } }
        }.getOrNull()

        if (animeExt != null) {
            if (overrideSfw.contains(animeExt.pkgName)) return false
            if (overrideNsfw.contains(animeExt.pkgName)) return true
            if (animeExt.isNsfw) return true
        }

        // 3. Check Novel extension
        val novelExt = runCatching {
            Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                .installedPluginsFlow
                .value
                .find { plugin ->
                    eu.kanade.tachiyomi.extension.novel.NovelPluginId.toSourceId(plugin.id) == sourceId ||
                        plugin.id == sourceId.toString() ||
                        plugin.pkgName?.let { overrideSfw.contains(it) || overrideNsfw.contains(it) } == true
                }
        }.getOrNull()

        if (novelExt != null) {
            val key = novelExt.pkgName ?: novelExt.id
            if (overrideSfw.contains(key) || overrideSfw.contains(novelExt.id)) return false
            if (overrideNsfw.contains(key) || overrideNsfw.contains(novelExt.id)) return true
            if (novelExt.isNsfw) return true
        }

        // 4. Name checking for adult markers
        val sourceManager = runCatching { Injekt.get<SourceManager>() }.getOrNull()
        val source = sourceManager?.get(sourceId)
        val nameToCheck = sourceName ?: source?.name ?: ext?.name ?: animeExt?.name ?: novelExt?.name ?: ""
        if (nameToCheck.contains("nsfw", ignoreCase = true) ||
            nameToCheck.contains("hentai", ignoreCase = true) ||
            nameToCheck.contains("18+", ignoreCase = true)
        ) {
            if (ext != null && overrideSfw.contains(ext.pkgName)) return false
            if (animeExt != null && overrideSfw.contains(animeExt.pkgName)) return false
            if (novelExt != null) {
                val key = novelExt.pkgName ?: novelExt.id
                if (overrideSfw.contains(key) || overrideSfw.contains(novelExt.id)) return false
            }
            return true
        }

        return false
    }

    /**
     * Evaluates any media item (Title + Source + Genres) to determine 18+ status.
     */
    fun is18PlusItem(sourceId: Long, genres: List<String>?, title: String?): Boolean {
        val checkTitle = title ?: ""
        if (checkTitle.contains("[Uncensored]", ignoreCase = true)) return true
        if (is18PlusSource(sourceId)) return true
        if (!genres.isNullOrEmpty()) {
            for (genre in genres) {
                if (is18PlusTag(genre)) return true
            }
        }
        return false
    }

    /**
     * Evaluates a manga entry as a whole (Title + Source + Genres) to determine 18+ status.
     */
    fun is18PlusManga(manga: Manga?, title: String? = null): Boolean {
        if (manga == null) {
            val checkTitle = title ?: ""
            return checkTitle.contains("[Uncensored]", ignoreCase = true)
        }
        return is18PlusItem(manga.source, manga.genre, title ?: manga.title)
    }
}
// KMK <--
