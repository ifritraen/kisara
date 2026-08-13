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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

        if (!isManual) {
            delay(15000)
        }

        try {
            val mangaRepository = Injekt.get<MangaRepository>()
            val sourceManager = Injekt.get<SourceManager>()
            val suggestionRepository = Injekt.get<SuggestionRepository>()
            val networkToLocalManga = Injekt.get<NetworkToLocalManga>()
            val sourcePreferences = Injekt.get<SourcePreferences>()
            val suggestionsPreferences = Injekt.get<SuggestionsPreferences>()

            // 1. Scan favorites and history to update/populate taste database with deep engagement (duration + chapters)
            val favorites = mangaRepository.getFavorites()
            val readHistory = mangaRepository.getReadMangaNotInLibrary()
            val seed = (favorites + readHistory).distinctBy { it.id }

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

            val sortedTagFreqs = tagFrequencies.entries.sortedByDescending { it.value }
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

            val sortedSourceFreqs = sourceFrequencies.entries.sortedByDescending { it.value }
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

            val sortedAuthorFreqs = authorFrequencies.entries.sortedByDescending { it.value }
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

            val sortedArtistFreqs = artistFrequencies.entries.sortedByDescending { it.value }
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

            // 2. Fetch unblocked active configurations
            val allTags = suggestionRepository.getTags()
            val nonBlockedTags = allTags.filter { !it.isBlocked }
            val top10Tags = nonBlockedTags.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
            val activeTags = nonBlockedTags.filter { top10Tags.contains(it.tag) || it.isUserAdded }
                .sortedWith(compareBy<SuggestionTag> { it.sortOrder }.thenByDescending { it.count })

            val allSources = suggestionRepository.getSources()
            val nonBlockedSources = allSources.filter { !it.isBlocked }
            val top5Sources = nonBlockedSources.sortedByDescending { it.count }.take(5).map { it.sourceId }.toSet()
            val activeSources = nonBlockedSources.filter { top5Sources.contains(it.sourceId) || it.isUserAdded }
                .sortedWith(compareBy<SuggestionSource> { it.sortOrder }.thenByDescending { it.count })

            val allAuthors = suggestionRepository.getAuthors()
            val nonBlockedAuthors = allAuthors.filter { !it.isBlocked }
            val top5Authors = nonBlockedAuthors.sortedByDescending { it.count }.take(5).map { it.author }.toSet()
            val activeAuthors = nonBlockedAuthors.filter { top5Authors.contains(it.author) || it.isUserAdded }
                .sortedWith(compareBy<SuggestionAuthor> { it.sortOrder }.thenByDescending { it.count })

            val allArtists = suggestionRepository.getArtists()
            val nonBlockedArtists = allArtists.filter { !it.isBlocked }
            val top5Artists = nonBlockedArtists.sortedByDescending { it.count }.take(5).map { it.artist }.toSet()
            val activeArtists = nonBlockedArtists.filter { top5Artists.contains(it.artist) || it.isUserAdded }
                .sortedWith(compareBy<SuggestionArtist> { it.sortOrder }.thenByDescending { it.count })

            // Fetch defaults if empty
            val finalTags = if (activeTags.isEmpty()) {
                allTags.filter { !it.isBlocked }.sortedByDescending { it.count }.take(5)
            } else {
                activeTags
            }

            val finalSources = if (activeSources.isEmpty()) {
                allSources.filter { !it.isBlocked }.sortedByDescending { it.count }.take(5)
            } else {
                activeSources
            }

            val finalAuthors = if (activeAuthors.isEmpty()) {
                allAuthors.filter { !it.isBlocked }.sortedByDescending { it.count }.take(2)
            } else {
                activeAuthors
            }

            val finalArtists = if (activeArtists.isEmpty()) {
                allArtists.filter { !it.isBlocked }.sortedByDescending { it.count }.take(2)
            } else {
                activeArtists
            }

            if (finalTags.isEmpty() || finalSources.isEmpty()) {
                SuggestionsReport.log("WARNING", "No active tags (${finalTags.size}) or sources (${finalSources.size}) found in taste seed database.")
                logcat(LogPriority.INFO) { "No active tags or sources config found for suggestions." }
                return Result.success()
            }

            val showNsfw = sourcePreferences.showNsfwSource().get()
            val onlineSources = sourceManager.getOnlineSources()
                .filter { src ->
                    finalSources.any { it.sourceId == src.id } &&
                        (showNsfw || !src.name.contains("nsfw", ignoreCase = true))
                }

            SuggestionsReport.log("INFO", "Loaded suggestions active config. Selected tags: ${finalTags.map { it.tag }}. Searched extensions: ${onlineSources.map { "${it.name} (${it.id})" }}")

            if (onlineSources.isEmpty()) {
                SuggestionsReport.log("WARNING", "No online sources match active suggestions source config.")
                logcat(LogPriority.INFO) { "No active online sources match suggestions config." }
                return Result.success()
            }

            val favoriteAuthors = nonBlockedAuthors.map { it.author }.toSet()
            val favoriteArtists = nonBlockedArtists.map { it.artist }.toSet()

            val topAuthors = finalAuthors.map { it.author }
            val topArtists = finalArtists.map { it.artist }

            val maxTagsToMatch = suggestionsPreferences.maxTagsToMatch().get()
            val searchTerms = mutableListOf<String>()

            // Bucket 1: Explicit user-added tags & top authors/artists
            finalTags.filter { it.isUserAdded }.forEach { searchTerms.add(it.tag) }
            searchTerms.addAll(topAuthors.take(1))
            searchTerms.addAll(topArtists.take(1))

            // Bucket 2: Weighted Roulette Sampling across top and niche tags to avoid monopoly
            val candidatePool = finalTags.filterNot { it.isUserAdded }.toMutableList()
            if (candidatePool.isNotEmpty()) {
                // Pick top ranked tag
                searchTerms.add(candidatePool.removeAt(0).tag)

                // Shuffle / roulette sample secondary niche tags
                val remainingTags = (candidatePool + allTags.filter { !it.isBlocked && !finalTags.any { ft -> ft.tag == it.tag } })
                    .distinctBy { it.tag }
                    .shuffled()
                searchTerms.addAll(remainingTags.take(maxTagsToMatch - searchTerms.size).map { it.tag })
            }

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
            val random = java.util.Random()

            suspend fun processRank(rankIdx: Int, isFirstRank: Boolean) {
                if (rankIdx < 0 || rankIdx >= distinctSearchTerms.size) return
                val currentSearchTerm = distinctSearchTerms[rankIdx]
                SuggestionsReport.log("INFO", "Processing rank $rankIdx. Selected query/tag: '$currentSearchTerm'")
                logcat(LogPriority.INFO) { "Fetching suggestions rank $rankIdx (query/tag: $currentSearchTerm)" }

                // Dynamic page offset sampling (page 1 or 2) for deep exploration
                val pageToFetch = if (random.nextFloat() < 0.35f) 2 else 1

                val candidates = mutableMapOf<String, Pair<eu.kanade.tachiyomi.source.model.SManga, Long>>()
                coroutineScope {
                    val jobs = onlineSources.map { source ->
                        async {
                            semaphore.withPermit {
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

                val scoredSuggestions = mutableListOf<Pair<Manga, Double>>()
                val candidatesBySource = filteredCandidates.groupBy { it.second }

                // Dynamically compute candidates to initialize to fill suggestions limit
                val combos = finalTags.size * onlineSources.size
                val limitPref = suggestionsPreferences.maxSuggestionsToDisplay().get()
                val candidatesToFetch = if (combos > 0) {
                    Math.ceil(limitPref.toDouble() / combos).toInt().coerceIn(2, 10)
                } else {
                    5
                }

                coroutineScope {
                    val jobs = candidatesBySource.flatMap { (sourceId, candidates) ->
                        val source = onlineSources.firstOrNull { it.id == sourceId } ?: return@flatMap emptyList()
                        candidates.take(candidatesToFetch).map { (smanga, _) ->
                            async {
                                try {
                                    val networkManga = smanga.toDomainManga(sourceId)
                                    var localManga = networkToLocalManga(networkManga)

                                    if (localManga.favorite || favoriteUrls.contains(localManga.url) || favoriteTitles.contains(localManga.title.lowercase().trim())) {
                                        return@async
                                    }

                                    if (!localManga.initialized) {
                                        try {
                                            logcat(LogPriority.INFO) { "Fetching details/genres for suggestion: ${smanga.title}" }
                                            val details = source.getMangaDetails(smanga)
                                            try {
                                                if (details.url.isNullOrBlank()) {
                                                    details.url = smanga.url
                                                }
                                            } catch (urlErr: Exception) {
                                                details.url = smanga.url
                                            }
                                            localManga = networkToLocalManga(details.toDomainManga(sourceId))
                                        } catch (e: Exception) {
                                            logcat(LogPriority.WARN, e) { "Failed fetching details for: ${smanga.title}" }
                                        }
                                    }

                                    val hasBlockedTag = localManga.genre.orEmpty().any { genre ->
                                        val clean = cleanAndFilterGenre(genre)
                                        clean != null && blockedTagsGlobal.contains(clean)
                                    }
                                    val isHideNsfwEnabled = Injekt.get<eu.kanade.domain.ui.UiPreferences>().kisaraHideNsfwSuggestions().get()
                                    val isNsfwManga = isHideNsfwEnabled && eu.kanade.tachiyomi.util.NsfwDetector.isNsfw(localManga, localManga.title)
                                    if (!hasBlockedTag && !isNsfwManga) {
                                        val extWeight = sourceWeightsMap[sourceId] ?: 0.1
                                        if (localManga.initialized) {
                                            var tagSum = 0.0
                                            val matchedGenres = mutableListOf<String>()
                                            localManga.genre.orEmpty().forEach { genre ->
                                                val clean = cleanAndFilterGenre(genre)
                                                if (clean != null) {
                                                    val tagWeight = tagWeightsMap[clean]
                                                    if (tagWeight != null) {
                                                        tagSum += tagWeight
                                                        matchedGenres.add("$clean (w: ${String.format("%.2f", tagWeight)})")
                                                    }
                                                }
                                            }

                                            val matchedAuthor = localManga.author?.lowercase()?.trim()
                                            val matchedArtist = localManga.artist?.lowercase()?.trim()
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
                                                return@async
                                            }
                                            val score = extWeight * tagSum
                                            SuggestionsReport.log("INFO", "Candidate '${smanga.title}' scored ${String.format("%.4f", score)} (extWeight: ${String.format("%.2f", extWeight)}, matchedTags: $matchedGenres)")
                                            if (score > 0.0) {
                                                synchronized(scoredSuggestions) {
                                                    scoredSuggestions.add(Pair(localManga, score))
                                                }
                                            }
                                        } else {
                                            val cleanCurrent = cleanAndFilterGenre(currentSearchTerm) ?: currentSearchTerm.lowercase().trim()
                                            val tagSum = tagWeightsMap[cleanCurrent] ?: 0.5
                                            val score = extWeight * tagSum
                                            SuggestionsReport.log("INFO", "Candidate '${smanga.title}' (uninitialized) scored ${String.format("%.4f", score)} (extWeight: ${String.format("%.2f", extWeight)}, currentSearchTerm: '$cleanCurrent', weight: ${String.format("%.2f", tagSum)})")
                                            if (score > 0.0) {
                                                synchronized(scoredSuggestions) {
                                                    scoredSuggestions.add(Pair(localManga, score))
                                                }
                                            }
                                        }
                                    } else {
                                        SuggestionsReport.log("INFO", "Candidate '${smanga.title}' skipped: has blocked tag(s) or is NSFW.")
                                    }
                                } catch (e: Exception) {
                                    logcat(LogPriority.WARN, e) { "Failed processing suggestion: ${smanga.title}" }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                // Retrieve current suggestions to merge/deduplicate
                val currentList = if (isFirstRank) {
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

                val maxSuggestions = limitPref + rankIdx * 50
                val finalSuggestions = mergedMap.values
                    .sortedByDescending { it.relevance }
                    .take(maxSuggestions)
                    .map { Pair(it.manga, it.relevance) }

                SuggestionsReport.log("INFO", "Rank $rankIdx completed. Selected and returned back ${finalSuggestions.size} suggestions out of ${mergedMap.size} merged candidates.")
                finalSuggestions.forEachIndexed { idx, (manga, relevance) ->
                    if (idx < 5 || idx % 20 == 0) {
                        SuggestionsReport.log("INFO", "Suggestion position #${idx + 1}: '${manga.title}' (relevance score: ${String.format("%.4f", relevance)})")
                    }
                }

                suggestionRepository.replace(finalSuggestions)
            }

            if (rankToLoad != -1) {
                // Fetch a specific rank/page only (lazy scrolling trigger)
                setProgress(androidx.work.workDataOf("progress" to 1, "total" to 1))
                processRank(rankToLoad, isFirstRank = false)
            } else {
                // Full continuous update sequence starting from rank 0
                for (rank in 0 until totalRanks) {
                    setProgress(androidx.work.workDataOf("progress" to rank + 1, "total" to totalRanks))
                    if (rank > 0) {
                        // Gentle background delay between ranks to protect CPU
                        delay(12000)
                    }
                    processRank(rank, isFirstRank = (rank == 0))
                }
            }

            context.getSharedPreferences("suggestions_prefs", Context.MODE_PRIVATE).edit().remove("last_error").apply()
            SuggestionsReport.log("INFO", "Suggestions updates finished successfully.")
            logcat(LogPriority.INFO) { "Suggestions updated successfully." }
            return Result.success()
        } catch (e: Exception) {
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
        private var hasTriggeredThisSession = false

        fun triggerOnAppStart(context: Context) {
            val suggestionsPreferences = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.suggestions.service.SuggestionsPreferences>()
            if (!suggestionsPreferences.isSuggestionsEnabled().get()) return

            synchronized(this) {
                if (hasTriggeredThisSession) return
                hasTriggeredThisSession = true
            }

            val request = androidx.work.OneTimeWorkRequestBuilder<SuggestionsWorker>()
                .setInputData(androidx.work.workDataOf("is_manual" to true))
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "SuggestionsSessionWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
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

        fun scheduleBackground(context: Context, isEnabled: Boolean) {
            if (isEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<SuggestionsWorker>(
                    24,
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
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
                logcat(LogPriority.INFO) { "Scheduled periodic suggestions updates background job." }
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
