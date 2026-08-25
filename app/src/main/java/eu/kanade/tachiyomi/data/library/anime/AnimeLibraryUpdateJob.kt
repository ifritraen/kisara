package eu.kanade.tachiyomi.data.library.anime

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueued
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class AnimeLibraryUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val updateAnime: UpdateAnime = Injekt.get()
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get()

    private val notifier = AnimeLibraryUpdateNotifier(context)

    private var animeToUpdate: List<LibraryAnime> = emptyList()

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground anime update job" }
        }

        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val restrictions = libraryPreferences.autoUpdateDeviceRestrictions().get()
                if ((LibraryPreferences.DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        val categoryId = if (inputData.keyValueMap.containsKey(KEY_CATEGORY)) {
            inputData.getLong(KEY_CATEGORY, -1L)
        } else {
            -999L
        }
        addAnimeToQueue(categoryId)

        return withIOContext {
            try {
                updateLibrary()
                Result.success()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e) { "Failed to update anime library" }
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun addAnimeToQueue(categoryId: Long) {
        val libraryAnime = getLibraryAnime.await()
        animeToUpdate = if (categoryId >= 0) {
            libraryAnime.filter { it.category == categoryId }
        } else {
            libraryAnime
        }
    }

    private suspend fun updateLibrary() = coroutineScope {
        val semaphore = Semaphore(3)
        val progress = AtomicInteger(0)
        val updatedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val total = animeToUpdate.size
        val animes = animeToUpdate.map { it.anime }

        notifier.showProgressNotification(animes, 0, total, 0, 0)

        animeToUpdate.map { item ->
            async {
                semaphore.withPermit {
                    ensureActive()
                    val anime = item.anime
                    val source = sourceManager.get(anime.source)
                    if (source != null) {
                        try {
                            val networkEpisodes = source.getEpisodeList(anime.toSAnime())
                            val hasNew = syncEpisodesWithSource.await(networkEpisodes, anime, source)
                            if (hasNew.isNotEmpty()) {
                                updatedCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logcat(LogPriority.WARN, e) { "Failed to update anime ${anime.title}" }
                            failedCount.incrementAndGet()
                        }
                    }
                    val current = progress.incrementAndGet()
                    notifier.showProgressNotification(
                        animes,
                        current,
                        total,
                        updatedCount.get(),
                        failedCount.get(),
                    )
                }
            }
        }.awaitAll()
    }

    companion object {
        private const val TAG = "AnimeLibraryUpdate"
        private const val WORK_NAME_AUTO = "AnimeLibraryUpdate-Auto"
        private const val WORK_NAME_MANUAL = "AnimeLibraryUpdate-Manual"
        private const val KEY_CATEGORY = "category_id"
        private const val KEY_ENTRY_IDS = "entry_ids"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val request = androidx.work.PeriodicWorkRequestBuilder<AnimeLibraryUpdateJob>(
                    interval.toLong(),
                    java.util.concurrent.TimeUnit.HOURS,
                    10,
                    java.util.concurrent.TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 10, java.util.concurrent.TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(context: Context, categoryId: Long? = null): Boolean {
            val inputData = categoryId
                ?.let { workDataOf(KEY_CATEGORY to it) }
                ?: workDataOf()
            return enqueueManualUpdate(context, inputData)
        }

        private fun enqueueManualUpdate(context: Context, inputData: Data): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG) || wm.isRunningOrEnqueued(WORK_NAME_MANUAL)) {
                return false
            }

            val request = OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()

            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)
            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            val future = wm.getWorkInfos(workQuery)
            future.addListener(
                {
                    runCatching { future.get() }
                        .getOrDefault(emptyList())
                        .forEach {
                            wm.cancelWorkById(it.id)
                            if (it.tags.contains(WORK_NAME_AUTO)) {
                                setupTask(context)
                            }
                        }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }
}
