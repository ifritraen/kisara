package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.FeedBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SavedSearchBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupJarExtension
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardAssociation
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardConfig
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardPreferences
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),

    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator = ExtensionStoresBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    // KMK -->
    private val feedBackupCreator: FeedBackupCreator = FeedBackupCreator(),
    // KMK <--
    // SY -->
    private val savedSearchBackupCreator: SavedSearchBackupCreator = SavedSearchBackupCreator(),
    private val getMergedManga: GetMergedManga = Injekt.get(),
    // SY <--
    // Anime & Novel
    private val getAnimeFavorites: tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites = Injekt.get(),
    private val animeRepository: tachiyomi.domain.entries.anime.repository.AnimeRepository = Injekt.get(),
    private val animeBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator(),
    private val animeCategoriesBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator(),
    private val animeSourcesBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator(),

    private val getNovelFavorites: tachiyomi.domain.entries.novel.interactor.GetNovelFavorites = Injekt.get(),
    private val novelRepository: tachiyomi.domain.entries.novel.repository.NovelRepository = Injekt.get(),
    private val novelBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.NovelBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.NovelBackupCreator(),
    private val novelCategoriesBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.NovelCategoriesBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.NovelCategoriesBackupCreator(),
    private val novelSourcesBackupCreator: eu.kanade.tachiyomi.data.backup.create.creators.NovelSourcesBackupCreator = eu.kanade.tachiyomi.data.backup.create.creators.NovelSourcesBackupCreator(),
    // KMK -->
    private val getGranularTemplates: tachiyomi.domain.scoring.interactor.GetGranularTemplates = Injekt.get(),
    // KMK <--
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)

                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                dir?.createFile(getFilename(options.targetMode))
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val nonFavoriteManga = if (options.readEntries && options.libraryEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            // SY -->
            val mergedManga = if (options.libraryEntries) getMergedManga.await() else emptyList()
            // SY <--
            val backupManga = if (options.libraryEntries) {
                backupMangas(getFavorites.await() + nonFavoriteManga /* SY --> */ + mergedManga /* SY <-- */, options)
            } else {
                emptyList()
            }

            val nonFavoriteAnime = if (options.readEntries && options.animeEntries) animeRepository.getWatchedAnimeNotInLibrary() else emptyList()
            val backupAnime = if (options.animeEntries) {
                backupAnimes(getAnimeFavorites.await() + nonFavoriteAnime, options)
            } else {
                emptyList()
            }

            val nonFavoriteNovel = if (options.readEntries && options.novelEntries) novelRepository.getReadNovelNotInLibrary() else emptyList()
            val backupNovel = if (options.novelEntries) {
                backupNovels(getNovelFavorites.await() + nonFavoriteNovel, options)
            } else {
                emptyList()
            }

            val backup = Backup(
                backupManga = backupManga,
                backupCategories = backupCategories(options),
                backupSources = backupSources(backupManga),
                backupPreferences = backupAppPreferences(options),
                backupExtensionStores = backupExtensionStores(options),
                backupSourcePreferences = backupSourcePreferences(options),

                // SY -->
                backupSavedSearches = backupSavedSearches(options),
                // SY <--

                // KMK -->
                backupFeeds = backupFeeds(options),
                backupJarExtensions = backupJarExtensions(options),
                backupWireguardConfigs = backupWireguardConfigs(options),
                backupWireguardPrefs = backupWireguardPrefs(options),

                // Anime & Novel
                backupAnime = backupAnime,
                backupAnimeCategories = backupAnimeCategories(options),
                backupAnimeSources = backupAnimeSources(backupAnime),
                backupAnimeSourcePreferences = backupAnimeSourcePreferences(options),

                backupNovel = backupNovel,
                backupNovelCategories = backupNovelCategories(options),
                backupNovelSources = backupNovelSources(backupNovel),
                backupNovelSourcePreferences = backupNovelSourcePreferences(options),

                backupGranularTemplates = getGranularTemplates.await().filter { it.id > 0 }.map { tmpl ->
                    eu.kanade.tachiyomi.data.backup.models.BackupGranularTemplate(
                        name = tmpl.name,
                        mediaType = tmpl.mediaType,
                        criteria = tmpl.criteria.map { c ->
                            eu.kanade.tachiyomi.data.backup.models.BackupGranularTemplateCriterion(
                                id = c.id,
                                name = c.name,
                                weight = c.weight,
                            )
                        },
                        isDefault = tmpl.isDefault,
                    )
                },
                // KMK <--
            )

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use {
                    it.write(byteArray)
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (!options.libraryEntries) return emptyList()

        return mangaBackupCreator(mangas, options)
    }

    fun backupSources(mangas: List<BackupManga>): List<BackupSource> {
        return sourcesBackupCreator(mangas)
    }

    suspend fun backupAnimes(animes: List<tachiyomi.domain.entries.anime.model.Anime>, options: BackupOptions): List<eu.kanade.tachiyomi.data.backup.models.BackupAnime> {
        if (!options.animeEntries) return emptyList()

        return animeBackupCreator(animes, options)
    }

    suspend fun backupAnimeCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return animeCategoriesBackupCreator()
    }

    fun backupAnimeSources(animes: List<eu.kanade.tachiyomi.data.backup.models.BackupAnime>): List<BackupSource> {
        return animeSourcesBackupCreator(animes)
    }

    fun backupAnimeSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createAnimeSource(includePrivatePreferences = options.privateSettings)
    }

    suspend fun backupNovels(novels: List<tachiyomi.domain.entries.novel.model.Novel>, options: BackupOptions): List<eu.kanade.tachiyomi.data.backup.models.BackupNovel> {
        if (!options.novelEntries) return emptyList()

        return novelBackupCreator(novels, options)
    }

    suspend fun backupNovelCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return novelCategoriesBackupCreator()
    }

    fun backupNovelSources(novels: List<eu.kanade.tachiyomi.data.backup.models.BackupNovel>): List<BackupSource> {
        return novelSourcesBackupCreator(novels)
    }

    fun backupNovelSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createNovelSource(includePrivatePreferences = options.privateSettings)
    }

    /* KMK --> */ suspend /* KMK <-- */ fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionRepoSettings) return emptyList()

        return extensionStoresBackupCreator()
    }

    fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    // SY -->
    suspend fun backupSavedSearches(options: BackupOptions): List<BackupSavedSearch> {
        if (!options.savedSearchesFeeds) return emptyList()

        return savedSearchBackupCreator()
    }
    // SY <--

    // KMK -->
    /**
     * Backup global Popular/Latest feeds
     */
    suspend fun backupFeeds(options: BackupOptions): List<BackupFeed> {
        if (!options.savedSearchesFeeds) return emptyList()

        return feedBackupCreator()
    }

    private fun backupJarExtensions(options: BackupOptions): List<BackupJarExtension> {
        if (!options.sideloadedExtensions) return emptyList()
        val extensionDir = File(context.filesDir, "jar_extensions")
        if (!extensionDir.exists() || !extensionDir.isDirectory) return emptyList()

        val list = mutableListOf<BackupJarExtension>()
        val files = extensionDir.listFiles { file -> file.extension == "jar" }.orEmpty()
        for (file in files) {
            try {
                val data = file.readBytes()
                val repoName = eu.kanade.tachiyomi.extension.JarExtensionManager.getRepoNameForJar(context, file.name)
                list.add(BackupJarExtension(filename = file.name, data = data, repoName = repoName))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to backup jar: ${file.name}" }
            }
        }
        return list
    }

    private fun backupWireguardConfigs(options: BackupOptions): List<BackupWireguardConfig> {
        if (!options.vpnSettings) return emptyList()
        val vpnDir = File(context.filesDir, "wireguard")
        if (!vpnDir.exists() || !vpnDir.isDirectory) return emptyList()

        val list = mutableListOf<BackupWireguardConfig>()
        val files = vpnDir.listFiles { file -> file.extension == "conf" }.orEmpty()
        for (file in files) {
            try {
                val content = file.readText()
                list.add(BackupWireguardConfig(filename = file.name, data = content))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to backup Wireguard config: ${file.name}" }
            }
        }
        return list
    }

    private fun backupWireguardPrefs(options: BackupOptions): BackupWireguardPreferences? {
        if (!options.vpnSettings) return null
        val vpnPrefs = context.getSharedPreferences("wireguard_prefs", Context.MODE_PRIVATE)
        val all = vpnPrefs.all
        val defaultProfile = all["default_profile"] as? String
        val associations = all.filterKeys { it.startsWith("source_") }
            .mapNotNull { (key, value) ->
                val strVal = value as? String ?: return@mapNotNull null
                BackupWireguardAssociation(key = key, value = strVal)
            }
        return BackupWireguardPreferences(defaultProfile = defaultProfile, sourceAssociations = associations)
    }
    // KMK <--

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """(kisara_(backup|manga|anime|novel)|${BuildConfig.APPLICATION_ID}|tachiyomi)_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}\.(proto\.gz|tachibk)""".toRegex()

        fun getFilename(mode: BackupTargetMode = BackupTargetMode.FULL): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            val prefix = when (mode) {
                BackupTargetMode.MANGA -> "kisara_manga"
                BackupTargetMode.ANIME -> "kisara_anime"
                BackupTargetMode.NOVEL -> "kisara_novel"
                BackupTargetMode.FULL -> "kisara_backup"
            }
            return "${prefix}_$date.proto.gz"
        }
    }
}
