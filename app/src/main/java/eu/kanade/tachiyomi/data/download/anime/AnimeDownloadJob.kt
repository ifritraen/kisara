package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.R
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It pauses active downloads while waiting for network recovery.
 */
class AnimeDownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val downloadManager: AnimeDownloadManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_EPISODE_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        if (downloadManager.queueState.value.isEmpty()) {
            return Result.failure()
        }

        var waitingForNetwork = false

        fun isNetworkAvailable(): Boolean {
            if (!applicationContext.isOnline()) return false
            if (downloadPreferences.downloadOnlyOverWifi().get() && !applicationContext.isConnectedToWifi()) return false
            return true
        }

        fun pauseForNetwork() {
            val reason = if (!applicationContext.isOnline()) {
                applicationContext.getString(R.string.download_notifier_no_network)
            } else {
                applicationContext.getString(R.string.download_notifier_text_only_wifi)
            }
            downloadManager.downloaderPauseForNetwork(reason)
            waitingForNetwork = downloadManager.queueState.value.isNotEmpty()
        }

        if (isNetworkAvailable()) {
            downloadManager.downloaderStart()
        } else {
            pauseForNetwork()
        }

        if (!downloadManager.isRunning && !waitingForNetwork) {
            return Result.failure()
        }

        setForegroundSafely()

        try {
            coroutineScope {
                var pendingPauseJob: Job? = null

                fun handleNetworkAvailable(available: Boolean) {
                    if (available) {
                        pendingPauseJob?.cancel()
                        pendingPauseJob = null
                        if (waitingForNetwork) {
                            waitingForNetwork = false
                            downloadManager.downloaderStart()
                        }
                    } else {
                        if (pendingPauseJob?.isActive == true) return
                        pendingPauseJob = launch {
                            delay(NETWORK_LOSS_GRACE_PERIOD)
                            if (!isNetworkAvailable()) {
                                pauseForNetwork()
                            }
                        }
                    }
                }

                val networkStatusJob = downloadPreferences.downloadOnlyOverWifi().changes()
                    .map { isNetworkAvailable() }
                    .distinctUntilChanged()
                    .onEach { handleNetworkAvailable(it) }
                    .launchIn(this)

                try {
                    while (
                        !isStopped &&
                        downloadManager.queueState.value.isNotEmpty() &&
                        (downloadManager.isRunning || waitingForNetwork || pendingPauseJob?.isActive == true)
                    ) {
                        delay(1.seconds)
                    }
                } finally {
                    pendingPauseJob?.cancel()
                    networkStatusJob.cancel()
                }
            }
        } finally {
            if (downloadManager.isRunning && downloadManager.queueState.value.isNotEmpty()) {
                // The worker is going away (system stop / cancellation). Park the queue instead of
                // leaving items stuck in DOWNLOADING so they can be resumed later.
                downloadManager.downloaderPause()
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "AnimeDownloader"

        /** How long connectivity may be unavailable before active downloads are paused. */
        private val NETWORK_LOSS_GRACE_PERIOD = 20.seconds

        /**
         * Enqueues the download worker.
         *
         * @param keepExistingWork pass true when a download is already actively running in this
         * process. REPLACE would cancel that running worker, which kills the active ffmpeg session
         * and restarts the episode from 0%.
         */
        fun start(context: Context, keepExistingWork: Boolean = false) {
            val downloadPreferences = Injekt.get<DownloadPreferences>()
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .setConstraints(getConstraints(downloadPreferences.downloadOnlyOverWifi().get()))
                // Expedited work starts far more reliably on throttling OEMs (MIUI/HyperOS).
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()
            // REPLACE revives work stuck in ENQUEUED (unsatisfied constraints or OEM throttling),
            // which KEEP would silently drop, so it stays the default. But REPLACE also cancels a
            // *running* worker, so use KEEP whenever a download is already in progress.
            val policy = if (keepExistingWork) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, policy, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        }

        private fun getConstraints(requireWifi: Boolean): Constraints {
            if (!requireWifi) {
                return Constraints(requiredNetworkType = NetworkType.CONNECTED)
            }

            val networkRequest = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            return Constraints.Builder()
                // The network request only applies to Android 9+, otherwise the network type is used.
                .setRequiredNetworkRequest(networkRequest, NetworkType.UNMETERED)
                .build()
        }
    }
}
