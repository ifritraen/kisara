package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupJarExtension
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardAssociation
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardConfig
import eu.kanade.tachiyomi.data.backup.models.BackupWireguardPreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.SavedSearchRestorer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val extensionStoreRestorer: ExtensionStoreRestorer = ExtensionStoreRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(isSync),
    // SY -->
    private val savedSearchRestorer: SavedSearchRestorer = SavedSearchRestorer(),
    // SY <--
    // KMK -->
    private val feedRestorer: FeedRestorer = FeedRestorer(),
    // KMK <--
    // Anime & Novel
    private val animeCategoriesRestorer: eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer = eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer(),
    private val novelCategoriesRestorer: eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer = eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer(),
    private val animeRestorer: eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer = eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer(isSync),
    private val novelRestorer: eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer = eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer(isSync),
    // KMK -->
    private val saveGranularTemplate: tachiyomi.domain.scoring.interactor.SaveGranularTemplate = Injekt.get(),
    // KMK <--
) {

    private var restoreAmount = 0
    private val restoreProgress = AtomicInteger()
    private val errors = Collections.synchronizedList(mutableListOf<Pair<Date, String>>())
    private val dispatcher: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()
    private val mangaProgressBatch = Runtime.getRuntime().availableProcessors() * 8

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        try {
            restoreFromFile(uri, options)

            val time = System.currentTimeMillis() - startTime

            val logFile = writeErrorLog()

            notifier.showRestoreComplete(
                time,
                errors.size,
                logFile.parent,
                logFile.name,
                isSync,
            )
        } finally {
            try {
                dispatcher.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources + backup.backupAnimeSources + backup.backupNovelSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size
        }
        if (options.animeEntries) {
            restoreAmount += backup.backupAnime.size
        }
        if (options.novelEntries) {
            restoreAmount += backup.backupNovel.size
        }
        if (options.categories) {
            if (backup.backupCategories.isNotEmpty()) restoreAmount += 1
            if (backup.backupAnimeCategories.isNotEmpty()) restoreAmount += 1
            if (backup.backupNovelCategories.isNotEmpty()) restoreAmount += 1
        }
        // SY -->
        if (options.savedSearchesFeeds) {
            restoreAmount += 1
        }
        // SY <--
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionRepoSettings) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.sideloadedExtensions && backup.backupJarExtensions.isNotEmpty()) {
            restoreAmount += 1
        }
        if (options.vpnSettings && (backup.backupWireguardConfigs.isNotEmpty() || backup.backupWireguardPrefs != null)) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(backup.backupCategories)
                restoreAnimeCategories(backup.backupAnimeCategories)
                restoreNovelCategories(backup.backupNovelCategories)
            }
            // SY -->
            if (options.savedSearchesFeeds) {
                restoreSavedSearches(
                    backup.backupSavedSearches,
                    // KMK -->
                    backup.backupFeeds,
                    // KMK <--
                )
            }
            // SY <--
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCategories.takeIf { options.categories })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences + backup.backupAnimeSourcePreferences + backup.backupNovelSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreManga(backup.backupManga, if (options.categories) backup.backupCategories else emptyList())
            }
            if (options.animeEntries && backup.backupAnime.isNotEmpty()) {
                restoreAnime(backup.backupAnime, if (options.categories) backup.backupAnimeCategories else emptyList())
            }
            if (options.novelEntries && backup.backupNovel.isNotEmpty()) {
                restoreNovel(backup.backupNovel, if (options.categories) backup.backupNovelCategories else emptyList())
            }
            if (options.extensionRepoSettings) {
                restoreExtensionStores(backup.backupExtensionStores)
            }
            if (options.sideloadedExtensions && backup.backupJarExtensions.isNotEmpty()) {
                restoreJarExtensions(backup.backupJarExtensions)
            }
            if (options.vpnSettings && (backup.backupWireguardConfigs.isNotEmpty() || backup.backupWireguardPrefs != null)) {
                restoreWireguard(backup.backupWireguardConfigs, backup.backupWireguardPrefs)
            }
            // KMK -->
            if (backup.backupGranularTemplates.isNotEmpty()) {
                backup.backupGranularTemplates.forEach { tmpl ->
                    saveGranularTemplate.await(
                        tachiyomi.domain.scoring.model.GranularScoreTemplate(
                            id = 0L,
                            name = tmpl.name,
                            mediaType = tmpl.mediaType,
                            criteria = tmpl.criteria.map {
                                tachiyomi.domain.scoring.model.GranularTemplateCriterion(
                                    id = it.id,
                                    name = it.name,
                                    weight = it.weight,
                                )
                            },
                            isDefault = tmpl.isDefault,
                        ),
                    )
                }
            }
            // KMK <--

            // TODO: optionally trigger online library + tracker update
        }
    }

    context(scope: CoroutineScope)
    private /* KMK --> */suspend /* KMK <-- */ fun restoreCategories(backupCategories: List<BackupCategory>) = withContext(dispatcher) {
        if (backupCategories.isEmpty()) return@withContext
        scope.ensureActive()
        categoriesRestorer(backupCategories)

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.categories),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    context(scope: CoroutineScope)
    private suspend fun restoreAnimeCategories(backupCategories: List<BackupCategory>) = withContext(dispatcher) {
        if (backupCategories.isEmpty()) return@withContext
        scope.ensureActive()
        animeCategoriesRestorer(backupCategories)

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(KMR.strings.label_anime),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                .show(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    context(scope: CoroutineScope)
    private suspend fun restoreNovelCategories(backupCategories: List<BackupCategory>) = withContext(dispatcher) {
        if (backupCategories.isEmpty()) return@withContext
        scope.ensureActive()
        novelCategoriesRestorer(backupCategories)

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(KMR.strings.label_novel),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                .show(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    // SY -->
    private fun CoroutineScope.restoreSavedSearches(
        backupSavedSearches: List<BackupSavedSearch>,
        // KMK -->
        backupFeeds: List<BackupFeed>,
        // KMK <--
    ) = launch(dispatcher) {
        ensureActive()
        savedSearchRestorer.restoreSavedSearches(backupSavedSearches)
        // KMK -->
        feedRestorer.restoreFeeds(backupFeeds)
        // KMK <--

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(KMR.strings.saved_searches_feeds),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }
    // SY <--

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
    ) = launch(dispatcher) {
        val sortedMangas = mangaRestorer.sortByNew(backupMangas)
        sortedMangas.chunked(100).forEach { chunk ->
            ensureActive()
            chunk.forEach { manga ->
                try {
                    mangaRestorer.restore(manga, backupCategories)
                } catch (e: Exception) {
                    val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
                    errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
                }
            }
            val currentProgress = restoreProgress.addAndGet(chunk.size)
            with(notifier) {
                showRestoreProgress(chunk.last().title, currentProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }

        val finalProgress = restoreProgress.get()
        if (finalProgress < restoreAmount) {
            with(notifier) {
                showRestoreProgress(context.stringResource(MR.strings.restoring_backup), finalProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }
    }

    private fun CoroutineScope.restoreAnime(
        backupAnimes: List<eu.kanade.tachiyomi.data.backup.models.BackupAnime>,
        backupCategories: List<BackupCategory>,
    ) = launch(dispatcher) {
        val sortedAnimes = animeRestorer.sortByNew(backupAnimes)
        sortedAnimes.chunked(100).forEach { chunk ->
            ensureActive()
            chunk.forEach { anime ->
                try {
                    animeRestorer.restore(anime, backupCategories)
                } catch (e: Exception) {
                    val sourceName = sourceMapping[anime.source] ?: anime.source.toString()
                    errors.add(Date() to "${anime.title} [$sourceName]: ${e.message}")
                }
            }
            val currentProgress = restoreProgress.addAndGet(chunk.size)
            with(notifier) {
                showRestoreProgress(chunk.last().title, currentProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }

        val finalProgress = restoreProgress.get()
        if (finalProgress < restoreAmount) {
            with(notifier) {
                showRestoreProgress(context.stringResource(MR.strings.restoring_backup), finalProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }
    }

    private fun CoroutineScope.restoreNovel(
        backupNovels: List<eu.kanade.tachiyomi.data.backup.models.BackupNovel>,
        backupCategories: List<BackupCategory>,
    ) = launch(dispatcher) {
        val sortedNovels = novelRestorer.sortByNew(backupNovels)
        sortedNovels.chunked(100).forEach { chunk ->
            ensureActive()
            chunk.forEach { novel ->
                try {
                    novelRestorer.restore(novel, backupCategories)
                } catch (e: Exception) {
                    val sourceName = sourceMapping[novel.source] ?: novel.source.toString()
                    errors.add(Date() to "${novel.title} [$sourceName]: ${e.message}")
                }
            }
            val currentProgress = restoreProgress.addAndGet(chunk.size)
            with(notifier) {
                showRestoreProgress(chunk.last().title, currentProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }

        val finalProgress = restoreProgress.get()
        if (finalProgress < restoreAmount) {
            with(notifier) {
                showRestoreProgress(context.stringResource(MR.strings.restoring_backup), finalProgress, restoreAmount, isSync)
                    .show(Notifications.ID_RESTORE_PROGRESS)
            }
        }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch(dispatcher) {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.app_settings),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch(dispatcher) {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        restoreProgress.incrementAndGet()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.source_settings),
                restoreProgress.get(),
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch(dispatcher) {
        backupExtensionStores
            .forEach {
                ensureActive()

                try {
                    extensionStoreRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Extension Store: ${it.name} : ${e.message}")
                }

                restoreProgress.incrementAndGet()
                with(notifier) {
                    showRestoreProgress(
                        context.stringResource(MR.strings.extensionStores),
                        restoreProgress.get(),
                        restoreAmount,
                        isSync,
                    )
                        // KMK -->
                        .show(Notifications.ID_RESTORE_PROGRESS)
                    // KMK <--
                }
            }
    }

    private suspend fun restoreJarExtensions(backupJars: List<BackupJarExtension>) {
        try {
            val extensionDir = File(context.filesDir, "jar_extensions")
            if (!extensionDir.exists()) {
                extensionDir.mkdirs()
            }
            val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
            val repoMap = uiPreferences.jarExtensionRepoMap().get().toMutableSet()

            for (backupJar in backupJars) {
                try {
                    val file = File(extensionDir, backupJar.filename)
                    file.writeBytes(backupJar.data)
                    backupJar.repoName?.let { repo ->
                        repoMap.removeAll { it.startsWith("${backupJar.filename}:") }
                        repoMap.add("${backupJar.filename}:$repo")
                    }
                } catch (e: Exception) {
                    errors.add(Date() to "Failed to restore jar ${backupJar.filename}: ${e.message}")
                }
            }
            uiPreferences.jarExtensionRepoMap().set(repoMap)
            eu.kanade.tachiyomi.extension.JarExtensionManager.initialize(context)

            restoreProgress.incrementAndGet()
            with(notifier) {
                showRestoreProgress(
                    context.stringResource(KMR.strings.sideloaded_extensions),
                    restoreProgress.get(),
                    restoreAmount,
                    isSync,
                ).show(Notifications.ID_RESTORE_PROGRESS)
            }
        } catch (e: Exception) {
            errors.add(Date() to "Failed to restore sideloaded extensions: ${e.message}")
        }
    }

    private suspend fun restoreWireguard(backupConfigs: List<BackupWireguardConfig>, backupPrefs: BackupWireguardPreferences?) {
        try {
            val vpnDir = File(context.filesDir, "wireguard")
            if (!vpnDir.exists()) {
                vpnDir.mkdirs()
            }
            for (config in backupConfigs) {
                try {
                    val file = File(vpnDir, config.filename)
                    file.writeText(config.data)
                } catch (e: Exception) {
                    errors.add(Date() to "Failed to restore Wireguard profile ${config.filename}: ${e.message}")
                }
            }

            if (backupPrefs != null) {
                val vpnPrefs = context.getSharedPreferences("wireguard_prefs", Context.MODE_PRIVATE)
                val editor = vpnPrefs.edit()
                backupPrefs.defaultProfile?.let { editor.putString("default_profile", it) }
                for (assoc in backupPrefs.sourceAssociations) {
                    editor.putString(assoc.key, assoc.value)
                }
                editor.apply()
            }

            restoreProgress.incrementAndGet()
            with(notifier) {
                showRestoreProgress(
                    context.stringResource(KMR.strings.vpn_settings),
                    restoreProgress.get(),
                    restoreAmount,
                    isSync,
                ).show(Notifications.ID_RESTORE_PROGRESS)
            }
        } catch (e: Exception) {
            errors.add(Date() to "Failed to restore Wireguard settings: ${e.message}")
        }
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("komikku_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
