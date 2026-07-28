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

    fun colorSecondaryTheme() = preferenceStore.getInt("pref_color_secondary_theme", 0xFF00B0FF.toInt())

    fun themeGradientEnabled() = preferenceStore.getBoolean("pref_theme_gradient_enabled", true)

    fun themeGlassOpacity() = preferenceStore.getFloat("pref_theme_glass_opacity", 0.85f)

    fun darkModeDepth() = preferenceStore.getInt("pref_dark_mode_depth", 0) // 0: OLED Black, 1: Midnight Navy, 2: Deep Charcoal, 3: Soft Tonal

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
    fun kisaraCoverTitleStyle() = preferenceStore.getString("kisara_cover_title_style", "default")
    fun normalCardStyle() = preferenceStore.getString("kisara_normal_card_style", "default")
    fun spotlightCardStyle() = preferenceStore.getString("kisara_spotlight_card_style", "default")
    fun continueReadingCardStyle() = preferenceStore.getString("kisara_continue_reading_card_style", "default")
    fun recentUpdatesCardStyle() = preferenceStore.getString("kisara_recent_updates_card_style", "default")
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
    fun bottomBarHeight() = preferenceStore.getInt("kisara_bottom_bar_height", 48)
    fun subBarHeight() = preferenceStore.getInt("kisara_sub_bar_height", 32)
    fun kisaraShowSubcategoriesInMainBar() = preferenceStore.getBoolean("kisara_show_subcategories_in_main_bar", false)
    fun bottomBarWidth() = preferenceStore.getInt("kisara_bottom_bar_width_dp", 360)
    fun bottomBarGap() = preferenceStore.getInt("kisara_bottom_bar_gap_dp", 12)
    fun bottomBarIconSize() = preferenceStore.getInt("kisara_bottom_bar_icon_size", 24)
    fun bottomBarKeepRatio() = preferenceStore.getBoolean("kisara_bottom_bar_keep_ratio", true)
    fun bottomBarHorizontalPadding() = preferenceStore.getInt("kisara_bottom_bar_horizontal_padding", 8)
    fun bottomBarAutoWidth() = preferenceStore.getBoolean("kisara_bottom_bar_auto_width", true)
    fun bottomBarVerticalPadding() = preferenceStore.getInt("kisara_bottom_bar_vertical_padding", 2)
    fun bottomBarCornerRadius() = preferenceStore.getInt("kisara_bottom_bar_corner_radius", 24)
    fun bottomBarButtonSize() = preferenceStore.getInt("kisara_bottom_bar_button_size_dp", 32)

    // Standard Bottom Bar Customizations
    fun standardBottomBarHeight() = preferenceStore.getInt("kisara_standard_bottom_bar_height", 80)
    fun standardBottomBarWidth() = preferenceStore.getInt("kisara_standard_standard_bottom_bar_width", 100)
    fun standardBottomBarOpacity() = preferenceStore.getInt("kisara_standard_bottom_bar_opacity", 100)
    fun standardBottomBarBlur() = preferenceStore.getInt("kisara_standard_bottom_bar_blur", 0)
    fun standardBottomBarBottomMargin() = preferenceStore.getInt("kisara_standard_bottom_bar_bottom_margin", 0)
    fun standardBottomBarCornerRadius() = preferenceStore.getInt("kisara_standard_bottom_bar_corner_radius", 0)

    fun vpnAutoConnectAtStart() = preferenceStore.getBoolean("kisara_vpn_auto_connect_at_start", false)
    fun vpnDisconnectOnClose() = preferenceStore.getBoolean("kisara_vpn_disconnect_on_close", true)
    fun jarExtensionRepos() = preferenceStore.getStringSet("kisara_jar_extension_repos", emptySet())
    fun jarExtensionRepoMap() = preferenceStore.getStringSet("kisara_jar_extension_repo_map", emptySet())

    // Home Landing Page Settings
    fun showHomeSuggestions() = preferenceStore.getBoolean("kisara_home_show_suggestions", true)
    fun showHomeHistory() = preferenceStore.getBoolean("kisara_home_show_history", true)
    fun showHomeUpdates() = preferenceStore.getBoolean("kisara_home_show_updates", true)
    fun showHomeLibrary() = preferenceStore.getBoolean("kisara_home_show_library", true)
    fun showHomeFeed() = preferenceStore.getBoolean("kisara_home_show_feed", true)
    fun homeFeedSourceCount() = preferenceStore.getInt("kisara_home_feed_source_count", 10)
    fun homeFeedItemsCount() = preferenceStore.getInt("kisara_home_feed_items_count", 10)
    fun homeSuggestionsAutoplay() = preferenceStore.getBoolean("kisara_home_suggestions_autoplay", true)
    fun homeSuggestionsAutoplayInterval() = preferenceStore.getInt("kisara_home_suggestions_autoplay_interval", 5)
    fun homeFeedUsePinnedSources() = preferenceStore.getBoolean("kisara_home_feed_use_pinned_sources", false)
    fun homeFeedPinnedSources() = preferenceStore.getStringSet("kisara_home_feed_pinned_sources", emptySet())
    fun homeFeedBackgroundPrefetch() = preferenceStore.getBoolean("kisara_home_feed_background_prefetch", false)

    enum class ReaderLoadingStyle {
        SUNSET,
        OCEAN,
        CYBERPUNK,
        AURORA,
        CLASSIC_DARK,
        AMOLED_BLACK,
    }

    fun readerLoadingStyle() = preferenceStore.getEnum("pref_reader_loading_style", ReaderLoadingStyle.CLASSIC_DARK)

    fun kisaraCustomNsfwTags() = preferenceStore.getStringSet("kisara_custom_nsfw_tags", emptySet())

    fun kisaraHideNsfwSuggestions() = preferenceStore.getBoolean("kisara_hide_nsfw_suggestions", false)

    fun kisaraBlurNsfwCovers() = preferenceStore.getBoolean("kisara_blur_nsfw_covers", false)

    fun disableGlassInReader() = preferenceStore.getBoolean("kisara_disable_glass_in_reader", false)

    fun performanceMode() = preferenceStore.getBoolean("kisara_performance_mode", false)

    fun bypassBlurOnTransitions() = preferenceStore.getBoolean("kisara_bypass_blur_on_transitions", true)

    fun disableGlassInBottomBar() = preferenceStore.getBoolean("kisara_disable_glass_in_bottom_bar", false)

    fun disableGlassInCategoryBar() = preferenceStore.getBoolean("kisara_disable_glass_in_category_bar", false)

    fun disableTabTransitions() = preferenceStore.getBoolean("kisara_disable_tab_transitions", false)
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
