package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)
    fun downloadFillermarkedItems() = preferenceStore.getBoolean("pref_download_fillermarked_items", true)

    fun removeExcludeCategories() = preferenceStore.getStringSet(REMOVE_EXCLUDE_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)
    fun downloadNewEpisodes() = preferenceStore.getBoolean("download_new_episode", false)
    fun downloadNewNovelChapters() = preferenceStore.getBoolean("download_new_novel", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapterCategoriesExclude() =
        preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)
    fun downloadNewUnseenEpisodesOnly() = preferenceStore.getBoolean("download_new_unread_episodes_only", false)
    fun downloadNewUnreadNovelChaptersOnly() = preferenceStore.getBoolean("download_new_unread_novel_chapters_only", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 5)

    fun parallelChapterLimit() = preferenceStore.getInt("download_parallel_chapter_limit", 1)

    // SY -->
    fun includeChapterUrlHash() = preferenceStore.getBoolean("download_include_chapter_url_hash", false)
    // SY <--

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)
    // KMK <--

    fun useExternalDownloader() = preferenceStore.getBoolean("use_external_downloader", false)
    fun externalDownloaderSelection() = preferenceStore.getString("external_downloader_selection", "")

    fun removeExcludeAnimeCategories() = preferenceStore.getStringSet("remove_exclude_anime_categories", emptySet())
    fun removeExcludeNovelCategories() = preferenceStore.getStringSet("remove_exclude_novel_categories", emptySet())

    fun autoDownloadWhileWatching() = preferenceStore.getInt("auto_download_while_watching", 0)

    fun novelDownloadDelayMs() = preferenceStore.getInt("pref_novel_download_delay_ms_key", 0)
    fun novelDownloadJitterMs() = preferenceStore.getInt("pref_novel_download_jitter_ms_key", 0)
    fun novelDownloadTimeoutMs() = preferenceStore.getInt("pref_novel_download_timeout_ms_key", 60_000)
    fun novelDownloadFailureCooldownMs() = preferenceStore.getInt("pref_novel_download_cooldown_ms_key", 5_000)

    fun downloadNewEpisodeCategories() = preferenceStore.getStringSet("download_new_anime_categories", emptySet())
    fun downloadNewNovelChapterCategories() = preferenceStore.getStringSet("download_new_novel_categories", emptySet())

    fun downloadNewEpisodeCategoriesExclude() = preferenceStore.getStringSet("download_new_anime_categories_exclude", emptySet())
    fun downloadNewNovelChapterCategoriesExclude() = preferenceStore.getStringSet("download_new_novel_categories_exclude", emptySet())

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
            "remove_exclude_anime_categories",
            "remove_exclude_novel_categories",
            "download_new_anime_categories",
            "download_new_novel_categories",
            "download_new_anime_categories_exclude",
            "download_new_novel_categories_exclude",
        )
    }
}
