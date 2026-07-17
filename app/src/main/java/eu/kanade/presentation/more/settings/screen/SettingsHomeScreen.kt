package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsHomeScreen : SearchableSettings {

    @Suppress("unused")
    private fun readResolve(): Any = SettingsHomeScreen

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes(): StringResource = KMR.strings.pref_home_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val context = androidx.compose.ui.platform.LocalContext.current

        val showSuggestions = uiPreferences.showHomeSuggestions().collectAsState().value
        val showHistory = uiPreferences.showHomeHistory().collectAsState().value
        val showUpdates = uiPreferences.showHomeUpdates().collectAsState().value
        val showLibrary = uiPreferences.showHomeLibrary().collectAsState().value
        val showFeed = uiPreferences.showHomeFeed().collectAsState().value

        val feedSourceCount = uiPreferences.homeFeedSourceCount().collectAsState().value
        val feedItemsCount = uiPreferences.homeFeedItemsCount().collectAsState().value
        val homeSuggestionsAutoplay = uiPreferences.homeSuggestionsAutoplay().collectAsState().value
        val homeSuggestionsAutoplayInterval = uiPreferences.homeSuggestionsAutoplayInterval().collectAsState().value
        val homeFeedUsePinnedSources = uiPreferences.homeFeedUsePinnedSources().collectAsState().value

        val onlineSources = remember { sourceManager.getOnlineSources() }
        val sourceEntries = remember(onlineSources) {
            onlineSources.associate { it.id.toString() to it.name }.toImmutableMap()
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_home_sections),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeSuggestions(),
                        title = stringResource(KMR.strings.pref_home_show_suggestions),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeHistory(),
                        title = stringResource(KMR.strings.pref_home_show_history),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeUpdates(),
                        title = stringResource(KMR.strings.pref_home_show_updates),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeLibrary(),
                        title = stringResource(KMR.strings.pref_home_show_library),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeFeed(),
                        title = stringResource(KMR.strings.pref_home_show_feed),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Spotlight Settings",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.homeSuggestionsAutoplay(),
                        title = "Autoplay Spotlight Carousel",
                        subtitle = "Automatically rotate recommended spotlight manga",
                        enabled = showSuggestions,
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = homeSuggestionsAutoplayInterval,
                        valueRange = 2..15,
                        title = "Autoplay Interval",
                        subtitle = "Delay in seconds between recommendation auto-switches",
                        valueString = "$homeSuggestionsAutoplayInterval seconds",
                        enabled = showSuggestions && homeSuggestionsAutoplay,
                        onValueChanged = {
                            uiPreferences.homeSuggestionsAutoplayInterval().set(it)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_home_feed_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = feedSourceCount,
                        valueRange = 1..20,
                        title = stringResource(KMR.strings.pref_home_feed_source_count),
                        subtitle = stringResource(KMR.strings.pref_home_feed_source_count_summary, feedSourceCount),
                        valueString = feedSourceCount.toString(),
                        enabled = showFeed && !homeFeedUsePinnedSources,
                        onValueChanged = {
                            uiPreferences.homeFeedSourceCount().set(it)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = feedItemsCount,
                        valueRange = 1..30,
                        title = stringResource(KMR.strings.pref_home_feed_items_count),
                        subtitle = stringResource(KMR.strings.pref_home_feed_items_count_summary, feedItemsCount),
                        valueString = feedItemsCount.toString(),
                        enabled = showFeed,
                        onValueChanged = {
                            uiPreferences.homeFeedItemsCount().set(it)
                            true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.homeFeedUsePinnedSources(),
                        title = "Use Pinned Sources for Feed",
                        subtitle = "Only fetch feed updates from pinned sources below instead of recommendations",
                        enabled = showFeed,
                    ),
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = uiPreferences.homeFeedPinnedSources(),
                        entries = sourceEntries,
                        title = "Pinned Feed Sources",
                        subtitle = "Select which sources to show in the Explore Feed",
                        enabled = showFeed && homeFeedUsePinnedSources,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.homeFeedBackgroundPrefetch(),
                        title = "Background Feed Pre-fetching",
                        subtitle = "Fetch new items periodically in the background when charging on Wi-Fi",
                        enabled = showFeed,
                        onValueChanged = { isEnabled ->
                            eu.kanade.tachiyomi.data.suggestions.HomeFeedWorker.scheduleBackground(context, isEnabled)
                            true
                        },
                    ),
                ),
            ),
        )
    }
}
