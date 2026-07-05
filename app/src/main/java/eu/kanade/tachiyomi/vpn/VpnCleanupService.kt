package eu.kanade.tachiyomi.vpn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VpnCleanupService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val binder = android.os.Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupVpn()
        stopSelf()
        return false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        cleanupVpn()
        stopSelf()
    }

    private fun cleanupVpn() {
        val uiPreferences = Injekt.get<UiPreferences>()
        if (uiPreferences.vpnDisconnectOnClose().get()) {
            val wireguardManager = Injekt.get<WireguardManager>()
            runBlocking {
                wireguardManager.stopTunnel()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
