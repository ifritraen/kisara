package eu.kanade.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationJob(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val translationManager: TranslationManager = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle("Translating chapters...")
            setContentText("Preparing translation queue")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
            setOngoing(true)
        }.build()
        return ForegroundInfo(
            ID_TRANSLATOR_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        val pending = translationManager.queueState.value
        if (pending.isEmpty()) {
            return Result.success()
        }

        setForegroundSafely()

        val progressJob = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            translationManager.progressState.collectLatest { progress ->
                if (progress != null) {
                    val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
                        setContentTitle("Translating ${progress.chapterName}")
                        setContentText("${progress.step} (${progress.currentPage}/${progress.totalPages})")
                        setSmallIcon(android.R.drawable.stat_sys_download)
                        setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
                        setProgress(progress.totalPages, progress.currentPage, false)
                        setOngoing(true)
                    }.build()
                    val foregroundInfo = ForegroundInfo(
                        ID_TRANSLATOR_PROGRESS,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        } else {
                            0
                        },
                    )
                    try {
                        setForeground(foregroundInfo)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to set foreground for TranslationJob" }
                    }
                }
            }
        }

        try {
            translationManager.translateQueueSync()
        } finally {
            progressJob.cancel()
            try {
                progressJob.join()
            } catch (e: Exception) {
                // Ignore
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "TranslationJob"
        private const val ID_TRANSLATOR_PROGRESS = -501

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }
    }
}
