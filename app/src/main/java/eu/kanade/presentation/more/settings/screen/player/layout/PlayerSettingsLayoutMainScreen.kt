package eu.kanade.presentation.more.settings.screen.player.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.layout.PlayerLayoutOrientation
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

object PlayerSettingsLayoutMainScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_player_layout

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_player_layout),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_player_layout_portrait),
                        subtitle = stringResource(KMR.strings.pref_player_layout_portrait_summary),
                        onClick = {
                            navigator.push(PlayerSettingsLayoutScreen(PlayerLayoutOrientation.Portrait))
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_player_layout_landscape),
                        subtitle = stringResource(KMR.strings.pref_player_layout_landscape_summary),
                        onClick = {
                            navigator.push(PlayerSettingsLayoutScreen(PlayerLayoutOrientation.Landscape))
                        },
                    ),
                ),
            ),
        )
    }
}
