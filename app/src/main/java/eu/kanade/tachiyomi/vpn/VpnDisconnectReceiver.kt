package eu.kanade.tachiyomi.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VpnDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISCONNECT_VPN) {
            val pendingResult = goAsync()
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    Injekt.get<WireguardManager>().stopTunnel()
                } catch (e: Exception) {
                    android.util.Log.e("VpnDisconnectReceiver", "Failed to disconnect VPN: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DISCONNECT_VPN = "eu.kanade.tachiyomi.vpn.ACTION_DISCONNECT_VPN"
    }
}
