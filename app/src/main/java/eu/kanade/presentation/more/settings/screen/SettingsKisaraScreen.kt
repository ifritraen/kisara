package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsKisaraScreen : SearchableSettings {

    @Suppress("unused")
    private fun readResolve(): Any = SettingsKisaraScreen

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes(): StringResource = KMR.strings.label_kisara_settings

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        val floatingBottomBarPref = uiPreferences.floatingBottomBar()
        val floatingBottomBar by floatingBottomBarPref.collectAsState()

        val bottomBarOpacityPref = uiPreferences.bottomBarOpacity()
        val bottomBarOpacity by bottomBarOpacityPref.collectAsState()

        val bottomBarBlurPref = uiPreferences.bottomBarBlur()
        val bottomBarBlur by bottomBarBlurPref.collectAsState()

        val showCategoryTabsPref = uiPreferences.showCategoryTabs()
        val showCategoryTabs by showCategoryTabsPref.collectAsState()

        val showTopTabBarPref = uiPreferences.showTopTabBar()
        val showTopTabBar by showTopTabBarPref.collectAsState()

        val subTabsBottomMarginPref = uiPreferences.subTabsBottomMargin()
        val subTabsBottomMargin by subTabsBottomMarginPref.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.label_kisara_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = floatingBottomBarPref,
                        title = stringResource(KMR.strings.pref_floating_bottom_bar),
                        subtitle = stringResource(KMR.strings.pref_floating_bottom_bar_summary),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = bottomBarOpacity,
                        valueRange = 0..100,
                        title = stringResource(KMR.strings.pref_bottom_bar_opacity),
                        valueString = "$bottomBarOpacity%",
                        enabled = floatingBottomBar,
                        onValueChanged = { bottomBarOpacityPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = bottomBarBlur,
                        valueRange = 0..24,
                        title = stringResource(KMR.strings.pref_bottom_bar_blur),
                        valueString = if (bottomBarBlur > 0) "$bottomBarBlur dp" else stringResource(MR.strings.disabled),
                        enabled = floatingBottomBar,
                        onValueChanged = { bottomBarBlurPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.kisaraFrostedGlass(),
                        title = stringResource(KMR.strings.pref_kisara_frosted_glass),
                        subtitle = stringResource(KMR.strings.pref_kisara_frosted_glass_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabs(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsHome(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_home),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_home_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsLibrary(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_library),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_library_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsBrowse(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_browse),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_browse_summary),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = subTabsBottomMargin,
                        valueRange = 0..80,
                        title = stringResource(KMR.strings.pref_sub_tabs_bottom_margin),
                        subtitle = stringResource(KMR.strings.pref_sub_tabs_bottom_margin_summary),
                        valueString = if (subTabsBottomMargin > 0) "$subTabsBottomMargin dp" else stringResource(MR.strings.disabled),
                        onValueChanged = { subTabsBottomMarginPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showTopTabBarPref,
                        title = stringResource(KMR.strings.pref_kisara_show_top_tab_bar),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_top_tab_bar_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showCategoryTabsPref,
                        title = stringResource(KMR.strings.pref_kisara_show_category_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_category_tabs_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.hideTopBarOnScroll(),
                        title = stringResource(KMR.strings.pref_kisara_hide_top_bar),
                        subtitle = stringResource(KMR.strings.pref_kisara_hide_top_bar_summary),
                        enabled = showCategoryTabs || showTopTabBar,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.categoryBarCarouselStyle(),
                        title = stringResource(KMR.strings.pref_kisara_carousel_category_style),
                        subtitle = stringResource(KMR.strings.pref_kisara_carousel_category_style_summary),
                        enabled = showCategoryTabs,
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.chapterSheetMinHeightDp().get(),
                        valueRange = 40..120,
                        title = stringResource(KMR.strings.pref_kisara_chapter_sheet_min_height),
                        subtitle = stringResource(KMR.strings.pref_kisara_chapter_sheet_min_height_summary),
                        valueString = "${uiPreferences.chapterSheetMinHeightDp().get()} dp",
                        onValueChanged = {
                            uiPreferences.chapterSheetMinHeightDp().set(it)
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.chapterSheetMaxHeightPct().get(),
                        valueRange = 30..90,
                        title = stringResource(KMR.strings.pref_kisara_chapter_sheet_max_height),
                        subtitle = stringResource(KMR.strings.pref_kisara_chapter_sheet_max_height_summary),
                        valueString = "${uiPreferences.chapterSheetMaxHeightPct().get()}%",
                        onValueChanged = {
                            uiPreferences.chapterSheetMaxHeightPct().set(it)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.label_duplicate),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.duplicateMaxScanCount().get(),
                        valueRange = 0..200,
                        title = stringResource(KMR.strings.pref_kisara_duplicate_max_scan),
                        subtitle = stringResource(KMR.strings.pref_kisara_duplicate_max_scan_summary),
                        valueString = if (uiPreferences.duplicateMaxScanCount().get() > 0) "${uiPreferences.duplicateMaxScanCount().get()} groups" else "Unlimited",
                        onValueChanged = {
                            uiPreferences.duplicateMaxScanCount().set(it)
                        },
                    ),
                ),
            ),
        )
    }
}
