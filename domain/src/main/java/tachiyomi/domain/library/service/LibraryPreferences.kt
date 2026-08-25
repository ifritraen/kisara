package tachiyomi.domain.library.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.manga.model.Manga

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun displayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun sortingMode() = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    fun randomSortSeed() = preferenceStore.getInt("library_random_sort_seed", 0)

    fun portraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    fun lastUpdatedTimestamp() = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)

    // KMK -->
    fun showUpdatingProgressBanner() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_updating_progress_banner_key"),
        true,
    )
    // KMK <--

    fun coverRatios() = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_ratios_key"),
        emptySet(),
    )

    fun coverColors() = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_colors_key"),
        emptySet(),
    )
    // KMK <--

    fun autoUpdateDeviceRestrictions() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    fun autoUpdateMangaRestrictions() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
            MANGA_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    // KMK -->
    fun fetchMetadataOnAdd() = preferenceStore.getBoolean("fetch_metadata_on_add", false)
    fun fetchChaptersOnAdd() = preferenceStore.getBoolean("fetch_chapters_on_add", false)
    // KMK <--

    fun showContinueReadingButton() = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    fun markDuplicateReadChapterAsRead() = preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    // region Filter

    fun filterDownloaded() = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    fun filterUnread() = preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    fun filterStarted() = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    fun filterBookmarked() = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    fun filterCompleted() = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    fun filterIntervalCustom() = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    // SY -->
    fun filterLewd() = preferenceStore.getEnum(
        "pref_filter_library_lewd_v2",
        TriState.DISABLED,
    )
    // SY <--

    // KMK -->
    fun filterCategories() = preferenceStore.getBoolean(
        "pref_filter_library_categories",
        false,
    )

    fun filterCategoriesInclude() = preferenceStore.getStringSet(FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY, emptySet())

    fun filterCategoriesExclude() = preferenceStore.getStringSet(FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun filterExtensionTag() = preferenceStore.getString(
        "pref_filter_library_extension_tag",
        "",
    )
    // KMK <--

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // region Badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun unreadBadge() = preferenceStore.getBoolean("display_unread_badge", true)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", true)

    // KMK -->
    fun sourceBadge() = preferenceStore.getBoolean("display_source_badge", true)

    fun scoreBadge() = preferenceStore.getBoolean("display_score_badge", true)

    fun externalStatusBadge() = preferenceStore.getBoolean("display_external_status_badge", false)

    fun useLangIcon() = preferenceStore.getBoolean("display_language_text", true)
    // KMK <--

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)
    fun newUpdatesCount() = preferenceStore.getInt(Preference.appStateKey("library_unseen_updates_count"), 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    fun lastUsedCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", false)

    fun subcategoryTabs() = preferenceStore.getBoolean("display_subcategory_tabs", false)

    // KMK -->
    fun categoryBarPinned() = preferenceStore.getBoolean("pref_category_bar_pinned", true)
    // KMK <--

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    // KMK -->
    fun showHiddenCategories() = preferenceStore.getBoolean("hide_hidden_categories", false)
    // KMK <--

    fun updateCategories() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_PREF_KEY, emptySet())

    fun updateCategoriesExclude() = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    // endregion

    // region Chapter

    fun filterChapterByRead() = preferenceStore.getLong(
        "default_chapter_filter_by_read",
        Manga.SHOW_ALL,
    )

    fun filterChapterByDownloaded() = preferenceStore.getLong(
        "default_chapter_filter_by_downloaded",
        Manga.SHOW_ALL,
    )

    fun filterChapterByBookmarked() = preferenceStore.getLong(
        "default_chapter_filter_by_bookmarked",
        Manga.SHOW_ALL,
    )

    // and upload date
    fun sortChapterBySourceOrNumber() = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    fun displayChapterByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    fun sortChapterByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.unreadFilterRaw)
        filterChapterByDownloaded().set(manga.downloadedFilterRaw)
        filterChapterByBookmarked().set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    fun filterNovelChapterByRead() = preferenceStore.getLong("default_novel_chapter_filter_by_read", 0L)
    fun filterNovelChapterByDownloaded() = preferenceStore.getLong("default_novel_chapter_filter_by_downloaded", 0L)
    fun filterNovelChapterByBookmarked() = preferenceStore.getLong("default_novel_chapter_filter_by_bookmarked", 0L)
    fun sortNovelChapterBySourceOrNumber() = preferenceStore.getLong("default_novel_chapter_sort_by_source_or_number", 0L)
    fun sortNovelChapterByAscendingOrDescending() = preferenceStore.getLong("default_novel_chapter_sort_by_ascending_or_descending", 1L)
    fun displayNovelChapterByNameOrNumber() = preferenceStore.getLong("default_novel_chapter_display_by_name_or_number", 0L)

    fun setNovelChapterSettingsDefault(novel: tachiyomi.domain.entries.novel.model.Novel) {
        filterNovelChapterByRead().set(novel.unreadFilterRaw)
        filterNovelChapterByDownloaded().set(novel.downloadedFilterRaw)
        filterNovelChapterByBookmarked().set(novel.bookmarkedFilterRaw)
        sortNovelChapterBySourceOrNumber().set(novel.sorting)
        displayNovelChapterByNameOrNumber().set(novel.displayMode)
        sortNovelChapterByAscendingOrDescending().set(
            if (novel.sortDescending()) 0L else 1L,
        )
    }

    fun filterEpisodeBySeen() = preferenceStore.getLong("default_episode_filter_by_seen", 0L)
    fun filterEpisodeByDownloaded() = preferenceStore.getLong("default_episode_filter_by_downloaded", 0L)
    fun filterEpisodeByBookmarked() = preferenceStore.getLong("default_episode_filter_by_bookmarked", 0L)
    fun filterEpisodeByFillermarked() = preferenceStore.getLong("default_episode_filter_by_fillermarked", 0L)
    fun sortEpisodeBySourceOrNumber() = preferenceStore.getLong("default_episode_sort_by_source_or_number", 0L)
    fun sortEpisodeByAscendingOrDescending() = preferenceStore.getLong("default_episode_sort_by_ascending_or_descending", 1L)
    fun displayEpisodeByNameOrNumber() = preferenceStore.getLong("default_episode_display_by_name_or_number", 0L)
    fun showEpisodeThumbnailPreviews() = preferenceStore.getLong("default_episode_show_thumbnail_previews", 0L)
    fun showEpisodeSummaries() = preferenceStore.getLong("default_episode_show_summaries", 0L)

    fun filterSeasonByDownload() = preferenceStore.getLong("default_season_filter_by_download", 0L)
    fun filterSeasonByUnseen() = preferenceStore.getLong("default_season_filter_by_unseen", 0L)
    fun filterSeasonByStarted() = preferenceStore.getLong("default_season_filter_by_started", 0L)
    fun filterSeasonByCompleted() = preferenceStore.getLong("default_season_filter_by_completed", 0L)
    fun filterSeasonByBookmarked() = preferenceStore.getLong("default_season_filter_by_bookmarked", 0L)
    fun filterSeasonByFillermarked() = preferenceStore.getLong("default_season_filter_by_fillermarked", 0L)
    fun sortSeasonBySourceOrNumber() = preferenceStore.getLong("default_season_sort_by_source_or_number", 0L)
    fun sortSeasonByAscendingOrDescending() = preferenceStore.getLong("default_season_sort_by_ascending_or_descending", 1L)
    fun seasonDisplayGridMode() = preferenceStore.getLong("default_season_display_grid_mode", 0L)
    fun seasonDisplayGridSize() = preferenceStore.getInt("default_season_display_grid_size", 0)
    fun seasonDownloadOverlay() = preferenceStore.getBoolean("default_season_download_overlay", false)
    fun seasonUnseenOverlay() = preferenceStore.getBoolean("default_season_unseen_overlay", false)
    fun seasonLocalOverlay() = preferenceStore.getBoolean("default_season_local_overlay", false)
    fun seasonLangOverlay() = preferenceStore.getBoolean("default_season_lang_overlay", false)
    fun seasonContinueOverlay() = preferenceStore.getBoolean("default_season_continue_overlay", false)
    fun seasonDisplayMode() = preferenceStore.getLong("default_season_display_mode", 0L)

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen().set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded().set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked().set(anime.bookmarkedFilterRaw)
        filterEpisodeByFillermarked().set(anime.fillermarkedFilterRaw)
        sortEpisodeBySourceOrNumber().set(anime.sorting)
        displayEpisodeByNameOrNumber().set(anime.displayMode)
        sortEpisodeByAscendingOrDescending().set(
            if (anime.sortDescending()) 0L else 1L,
        )
    }

    fun setSeasonSettingsDefault(anime: Anime) {
        filterSeasonByDownload().set(anime.seasonDownloadedFilterRaw)
        filterSeasonByUnseen().set(anime.seasonUnseenFilterRaw)
        filterSeasonByStarted().set(anime.seasonStartedFilterRaw)
        filterSeasonByCompleted().set(anime.seasonCompletedFilterRaw)
        filterSeasonByBookmarked().set(anime.seasonBookmarkedFilterRaw)
        filterSeasonByFillermarked().set(anime.seasonFillermarkedFilterRaw)
        sortSeasonBySourceOrNumber().set(anime.seasonSorting)
        sortSeasonByAscendingOrDescending().set(
            if (anime.seasonSortDescending()) 0L else 1L,
        )
    }

    fun novelUpdateCategories() = preferenceStore.getStringSet("novellib_update_categories", emptySet())
    fun novelUpdateCategoriesExclude() = preferenceStore.getStringSet("novellib_update_categories_exclude", emptySet())

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    fun hideMissingChapters() = preferenceStore.getBoolean("pref_hide_missing_chapter_indicators", false)

    // KMK -->
    fun showEmptyCategoriesSearch() = preferenceStore.getBoolean("show_empty_categories_search", false)
    // KMK <--
    // endregion

    // region Swipe Actions

    fun swipeToStartAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_end_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    fun swipeToEndAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleRead,
    )

    fun updateMangaTitles() = preferenceStore.getBoolean("pref_update_library_manga_titles", false)

    fun disallowNonAsciiFilenames() = preferenceStore.getBoolean("disallow_non_ascii_filenames", false)

    // endregion

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    enum class NovelSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,
        ToggleFillermark,
        Download,
        Disabled,
    }

    fun swipeEpisodeStartAction() = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleSeen,
    )

    fun swipeEpisodeEndAction() = preferenceStore.getEnum(
        "pref_episode_swipe_end_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    fun swipeNovelStartAction() = preferenceStore.getEnum(
        "pref_novel_swipe_start_action",
        NovelSwipeAction.ToggleRead,
    )

    fun swipeNovelEndAction() = preferenceStore.getEnum(
        "pref_novel_swipe_end_action",
        NovelSwipeAction.ToggleBookmark,
    )

    // SY -->
    fun sortTagsForLibrary() = preferenceStore.getStringSet("sort_tags_for_library", mutableSetOf())
    fun groupLibraryUpdateType() = preferenceStore.getEnum("group_library_update_type", GroupLibraryMode.GLOBAL)
    fun groupLibraryBy() = preferenceStore.getInt("group_library_by", LibraryGroup.BY_DEFAULT)
    // SY <--

    // Anime extensions
    fun animeSortingMode() = preferenceStore.getObjectFromString(
        "library_anime_sorting_mode",
        tachiyomi.domain.library.anime.model.AnimeLibrarySort.default,
        tachiyomi.domain.library.anime.model.AnimeLibrarySort.Serializer::serialize,
        tachiyomi.domain.library.anime.model.AnimeLibrarySort.Serializer::deserialize,
    )
    fun updateSeasonOnRefresh() = preferenceStore.getBoolean("pref_update_season_on_refresh", true)
    fun randomAnimeSortSeed() = preferenceStore.getInt("library_anime_random_sort_seed", 0)
    fun animeGroupLibraryBy() = preferenceStore.getInt("group_animelib_by", LibraryGroup.BY_DEFAULT)
    fun defaultAnimeCategory() = preferenceStore.getInt("default_anime_category", -1)
    fun defaultNovelCategory() = preferenceStore.getInt("default_novel_category", -1)
    fun animeUpdateCategories() = preferenceStore.getStringSet("animelib_update_categories", emptySet())
    fun animeUpdateCategoriesExclude() = preferenceStore.getStringSet("animelib_update_categories_exclude", emptySet())
    fun animePortraitColumns() = preferenceStore.getInt("pref_library_anime_columns_portrait_key", 0)
    fun animeLandscapeColumns() = preferenceStore.getInt("pref_library_anime_columns_landscape_key", 0)
    fun autoUpdateItemRestrictions() = autoUpdateMangaRestrictions()
    fun newNovelUpdatesCount() = preferenceStore.getInt("library_novel_new_updates_count", 0)

    fun setDisplayModeForAnime(mode: LibraryDisplayMode) {
        // no-op or persist if needed
    }

    fun setDisplayModeForAnime(category: tachiyomi.domain.category.model.Category?, mode: LibraryDisplayMode) {
        // no-op or persist if needed
    }

    // Anime preferences
    fun filterDownloadedAnime() = preferenceStore.getEnum("pref_filter_library_anime_downloaded_v2", TriState.DISABLED)
    fun filterUnseenAnime() = preferenceStore.getEnum("pref_filter_library_anime_unseen_v2", TriState.DISABLED)
    fun filterStartedAnime() = preferenceStore.getEnum("pref_filter_library_anime_started_v2", TriState.DISABLED)
    fun filterBookmarkedAnime() = preferenceStore.getEnum("pref_filter_library_anime_bookmarked_v2", TriState.DISABLED)
    fun filterCompletedAnime() = preferenceStore.getEnum("pref_filter_library_anime_completed_v2", TriState.DISABLED)
    fun filterAnimeLanguages() = preferenceStore.getStringSet("pref_filter_library_anime_languages", emptySet())
    fun animeDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_library_anime",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )
    fun lastUsedAnimeCategory() = preferenceStore.getInt("last_used_anime_category", 0)

    // Anime & Novel extensions
    fun filterDownloadedNovel() = preferenceStore.getEnum("pref_filter_library_novel_downloaded_v2", TriState.DISABLED)
    fun filterUnreadNovel() = preferenceStore.getEnum("pref_filter_library_novel_unread_v2", TriState.DISABLED)
    fun filterStartedNovel() = preferenceStore.getEnum("pref_filter_library_novel_started_v2", TriState.DISABLED)
    fun filterBookmarkedNovel() = preferenceStore.getEnum("pref_filter_library_novel_bookmarked_v2", TriState.DISABLED)
    fun filterCompletedNovel() = preferenceStore.getEnum("pref_filter_library_novel_completed_v2", TriState.DISABLED)
    fun filterNovelLanguages() = preferenceStore.getStringSet("pref_filter_library_novel_languages", emptySet())
    fun novelSortingMode() = preferenceStore.getObjectFromString(
        "library_novel_sorting_mode",
        tachiyomi.domain.library.novel.model.NovelLibrarySort.default,
        tachiyomi.domain.library.novel.model.NovelLibrarySort.Serializer::serialize,
        tachiyomi.domain.library.novel.model.NovelLibrarySort.Serializer::deserialize,
    )
    fun randomNovelSortSeed() = preferenceStore.getInt("library_novel_random_sort_seed", 0)
    fun separateDisplayModePerMedia() = preferenceStore.getBoolean("separate_display_mode_per_media", false)
    fun novelDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_library_novel",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )
    fun novelPortraitColumns() = preferenceStore.getInt("pref_library_novel_columns_portrait_key", 0)
    fun novelLandscapeColumns() = preferenceStore.getInt("pref_library_novel_columns_landscape_key", 0)
    fun lastUsedNovelCategory() = preferenceStore.getInt("last_used_novel_category", 0)
    fun markDuplicateSeenEpisodeAsSeen() = preferenceStore.getStringSet("mark_duplicate_seen_episode_seen", emptySet())

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
        const val MANGA_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val ENTRY_NON_COMPLETED = MANGA_NON_COMPLETED
        const val ENTRY_HAS_UNVIEWED = MANGA_HAS_UNREAD
        const val ENTRY_NON_VIEWED = MANGA_NON_READ
        const val ENTRY_OUTSIDE_RELEASE_PERIOD = MANGA_OUTSIDE_RELEASE_PERIOD

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"
        const val MARK_DUPLICATE_EPISODE_SEEN_NEW = "new"
        const val MARK_DUPLICATE_EPISODE_SEEN_EXISTING = "existing"

        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"

        // KMK -->
        private const val FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY = "pref_filter_library_categories_include"
        private const val FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY = "pref_filter_library_categories_exclude"
        // KMK <--

        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
            // KMK -->
            FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY,
            FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY,
            // KMK <--
        )
    }
}
