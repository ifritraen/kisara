package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR

enum class BackupTargetMode {
    MANGA,
    ANIME,
    NOVEL,
    FULL,
}

data class BackupOptions(
    val targetMode: BackupTargetMode = BackupTargetMode.FULL,
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val readEntries: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,
    // SY -->
    val customInfo: Boolean = true,
    val savedSearchesFeeds: Boolean = true,
    // SY <--
    // KMK -->
    val sideloadedExtensions: Boolean = true,
    val vpnSettings: Boolean = true,
    val animeEntries: Boolean = true,
    val novelEntries: Boolean = true,
    // KMK <--
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        readEntries,
        appSettings,
        extensionRepoSettings,
        sourceSettings,
        privateSettings,
        // SY -->
        customInfo,
        savedSearchesFeeds,
        // SY <--
        // KMK -->
        sideloadedExtensions,
        vpnSettings,
        animeEntries,
        novelEntries,
        // KMK <--
    )

    fun canCreate(): Boolean = when (targetMode) {
        BackupTargetMode.MANGA -> libraryEntries || categories || sourceSettings || savedSearchesFeeds
        BackupTargetMode.ANIME -> animeEntries || categories || sourceSettings || savedSearchesFeeds
        BackupTargetMode.NOVEL -> novelEntries || categories || sourceSettings || savedSearchesFeeds
        BackupTargetMode.FULL -> libraryEntries || animeEntries || novelEntries || categories || appSettings || extensionRepoSettings || sourceSettings || savedSearchesFeeds || sideloadedExtensions || vpnSettings
    }

    companion object {
        fun forManga() = BackupOptions(
            targetMode = BackupTargetMode.MANGA,
            libraryEntries = true,
            animeEntries = false,
            novelEntries = false,
            appSettings = false,
            vpnSettings = false,
        )

        fun forAnime() = BackupOptions(
            targetMode = BackupTargetMode.ANIME,
            libraryEntries = false,
            animeEntries = true,
            novelEntries = false,
            appSettings = false,
            vpnSettings = false,
        )

        fun forNovel() = BackupOptions(
            targetMode = BackupTargetMode.NOVEL,
            libraryEntries = false,
            animeEntries = false,
            novelEntries = true,
            appSettings = false,
            vpnSettings = false,
        )

        fun forFull() = BackupOptions(
            targetMode = BackupTargetMode.FULL,
            libraryEntries = true,
            animeEntries = true,
            novelEntries = true,
            appSettings = true,
            vpnSettings = true,
        )

        val mangaLibraryOptions = persistentListOf(
            Entry(
                label = MR.strings.manga,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = SYMR.strings.custom_entry_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = KMR.strings.saved_searches_feeds,
                getter = BackupOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
        )

        val animeLibraryOptions = persistentListOf(
            Entry(
                label = KMR.strings.label_anime,
                getter = BackupOptions::animeEntries,
                setter = { options, enabled -> options.copy(animeEntries = enabled) },
            ),
            Entry(
                label = KMR.strings.episodes,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.animeEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.animeEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.animeEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.animeEntries },
            ),
            Entry(
                label = SYMR.strings.custom_entry_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.animeEntries },
            ),
            Entry(
                label = KMR.strings.saved_searches_feeds,
                getter = BackupOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
        )

        val novelLibraryOptions = persistentListOf(
            Entry(
                label = KMR.strings.label_novel,
                getter = BackupOptions::novelEntries,
                setter = { options, enabled -> options.copy(novelEntries = enabled) },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.novelEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.novelEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.novelEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.novelEntries },
            ),
            Entry(
                label = SYMR.strings.custom_entry_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.novelEntries },
            ),
            Entry(
                label = KMR.strings.saved_searches_feeds,
                getter = BackupOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
        )

        val fullLibraryOptions = persistentListOf(
            Entry(
                label = MR.strings.manga,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = KMR.strings.label_anime,
                getter = BackupOptions::animeEntries,
                setter = { options, enabled -> options.copy(animeEntries = enabled) },
            ),
            Entry(
                label = KMR.strings.label_novel,
                getter = BackupOptions::novelEntries,
                setter = { options, enabled -> options.copy(novelEntries = enabled) },
            ),
            Entry(
                label = KMR.strings.chapters_episodes,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries || it.animeEntries || it.novelEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries || it.animeEntries || it.novelEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries || it.animeEntries || it.novelEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.libraryEntries || it.animeEntries || it.novelEntries },
            ),
            // SY -->
            Entry(
                label = SYMR.strings.custom_entry_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.libraryEntries || it.animeEntries || it.novelEntries },
            ),
            Entry(
                // KMK-->
                label = KMR.strings.saved_searches_feeds,
                // KMK <--
                getter = BackupOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
            // SY <--
        )

        val settingsOptions = persistentListOf(
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = BackupOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
            // KMK -->
            Entry(
                label = KMR.strings.sideloaded_extensions,
                getter = BackupOptions::sideloadedExtensions,
                setter = { options, enabled -> options.copy(sideloadedExtensions = enabled) },
            ),
            Entry(
                label = KMR.strings.vpn_settings,
                getter = BackupOptions::vpnSettings,
                setter = { options, enabled -> options.copy(vpnSettings = enabled) },
            ),
            // KMK <--
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array[0],
            categories = array[1],
            chapters = array[2],
            tracking = array[3],
            history = array[4],
            readEntries = array[5],
            appSettings = array[6],
            extensionRepoSettings = array[7],
            sourceSettings = array[8],
            privateSettings = array[9],
            // SY -->
            customInfo = array[10],
            savedSearchesFeeds = array[11],
            // SY <--
            // KMK -->
            sideloadedExtensions = array.getOrElse(12) { true },
            vpnSettings = array.getOrElse(13) { true },
            animeEntries = array.getOrElse(14) { true },
            novelEntries = array.getOrElse(15) { true },
            // KMK <--
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
