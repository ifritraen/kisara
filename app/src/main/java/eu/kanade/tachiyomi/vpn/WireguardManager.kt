package eu.kanade.tachiyomi.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File

class KmkTunnel(private val name: String) : Tunnel {
    override fun getName(): String = name
    override fun onStateChange(state: State) {}
}

class WireguardManager(private val context: Context) {

    private val backend by lazy { GoBackend(context) }
    private val vpnDir = File(context.filesDir, "wireguard")
    private val prefs = context.getSharedPreferences("wireguard_prefs", Context.MODE_PRIVATE)

    private val _activeTunnel = MutableStateFlow<String?>(null)
    val activeTunnel: StateFlow<String?> = _activeTunnel.asStateFlow()

    private var autoStartedSourceId: Long? = null

    init {
        if (!vpnDir.exists()) {
            vpnDir.mkdirs()
        }
    }

    fun getProfiles(): List<String> {
        return vpnDir.listFiles { file -> file.extension == "conf" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            .orEmpty()
    }

    fun importProfile(name: String, content: String): Boolean {
        return try {
            Config.parse(ByteArrayInputStream(content.toByteArray()))
            val file = File(vpnDir, "$name.conf")
            file.writeText(content)
            true
        } catch (e: Exception) {
            android.util.Log.e("WireguardManager", "Failed to import profile $name: ${e.message}", e)
            false
        }
    }

    fun deleteProfile(name: String) {
        val file = File(vpnDir, "$name.conf")
        if (file.exists()) {
            file.delete()
        }
        if (getDefaultProfile() == name) {
            setDefaultProfile(null)
        }
        val allPrefs = prefs.all
        val editor = prefs.edit()
        for ((key, value) in allPrefs) {
            if (key.startsWith("source_") && value == name) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun getProfileConfig(name: String): String? {
        val file = File(vpnDir, "$name.conf")
        return if (file.exists()) file.readText() else null
    }

    suspend fun startTunnel(name: String): Boolean = withContext(Dispatchers.IO) {
        val configText = getProfileConfig(name) ?: return@withContext false
        try {
            val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
            val currentInterface = config.`interface`
            val builder = com.wireguard.config.Interface.Builder()
                .addAddresses(currentInterface.addresses)
                .addDnsServers(currentInterface.dnsServers)
                .addDnsSearchDomains(currentInterface.dnsSearchDomains)
                .setKeyPair(currentInterface.keyPair)
            currentInterface.listenPort.ifPresent { builder.setListenPort(it) }
            currentInterface.mtu.ifPresent { builder.setMtu(it) }
            builder.includeApplication(context.packageName)
            val appConfig = Config.Builder()
                .setInterface(builder.build())
                .addPeers(config.peers)
                .build()
            backend.setState(KmkTunnel(name), State.UP, appConfig)
            _activeTunnel.value = name
            showVpnNotification(name)
            try {
                context.startService(Intent(context, VpnCleanupService::class.java))
            } catch (e: Exception) {
                android.util.Log.e("WireguardManager", "Failed to start VpnCleanupService: ${e.message}", e)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("WireguardManager", "Failed to start tunnel $name: ${e.message}", e)
            false
        }
    }

    suspend fun stopTunnel() = withContext(Dispatchers.IO) {
        try {
            val running = backend.runningTunnelNames
            for (tunnel in running) {
                backend.setState(KmkTunnel(tunnel), State.DOWN, null)
            }
            _activeTunnel.value = null
            autoStartedSourceId = null
            dismissVpnNotification()
            try {
                context.stopService(Intent(context, VpnCleanupService::class.java))
            } catch (e: Exception) {
                // Ignore
            }
        } catch (e: Exception) {
            android.util.Log.e("WireguardManager", "Failed to stop tunnel: ${e.message}", e)
        }
    }

    private fun showVpnNotification(profileName: String) {
        val intent = Intent(context, VpnDisconnectReceiver::class.java).apply {
            action = VpnDisconnectReceiver.ACTION_DISCONNECT_VPN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        context.notify(
            Notifications.ID_VPN,
            Notifications.CHANNEL_VPN,
        ) {
            setContentTitle("VPN Connected")
            setContentText("Active Profile: $profileName")
            setSmallIcon(R.drawable.globe)
            setOngoing(true)
            setShowWhen(false)
            addAction(
                R.drawable.ic_close_24dp,
                "Disconnect",
                pendingIntent,
            )
            setContentIntent(pendingIntent)
        }
    }

    private fun dismissVpnNotification() {
        context.cancelNotification(Notifications.ID_VPN)
    }

    fun getDefaultProfile(): String? {
        return prefs.getString("default_profile", null)
    }

    fun setDefaultProfile(name: String?) {
        prefs.edit().putString("default_profile", name).apply()
    }

    fun cleanSourceName(name: String): String {
        return name.replace(Regex("\\s*\\([^)]+\\)"), "").trim()
    }

    fun getSourceProfileByName(cleanedName: String): String? {
        return prefs.getString("source_name_$cleanedName", null)
    }

    fun associateSourceByName(cleanedName: String, profileName: String?) {
        if (profileName == null) {
            prefs.edit().remove("source_name_$cleanedName").apply()
        } else {
            prefs.edit().putString("source_name_$cleanedName", profileName).apply()
        }
    }

    fun getSourceProfile(sourceId: Long): String? {
        val source = try {
            Injekt.get<SourceManager>().getOrStub(sourceId)
        } catch (e: Exception) {
            null
        }
        val cleanedName = source?.name?.let { cleanSourceName(it) }
        return if (cleanedName != null) {
            getSourceProfileByName(cleanedName) ?: prefs.getString("source_$sourceId", null)
        } else {
            prefs.getString("source_$sourceId", null)
        }
    }

    fun associateSource(sourceId: Long, profileName: String?) {
        val source = try {
            Injekt.get<SourceManager>().getOrStub(sourceId)
        } catch (e: Exception) {
            null
        }
        val cleanedName = source?.name?.let { cleanSourceName(it) }
        if (cleanedName != null) {
            associateSourceByName(cleanedName, profileName)
        } else {
            if (profileName == null) {
                prefs.edit().remove("source_$sourceId").apply()
            } else {
                prefs.edit().putString("source_$sourceId", profileName).apply()
            }
        }
    }

    private val activeRequesters = mutableSetOf<String>()

    suspend fun startTunnelForSource(sourceId: Long, requesterKey: String = "generic") {
        val profile = getSourceProfile(sourceId) ?: getDefaultProfile()
        if (profile != null) {
            synchronized(activeRequesters) {
                activeRequesters.add(requesterKey)
            }
            if (_activeTunnel.value == null) {
                if (startTunnel(profile)) {
                    autoStartedSourceId = sourceId
                }
            }
        }
    }

    suspend fun stopTunnelForSource(sourceId: Long, requesterKey: String = "generic") {
        val becameEmpty = synchronized(activeRequesters) {
            activeRequesters.remove(requesterKey)
            activeRequesters.isEmpty()
        }
        if (becameEmpty && autoStartedSourceId == sourceId) {
            stopTunnel()
        }
    }
}
