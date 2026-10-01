package eu.kanade.tachiyomi.data.suggestions

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.repository.SuggestionRepository
import tachiyomi.domain.suggestions.service.SuggestionsPreferences
import tachiyomi.domain.suggestions.service.SuggestionsPreferences.NsfwSuggestionMode
import eu.kanade.tachiyomi.data.ai.NsfwTagClassifier
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SuggestionsWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val isManual = inputData.getBoolean("is_manual", false)
        val rankToLoad = inputData.getInt("rank_to_load", -1)
        SuggestionsReport.clear()
        SuggestionsReport.log("INFO", "Starting suggestions updates in background (manual=$isManual, rankToLoad=$rankToLoad)")
        logcat(LogPriority.INFO) { "Starting suggestions updates in background" }

        suspend fun awaitReaderClosed() {
            if (isReaderActive.value) {
                SuggestionsReport.log("INFO", "Reader is active. SuggestionsWorker paused/holding until reader closes.")
                logcat(LogPriority.INFO) { "Reader is active. SuggestionsWorker paused." }
                isReaderActive.first { !it }
                SuggestionsReport.log("INFO", "Reader closed. Resuming suggestions fetch.")
                logcat(LogPriority.INFO) { "Reader closed. Resuming suggestions fetch." }
            }
        }

        awaitReaderClosed()

        if (!isManual) {
            delay(15000)
            awaitReaderClosed()
        }

        try {
            val mangaRepository = Injekt.get<MangaRepository>()
            val sourceManager = Injekt.get<SourceManager>()
            val suggestionRepository = Injekt.get<SuggestionRepository>()
            val networkToLocalManga = Injekt.get<NetworkToLocalManga>()
            val sourcePreferences = Injekt.get<SourcePreferences>()
            val suggestionsPreferences = Injekt.get<SuggestionsPreferences>()

            var hasReplacedInCurrentRun = (rankToLoad != -1)

            awaitReaderClosed()

            // 1. Scan favorites and history to update/populate taste database with deep engagement (duration + chapters)
            val favorites = mangaRepository.getFavorites()
            val readHistory = mangaRepository.getReadMangaNotInLibrary()
            val seed = (favorites.take(100) + readHistory.take(50)).distinctBy { it.id }

            // Sync tags with time decay, reading duration, chapter depth, and generic blacklist
            val genericTags = setOf("manga", "webtoon", "comic", "scanlation", "translation", "english", "raw", "doujinshi", "oneshot")
            val historyRepository = Injekt.get<tachiyomi.domain.history.repository.HistoryRepository>()
            val chapterRepository = Injekt.get<tachiyomi.domain.chapter.repository.ChapterRepository>()
            val now = System.currentTimeMillis()

            val tagFrequencies = mutableMapOf<String, Double>()
            val recentAuthors = mutableSetOf<String>()
            val recentArtists = mutableSetOf<String>()

            seed.forEach { manga ->
                val history = try {
                    historyRepository.getHistoryByMangaId(manga.id)
                } catch (e: Exception) {
                    emptyList()
                }
                val totalReadDurationMs = history.sumOf { it.readDuration }
                val durationMinutes = totalReadDurationMs / (1000.0 * 60.0)
                val lastReadTime = history.mapNotNull { it.readAt?.time }.maxOrNull()
                val readChaptersCount = try {
                    chapterRepository.getChapterByMangaId(manga.id).count { it.read }
                } catch (e: Exception) {
                    0
                }

                val recencyMultiplier = if (lastReadTime != null) {
                    val diffDays = (now - lastReadTime) / (1000.0 * 60 * 60 * 24)
                    if (diffDays <= 7) {
                        manga.author?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { recentAuthors.add(it) }
                        manga.artist?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { recentArtists.add(it) }
                    }
                    when {
                        diffDays <= 7 -> 2.5
                        diffDays <= 30 -> 1.8
                        diffDays <= 90 -> 1.0
                        else -> 0.4
                    }
                } else {
                    1.0
                }

                // Logarithmic scaling for chapters read & reading duration to reward deeply engaged manga
                val chapterMultiplier = 1.0 + Math.log(1.0 + readChaptersCount.coerceAtLeast(0)) * 0.5
                val durationMultiplier = 1.0 + Math.log(1.0 + (durationMinutes / 10.0).coerceAtLeast(0.0)) * 0.4
                val mangaEngagementWeight = (if (manga.favorite) 1.5 else 1.0) * recencyMultiplier * chapterMultiplier * durationMultiplier

                manga.genre.orEmpty().forEach { genre ->
                    val cleanGenre = cleanAndFilterGenre(genre)
                    if (cleanGenre != null && !genericTags.contains(cleanGenre) && cleanGenre.isNotEmpty()) {
                        tagFrequencies[cleanGenre] = (tagFrequencies[cleanGenre] ?: 0.0) + mangaEngagementWeight
                    }
                }
            }

            val existingTags = suggestionRepository.getTags()
            val existingTagsMap = existingTags.associateBy { it.tag }
            var nextTagSortOrder = (existingTags.maxOfOrNull { it.sortOrder } ?: -1L) + 1

            val sortedTagFreqs = tagFrequencies.entries.sortedByDescending { it.value }.take(50)
            sortedTagFreqs.forEach { (tagText, countDouble) ->
                val count = Math.round(Math.log(1.0 + countDouble) * 10.0).toLong().coerceAtLeast(1L)
                val existing = existingTagsMap[tagText]
                if (existing != null) {
                    suggestionRepository.insertTag(existing.copy(count = count))
                } else {
                    suggestionRepository.insertTag(
                        SuggestionTag(
                            tag = tagText,
                            count = count,
                            isBlocked = false,
                            isUserAdded = false,
                            sortOrder = nextTagSortOrder++,
                        ),
                    )
                }
            }

            // Sync sources/extensions
            val sourceFrequencies = seed.groupingBy { it.source }.eachCount()
            val existingSources = suggestionRepository.getSources()
            val existingSourcesMap = existingSources.associateBy { it.sourceId }
            var nextSourceSortOrder = (existingSources.maxOfOrNull { it.sortOrder } ?: -1L) + 1

            val sortedSourceFreqs = sourceFrequencies.entries.sortedByDescending { it.value }.take(20)
            sortedSourceFreqs.forEach { (sourceId, count) ->
                val existing = existingSourcesMap[sourceId]
                if (existing != null) {
                    suggestionRepository.insertSource(existing.copy(count = count.toLong()))
                } else {
                    suggestionRepository.insertSource(
                        SuggestionSource(
                            sourceId = sourceId,
                            count = count.toLong(),
                            isBlocked = false,
                            isUserAdded = false,
                            sortOrder = nextSourceSortOrder++,
                        ),
                    )
                }
            }

            // Sync authors
            val authorFrequencies = seed.mapNotNull { it.author?.lowercase()?.trim() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            val existingAuthors = suggestionRepository.getAuthors()
            val existingAuthorsMap = existingAuthors.associateBy { it.author }
            var nextAuthorSortOrder = (existingAuthors.maxOfOrNull { it.sortOrder } ?: -1L) + 1

            val sortedAuthorFreqs = authorFrequencies.entries.sortedByDescending { it.value }.take(20)
            sortedAuthorFreqs.forEach { (authorText, count) ->
                val existing = existingAuthorsMap[authorText]
                if (existing != null) {
                    suggestionRepository.insertAuthor(existing.copy(count = count.toLong()))
                } else {
                    suggestionRepository.insertAuthor(
                        SuggestionAuthor(
                            author = authorText,
                            count = count.toLong(),
                            isBlocked = false,
                            isUserAdded = false,
                            sortOrder = nextAuthorSortOrder++,
                        ),
                    )
                }
            }

            // Sync artists
            val artistFrequencies = seed.mapNotNull { it.artist?.lowercase()?.trim() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            val existingArtists = suggestionRepository.getArtists()
            val existingArtistsMap = existingArtists.associateBy { it.artist }
            var nextArtistSortOrder = (existingArtists.maxOfOrNull { it.sortOrder } ?: -1L) + 1

            val sortedArtistFreqs = artistFrequencies.entries.sortedByDescending { it.value }.take(20)
            sortedArtistFreqs.forEach { (artistText, count) ->
                val existing = existingArtistsMap[artistText]
                if (existing != null) {
                    suggestionRepository.insertArtist(existing.copy(count = count.toLong()))
                } else {
                    suggestionRepository.insertArtist(
                        SuggestionArtist(
                            artist = artistText,
                            count = count.toLong(),
                            isBlocked = false,
                            isUserAdded = false,
                            sortOrder = nextArtistSortOrder++,
                        ),
                    )
                }
            }

            // 2. Fetch unblocked active configurations with AI 18+ categorization
            val isHideNsfwGlobally = Injekt.get<eu.kanade.domain.ui.UiPreferences>().kisaraHideNsfwSuggestions().get()
            val nsfwModePref = suggestionsPreferences.nsfwSuggestionMode().get()
            val effectiveNsfwMode = if (isHideNsfwGlobally) NsfwSuggestionMode.SAFE_ONLY else nsfwModePref

            val allTags = suggestionRepository.getTags()
            val nonBlockedTags = allTags.filter { !it.isBlocked }

            // Partition tags using AI Character N-gram & canonical taxonomy
            val safeTags = nonBlockedTags.filter { !NsfwTagClassifier.is18PlusTag(it.tag) }
            val nsfwTags = nonBlockedTags.filter { NsfwTagClassifier.is18PlusTag(it.tag) }

            val allOnlineSources = sourceManager.getOnlineSources()
            // Partition sources using extension metadata + manual safe whitelist overrides
            val safeSources = allOnlineSources.filter { !NsfwTagClassifier.is18PlusSource(it.id, it.name) }
            val nsfwSources = allOnlineSources.filter { NsfwTagClassifier.is18PlusSource(it.id, it.name) }

            val random = java.util.Random()
            val maxTagsToMatch = suggestionsPreferences.maxTagsToMatch().get().coerceAtLeast(3)

            // Stochastic Top-X Pool -> Weighted Pick-Y Roulette
            fun sampleTagsWithoutReplacement(
                pool: List<SuggestionTag>,
                count: Int,
            ): List<SuggestionTag> {
                if (pool.size <= count) return pool.shuffled(random)
                val remaining = pool.toMutableList()
                val selected = mutableListOf<SuggestionTag>()
                while (selected.size < count && remaining.isNotEmpty()) {
                    val weights = remaining.map { Math.log(1.0 + it.count.coerceAtLeast(1L)) }
                    val totalWeight = weights.sum()
                    if (totalWeight <= 0.0) {
                        selected.add(remaining.removeAt(random.nextInt(remaining.size)))
                        continue
                    }
                    val r = random.nextDouble() * totalWeight
                    var cumulative = 0.0
                    var chosenIndex = 0
                    for (i in remaining.indices) {
                        cumulative += weights[i]
                        if (r <= cumulative) {
                            chosenIndex = i
                            break
                        }
                    }
                    selected.add(remaining.removeAt(chosenIndex))
                }
                return selected
            }

            // Sample active tags based on effective 18+ mode
            val sampledTags = when (effectiveNsfwMode) {
                NsfwSuggestionMode.SAFE_ONLY -> {
                    val pool = safeTags.sortedByDescending { it.count }.take(30)
                    sampleTagsWithoutReplacement(pool, maxTagsToMatch)
                }
                NsfwSuggestionMode.NSFW_ONLY -> {
                    val pool = nsfwTags.sortedByDescending { it.count }.take(30)
                    if (pool.isNotEmpty()) {
                        sampleTagsWithoutReplacement(pool, maxTagsToMatch)
                    } else {
                        val fallback = safeTags.sortedByDescending { it.count }.take(30)
                        sampleTagsWithoutReplacement(fallback, maxTagsToMatch)
                    }
                }
                NsfwSuggestionMode.BALANCED -> {
                    val safeQuota = maxTagsToMatch / 2
                    val nsfwQuota = maxTagsToMatch - safeQuota
                    val pickedSafe = sampleTagsWithoutReplacement(safeTags.sortedByDescending { it.count }.take(25), safeQuota)
                    val pickedNsfw = sampleTagsWithoutReplacement(nsfwTags.sortedByDescending { it.count }.take(25), nsfwQuota)
                    (pickedSafe + pickedNsfw).shuffled(random)
                }
            }

            val userAddedTags = when (effectiveNsfwMode) {
                NsfwSuggestionMode.SAFE_ONLY -> nonBlockedTags.filter { it.isUserAdded && !NsfwTagClassifier.is18PlusTag(it.tag) }
                NsfwSuggestionMode.NSFW_ONLY -> nonBlockedTags.filter { it.isUserAdded && NsfwTagClassifier.is18PlusTag(it.tag) }
                NsfwSuggestionMode.BALANCED -> nonBlockedTags.filter { it.isUserAdded }
            }
            val finalTags = (userAddedTags + sampledTags).distinctBy { it.tag }

            // Sample sources based on effective 18+ mode
            val allSources = suggestionRepository.getSources()
            val nonBlockedSources = allSources.filter { !it.isBlocked }
            val onlineSources = when (effectiveNsfwMode) {
                NsfwSuggestionMode.SAFE_ONLY -> {
                    val matchedSafe = safeSources.filter { src -> nonBlockedSources.any { it.sourceId == src.id } }
                    val pool = matchedSafe.ifEmpty { safeSources }
                    pool.shuffled(random).take(5)
                }
                NsfwSuggestionMode.NSFW_ONLY -> {
                    val matchedNsfw = nsfwSources.filter { src -> nonBlockedSources.any { it.sourceId == src.id } }
                    val pool = matchedNsfw.ifEmpty { nsfwSources.ifEmpty { allOnlineSources } }
                    pool.shuffled(random).take(5)
                }
                NsfwSuggestionMode.BALANCED -> {
                    val pickedSafe = safeSources.shuffled(random).take(3)
                    val pickedNsfw = nsfwSources.shuffled(random).take(2)
                    (pickedSafe + pickedNsfw).ifEmpty { allOnlineSources.take(5) }
                }
            }

            val allAuthors = suggestionRepository.getAuthors()
            val nonBlockedAuthors = allAuthors.filter { !it.isBlocked }
            val finalAuthors = nonBlockedAuthors.sortedByDescending { it.count }.take(2)

            val allArtists = suggestionRepository.getArtists()
            val nonBlockedArtists = allArtists.filter { !it.isBlocked }
            val finalArtists = nonBlockedArtists.sortedByDescending { it.count }.take(2)

            val finalSources = onlineSources.map {
                SuggestionSource(sourceId = it.id, count = 1L, isBlocked = false, isUserAdded = false, sortOrder = 0L)
            }

            if (finalTags.isEmpty() || onlineSources.isEmpty()) {
                SuggestionsReport.log("WARNING", "No active tags (${finalTags.size}) or sources (${onlineSources.size}) available for suggestions.")
                logcat(LogPriority.INFO) { "No active tags or sources config found for suggestions." }
                return Result.success()
            }

            SuggestionsReport.log("INFO", "Loaded suggestions active config. Mode: $effectiveNsfwMode. Selected tags: ${finalTags.map { it.tag }}. Searched extensions: ${onlineSources.map { "${it.name} (${it.id})" }}")

            val favoriteAuthors = nonBlockedAuthors.map { it.author }.toSet()
            val favoriteArtists = nonBlockedArtists.map { it.artist }.toSet()
            val topAuthors = finalAuthors.map { it.author }
            val topArtists = finalArtists.map { it.artist }

            val searchTerms = mutableListOf<String>()
            userAddedTags.forEach { searchTerms.add(it.tag) }
            searchTerms.addAll(topAuthors.take(1))
            searchTerms.addAll(topArtists.take(1))
            // Sampled tags shuffled to prevent rank 0 monopolization
            searchTerms.addAll(sampledTags.map { it.tag })

            val totalRanks = searchTerms.distinct().size
            val distinctSearchTerms = searchTerms.distinct()

            val favoriteUrls = favorites.map { it.url }.toSet()
            val historyUrls = readHistory.map { it.url }.toSet()
            val favoriteTitles = favorites.map { it.title.lowercase().trim() }.toSet()
            val historyTitles = readHistory.map { it.title.lowercase().trim() }.toSet()

            val dismissed = suggestionRepository.getDismissed()
            val dismissedUrls = dismissed.map { it.first }.toSet()
            val dismissedTitles = dismissed.map { it.second.lowercase().trim() }.toSet()

            // Semaphore to throttle extension concurrent queries
            val semaphore = Semaphore(3)

            suspend fun processRank(rankIdx: Int) {
                if (rankIdx < 0 || rankIdx >= distinctSearchTerms.size) return
                awaitReaderClosed()
                val currentSearchTerm = distinctSearchTerms[rankIdx]
                SuggestionsReport.log("INFO", "Processing rank $rankIdx. Selected query/tag: '$currentSearchTerm'")
                logcat(LogPriority.INFO) { "Fetching suggestions rank $rankIdx (query/tag: $currentSearchTerm)" }

                // Dynamic deep page offset sampling (pages 1 to 4) for diverse catalog exploration
                val pageRoll = random.nextInt(100)
                val pageToFetch = when {
                    pageRoll < 40 -> 1 // 40% page 1
                    pageRoll < 70 -> 2 // 30% page 2
                    pageRoll < 85 -> 3 // 15% page 3
                    else -> 4          // 15% page 4
                }

                val candidates = mutableMapOf<String, Pair<eu.kanade.tachiyomi.source.model.SManga, Long>>()
                coroutineScope {
                    val jobs = onlineSources.map { source ->
                        async {
                            semaphore.withPermit {
                                awaitReaderClosed()
                                try {
                                    SuggestionsReport.log("INFO", "Extension '${source.name}' starting search for: '$currentSearchTerm' (page $pageToFetch)")
                                    val results = source.getSearchManga(pageToFetch, currentSearchTerm, eu.kanade.tachiyomi.source.model.FilterList())
                                    val fetchedSize = results.mangas.size
                                    SuggestionsReport.log("INFO", "Extension '${source.name}' returned $fetchedSize results.")

                                    SuggestionsReport.fetchedCount.update { it + fetchedSize }
                                    SuggestionsReport.fetchedBySource.update { map ->
                                        map + (source.name to (map[source.name] ?: 0) + fetchedSize)
                                    }

                                    results.mangas.forEach { smanga ->
                                        synchronized(candidates) {
                                            candidates[smanga.url] = Pair(smanga, source.id)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    SuggestionsReport.log("ERROR", "Failed to search '${source.name}': ${e.message}", e)
                                    logcat(LogPriority.WARN, e) { "Failed suggestions fetch for source: ${source.name}" }

                                    SuggestionsReport.failedCount.update { it + 1 }
                                    SuggestionsReport.failedBySource.update { map ->
                                        map + (source.name to (map[source.name] ?: 0) + 1)
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                val filteredCandidates = candidates.values.filter { (smanga, _) ->
                    val titleClean = smanga.title.lowercase().trim()
                    val exclude = favoriteUrls.contains(smanga.url) ||
                        historyUrls.contains(smanga.url) ||
                        favoriteTitles.contains(titleClean) ||
                        historyTitles.contains(titleClean) ||
                        dismissedUrls.contains(smanga.url) ||
                        dismissedTitles.contains(titleClean)
                    if (exclude) {
                        SuggestionsReport.libraryFilteredCount.update { it + 1 }
                    }
                    !exclude
                }

                SuggestionsReport.log("INFO", "Candidates filtering: ${filteredCandidates.size} remaining out of ${candidates.size} total candidates (removed library, read history, and dismissed items).")

                val maxTagsToMatch = suggestionsPreferences.maxTagsToMatch().get()
                val topMatchingTags = finalTags.take(maxTagsToMatch)
                val tagWeightsMap = topMatchingTags.mapIndexed { index, tag ->
                    tag.tag.lowercase().trim() to ((maxTagsToMatch - index).toDouble() / maxTagsToMatch)
                }.toMap()

                val sourceWeightsMap = finalSources.mapIndexed { index, src ->
                    src.sourceId to ((finalSources.size - index).toDouble() / finalSources.size)
                }.toMap()

                val blockedTagsGlobal = sourcePreferences.blockedTags().get().map { it.lowercase().trim() }.toSet()
                val cooldownPrefs = context.getSharedPreferences("suggestions_cooldown_cache", Context.MODE_PRIVATE)
                val cooldownUrls = cooldownPrefs.getStringSet("recent_suggested_urls", emptySet()) ?: emptySet()

                val scoredSuggestions = mutableListOf<Pair<Manga, Double>>()
                val candidatesBySource = filteredCandidates.groupBy { it.second }

                // Allow each source to supply up to 8 candidates per rank for thorough diversity
                val candidatesToFetch = 8

                coroutineScope {
                    val jobs = candidatesBySource.flatMap { (sourceId, candidates) ->
                        val source = onlineSources.firstOrNull { it.id == sourceId } ?: return@flatMap emptyList()
                        candidates.take(candidatesToFetch).map { (smanga, _) ->
                            async {
                                semaphore.withPermit {
                                    awaitReaderClosed()
                                    try {
                                        val networkManga = smanga.toDomainManga(sourceId)

                                        if (networkManga.favorite || favoriteUrls.contains(networkManga.url) || favoriteTitles.contains(networkManga.title.lowercase().trim())) {
                                            return@withPermit
                                        }

                                        var candidateManga = networkManga
                                        if (!candidateManga.initialized) {
                                            try {
                                                logcat(LogPriority.INFO) { "Fetching details/genres for suggestion: ${smanga.title}" }
                                                val details = withTimeoutOrNull(15_000L) {
                                                    source.getMangaDetails(smanga)
                                                }
                                                if (details != null) {
                                                    try {
                                                        if (details.url.isNullOrBlank()) {
                                                            details.url = smanga.url
                                                        }
                                                    } catch (urlErr: Exception) {
                                                        details.url = smanga.url
                                                    }
                                                    candidateManga = details.toDomainManga(sourceId)
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                logcat(LogPriority.WARN, e) { "Failed fetching details for: ${smanga.title}" }
                                            }
                                        }

                                        val candTitleClean = candidateManga.title.lowercase().trim()
                                        if (favoriteTitles.contains(candTitleClean) || historyTitles.contains(candTitleClean) || dismissedTitles.contains(candTitleClean)) {
                                            return@withPermit
                                        }

                                        val hasBlockedTag = candidateManga.genre.orEmpty().any { genre ->
                                            val clean = cleanAndFilterGenre(genre)
                                            clean != null && blockedTagsGlobal.contains(clean)
                                        }
                                        val isManga18Plus = NsfwTagClassifier.is18PlusManga(candidateManga, candidateManga.title)
                                        val isAllowedByMode = when (effectiveNsfwMode) {
                                            NsfwSuggestionMode.SAFE_ONLY -> !isManga18Plus
                                            NsfwSuggestionMode.NSFW_ONLY -> isManga18Plus
                                            NsfwSuggestionMode.BALANCED -> true
                                        }
                                        if (!hasBlockedTag && isAllowedByMode) {
                                            val extWeight = sourceWeightsMap[sourceId] ?: 0.1
                                            var candidateScore = 0.0
                                            if (candidateManga.initialized) {
                                                var tagSum = 0.0
                                                val matchedGenres = mutableListOf<String>()
                                                candidateManga.genre.orEmpty().forEach { genre ->
                                                    val clean = cleanAndFilterGenre(genre)
                                                    if (clean != null) {
                                                        val tagWeight = tagWeightsMap[clean]
                                                        if (tagWeight != null) {
                                                            tagSum += tagWeight
                                                            matchedGenres.add("$clean (w: ${String.format("%.2f", tagWeight)})")
                                                        }
                                                    }
                                                }

                                                val matchedAuthor = candidateManga.author?.lowercase()?.trim()
                                                val matchedArtist = candidateManga.artist?.lowercase()?.trim()
                                                if (!matchedAuthor.isNullOrBlank()) {
                                                    if (recentAuthors.contains(matchedAuthor)) {
                                                        tagSum += 2.5
                                                        matchedGenres.add("Recent Author: $matchedAuthor (w: 2.50)")
                                                    } else if (favoriteAuthors.contains(matchedAuthor)) {
                                                        tagSum += 1.5
                                                        matchedGenres.add("Author: $matchedAuthor (w: 1.50)")
                                                    }
                                                }
                                                if (!matchedArtist.isNullOrBlank()) {
                                                    if (recentArtists.contains(matchedArtist)) {
                                                        tagSum += 2.5
                                                        matchedGenres.add("Recent Artist: $matchedArtist (w: 2.50)")
                                                    } else if (favoriteArtists.contains(matchedArtist)) {
                                                        tagSum += 1.5
                                                        matchedGenres.add("Artist: $matchedArtist (w: 1.50)")
                                                    }
                                                }

                                                if (tagSum == 0.0) {
                                                    SuggestionsReport.log("INFO", "Candidate '${smanga.title}' skipped: no matching tags or author/artist.")
                                                    SuggestionsReport.zeroScoreCount.update { it + 1 }
                                                    return@withPermit
                                                }
                                                var score = extWeight * tagSum
                                                if (cooldownUrls.contains(smanga.url)) {
                                                    // 60% recency cooldown penalty to rotate fresh titles
                                                    score *= 0.4
                                                }
                                                SuggestionsReport.log("INFO", "Candidate '${smanga.title}' scored ${String.format("%.4f", score)} (extWeight: ${String.format("%.2f", extWeight)}, matchedTags: $matchedGenres, cooldown=${cooldownUrls.contains(smanga.url)})")
                                                candidateScore = score
                                            } else {
                                                val cleanCurrent = cleanAndFilterGenre(currentSearchTerm) ?: currentSearchTerm.lowercase().trim()
                                                val tagSum = tagWeightsMap[cleanCurrent] ?: 0.5
                                                var score = extWeight * tagSum
                                                if (cooldownUrls.contains(smanga.url)) {
                                                    score *= 0.4
                                                }
                                                SuggestionsReport.log("INFO", "Candidate '${smanga.title}' (uninitialized) scored ${String.format("%.4f", score)} (extWeight: ${String.format("%.2f", extWeight)}, currentSearchTerm: '$cleanCurrent', weight: ${String.format("%.2f", tagSum)}, cooldown=${cooldownUrls.contains(smanga.url)})")
                                                candidateScore = score
                                            }

                                            // SQLite Database Hygiene: only persist to DB if candidate passed all filters and has positive score
                                            if (candidateScore > 0.0) {
                                                val localManga = networkToLocalManga(candidateManga)
                                                synchronized(scoredSuggestions) {
                                                    scoredSuggestions.add(Pair(localManga, candidateScore))
                                                }
                                            }
                                        } else {
                                            SuggestionsReport.log("INFO", "Candidate '${smanga.title}' skipped: has blocked tag(s) or violates 18+ mode ($effectiveNsfwMode).")
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        logcat(LogPriority.WARN, e) { "Failed processing suggestion: ${smanga.title}" }
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                if (scoredSuggestions.isNotEmpty()) {
                    // Retrieve current suggestions to merge/deduplicate
                    val currentList = if (!hasReplacedInCurrentRun) {
                        emptyList()
                    } else {
                        try {
                            suggestionRepository.observeAll().first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }

                    val mergedMap = currentList.associateBy { it.manga.url }.toMutableMap()
                    scoredSuggestions.forEach { (manga, score) ->
                        val existing = mergedMap[manga.url]
                        if (existing == null || score > existing.relevance) {
                            mergedMap[manga.url] = tachiyomi.domain.suggestions.model.Suggestion(
                                manga = manga,
                                relevance = score,
                                createdAt = System.currentTimeMillis(),
                            )
                        }
                    }

                    val limitPref = suggestionsPreferences.maxSuggestionsToDisplay().get()
                    val maxSuggestions = limitPref + rankIdx * 50

                    // Epsilon-Greedy Discovery: 70% highest scoring + 30% stochastic exploration
                    val sortedCandidates = mergedMap.values.sortedByDescending { it.relevance }
                    val topQuota = (maxSuggestions * 0.70).toInt().coerceAtLeast(1)
                    val exploreQuota = (maxSuggestions - topQuota).coerceAtLeast(0)

                    val topPicks = sortedCandidates.take(topQuota)
                    val explorePool = sortedCandidates.drop(topQuota).take(150)
                    val explorePicks = explorePool.shuffled(random).take(exploreQuota)

                    // Interleave exploration candidates into the top list at positions 4, 9, 14, 19...
                    // so Spotlight (first 15 items) showcases genuine discoveries alongside top relevance
                    val interleaved = mutableListOf<tachiyomi.domain.suggestions.model.Suggestion>()
                    var topIdx = 0
                    var exploreIdx = 0
                    while (topIdx < topPicks.size || exploreIdx < explorePicks.size) {
                        val currentPosition = interleaved.size
                        val isExploreSlot = (currentPosition % 5 == 4)
                        if (isExploreSlot && exploreIdx < explorePicks.size) {
                            interleaved.add(explorePicks[exploreIdx++])
                        } else if (topIdx < topPicks.size) {
                            interleaved.add(topPicks[topIdx++])
                        } else if (exploreIdx < explorePicks.size) {
                            interleaved.add(explorePicks[exploreIdx++])
                        }
                    }

                    // Calibrate monotonic descending relevance across interleaved order
                    // so that SQLite's 'ORDER BY suggestions.relevance DESC' and UI sorting preserve
                    // the exact interleaved positions (including discovery picks at 4, 9, 14...)
                    val maxScore = (sortedCandidates.firstOrNull()?.relevance ?: 2.0).coerceAtLeast(1.0)
                    val rawMin = sortedCandidates.take(maxSuggestions).lastOrNull()?.relevance ?: 0.5
                    val minScore = if (rawMin < maxScore) rawMin.coerceAtLeast(0.2) else (maxScore * 0.2)
                    val scoreDenominator = (interleaved.size - 1).coerceAtLeast(1).toDouble()

                    // Re-fetch latest dismissed URLs to ensure in-flight dismissals are not resurrected
                    val latestDismissedUrls = try {
                        suggestionRepository.getDismissed().map { it.first }.toSet()
                    } catch (e: Exception) {
                        emptySet()
                    }

                    val finalSuggestions = interleaved.mapIndexedNotNull { idx, suggestion ->
                        if (suggestion.manga.url in latestDismissedUrls) return@mapIndexedNotNull null
                        val calibratedScore = maxScore - (idx / scoreDenominator) * (maxScore - minScore)
                        Pair(suggestion.manga, calibratedScore)
                    }

                    // Record newly suggested URLs to cooldown cache so next run features new titles
                    val newlySuggestedUrls = finalSuggestions.take(50).map { it.first.url }.toSet()
                    cooldownPrefs.edit().putStringSet("recent_suggested_urls", newlySuggestedUrls).apply()

                    SuggestionsReport.log("INFO", "Rank $rankIdx completed. Saving ${finalSuggestions.size} suggestions (fresh replacement=${!hasReplacedInCurrentRun}) out of ${mergedMap.size} merged candidates.")
                    finalSuggestions.forEachIndexed { idx, (manga, relevance) ->
                        if (idx < 5 || idx % 20 == 0) {
                            SuggestionsReport.log("INFO", "Suggestion position #${idx + 1}: '${manga.title}' (relevance score: ${String.format("%.4f", relevance)})")
                        }
                    }

                    suggestionRepository.replace(finalSuggestions)

                    if (!hasReplacedInCurrentRun) {
                        hasReplacedInCurrentRun = true
                        suggestionsPreferences.lastSuggestionsFetchTime().set(System.currentTimeMillis())
                    }
                } else {
                    SuggestionsReport.log("INFO", "Rank $rankIdx completed with 0 scored suggestions.")
                }
            }

            if (rankToLoad != -1) {
                // Fetch a specific rank/page only (lazy scrolling trigger)
                setProgress(androidx.work.workDataOf("progress" to 1, "total" to 1))
                awaitReaderClosed()
                processRank(rankToLoad)
            } else {
                // Full continuous update sequence starting from rank 0
                for (rank in 0 until totalRanks) {
                    awaitReaderClosed()
                    setProgress(androidx.work.workDataOf("progress" to rank + 1, "total" to totalRanks))
                    if (rank > 0) {
                        // Gentle background delay between ranks to protect CPU
                        delay(12000)
                        awaitReaderClosed()
                    }
                    processRank(rank)
                }
            }

            context.getSharedPreferences("suggestions_prefs", Context.MODE_PRIVATE).edit().remove("last_error").apply()
            SuggestionsReport.log("INFO", "Suggestions updates finished successfully.")
            logcat(LogPriority.INFO) { "Suggestions updated successfully." }
            return Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                SuggestionsReport.log("INFO", "SuggestionsWorker cancelled/interrupted.")
                throw e
            }
            SuggestionsReport.log("ERROR", "SuggestionsWorker execution failed: ${e.message}", e)
            logcat(LogPriority.ERROR, e) { "Error in SuggestionsWorker" }
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                context.getSharedPreferences("suggestions_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_error", sw.toString())
                    .apply()
            } catch (ignored: java.lang.Exception) {}
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "SuggestionsWorker"
        val isReaderActive = MutableStateFlow(false)
        private var hasTriggeredThisSession = false

        fun triggerOnAppStart(context: Context, force: Boolean = false) {
            val suggestionsPreferences = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.suggestions.service.SuggestionsPreferences>()
            if (!suggestionsPreferences.isSuggestionsEnabled().get()) return

            if (!force) {
                synchronized(this) {
                    if (hasTriggeredThisSession) return
                    hasTriggeredThisSession = true
                }
            }

            val request = androidx.work.OneTimeWorkRequestBuilder<SuggestionsWorker>()
                .setInputData(androidx.work.workDataOf("is_manual" to true))
                .build()

            val policy = if (force) {
                androidx.work.ExistingWorkPolicy.REPLACE
            } else {
                androidx.work.ExistingWorkPolicy.KEEP
            }

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "SuggestionsSessionWork",
                policy,
                request,
            )
        }

        fun isUpdateRunning(context: Context): Boolean {
            val workManager = androidx.work.WorkManager.getInstance(context)
            val workInfos = try {
                workManager.getWorkInfosForUniqueWork("SuggestionsSessionWork").get()
            } catch (e: Exception) {
                emptyList()
            }
            return workInfos.any { !it.state.isFinished }
        }

        fun scheduleBackground(context: Context, isEnabled: Boolean, intervalHours: Int? = null) {
            val suggestionsPreferences = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.suggestions.service.SuggestionsPreferences>()
            if (isEnabled) {
                val hours = (intervalHours ?: suggestionsPreferences.suggestionsInterval().get()).coerceIn(6, 168)
                // KMK --> Relaxed: CONNECTED (not UNMETERED) and no charging requirement.
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                // KMK <--

                val request = PeriodicWorkRequestBuilder<SuggestionsWorker>(
                    hours.toLong(),
                    TimeUnit.HOURS,
                    15,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    request,
                )
                logcat(LogPriority.INFO) { "Scheduled periodic suggestions updates background job every ${hours}h." }
            } else {
                cancelBackground(context)
            }
        }

        fun cancelBackground(context: Context) {
            androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            logcat(LogPriority.INFO) { "Cancelled suggestions background job." }
        }

        fun cleanAndFilterGenre(genre: String): String? {
            val trimmed = genre.trim()
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                val prefix = parts[0].lowercase().trim()
                val value = parts[1].trim()
                if (prefix == "tag" || prefix == "male" || prefix == "female") {
                    return value.lowercase()
                } else {
                    return null
                }
            }
            return trimmed.lowercase()
        }
    }
}
