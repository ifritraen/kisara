package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
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
        val navigator = LocalNavigator.currentOrThrow
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

        val bottomBarBottomMarginPref = uiPreferences.bottomBarBottomMargin()
        val bottomBarBottomMargin by bottomBarBottomMarginPref.collectAsState()

        val kisaraGlassColorTypePref = uiPreferences.kisaraGlassColorType()
        val kisaraGlassColorType by kisaraGlassColorTypePref.collectAsState()

        val kisaraGlassColorMixPref = uiPreferences.kisaraGlassColorMix()
        val kisaraGlassColorMix by kisaraGlassColorMixPref.collectAsState()

        val kisaraGlassCustomColorPref = uiPreferences.kisaraGlassCustomColor()
        val kisaraGlassCustomColor by kisaraGlassCustomColorPref.collectAsState()

        val readerAppBarOpacityPref = uiPreferences.readerAppBarOpacity()
        val readerAppBarOpacity by readerAppBarOpacityPref.collectAsState()

        val readerAppBarColorMixPref = uiPreferences.readerAppBarColorMix()
        val readerAppBarColorMix by readerAppBarColorMixPref.collectAsState()

        var showColorPicker by remember { mutableStateOf(false) }

        val kisaraShowItemCountInTabsPref = uiPreferences.kisaraShowItemCountInTabs()
        val kisaraShowItemCountInTabs by kisaraShowItemCountInTabsPref.collectAsState()

        val categoryBarSelectedFontColorTypePref = uiPreferences.categoryBarSelectedFontColorType()
        val categoryBarSelectedFontColorType by categoryBarSelectedFontColorTypePref.collectAsState()

        val categoryBarSelectedFontCustomColorPref = uiPreferences.categoryBarSelectedFontCustomColor()
        val categoryBarSelectedFontCustomColor by categoryBarSelectedFontCustomColorPref.collectAsState()

        val bottomBarHeightPref = uiPreferences.bottomBarHeight()
        val bottomBarHeight by bottomBarHeightPref.collectAsState()

        val subBarHeightPref = uiPreferences.subBarHeight()
        val subBarHeight by subBarHeightPref.collectAsState()

        val kisaraShowSubcategoriesInMainBarPref = uiPreferences.kisaraShowSubcategoriesInMainBar()
        val bottomBarWidthPref = uiPreferences.bottomBarWidth()
        val bottomBarWidth by bottomBarWidthPref.collectAsState()

        var showFontColorPicker by remember { mutableStateOf(false) }

        if (showColorPicker) {
            val controller = rememberColorPickerController()
            AlertDialog(
                onDismissRequest = { showColorPicker = false },
                title = { Text(text = "Choose Custom Glass Color") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HsvColorPicker(
                            modifier = Modifier.size(240.dp),
                            controller = controller,
                            initialColor = Color(kisaraGlassCustomColor),
                            onColorChanged = { },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BrightnessSlider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            controller = controller,
                            initialColor = Color(kisaraGlassCustomColor),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedColor = controller.selectedColor.value
                            kisaraGlassCustomColorPref.set(selectedColor.toArgb())
                            showColorPicker = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showColorPicker = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showFontColorPicker) {
            val controller = rememberColorPickerController()
            AlertDialog(
                onDismissRequest = { showFontColorPicker = false },
                title = { Text(text = stringResource(KMR.strings.pref_category_bar_selected_font_custom_color)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HsvColorPicker(
                            modifier = Modifier.size(240.dp),
                            controller = controller,
                            initialColor = Color(categoryBarSelectedFontCustomColor),
                            onColorChanged = { },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BrightnessSlider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            controller = controller,
                            initialColor = Color(categoryBarSelectedFontCustomColor),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedColor = controller.selectedColor.value
                            categoryBarSelectedFontCustomColorPref.set(selectedColor.toArgb())
                            showFontColorPicker = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFontColorPicker = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

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
                    Preference.PreferenceItem.ListPreference(
                        preference = kisaraGlassColorTypePref,
                        entries = mapOf(
                            0 to stringResource(KMR.strings.glass_color_type_default),
                            1 to stringResource(KMR.strings.glass_color_type_accent),
                            2 to stringResource(KMR.strings.glass_color_type_surface),
                            3 to stringResource(KMR.strings.glass_color_type_black),
                            4 to stringResource(KMR.strings.glass_color_type_white),
                            5 to "Custom Color",
                        ).toImmutableMap(),
                        title = stringResource(KMR.strings.pref_glass_color_type),
                        subtitle = "%s",
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Custom Glass Color",
                        subtitle = if (kisaraGlassColorType == 5) "#%08X".format(kisaraGlassCustomColor) else "Tap to choose a custom color",
                        enabled = kisaraGlassColorType == 5,
                        onClick = { showColorPicker = true },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = kisaraGlassColorMix,
                        valueRange = 0..100,
                        title = stringResource(KMR.strings.pref_glass_color_mix),
                        subtitle = stringResource(KMR.strings.pref_glass_color_mix_summary),
                        valueString = "$kisaraGlassColorMix%",
                        enabled = kisaraGlassColorType != 0,
                        onValueChanged = { kisaraGlassColorMixPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = readerAppBarOpacity,
                        valueRange = 0..100,
                        title = "Reader App Bar Opacity",
                        subtitle = "Opacity of the reader top and bottom bars",
                        valueString = "$readerAppBarOpacity%",
                        onValueChanged = { readerAppBarOpacityPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = readerAppBarColorMix,
                        valueRange = 0..100,
                        title = "Reader App Bar Color Mix",
                        subtitle = "Color mix ratio of custom color on the reader app bars",
                        valueString = "$readerAppBarColorMix%",
                        enabled = kisaraGlassColorType != 0,
                        onValueChanged = { readerAppBarColorMixPref.set(it) },
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
                        valueRange = -20..80,
                        title = stringResource(KMR.strings.pref_sub_tabs_bottom_margin),
                        subtitle = stringResource(KMR.strings.pref_sub_tabs_bottom_margin_summary),
                        valueString = if (subTabsBottomMargin != 0) "$subTabsBottomMargin dp" else stringResource(MR.strings.disabled),
                        onValueChanged = { subTabsBottomMarginPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = bottomBarBottomMargin,
                        valueRange = -40..80,
                        title = stringResource(KMR.strings.pref_bottom_bar_bottom_margin),
                        subtitle = stringResource(KMR.strings.pref_bottom_bar_bottom_margin_summary),
                        valueString = "$bottomBarBottomMargin dp",
                        onValueChanged = { bottomBarBottomMarginPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = kisaraShowItemCountInTabsPref,
                        title = stringResource(KMR.strings.pref_kisara_show_item_count_in_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_item_count_in_tabs_summary),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = bottomBarHeight,
                        valueRange = 10..100,
                        title = "Bottom Bar Height",
                        valueString = "$bottomBarHeight dp",
                        onValueChanged = { bottomBarHeightPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = subBarHeight,
                        valueRange = 10..60,
                        title = "Sub-Bar Height",
                        valueString = "$subBarHeight dp",
                        onValueChanged = { subBarHeightPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = kisaraShowSubcategoriesInMainBarPref,
                        title = "Show subcategories in category bar",
                        subtitle = "Include subcategories inside the main category bar alongside parent categories",
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = bottomBarWidth,
                        valueRange = 30..100,
                        title = "Bottom Bar Width",
                        subtitle = "Width fraction of the bottom navigation bar (capsule style)",
                        valueString = "$bottomBarWidth%",
                        onValueChanged = { bottomBarWidthPref.set(it) },
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
                    Preference.PreferenceItem.ListPreference(
                        preference = categoryBarSelectedFontColorTypePref,
                        entries = mapOf(
                            0 to stringResource(KMR.strings.category_bar_selected_font_color_default),
                            1 to stringResource(KMR.strings.category_bar_selected_font_color_accent),
                            2 to stringResource(KMR.strings.category_bar_selected_font_color_custom),
                        ).toImmutableMap(),
                        title = stringResource(KMR.strings.pref_category_bar_selected_font_color),
                        subtitle = "%s",
                        enabled = showCategoryTabs,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_category_bar_selected_font_custom_color),
                        subtitle = if (categoryBarSelectedFontColorType == 2) "#%08X".format(categoryBarSelectedFontCustomColor) else "Tap to choose a custom color",
                        enabled = showCategoryTabs && categoryBarSelectedFontColorType == 2,
                        onClick = { showFontColorPicker = true },
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
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_category_translations),
                        subtitle = stringResource(KMR.strings.pref_translation_summary),
                        onClick = { navigator.push(SettingsTranslationScreen) },
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
