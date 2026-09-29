package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.BackupRestoreStatus
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.translation.ColorizerManager
import eu.kanade.translation.SuperResolutionManager
import eu.kanade.translation.TranslationManager
import eu.kanade.translation.data.TranslationProvider
import exh.eh.EHentaiUpdateHelper
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.archive.CbzCrypto
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Manga_external_metadata
import tachiyomi.data.Mangas
import tachiyomi.data.Mini_history
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

// SY -->
private const val LEGACY_DATABASE_NAME = "tachiyomi.db"
// SY <--

class AppModule(val app: Application) : InjektModule {
    // SY -->
    private val securityPreferences: SecurityPreferences by injectLazy()
    // SY <--

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<android.content.Context>(app)
        addSingletonFactory { eu.kanade.tachiyomi.vpn.WireguardManager(app) }

        addSingletonFactory<SqlDriver> {
            // SY -->
            if (securityPreferences.encryptDatabase().get()) {
                System.loadLibrary("sqlcipher")
            }

            // SY <--
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = app,
                // SY -->
                name = if (securityPreferences.encryptDatabase().get()) {
                    CbzCrypto.DATABASE_NAME
                } else {
                    LEGACY_DATABASE_NAME
                },
                factory = if (securityPreferences.encryptDatabase().get()) {
                    SupportOpenHelperFactory(CbzCrypto.getDecryptedPasswordSql(), null, false, 25)
                } else if (isDebugBuildType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Support database inspector in Android Studio
                    FrameworkSQLiteOpenHelperFactory()
                } else {
                    RequerySQLiteOpenHelperFactory()
                },
                // SY <--
                callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        setPragma(db, "foreign_keys = ON")
                        setPragma(db, "journal_mode = WAL")
                        setPragma(db, "synchronous = NORMAL")
                    }
                    private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                        val cursor = db.query("PRAGMA $pragma")
                        cursor.moveToFirst()
                        cursor.close()
                    }
                },
            )
        }
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                ),
                manga_external_metadataAdapter = Manga_external_metadata.Adapter(
                    genresAdapter = StringListColumnAdapter,
                    tagsAdapter = StringListColumnAdapter,
                ),
                mini_historyAdapter = Mini_history.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
            )
        }
        addSingletonFactory<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

        // Novel Database
        val sqlDriverNovel = AndroidSqliteDriver(
            schema = tachiyomi.novel.data.NovelDatabase.Schema,
            context = app,
            name = "tachiyomi.noveldb",
            factory = if (isDebugBuildType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(tachiyomi.novel.data.NovelDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            tachiyomi.novel.data.NovelDatabase(
                driver = sqlDriverNovel,
                novel_historyAdapter = datanovel.Novel_history.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(
                    memoAdapter = tachiyomi.data.MemoColumnAdapter,
                ),
                novelsAdapter = datanovel.Novels.Adapter(
                    memoAdapter = tachiyomi.data.MemoColumnAdapter,
                    genreAdapter = StringListColumnAdapter,
                    custom_genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                ),
            )
        }
        addSingletonFactory<tachiyomi.data.handlers.novel.NovelDatabaseHandler> {
            tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler(
                get(),
                sqlDriverNovel,
            )
        }

        // Anime Database
        val sqlDriverAnime = AndroidSqliteDriver(
            schema = tachiyomi.mi.data.AnimeDatabase.Schema,
            context = app,
            name = "tachiyomi.animedb",
            factory = if (isDebugBuildType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(tachiyomi.mi.data.AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            tachiyomi.mi.data.AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = dataanime.Animehistory.Adapter(
                    last_seenAdapter = tachiyomi.data.DateColumnAdapter,
                ),
                episodesAdapter = dataanime.Episodes.Adapter(memoAdapter = tachiyomi.data.MemoColumnAdapter),
                animesAdapter = dataanime.Animes.Adapter(
                    memoAdapter = tachiyomi.data.MemoColumnAdapter,
                    genreAdapter = tachiyomi.data.StringListColumnAdapter,
                    custom_genreAdapter = tachiyomi.data.StringListColumnAdapter,
                    update_strategyAdapter = tachiyomi.data.AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = tachiyomi.data.FetchTypeColumnAdapter,
                ),
            )
        }
        addSingletonFactory<tachiyomi.data.handlers.anime.AnimeDatabaseHandler> {
            tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler(
                get(),
                sqlDriverAnime,
            )
        }



        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { UniFileTempFileManager(app) }

        addSingletonFactory { ChapterCache(app, get(), get()) }
        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get(), isDebugBuildType) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.anischedule.AniScheduleApi(get()) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.AnimeCoverCache(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache(app) }
        addSingletonFactory<tachiyomi.domain.source.anime.service.AnimeSourceManager> {
            eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager(app, get(), get())
        }
        addSingletonFactory { eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache(app) }
        addSingletonFactory { eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore(app) }
        addSingletonFactory { eu.kanade.domain.track.novel.store.DelayedNovelTrackingStore(app) }
        addSingletonFactory { tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { tachiyomi.source.local.image.anime.LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager(app, get()) }
        addSingletonFactory { tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager(app, get()) }

        // Player preferences & Subtitle Translation services
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationDiskCache(java.io.File(app.cacheDir, "subtitles")) }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.player.subtitle.translation.GoogleSubtitleTranslationProvider(get())
        }
        addSingletonFactory<eu.kanade.tachiyomi.ui.player.subtitle.translation.AiSubtitleTranslationClient> {
            eu.kanade.tachiyomi.ui.player.subtitle.translation.NovelReaderAiSubtitleTranslationClient(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.player.subtitle.translation.AiSubtitleTranslationProvider(get())
        }
        addSingletonFactory {
            val providers = mapOf(
                eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProviderId.Google to get<eu.kanade.tachiyomi.ui.player.subtitle.translation.GoogleSubtitleTranslationProvider>(),
                eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationProviderId.Ai to get<eu.kanade.tachiyomi.ui.player.subtitle.translation.AiSubtitleTranslationProvider>(),
            )
            eu.kanade.tachiyomi.ui.player.subtitle.translation.SubtitleTranslationCoordinator(
                providers = providers,
                cache = get(),
            )
        }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.Anime4KManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.utils.TrackSelect(get(), get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.aniskip.AniSkipApi() }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.aniskip.SubtitleSyncCoordinator(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.AudioPreferences(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.GesturePreferences(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences(get()) }

        addSingletonFactory { tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem(get()) }
        addSingletonFactory { tachiyomi.source.local.image.novel.LocalNovelCoverManager(app, get()) }

        // Novel Extension Manager & Runtime Wiring
        addSingletonFactory {
            tachiyomi.data.extension.novel.NovelPluginStorage(java.io.File(app.filesDir, "novel_plugins"))
        }
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage> {
            eu.kanade.tachiyomi.extension.novel.repo.InMemoryNovelPluginStorage()
        }
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginKeyValueStore> {
            tachiyomi.data.extension.novel.AndroidNovelPluginKeyValueStore(app)
        }
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexFetcher> {
            eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcher(get<NetworkHelper>().client)
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexParser(get())
        }
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade> {
            val novelExtensionStoreRepository: mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository = get()
            val repoProvider = object : eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProvider {
                override suspend fun getAll(): List<mihon.domain.extensionrepo.model.ExtensionRepo> {
                    return novelExtensionStoreRepository.getAll().map { store ->
                        mihon.domain.extensionrepo.model.ExtensionRepo(
                            baseUrl = store.indexUrl,
                            name = store.name,
                            shortName = store.name,
                            website = store.contact.website,
                            signingKeyFingerprint = store.signingKey,
                        )
                    }
                }
            }
            eu.kanade.tachiyomi.extension.novel.api.NovelPluginApi(
                repoProvider = repoProvider,
                fetcher = get(),
                parser = get(),
            )
        }
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginDownloader> {
            tachiyomi.data.extension.novel.NetworkNovelPluginDownloader(get<NetworkHelper>().client)
        }
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginInstallerFacade> {
            tachiyomi.data.extension.novel.NovelPluginInstaller(
                downloader = get(),
                repository = get(),
                storage = get(),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory(
                context = app,
                networkHelper = get(),
                keyValueStore = get(),
                json = get(),
                domainAliasResolver = eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolver(
                    eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides(),
                ),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings(get())
        }
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory> {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSourceFactory(
                runtimeFactory = get(),
                pluginStorage = get(),
                json = get(),
                runtimeOverrides = eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides(),
                keyValueStore = get(),
                assetBindings = get(),
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionInstaller(
                context = app,
                client = get<NetworkHelper>().client,
                basePreferences = get(),
            )
        }
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager> {
            eu.kanade.tachiyomi.extension.novel.DefaultNovelExtensionManager(
                context = app,
                repository = get(),
                api = get(),
                installer = get(),
                sourceFactory = get(),
                kotlinInstaller = get(),
                trustExtension = get(),
            )
        }

        addSingletonFactory<tachiyomi.domain.source.novel.service.NovelSourceManager> {
            eu.kanade.tachiyomi.source.novel.AndroidNovelSourceManager(app, get(), get())
        }
        addSingletonFactory { eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache() }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.NovelCoverCache(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.cache.SeriesCoverCache(app) }

        // Novel AI & Cloud Translation Services
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptResolver(app) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver(app) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver(app) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService(get<NetworkHelper>().client) }
        addSingletonFactory {
            eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService(
                client = get<NetworkHelper>().client,
                json = get(),
                promptResolver = get(),
            )
        }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService(get<NetworkHelper>().client, get()) }
        addSingletonFactory {
            val deepSeekResolver = get<eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekPromptResolver>()
            eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService(
                client = get<NetworkHelper>().client,
                json = get(),
                resolveSystemPrompt = { mode, family -> deepSeekResolver.resolveSystemPrompt(mode, family) },
            )
        }
        addSingletonFactory {
            val mistralResolver = get<eu.kanade.tachiyomi.ui.reader.novel.translation.MistralPromptResolver>()
            eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService(
                client = get<NetworkHelper>().client,
                json = get(),
                resolveSystemPrompt = { mode, family -> mistralResolver.resolveSystemPrompt(mode, family) },
            )
        }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.MistralModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceModelsService(get<NetworkHelper>().client, get()) }
        addSingletonFactory { eu.kanade.tachiyomi.data.translation.TranslationQueueManager(get(), get()) }

        addSingletonFactory { aniyomi.core.common.torrent.TorrentServerApi(get(), get()) }
        addSingletonFactory { aniyomi.core.common.torrent.TorrentServerUtils(get(), get()) }

        addSingletonFactory { StorageManager(app, get()) }

        // SY -->
        addSingletonFactory { EHentaiUpdateHelper(app) }

        addSingletonFactory { PagePreviewCache(app) }
        // SY <--

        // KMK -->
        addSingletonFactory { BackupRestoreStatus() }
        addSingletonFactory { SyncStatus() }
        addSingletonFactory { LibraryUpdateStatus() }
        addSingletonFactory { TranslationProvider(app) }
        addSingletonFactory { TranslationManager(app) }
        addSingletonFactory { ColorizerManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.ai.MangaColorizeEngine() }
        addSingletonFactory { SuperResolutionManager(app) }
        addSingletonFactory { eu.kanade.tachiyomi.data.favorite.FavoriteManager(app) }
        // KMK <--

        // AM (CONNECTIONS) -->
        addSingletonFactory { ConnectionsManager() }
        // <-- AM (CONNECTIONS)

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()

            // SY -->
            get<GetCustomMangaInfo>()
            // SY <--
        }

        addSingletonFactory { GoogleDriveService(app) }
    }
}
