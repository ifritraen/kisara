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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.repository.SuggestionRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SuggestionsWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        logcat(LogPriority.INFO) { "Starting suggestions updates in background" }
        try {
            val mangaRepository = Injekt.get<MangaRepository>()
            val sourceManager = Injekt.get<SourceManager>()
            val suggestionRepository = Injekt.get<SuggestionRepository>()
            val networkToLocalManga = Injekt.get<NetworkToLocalManga>()
            val sourcePreferences = Injekt.get<SourcePreferences>()

            // 1. Get seed content
            val favorites = mangaRepository.getFavorites()
            val readHistory = mangaRepository.getReadMangaNotInLibrary()
            val seed = (favorites + readHistory).distinctBy { it.id }

            val weightedTags = mutableMapOf<String, Double>()
            val topTags = mutableListOf<String>()

            if (seed.isNotEmpty()) {
                val allTags = seed.flatMap { it.genre.orEmpty() }
                if (allTags.isNotEmpty()) {
                    val frequencies = allTags.groupingBy { it.lowercase() }.eachCount()
                    val maxFreq = frequencies.values.maxOrNull()?.toDouble() ?: 1.0
                    frequencies.mapValuesTo(weightedTags) { (_, count) ->
                        0.1 + 0.9 * (count.toDouble() / maxFreq)
                    }

                    // Top 3 tags to search
                    weightedTags.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .forEach { topTags.add(it.key) }
                }
            }

            // 3. Prioritize sources
            val seedSourceIds = seed.map { it.source }.toSet()
            val showNsfw = sourcePreferences.showNsfwSource().get()

            val enabledSources = sourceManager.getOnlineSources()
                .filter { source ->
                    // Exclude sources if NSFW is disabled and the source tag/name is suspicious
                    showNsfw || !source.name.contains("nsfw", ignoreCase = true)
                }

            val prioritizedSources = enabledSources.sortedWith(
                compareByDescending { seedSourceIds.contains(it.id) },
            ).take(10) // Limit to 10 sources to prevent rate limits/overload

            if (prioritizedSources.isEmpty()) {
                logcat(LogPriority.INFO) { "No sources available for querying suggestions." }
                return Result.success()
            }

            // 4. Query sources concurrently using Semaphore
            val semaphore = Semaphore(3)
            val candidates = mutableMapOf<String, Pair<eu.kanade.tachiyomi.source.model.SManga, Long>>() // url -> (SManga, sourceId)

            coroutineScope {
                val jobs = prioritizedSources.map { source ->
                    async {
                        semaphore.withPermit {
                            if (topTags.isEmpty()) {
                                try {
                                    val results = source.getPopularManga(1)
                                    results.mangas.forEach { smanga ->
                                        synchronized(candidates) {
                                            candidates[smanga.url] = Pair(smanga, source.id)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logcat(LogPriority.WARN, e) { "Failed querying popular suggestions for source: ${source.name}" }
                                }
                            } else {
                                for (tag in topTags) {
                                    try {
                                        val results = source.getSearchManga(1, tag, eu.kanade.tachiyomi.source.model.FilterList())
                                        results.mangas.forEach { smanga ->
                                            synchronized(candidates) {
                                                candidates[smanga.url] = Pair(smanga, source.id)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        logcat(LogPriority.WARN, e) { "Failed querying suggestions for source: ${source.name}" }
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // 5. Convert candidates and calculate relevance
            val favoriteUrls = favorites.map { it.url }.toSet()
            val historyUrls = readHistory.map { it.url }.toSet()
            val filteredCandidates = candidates.values.filter { (smanga, _) ->
                !favoriteUrls.contains(smanga.url) && !historyUrls.contains(smanga.url)
            }

            val scoredSuggestions = mutableListOf<Pair<Manga, Double>>()
            for ((smanga, sourceId) in filteredCandidates.take(50)) { // limit to 50 local inserts
                try {
                    val networkManga = smanga.toDomainManga(sourceId)
                    val localManga = networkToLocalManga(networkManga)

                    // Score relevance
                    var score = 0.0
                    if (weightedTags.isEmpty()) {
                        score = 1.0
                    } else {
                        localManga.genre.orEmpty().forEach { tag ->
                            score += weightedTags[tag.lowercase()] ?: 0.0
                        }
                    }
                    if (score > 0.0) {
                        scoredSuggestions.add(Pair(localManga, score))
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed importing suggestion: ${smanga.title}" }
                }
            }

            // 6. Save top suggestions
            val topSuggestions = scoredSuggestions.sortedByDescending { it.second }.take(30)
            suggestionRepository.replace(topSuggestions)
            context.getSharedPreferences("suggestions_prefs", Context.MODE_PRIVATE).edit().remove("last_error").apply()
            logcat(LogPriority.INFO) { "Suggestions updated successfully with ${topSuggestions.size} entries." }
            return Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in SuggestionsWorker" }
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                context.getSharedPreferences("suggestions_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_error", sw.toString())
                    .apply()
            } catch (ignored: Exception) {}
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "SuggestionsWorker"

        fun scheduleBackground(context: Context, isEnabled: Boolean) {
            if (isEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi
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
    }
}
