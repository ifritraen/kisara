package eu.kanade.tachiyomi.extension.anime.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.anime.installer.InstallerAnime
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The installer which installs, updates and uninstalls anime extensions.
 *
 * @param context The application context.
 */
internal class AnimeExtensionInstaller(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeSteps = ConcurrentHashMap<Long, MutableStateFlow<InstallStep>>()
    private val sideloadErrors = ConcurrentHashMap<String, Throwable>()

    fun getAndClearSideloadError(pkgName: String): Throwable? {
        return sideloadErrors.remove(pkgName)
    }

    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()
    private val httpClient: OkHttpClient = Injekt.get<NetworkHelper>().client

    /**
     * Adds the given anime extension to the downloads queue and returns a flow containing its
     * step in the installation process.
     *
     * @param url The url of the apk.
     * @param extension The extension to install.
     */
    fun downloadAndInstall(
        url: String,
        extension: AnimeExtension,
        isSideload: Boolean = false,
        isUpdateForPrivatelyInstalled: Boolean = false,
    ): Flow<InstallStep> {
        val pkgName = extension.pkgName
        val downloadId = pkgName.toDownloadId()
        cancelInstall(pkgName)

        val step = MutableStateFlow(InstallStep.Pending)
        activeSteps[downloadId] = step

        val job = scope.launch {
            val tmpFile = File(context.cacheDir, "anime_extension_$pkgName.apk")
            try {
                step.value = InstallStep.Downloading
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download extension: HTTP ${response.code}")
                    }
                    tmpFile.outputStream().use { output ->
                        response.body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                step.value = InstallStep.Installing
                if (isSideload) {
                    try {
                        eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.storeSideloadedApk(context, extension.pkgName, tmpFile)
                        step.value = InstallStep.Installed
                        Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>().registerSideloadedExtension(extension.pkgName)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to store sideloaded anime extension" }
                        sideloadErrors[extension.pkgName] = e
                        step.value = InstallStep.Error
                    }
                    tmpFile.delete()
                } else {
                    installApk(downloadId, tmpFile, pkgName)
                }
            } catch (e: Exception) {
                if (e !is InterruptedException) {
                    logcat(LogPriority.ERROR, e)
                    if (isSideload) {
                        sideloadErrors[extension.pkgName] = e
                    }
                    step.value = InstallStep.Error
                }
                tmpFile.delete()
            }
        }

        activeJobs[pkgName] = job

        return step.asStateFlow().onCompletion {
            activeJobs.remove(pkgName)
            activeSteps.remove(downloadId)
            job.cancel()
        }
    }

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param tempFile The file of the extension to install. Delete after use.
     */
    private fun installApk(downloadId: Long, tempFile: File, pkgName: String) {
        when (val installer = extensionInstaller.get()) {
            BasePreferences.ExtensionInstaller.LEGACY -> {
                val intent = Intent(context, AnimeExtensionInstallActivity::class.java)
                    .setDataAndType(tempFile.getUriCompat(context), APK_MIME)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                    .putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

                context.startActivity(intent)
            }
            BasePreferences.ExtensionInstaller.PRIVATE -> {
                try {
                    if (AnimeExtensionLoader.installPrivateExtensionFile(context, tempFile)) {
                        updateInstallStep(downloadId, InstallStep.Installed)
                    } else {
                        updateInstallStep(downloadId, InstallStep.Error)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to install private anime extension." }
                    updateInstallStep(downloadId, InstallStep.Error)
                }

                tempFile.delete()
            }
            else -> {
                val intent = AnimeExtensionInstallService.getIntent(
                    context,
                    downloadId,
                    tempFile.getUriCompat(context),
                    installer,
                    pkgName,
                )
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    /**
     * Cancels extension install.
     */
    fun cancelInstall(pkgName: String) {
        activeJobs.remove(pkgName)?.cancel()
        InstallerAnime.cancelInstallQueue(context, pkgName.toDownloadId())
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        AnimeExtensionLoader.uninstallPrivateExtension(context, pkgName)
        eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.deleteSideloadedApk(context, pkgName)
        if (context.isPackageInstalled(pkgName)) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            AnimeExtensionInstallReceiver.notifyRemoved(context, pkgName)
        }
    }

    /**
     * Sets the step of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param step New install step.
     */
    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        activeSteps[downloadId]?.let { it.value = step }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "AnimeExtensionInstaller.extra.DOWNLOAD_ID"
        const val EXTRA_PACKAGE_NAME = "AnimeExtensionInstaller.extra.PACKAGE_NAME"

        /** Convert packageName to download ID avoiding negative number */
        private fun String.toDownloadId(): Long = hashCode().toLong() and 0xFFFFFFFFL
    }
}
