package eu.kanade.domain.ui

import androidx.compose.material3.FabPosition
import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.MONET,
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    // KMK -->
    fun colorTheme() = preferenceStore.getInt("pref_color_theme", 0xFFDF0090.toInt())

    fun customThemeStyle() = preferenceStore.getEnum("pref_custom_theme_style_key", PaletteStyle.Fidelity)

    fun themeCoverBased() = preferenceStore.getBoolean("pref_theme_cover_based_key", true)

    fun themeCoverBasedStyle() = preferenceStore.getEnum("pref_theme_cover_based_style_key", PaletteStyle.Vibrant)

    fun preloadLibraryColor() = preferenceStore.getBoolean("pref_preload_library_color_key", true)
    // KMK <--

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun imagesInDescription() = preferenceStore.getBoolean("pref_render_images_description", true)

    // SY -->

    fun expandFilters() = preferenceStore.getBoolean("eh_expand_filters", false)

    fun hideFeedTab() = preferenceStore.getBoolean("hide_latest_tab", false)

    fun feedTabInFront() = preferenceStore.getBoolean("latest_tab_position", false)

    // KMK -->
    fun expandRelatedMangas() = preferenceStore.getBoolean("expand_related_mangas", true)

    fun relatedMangasInOverflow() = preferenceStore.getBoolean("related_mangas_in_overflow", false)

    fun showHomeOnRelatedMangas() = preferenceStore.getBoolean("show_home_on_related_mangas", true)

    fun readButtonPosition() = preferenceStore.getString("reading_button_position", FabPosition.End.toString())

    fun usePanoramaCoverFlow() = preferenceStore.getBoolean("use_panorama_cover_flow", false)

    fun usePanoramaCoverAlways() = preferenceStore.getBoolean("use_panorama_cover_grid", true)

    fun usePanoramaCoverMangaInfo() = preferenceStore.getBoolean("use_panorama_cover_manga_info", false)

    fun topAlignCover() = preferenceStore.getBoolean("top_align_cover", false)

    fun libraryParentChildLayout() = preferenceStore.getBoolean("pref_library_parent_child_layout", false)
    // KMK <--

    fun recommendsInOverflow() = preferenceStore.getBoolean("recommends_in_overflow", false)

    fun mergeInOverflow() = preferenceStore.getBoolean("merge_in_overflow", true)

    fun previewsRowCount() = preferenceStore.getInt("pref_previews_row_count", 4)

    fun useNewSourceNavigation() = preferenceStore.getBoolean("use_new_source_navigation", true)

    fun bottomBarLabels() = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    // KMK -->
    fun floatingBottomBar() = preferenceStore.getBoolean("pref_floating_bottom_bar", true)
    fun bottomBarOpacity() = preferenceStore.getInt("pref_bottom_bar_opacity", 80)
    fun bottomBarBlur() = preferenceStore.getInt("pref_bottom_bar_blur", 8)
    fun readerAppBarOpacity() = preferenceStore.getInt("reader_app_bar_opacity", 80)
    fun readerAppBarColorMix() = preferenceStore.getInt("reader_app_bar_color_mix", 0)

    // Kisara Settings
    fun kisaraFrostedGlass() = preferenceStore.getBoolean("kisara_frosted_glass", true)
    fun showCategoryTabs() = preferenceStore.getBoolean("kisara_show_category_tabs", false)
    fun hideTopBarOnScroll() = preferenceStore.getBoolean("kisara_hide_top_bar_on_scroll", true)
    fun duplicateMaxScanCount() = preferenceStore.getInt("kisara_duplicate_max_scan_count", 0)
    fun chapterSheetMinHeightDp() = preferenceStore.getInt("kisara_chapter_sheet_min_height", 144)
    fun chapterSheetMaxHeightPct() = preferenceStore.getInt("kisara_chapter_sheet_max_height", 60)
    fun categoryBarCarouselStyle() = preferenceStore.getBoolean("kisara_category_bar_carousel_style", false)
    fun duplicateHistory() = preferenceStore.getStringSet("kisara_duplicate_history", emptySet())
    fun alwaysShowSubTabs() = preferenceStore.getBoolean("kisara_always_show_sub_tabs", true)
    fun alwaysShowSubTabsHome() = preferenceStore.getBoolean("kisara_always_show_sub_tabs_home", true)
    fun alwaysShowSubTabsLibrary() = preferenceStore.getBoolean("kisara_always_show_sub_tabs_library", true)
    fun alwaysShowSubTabsBrowse() = preferenceStore.getBoolean("kisara_always_show_sub_tabs_browse", true)
    fun subTabsBottomMargin() = preferenceStore.getInt("kisara_sub_tabs_bottom_margin", 0)
    fun bottomBarBottomMargin() = preferenceStore.getInt("kisara_bottom_bar_bottom_margin", 12)
    fun showTopTabBar() = preferenceStore.getBoolean("kisara_show_top_tab_bar", false)
    fun kisaraGlassColorType() = preferenceStore.getInt("kisara_glass_color_type", 0)
    fun kisaraGlassColorMix() = preferenceStore.getInt("kisara_glass_color_mix", 0)
    fun kisaraGlassCustomColor() = preferenceStore.getInt("kisara_glass_custom_color", 0xFFFFFFFF.toInt())
    fun kisaraShowItemCountInTabs() = preferenceStore.getBoolean("kisara_show_item_count_in_tabs", false)
    fun categoryBarSelectedFontColorType() = preferenceStore.getInt("kisara_category_bar_selected_font_color_type", 0)
    fun categoryBarSelectedFontCustomColor() = preferenceStore.getInt("kisara_category_bar_selected_font_custom_color", 0xFFFFFFFF.toInt())
    fun bottomBarHeight() = preferenceStore.getInt("kisara_bottom_bar_height", 64)
    fun subBarHeight() = preferenceStore.getInt("kisara_sub_bar_height", 32)
    fun kisaraShowSubcategoriesInMainBar() = preferenceStore.getBoolean("kisara_show_subcategories_in_main_bar", false)
    fun bottomBarWidth() = preferenceStore.getInt("kisara_bottom_bar_width", 100)
    // KMK <--

    fun showNavUpdates() = preferenceStore.getBoolean("pref_show_updates_button", true)

    fun showNavHistory() = preferenceStore.getBoolean("pref_show_history_button", true)

    // SY <--

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
