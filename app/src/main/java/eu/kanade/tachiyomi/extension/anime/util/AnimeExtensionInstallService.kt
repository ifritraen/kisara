package eu.kanade.tachiyomi.extension.anime.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.installer.InstallerAnime
import eu.kanade.tachiyomi.extension.anime.installer.PackageInstallerInstallerAnime
import eu.kanade.tachiyomi.extension.anime.installer.ShizukuInstallerAnime
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller.Companion.EXTRA_DOWNLOAD_ID
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller.Companion.EXTRA_PACKAGE_NAME
import eu.kanade.tachiyomi.util.system.getSerializableExtraCompat
import eu.kanade.tachiyomi.util.system.notificationBuilder
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class AnimeExtensionInstallService : Service() {

    private var installer: InstallerAnime? = null
    private var installerType: BasePreferences.ExtensionInstaller? = null

    override fun onCreate() {
        val notification = notificationBuilder(Notifications.CHANNEL_EXTENSIONS_UPDATE) {
            setSmallIcon(R.drawable.ic_kisara)
            setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
            setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.drawable.kisara))
            setAutoCancel(false)
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(stringResource(MR.strings.ext_install_service_notif))
            setProgress(100, 100, true)
        }.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_EXTENSION_INSTALLER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.ID_EXTENSION_INSTALLER, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)?.takeIf { it != -1L }
        val installerUsed = intent?.getSerializableExtraCompat<BasePreferences.ExtensionInstaller>(
            EXTRA_INSTALLER,
        )
        val pkgName = intent?.getStringExtra(AnimeExtensionInstaller.Companion.EXTRA_PACKAGE_NAME)
        if (uri == null || id == null || installerUsed == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (installer == null || (installerType != installerUsed && installer!!.isIdle())) {
            installer = when (installerUsed) {
                BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> PackageInstallerInstallerAnime(
                    this,
                )
                BasePreferences.ExtensionInstaller.SHIZUKU -> ShizukuInstallerAnime(this)
                else -> {
                    logcat(LogPriority.ERROR) { "Not implemented for installer $installerUsed" }
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        installerType = installerUsed
        installer!!.addToQueue(id, uri, pkgName)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        installer?.onDestroy()
        installer = null
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_INSTALLER = "EXTRA_INSTALLER"

        fun getIntent(
            context: Context,
            downloadId: Long,
            uri: Uri,
            installer: BasePreferences.ExtensionInstaller,
            pkgName: String? = null,
        ): Intent {
            return Intent(context, AnimeExtensionInstallService::class.java)
                .setDataAndType(uri, AnimeExtensionInstaller.APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .putExtra(EXTRA_INSTALLER, installer)
                .putExtra(EXTRA_PACKAGE_NAME, pkgName)
        }
    }
}
