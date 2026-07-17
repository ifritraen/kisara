package eu.kanade.tachiyomi.data.suggestions

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.home.CachedFeedManga
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.interactor.GetSuggestionSources
import tachiyomi.domain.suggestions.model.SuggestionSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit

class HomeFeedWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        logcat(LogPriority.INFO) { "Starting home feed pre-fetch background task" }
        try {
            val uiPreferences = Injekt.get<UiPreferences>()
            val sourceManager = Injekt.get<SourceManager>()
            val getSuggestionSources = Injekt.get<GetSuggestionSources>()
            val networkToLocalManga = Injekt.get<NetworkToLocalManga>()

            val feedSourceLimit = uiPreferences.homeFeedSourceCount().get()
            val feedItemLimit = uiPreferences.homeFeedItemsCount().get()

            val usePinned = uiPreferences.homeFeedUsePinnedSources().get()
            val pinnedSet = uiPreferences.homeFeedPinnedSources().get().mapNotNull { it.toLongOrNull() }.toSet()

            val activeSources = if (usePinned && pinnedSet.isNotEmpty()) {
                val suggestionSources = getSuggestionSources.await()
                pinnedSet.mapNotNull { pinnedId ->
                    suggestionSources.firstOrNull { it.sourceId == pinnedId } ?: SuggestionSource(
                        sourceId = pinnedId,
                        isBlocked = false,
                        isUserAdded = true,
                        count = 0,
                        sortOrder = 0,
                    )
                }
            } else {
                getSuggestionSources.await()
                    .filter { !it.isBlocked }
                    .sortedWith(compareByDescending<SuggestionSource> { it.isUserAdded }.thenByDescending { it.count })
                    .take(feedSourceLimit)
            }

            if (activeSources.isEmpty()) {
                return Result.success()
            }

            val onlineSources = sourceManager.getOnlineSources()
            val targetSources = activeSources.mapNotNull { suggestionSrc ->
                onlineSources.firstOrNull { it.id == suggestionSrc.sourceId } as? CatalogueSource
            }

            val now = System.currentTimeMillis()
            val results = coroutineScope {
                val fetchJobs = targetSources.map { source ->
                    async {
                        try {
                            val mangaPage = if (source.supportsLatest) {
                                source.getLatestUpdates(1)
                            } else {
                                source.getPopularManga(1)
                            }

                            mangaPage.mangas.take(feedItemLimit).map { smanga ->
                                val networkManga = smanga.toDomainManga(source.id)
                                val localManga = networkToLocalManga(networkManga)
                                CachedFeedManga(
                                    id = localManga.id,
                                    sourceId = source.id,
                                    title = localManga.title,
                                    thumbnailUrl = localManga.thumbnailUrl,
                                    favorite = localManga.favorite,
                                    coverLastModified = localManga.coverLastModified,
                                    sourceName = source.name,
                                    fetchTime = now,
                                )
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to fetch background feed updates for source: ${source.name}" }
                            emptyList()
                        }
                    }
                }
                fetchJobs.awaitAll()
            }

            val mixedList = mutableListOf<CachedFeedManga>()
            for (i in 0 until feedItemLimit) {
                for (sourceList in results) {
                    if (i < sourceList.size) {
                        mixedList.add(sourceList[i])
                    }
                }
            }

            if (mixedList.isNotEmpty()) {
                val cacheFile = File(context.cacheDir, "home_feed_cache.json")
                try {
                    cacheFile.writeText(json.encodeToString(mixedList))
                    logcat(LogPriority.INFO) { "Home feed background pre-fetch cache updated successfully." }
                } catch (ioe: Exception) {
                    logcat(LogPriority.WARN, ioe) { "Failed to write feed cache file in background" }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in HomeFeedWorker" }
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "HomeFeedWorker"

        fun scheduleBackground(context: Context, isEnabled: Boolean) {
            if (isEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<HomeFeedWorker>(
                    12,
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
                logcat(LogPriority.INFO) { "Scheduled periodic home feed pre-fetch background job." }
            } else {
                cancelBackground(context)
            }
        }

        fun cancelBackground(context: Context) {
            androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            logcat(LogPriority.INFO) { "Cancelled home feed pre-fetch background job." }
        }
    }
}
