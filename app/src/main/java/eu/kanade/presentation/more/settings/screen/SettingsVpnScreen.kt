package eu.kanade.presentation.more.settings.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsVpnScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = KMR.strings.pref_category_vpn

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val wireguardManager = remember { Injekt.get<eu.kanade.tachiyomi.vpn.WireguardManager>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }

        var profiles by remember { mutableStateOf(wireguardManager.getProfiles()) }
        val activeTunnel by wireguardManager.activeTunnel.collectAsState()
        var defaultProfile by remember { mutableStateOf(wireguardManager.getDefaultProfile()) }

        var selectedProfileForMenu by remember { mutableStateOf<String?>(null) }
        var selectedGroupedSourceNameForProfileSelection by remember { mutableStateOf<String?>(null) }

        var pendingProfileToConnect by remember { mutableStateOf<String?>(null) }
        val vpnLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    pendingProfileToConnect?.let { profile ->
                        scope.launch {
                            wireguardManager.startTunnel(profile)
                        }
                    }
                }
                pendingProfileToConnect = null
            },
        )

        val sources = remember {
            sourceManager.getOnlineSources()
                .sortedBy { it.name }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                try {
                    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) cursor.getString(index) else null
                        } else {
                            null
                        }
                    } ?: uri.path?.substringAfterLast('/') ?: "imported"

                    if (name.endsWith(".zip", ignoreCase = true)) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val zipInput = java.util.zip.ZipInputStream(input)
                            var entry = zipInput.nextEntry
                            var importedAny = false
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.endsWith(".conf", ignoreCase = true)) {
                                    val entryName = entry.name.substringAfterLast('/').removeSuffix(".conf").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                    val bos = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(4096)
                                    var len = zipInput.read(buffer)
                                    while (len != -1) {
                                        bos.write(buffer, 0, len)
                                        len = zipInput.read(buffer)
                                    }
                                    val content = bos.toString("UTF-8")
                                    if (wireguardManager.importProfile(entryName, content)) {
                                        importedAny = true
                                    }
                                }
                                zipInput.closeEntry()
                                entry = zipInput.nextEntry
                            }
                            if (importedAny) {
                                profiles = wireguardManager.getProfiles()
                            }
                        }
                    } else {
                        val cleanName = name.removeSuffix(".conf").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        val content = context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader().readText()
                        }
                        if (content != null) {
                            if (wireguardManager.importProfile(cleanName, content)) {
                                profiles = wireguardManager.getProfiles()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsVpnScreen", "Import failed: ${e.message}", e)
                }
            }
        }

        // Context Menu Dialog for Profile click
        selectedProfileForMenu?.let { profile ->
            AlertDialog(
                onDismissRequest = { selectedProfileForMenu = null },
                title = { Text(text = profile) },
                text = {
                    Column {
                        val isActive = activeTunnel == profile
                        val isDefault = defaultProfile == profile
                        TextButton(
                            onClick = {
                                scope.launch {
                                    if (isActive) {
                                        wireguardManager.stopTunnel()
                                    } else {
                                        val intent = android.net.VpnService.prepare(context)
                                        if (intent != null) {
                                            pendingProfileToConnect = profile
                                            vpnLauncher.launch(intent)
                                        } else {
                                            wireguardManager.startTunnel(profile)
                                        }
                                    }
                                    selectedProfileForMenu = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = if (isActive) "Disconnect" else "Connect")
                        }
                        TextButton(
                            onClick = {
                                val newDefault = if (isDefault) null else profile
                                wireguardManager.setDefaultProfile(newDefault)
                                defaultProfile = newDefault
                                selectedProfileForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = if (isDefault) "Remove Default" else "Set as Default Profile")
                        }
                        TextButton(
                            onClick = {
                                wireguardManager.deleteProfile(profile)
                                profiles = wireguardManager.getProfiles()
                                defaultProfile = wireguardManager.getDefaultProfile()
                                selectedProfileForMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Delete Profile")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedProfileForMenu = null }) {
                        Text(text = "Cancel")
                    }
                },
            )
        }

        // Dialog for Source VPN Profile mapping
        selectedGroupedSourceNameForProfileSelection?.let { cleanedName ->
            var currentSelection by remember {
                mutableStateOf(wireguardManager.getSourceProfileByName(cleanedName) ?: "default")
            }
            AlertDialog(
                onDismissRequest = { selectedGroupedSourceNameForProfileSelection = null },
                title = { Text(text = "Select VPN for $cleanedName") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSelection = "default" }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = currentSelection == "default",
                                onClick = { currentSelection = "default" },
                            )
                            Text(text = "Use Default VPN Profile", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSelection = "none" }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = currentSelection == "none",
                                onClick = { currentSelection = "none" },
                            )
                            Text(text = "None (No VPN)", modifier = Modifier.padding(start = 8.dp))
                        }
                        profiles.forEach { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentSelection = profile }
                                    .padding(vertical = 8.dp),
                            ) {
                                RadioButton(
                                    selected = currentSelection == profile,
                                    onClick = { currentSelection = profile },
                                )
                                Text(text = profile, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val value = when (currentSelection) {
                                "default" -> null
                                "none" -> "none"
                                else -> currentSelection
                            }
                            wireguardManager.associateSourceByName(cleanedName, value)
                            selectedGroupedSourceNameForProfileSelection = null
                        },
                    ) {
                        Text(text = "OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedGroupedSourceNameForProfileSelection = null }) {
                        Text(text = "Cancel")
                    }
                },
            )
        }

        val preferences = mutableListOf<Preference>()

        // Group the core VPN preferences into "VPN Settings" group
        preferences.add(
            Preference.PreferenceGroup(
                title = "VPN Settings",
                preferenceItems = listOf(
                    // Import WireGuard profile
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_vpn_import),
                        subtitle = stringResource(KMR.strings.pref_vpn_import_summary),
                        onClick = { importLauncher.launch("*/*") },
                    ),
                    // Auto-connect VPN on app start
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.vpnAutoConnectAtStart(),
                        title = "Auto-connect VPN on app start",
                        subtitle = "Automatically connect to the default VPN profile when opening the app",
                    ),
                    // Auto-disconnect VPN on app close
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.vpnDisconnectOnClose(),
                        title = "Auto-disconnect VPN on app close",
                        subtitle = "Automatically disconnect the active VPN when the app is closed",
                    ),
                    // Default Profile Info/Selection
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_vpn_default_profile),
                        subtitle = defaultProfile ?: "None",
                    ),
                ).toImmutableList(),
            ),
        )

        // List of Profiles
        if (profiles.isNotEmpty()) {
            preferences.add(
                Preference.PreferenceGroup(
                    title = stringResource(KMR.strings.pref_vpn_profiles),
                    preferenceItems = profiles.map { profile ->
                        val isDefault = defaultProfile == profile
                        val isActive = activeTunnel == profile
                        Preference.PreferenceItem.TextPreference(
                            title = profile +
                                (if (isActive) " (Active)" else "") +
                                (if (isDefault) " (Default)" else ""),
                            subtitle = if (isActive) "Active • Tap for options" else "Tap for options",
                            onClick = {
                                selectedProfileForMenu = profile
                            },
                        )
                    }.toImmutableList(),
                ),
            )
        }

        // List of Sources
        val groupedSources = remember {
            sourceManager.getOnlineSources()
                .groupBy { wireguardManager.cleanSourceName(it.name) }
                .toSortedMap()
        }

        if (groupedSources.isNotEmpty()) {
            preferences.add(
                Preference.PreferenceGroup(
                    title = stringResource(KMR.strings.pref_vpn_sources),
                    preferenceItems = groupedSources.map { (cleanedName, _) ->
                        val associated = wireguardManager.getSourceProfileByName(cleanedName)
                        val displayValue = when (associated) {
                            null -> "Use Default"
                            "none" -> "None"
                            else -> associated
                        }
                        Preference.PreferenceItem.TextPreference(
                            title = cleanedName,
                            subtitle = displayValue,
                            onClick = {
                                selectedGroupedSourceNameForProfileSelection = cleanedName
                            },
                        )
                    }.toImmutableList(),
                ),
            )
        }

        return preferences
    }
}
