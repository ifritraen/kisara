package eu.kanade.domain

import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.interactor.TrackChapter
import mihon.data.extension.repository.ExtensionStoreRepositoryImpl
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.SourceRepositoryImpl
import tachiyomi.data.source.StubSourceRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.interactor.UpdateCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryManga
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.repository.UpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<CategoryRepository> { CategoryRepositoryImpl(get()) }
        addFactory { GetCategories(get()) }
        addFactory { ResetCategoryFlags(get(), get()) }
        addFactory { SetDisplayMode(get()) }
        addFactory { SetSortModeForCategory(get(), get()) }
        addFactory { CreateCategoryWithName(get(), get()) }
        addFactory { RenameCategory(get()) }
        addFactory { ReorderCategory(get()) }
        addFactory { UpdateCategory(get()) }
        addFactory { DeleteCategory(get(), get(), get()) }
        // KMK -->
        addFactory { HideCategory(get()) }
        // KMK <--

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get(), get()) }
        addFactory { GetUpcomingManga(get()) }
        addFactory { ResetViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { FetchInterval(get()) }
        addFactory { SetMangaDefaultChapterFlags(get(), get(), get()) }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { UpdateMangaFromRemote(get(), get(), get(), get(), get(), get(), get()) }
        addFactory { UpdateMangaNotes(get()) }
        addFactory { SetMangaCategories(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }
        addFactory {
            MigrateMangaUseCase(
                get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            )
        }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addSingletonFactory<TrackRepository> { TrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddTracks(get(), get(), get(), get()) }
        addFactory { RefreshTracks(get(), get(), get(), get()) }
        addFactory { DeleteTrack(get()) }
        addFactory { GetTracksPerManga(get(), get()) }
        addFactory { GetTracks(get()) }
        addFactory { InsertTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }
        addSingletonFactory { eu.kanade.domain.track.service.ResolveTrackProgressSync() }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetBookmarkedChaptersByMangaId(get(), get(), get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }
        addFactory { FilterChaptersForDownload(get(), get(), get(), get()) }

        addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
        addFactory { GetHistory(get()) }
        addFactory { UpsertHistory(get()) }
        addFactory { RemoveHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteDownload(get(), get()) }

        addFactory { GetExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetExtensionLanguages(get(), get()) }

        addSingletonFactory<UpdatesRepository> { UpdatesRepositoryImpl(get()) }
        addFactory { GetUpdates(get()) }

        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get(), get()) }
        addSingletonFactory<StubSourceRepository> { StubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledSources(get(), get()) }
        addFactory { GetLanguagesWithSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleSource(get()) }
        addFactory { ToggleSourcePin(get()) }
        addFactory { TrustExtension(get(), get()) }

        addSingletonFactory { ExtensionStoreService(get(), get(), get()) }
        addSingletonFactory<ExtensionStoreRepository> { ExtensionStoreRepositoryImpl(get(), get()) }
        addFactory { AddExtensionStore(get()) }
        addFactory { GetExtensionStoreCountAsFlow(get()) }
        addFactory { GetExtensionStores(get()) }
        addFactory { RemoveExtensionStore(get()) }
        addFactory { UpdateExtensionStores(get()) }
        addFactory { ToggleIncognito(get()) }
        addFactory { GetIncognitoState(get(), get(), get()) }

        // KMK -->
        addSingletonFactory<tachiyomi.domain.suggestions.repository.SuggestionRepository> {
            tachiyomi.data.suggestions.SuggestionRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.suggestions.interactor.GetSuggestions(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ClearSuggestions(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ReplaceSuggestions(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.GetSuggestionTags(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.GetSuggestionSources(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ModifySuggestionTag(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ModifySuggestionSource(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.GetSuggestionAuthors(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.GetSuggestionArtists(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ModifySuggestionAuthor(get()) }
        addFactory { tachiyomi.domain.suggestions.interactor.ModifySuggestionArtist(get()) }
        addSingletonFactory { eu.kanade.domain.entries.interactor.GetEntrySimilarTitles(get(), get(), get()) }
        // KMK <--

        // Novel Domain Wiring -->
        addSingletonFactory<tachiyomi.domain.category.novel.repository.NovelCategoryRepository> {
            tachiyomi.data.category.novel.NovelCategoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.category.novel.interactor.GetNovelCategories(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.ResetNovelCategoryFlags(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName(get(), get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.RenameNovelCategory(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.ReorderNovelCategory(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.UpdateNovelCategory(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.HideNovelCategory(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.DeleteNovelCategory(get(), get(), get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.SetNovelCategories(get()) }
        addFactory { tachiyomi.domain.category.novel.interactor.UpdateNovelCategoryFlags(get()) }

        addSingletonFactory<tachiyomi.domain.series.novel.repository.NovelSeriesRepository> {
            tachiyomi.data.series.novel.NovelSeriesRepositoryImpl(get(), get())
        }
        addFactory { tachiyomi.domain.series.novel.interactor.CreateNovelSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.DeleteNovelSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries(get(), get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.GetLibraryNovelSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.AddNovelsToSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.RemoveNovelFromSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.ReorderSeriesEntries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.ResolveNovelSeriesCover(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.UpdateNovelSeries(get()) }
        addFactory { tachiyomi.domain.series.novel.interactor.GetNovelIdsInAnySeries(get()) }

        addSingletonFactory<tachiyomi.domain.book.novel.repository.NovelBookStateRepository> {
            tachiyomi.data.book.novel.NovelBookStateRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.book.novel.interactor.GetNovelBookState(get()) }
        addFactory { tachiyomi.domain.book.novel.interactor.UpsertNovelBookState(get()) }
        addFactory { tachiyomi.domain.book.novel.interactor.SetNovelBookEnabled(get()) }
        addFactory { tachiyomi.domain.book.novel.interactor.SetNovelBookProgress(get()) }
        addFactory { tachiyomi.domain.book.novel.interactor.DeleteNovelBookState(get()) }

        addSingletonFactory<tachiyomi.domain.entries.novel.repository.NovelRepository> {
            tachiyomi.data.entries.novel.NovelRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetDuplicateLibraryNovel(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetNovel(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetNovelFavorites(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetLibraryNovel(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters(get(), get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.ResetNovelViewerFlags(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.NovelFetchInterval(get()) }
        addFactory { eu.kanade.domain.entries.novel.interactor.UpdateNovel(get(), get()) }
        addFactory { eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators(get()) }
        addFactory { eu.kanade.domain.entries.novel.interactor.SetNovelExcludedScanlators(get()) }

        addSingletonFactory<tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository> {
            tachiyomi.data.items.novelchapter.NovelChapterRepositoryImpl(get())
        }
        addSingletonFactory { tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters(get()) }
        addFactory { tachiyomi.domain.items.novelchapter.interactor.SetNovelDefaultChapterFlags(get(), get(), get()) }
        addFactory { tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter() }
        addFactory { eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.items.novelchapter.interactor.GetAvailableNovelScanlators(get()) }
        addFactory { eu.kanade.domain.items.novelchapter.interactor.GetNovelScanlatorChapterCounts(get()) }

        addSingletonFactory<tachiyomi.domain.history.novel.repository.NovelHistoryRepository> {
            tachiyomi.data.history.novel.NovelHistoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.history.novel.interactor.GetTotalNovelReadDuration(get()) }

        addSingletonFactory<tachiyomi.domain.updates.novel.repository.NovelUpdatesRepository> {
            tachiyomi.data.updates.novel.NovelUpdatesRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.updates.novel.interactor.GetNovelUpdates(get()) }

        addSingletonFactory<tachiyomi.domain.source.novel.repository.NovelSourceRepository> {
            tachiyomi.data.source.novel.NovelSourceRepositoryImpl(get(), get())
        }
        addSingletonFactory<tachiyomi.domain.source.novel.repository.NovelStubSourceRepository> {
            tachiyomi.data.source.novel.NovelStubSourceRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.source.novel.resolver.repository.OmniRuleRepository> {
            tachiyomi.data.source.novel.OmniRuleRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.source.novel.interactor.GetNovelSourcesWithNonLibraryNovels(get()) }
        addFactory { tachiyomi.domain.source.novel.interactor.GetRemoteNovel(get()) }
        addFactory {
            val preferences: eu.kanade.domain.source.service.SourcePreferences = get()
            eu.kanade.domain.source.novel.interactor.GetNovelSourcesWithFavoriteCount(get(), preferences)
        }
        addFactory {
            val preferences: eu.kanade.domain.source.service.SourcePreferences = get()
            eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources(
                repository = get(),
                preferences = preferences,
            )
        }
        addFactory {
            val preferences: eu.kanade.domain.source.service.SourcePreferences = get()
            eu.kanade.domain.source.novel.interactor.GetLanguagesWithNovelSources(
                repository = get(),
                preferences = preferences,
            )
        }
        addFactory { eu.kanade.domain.source.novel.interactor.ToggleNovelSource(get()) }
        addFactory { eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin(get()) }
        addFactory { eu.kanade.domain.source.novel.interactor.ToggleNovelIncognito(get()) }
        addFactory { eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState(get(), get(), get()) }

        addSingletonFactory<tachiyomi.domain.track.novel.repository.NovelTrackRepository> {
            tachiyomi.data.track.novel.NovelTrackRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.track.novel.interactor.DeleteNovelTrack(get()) }
        addFactory { tachiyomi.domain.track.novel.interactor.GetTracksPerNovel(get()) }
        addFactory { tachiyomi.domain.track.novel.interactor.GetNovelTracks(get()) }
        addFactory { tachiyomi.domain.track.novel.interactor.InsertNovelTrack(get()) }
        addFactory { eu.kanade.domain.track.novel.interactor.AddNovelTracks(get(), get(), get()) }
        addFactory { eu.kanade.domain.track.novel.interactor.TrackNovelChapter(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.novel.interactor.SyncNovelChapterProgressWithTrack(get(), get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.novel.interactor.RefreshNovelTracks(get(), get(), get(), get()) }
        addSingletonFactory<tachiyomi.domain.extension.novel.repository.NovelPluginRepository> {
            tachiyomi.data.extension.novel.NovelPluginRepositoryImpl(get())
        }
        addSingletonFactory<mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository> {
            mihon.data.repository.novel.NovelExtensionStoreRepositoryImpl(get(), get(), get())
        }
        addFactory { eu.kanade.domain.extension.novel.interactor.GetNovelExtensionLanguages(get(), get()) }
        addFactory { eu.kanade.domain.extension.novel.interactor.GetNovelExtensionSources(get(), get()) }
        addFactory { eu.kanade.domain.extension.novel.interactor.TrustNovelExtension(get(), get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepoCount(get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo(get()) }
        addFactory { eu.kanade.domain.entries.novel.interactor.MigrateNovelUseCase() }
        // Novel Domain Wiring <--

        // Anime Domain Wiring -->
        addSingletonFactory<tachiyomi.domain.category.anime.repository.AnimeCategoryRepository> {
            tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.category.anime.interactor.GetAnimeCategories(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.RenameAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.HideAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory(get(), get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetAnimeCategories(get()) }

        addSingletonFactory<tachiyomi.domain.entries.anime.repository.AnimeRepository> {
            tachiyomi.data.entries.anime.AnimeRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetLibraryAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons(get(), get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnime(get()) }
        addFactory { tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId(get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.GetNextEpisodes(get(), get(), get()) }
        addFactory { mihon.domain.upcoming.anime.interactor.GetUpcomingAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags(get(), get(), get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime(get(), get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.UpdateAnime(get(), get()) }
        addFactory { tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason() }
        addFactory { eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource(get(), get(), get(), get(), get()) }

        addSingletonFactory<tachiyomi.domain.items.episode.repository.EpisodeRepository> {
            tachiyomi.data.items.episode.EpisodeRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisode(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.UpdateEpisode(get()) }
        addFactory { eu.kanade.domain.items.episode.interactor.SetSeenStatus(get(), get(), get(), get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode() }
        addFactory { eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource(get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { mihon.domain.items.episode.interactor.FilterEpisodesForDownload(get(), get(), get()) }
        addFactory { eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload(get(), get()) }

        addSingletonFactory<tachiyomi.domain.history.anime.repository.AnimeHistoryRepository> {
            tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.history.anime.interactor.GetAnimeHistory(get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory(get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory(get()) }

        addSingletonFactory<tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository> {
            tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates(get()) }

        addSingletonFactory<tachiyomi.domain.source.anime.repository.AnimeSourceRepository> {
            tachiyomi.data.source.anime.AnimeSourceRepositoryImpl(get(), get())
        }
        addSingletonFactory<tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository> {
            tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime(get()) }
        addFactory { tachiyomi.domain.source.anime.interactor.GetRemoteAnime(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount(get(), get()) }
        addFactory {
            val preferences: eu.kanade.domain.source.service.SourcePreferences = get()
            eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources(
                repository = get(),
                preferences = preferences,
            )
        }
        addFactory {
            val preferences: eu.kanade.domain.source.service.SourcePreferences = get()
            eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources(
                repository = get(),
                preferences = preferences,
            )
        }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeSource(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState(get(), get(), get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType(get(), get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources(get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages(get(), get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension(get(), get()) }

        addSingletonFactory<tachiyomi.domain.track.anime.repository.AnimeTrackRepository> {
            tachiyomi.data.track.anime.AnimeTrackRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.GetTracksPerAnime(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.GetAnimeTracks(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.InsertAnimeTrack(get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.TrackEpisode(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack(get(), get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks(get(), get(), get(), get()) }

        addSingletonFactory<mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository> {
            mihon.data.repository.anime.AnimeExtensionStoreRepositoryImpl(get(), get(), get())
        }
        addFactory { mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount(get()) }
        addFactory { mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo(get()) }
        addFactory { mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo(get()) }

        addSingletonFactory<tachiyomi.domain.custombuttons.repository.CustomButtonRepository> {
            tachiyomi.data.custombutton.CustomButtonRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.custombuttons.interactor.CreateCustomButton(get()) }
        addFactory { tachiyomi.domain.custombuttons.interactor.DeleteCustomButton(get()) }
        addFactory { tachiyomi.domain.custombuttons.interactor.GetCustomButtons(get()) }
        addFactory { tachiyomi.domain.custombuttons.interactor.ReorderCustomButton(get()) }
        addFactory { tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton(get()) }
        addFactory { tachiyomi.domain.custombuttons.interactor.UpdateCustomButton(get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase() }
        // Anime Domain Wiring <--
    }
}
