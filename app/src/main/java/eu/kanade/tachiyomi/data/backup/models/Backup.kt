package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),
    // SY specific values
    @ProtoNumber(600) var backupSavedSearches: List<BackupSavedSearch> = emptyList(),
    // KMK -->
    // Global Popular/Latest feeds
    @ProtoNumber(610) var backupFeeds: List<BackupFeed> = emptyList(),
    // KMK <--
    // Kisara specific values
    @ProtoNumber(620) var backupJarExtensions: List<BackupJarExtension> = emptyList(),
    @ProtoNumber(630) var backupWireguardConfigs: List<BackupWireguardConfig> = emptyList(),
    @ProtoNumber(631) var backupWireguardPrefs: BackupWireguardPreferences? = null,
    // Anime (Standard Aniyomi & Kisara)
    @ProtoNumber(102) var backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(103) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(107) var backupAnimeSources: List<BackupSource> = emptyList(),
    @ProtoNumber(503) var backupAnimeSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(504) var backupAnimeFeeds: List<BackupFeed> = emptyList(),
    @ProtoNumber(505) var backupAnimeSavedSearches: List<BackupSavedSearch> = emptyList(),
    // Novel (Kisara)
    @ProtoNumber(700) var backupNovel: List<BackupNovel> = emptyList(),
    @ProtoNumber(701) var backupNovelCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(702) var backupNovelSources: List<BackupSource> = emptyList(),
    @ProtoNumber(703) var backupNovelSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(704) var backupNovelFeeds: List<BackupFeed> = emptyList(),
    @ProtoNumber(705) var backupNovelSavedSearches: List<BackupSavedSearch> = emptyList(),
)
