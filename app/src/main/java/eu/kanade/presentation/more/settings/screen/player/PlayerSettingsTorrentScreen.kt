package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import aniyomi.core.common.torrent.ProxyMode
import aniyomi.core.common.torrent.TorrentPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsTorrentScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_player_torrents

    @Composable
    override fun getPreferences(): List<Preference> {
        val torrentPreferences = remember { Injekt.get<TorrentPreferences>() }
        var showNotice by remember { mutableStateOf(false) }

        if (showNotice) {
            AlertDialog(
                onDismissRequest = { showNotice = false },
                title = { Text(stringResource(KMR.strings.pref_player_torrents_notice)) },
                text = {
                    Text(
                        stringResource(KMR.strings.pref_player_torrents_notice_text) +
                            "\n\n" +
                            stringResource(KMR.strings.pref_player_torrents_notice_footer),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            torrentPreferences.torrServerShownNotice().set(true)
                            torrentPreferences.torrServerEnable().set(true)
                            showNotice = false
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotice = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = torrentPreferences.torrServerEnable(),
                title = stringResource(KMR.strings.pref_player_torrents_enable),
                subtitle = stringResource(KMR.strings.pref_player_torrents_summary),
                onValueChanged = { enabled ->
                    if (!enabled) {
                        TorrentServerService.stop()
                        true
                    } else if (!torrentPreferences.torrServerShownNotice().get()) {
                        showNotice = true
                        false
                    } else {
                        true
                    }
                },
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = torrentPreferences.torrServerPort(),
                title = stringResource(KMR.strings.pref_player_torrents_port),
                subtitle = stringResource(KMR.strings.pref_player_torrents_port_summary),
                dialogSubtitle = stringResource(KMR.strings.pref_player_torrents_port_summary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                validate = ::isValidPort,
                errorMessage = { stringResource(KMR.strings.pref_player_torrents_port_error) },
            ),
            Preference.PreferenceItem.MultiLineEditTextPreference(
                preference = torrentPreferences.torrServerTrackers(),
                title = stringResource(KMR.strings.pref_player_torrents_trackers),
                canBeBlank = true,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.pref_player_torrents_trackers_reset),
                onClick = { torrentPreferences.torrServerTrackers().set(TorrentPreferences.DEFAULT_TRACKERS) },
            ),
            Preference.PreferenceItem.ListPreference(
                preference = torrentPreferences.torrServerProxyMode(),
                entries = ProxyMode.entries.associateWith { mode ->
                    when (mode) {
                        ProxyMode.None -> stringResource(KMR.strings.pref_player_torrents_proxy_mode_none)
                        ProxyMode.Tracker -> stringResource(KMR.strings.pref_player_torrents_proxy_mode_tracker)
                        ProxyMode.Peers -> stringResource(KMR.strings.pref_player_torrents_proxy_mode_peers)
                        ProxyMode.Full -> stringResource(KMR.strings.pref_player_torrents_proxy_mode_full)
                    }
                }.toImmutableMap(),
                title = stringResource(KMR.strings.pref_player_torrents_proxy_mode),
            ),
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = torrentPreferences.torrServerProxyUrl(),
                title = stringResource(KMR.strings.pref_player_torrents_proxy_url),
                dialogSubtitle = stringResource(KMR.strings.pref_player_torrents_proxy_url_dialog),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                validate = ::isValidProxyUrl,
                errorMessage = { value ->
                    if (value.isBlank()) {
                        ""
                    } else {
                        stringResource(KMR.strings.pref_player_torrents_proxy_url_invalid_uri)
                    }
                },
            ),
        )
    }

    internal fun isValidPort(value: String): Boolean {
        return value.toIntOrNull()?.let { it in 0..65535 } == true
    }

    internal fun isValidProxyUrl(value: String): Boolean {
        if (value.isBlank()) return true
        val uri = runCatching { value.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in setOf("http", "https", "socks4", "socks4a", "socks5", "socks5h") && !uri.host.isNullOrBlank()
    }
}
