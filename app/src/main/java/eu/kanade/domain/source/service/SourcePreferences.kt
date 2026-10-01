package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.model.CustomSearchGroup

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    enum class IncognitoPolicy {
        OFF,
        NSFW_ONLY,
        ALL,
    }

    fun sourceDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoExtensions() = preferenceStore.getStringSet("incognito_extensions", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet(
        // KMK -->
        PINNED_SOURCES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun pinnedSourcesOrdered() = preferenceStore.getString(
        "pinned_catalogues_ordered",
        "",
    )

    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    // KMK -->
    fun nsfwOverrideSfwExtensions() = preferenceStore.getStringSet("nsfw_override_sfw_extensions", emptySet())
    fun nsfwOverrideNsfwExtensions() = preferenceStore.getStringSet("nsfw_override_nsfw_extensions", emptySet())
    // KMK <--

    fun incognitoPolicy() = preferenceStore.getEnum("incognito_policy", IncognitoPolicy.OFF)

    fun blockedTags() = preferenceStore.getStringSet("system_blocked_tags", emptySet())

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // KMK -->
    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    // KMK <--

    @Deprecated("Use ExtensionRepoRepository instead", replaceWith = ReplaceWith("ExtensionRepoRepository.getAll()"))
    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    fun migrationSources() = preferenceStore.getLongArray("migration_sources", emptyList())

    fun migrationFlags() = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    fun migrationDeepSearchMode() = preferenceStore.getBoolean("migration_deep_search", false)

    fun migrationPrioritizeByChapters() = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    fun migrationHideUnmatched() = preferenceStore.getBoolean("migration_hide_unmatched", false)

    fun migrationHideWithoutUpdates() = preferenceStore.getBoolean("migration_hide_without_updates", false)

    // KMK -->
    fun searchClean() = preferenceStore.getBoolean("search_clean", false)

    fun searchFormat() = preferenceStore.getInt("search_format_int", 0)

    fun searchFuzzy() = preferenceStore.getBoolean("search_fuzzy", false)

    fun searchFuzzyThreshold() = preferenceStore.getInt("search_fuzzy_threshold", 60)

    fun migrationSmartSearchSingleEntry() = preferenceStore.getBoolean("migration_smart_search_single_entry", false)

    fun globalSearchPinnedState() = preferenceStore.getEnum(
        Preference.appStateKey("global_search_pinned_toggle_state"),
        SourceFilter.PinnedOnly,
    )

    fun globalSearchActiveCustomGroupId() = preferenceStore.getString("global_search_active_custom_group_id", "")

    fun customSearchGroups() = preferenceStore.getObjectFromString(
        "custom_search_groups",
        emptyList<CustomSearchGroup>(),
        { Json.encodeToString(it) },
        {
            try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                emptyList()
            }
        },
    )

    fun disabledRepos() = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    fun recommendationSearchFlags() = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    // KMK -->
    fun relatedMangas() = preferenceStore.getBoolean("related_mangas", true)

    fun customSourceTags() = preferenceStore.getStringSet("custom_source_tags", emptySet())

    fun sourceTagMappings() = preferenceStore.getStringSet("custom_source_tag_mappings", emptySet())

    fun customAnimeSourceTags() = preferenceStore.getStringSet("custom_anime_source_tags", emptySet())

    fun animeSourceTagMappings() = preferenceStore.getStringSet("custom_anime_source_tag_mappings", emptySet())

    fun customNovelSourceTags() = preferenceStore.getStringSet("custom_novel_source_tags", emptySet())

    fun novelSourceTagMappings() = preferenceStore.getStringSet("custom_novel_source_tag_mappings", emptySet())

    // Anime Source Preferences
    fun animeExtensionUpdatesCount() = preferenceStore.getInt("animeext_updates_count", 0)
    fun animeInstalledExtensionRepos() = preferenceStore.getStringSet("anime_installed_extension_repos", emptySet())
    fun disabledAnimeSources() = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())
    fun pinnedAnimeSources() = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    fun lastUsedAnimeSource() = preferenceStore.getLong(Preference.appStateKey("last_anime_catalogue_source"), -1)
    fun incognitoAnimeExtensions() = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
    fun animeExtensionRepositories() = preferenceStore.getStringSet("anime_extension_repositories", emptySet())
    fun animeExtensionRepos() = preferenceStore.getStringSet("anime_extension_repos", emptySet())
    fun animeExtensionStore() = preferenceStore.getBoolean("anime_extension_store", true)
    fun showAnimeExtensions() = preferenceStore.getBoolean("show_anime_extensions", true)
    fun hideInAnimeLibraryItems() = preferenceStore.getBoolean("browse_hide_in_anime_library_items", false)

    // Novel Source Preferences
    fun disabledNovelSources() = preferenceStore.getStringSet("hidden_novel_catalogues", emptySet())
    fun pinnedNovelSources() = preferenceStore.getStringSet("pinned_novel_catalogues", emptySet())
    fun lastUsedNovelSource() = preferenceStore.getLong(Preference.appStateKey("last_novel_catalogue_source"), -1)
    fun incognitoNovelExtensions() = preferenceStore.getStringSet("incognito_novel_extensions", emptySet())
    fun novelFeedSources() = preferenceStore.getStringSet(Preference.appStateKey("novel_feed_sources"), emptySet())
    fun hideInNovelLibraryItems() = preferenceStore.getBoolean("browse_hide_in_novel_library_items", false)
    fun novelExtensionUpdatesCount() = preferenceStore.getInt("novelext_updates_count", 0)
    fun novelInstalledExtensionRepos() = preferenceStore.getStringSet("novel_installed_extension_repos", emptySet())
    fun novelExtensionRepositories() = preferenceStore.getStringSet("novel_extension_repositories", emptySet())
    fun novelExtensionRepos() = preferenceStore.getStringSet("novel_extension_repos", emptySet())
    fun novelExtensionStore() = preferenceStore.getBoolean("novel_extension_store", true)
    fun showNovelExtensions() = preferenceStore.getBoolean("show_novel_extensions", true)
    fun importEpubAddToLibrary() = preferenceStore.getBoolean("pref_epub_import_add_to_library", true)
    fun autoCompileLocalEpubBook() = preferenceStore.getBoolean("pref_auto_compile_local_epub_book", false)
    fun suggestionsUseMangaUpdatesNovel() = preferenceStore.getBoolean("suggestions_use_mangaupdates_novel", true)
    fun suggestionsUseNovelUpdates() = preferenceStore.getBoolean("suggestions_use_novelupdates", true)
    fun entrySuggestionsEnabled() = preferenceStore.getBoolean("pref_entry_suggestions_enabled", true)

    companion object {
        const val PINNED_SOURCES_PREF_KEY = "pinned_catalogues"
    }
    // KMK <--
}
