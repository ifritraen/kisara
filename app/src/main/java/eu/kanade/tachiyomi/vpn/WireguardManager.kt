package eu.kanade.tachiyomi.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    suspend fun startTunnel(name: String): Boolean {
        val configText = getProfileConfig(name) ?: return false
        return try {
            val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
            backend.setState(KmkTunnel(name), State.UP, config)
            _activeTunnel.value = name
            true
        } catch (e: Exception) {
            android.util.Log.e("WireguardManager", "Failed to start tunnel $name: ${e.message}", e)
            false
        }
    }

    suspend fun stopTunnel() {
        val active = _activeTunnel.value ?: return
        try {
            backend.setState(KmkTunnel(active), State.DOWN, null)
            _activeTunnel.value = null
            autoStartedSourceId = null
        } catch (e: Exception) {
            android.util.Log.e("WireguardManager", "Failed to stop tunnel: ${e.message}", e)
        }
    }

    fun getDefaultProfile(): String? {
        return prefs.getString("default_profile", null)
    }

    fun setDefaultProfile(name: String?) {
        prefs.edit().putString("default_profile", name).apply()
    }

    fun getSourceProfile(sourceId: Long): String? {
        return prefs.getString("source_$sourceId", null)
    }

    fun associateSource(sourceId: Long, profileName: String?) {
        if (profileName == null) {
            prefs.edit().remove("source_$sourceId").apply()
        } else {
            prefs.edit().putString("source_$sourceId", profileName).apply()
        }
    }

    suspend fun startTunnelForSource(sourceId: Long) {
        if (_activeTunnel.value != null) return

        val profile = getSourceProfile(sourceId) ?: getDefaultProfile()
        if (profile != null) {
            if (startTunnel(profile)) {
                autoStartedSourceId = sourceId
            }
        }
    }

    suspend fun stopTunnelForSource(sourceId: Long) {
        if (autoStartedSourceId == sourceId) {
            stopTunnel()
        }
    }
}
