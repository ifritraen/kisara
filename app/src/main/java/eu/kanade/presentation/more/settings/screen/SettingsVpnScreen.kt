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

        var profiles by remember { mutableStateOf(wireguardManager.getProfiles()) }
        val activeTunnel by wireguardManager.activeTunnel.collectAsState()
        var defaultProfile by remember { mutableStateOf(wireguardManager.getDefaultProfile()) }

        var selectedProfileForMenu by remember { mutableStateOf<String?>(null) }
        var selectedSourceForProfileSelection by remember { mutableStateOf<HttpSource?>(null) }

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
                    val cleanName = name.removeSuffix(".conf").replace(Regex("[^a-zA-Z0-9_-]"), "_")

                    val content = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                    if (content != null) {
                        if (wireguardManager.importProfile(cleanName, content)) {
                            profiles = wireguardManager.getProfiles()
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
                                        wireguardManager.startTunnel(profile)
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
        selectedSourceForProfileSelection?.let { source ->
            var currentSelection by remember {
                mutableStateOf(wireguardManager.getSourceProfile(source.id) ?: "default")
            }
            AlertDialog(
                onDismissRequest = { selectedSourceForProfileSelection = null },
                title = { Text(text = "Select VPN for ${source.name}") },
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
                            wireguardManager.associateSource(source.id, value)
                            selectedSourceForProfileSelection = null
                        },
                    ) {
                        Text(text = "OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedSourceForProfileSelection = null }) {
                        Text(text = "Cancel")
                    }
                },
            )
        }

        val preferences = mutableListOf<Preference>()

        // Import WireGuard profile
        preferences.add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.pref_vpn_import),
                subtitle = stringResource(KMR.strings.pref_vpn_import_summary),
                onClick = { importLauncher.launch("*/*") },
            ),
        )

        // Default Profile Info/Selection
        preferences.add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.pref_vpn_default_profile),
                subtitle = defaultProfile ?: "None",
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
        if (sources.isNotEmpty()) {
            preferences.add(
                Preference.PreferenceGroup(
                    title = stringResource(KMR.strings.pref_vpn_sources),
                    preferenceItems = sources.map { source ->
                        val associated = wireguardManager.getSourceProfile(source.id)
                        val displayValue = when (associated) {
                            null -> "Use Default"
                            "none" -> "None"
                            else -> associated
                        }
                        Preference.PreferenceItem.TextPreference(
                            title = source.name,
                            subtitle = displayValue,
                            onClick = {
                                selectedSourceForProfileSelection = source
                            },
                        )
                    }.toImmutableList(),
                ),
            )
        }

        return preferences
    }
}
