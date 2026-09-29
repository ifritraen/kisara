package eu.kanade.tachiyomi.ui.track.matcher

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.browse.novel.migration.list.search.SmartNovelSourceSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackerSourceItem(
    val id: Long,
    val name: String,
    val lang: String = "",
)

data class TrackerMediaChapterItem(
    val id: Long,
    val name: String,
    val dateUpload: Long,
    val readOrWatched: Boolean,
    val scanlatorOrStudio: String?,
    val number: Double,
    val sourceOrder: Long = 0L,
)

// KMK -->
class TrackerSourceMatcher(
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getNovelChapters: GetNovelChapters = Injekt.get(),
) {
    private val mangaSearchEngine = SmartSourceSearchEngine(null)
    private val animeSearchEngine = SmartAnimeSourceSearchEngine(null, networkToLocalAnime)
    private val novelSearchEngine = SmartNovelSourceSearchEngine(null, networkToLocalNovel)

    sealed class MatchResult {
        data class Success(
            val sourceId: Long,
            val sourceName: String,
            val entryId: Long,
            val isFavorite: Boolean,
            val items: List<TrackerMediaChapterItem>,
        ) : MatchResult()

        data object NoMatch : MatchResult()
        data class Error(val error: Throwable) : MatchResult()
    }

    suspend fun matchAndFetch(
        title: String,
        sourceIds: List<Long>,
        mediaType: MediaType,
    ): MatchResult = withContext(Dispatchers.IO) {
        if (title.isBlank() || sourceIds.isEmpty()) {
            return@withContext MatchResult.NoMatch
        }

        for (sourceId in sourceIds) {
            val result = fetchFromSpecificSource(sourceId, title, mediaType)
            if (result is MatchResult.Success) {
                return@withContext result
            }
        }

        MatchResult.NoMatch
    }

    suspend fun fetchFromSpecificSource(
        sourceId: Long,
        title: String,
        mediaType: MediaType,
    ): MatchResult = withContext(Dispatchers.IO) {
        try {
            when (mediaType) {
                MediaType.ANIME -> fetchAnime(sourceId, title)
                MediaType.NOVEL -> fetchNovel(sourceId, title)
                MediaType.MANGA -> fetchManga(sourceId, title)
            }
        } catch (e: Exception) {
            logcat(logcat.LogPriority.ERROR, e) { "Error fetching from source $sourceId ($mediaType)" }
            MatchResult.Error(e)
        }
    }

    private suspend fun fetchManga(sourceId: Long, title: String): MatchResult {
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: return MatchResult.Error(IllegalArgumentException("Manga source not found: $sourceId"))

        val matchedManga = mangaSearchEngine.deepSearch(source, title)
            ?: return MatchResult.NoMatch

        val localManga = networkToLocalManga(matchedManga)
        val sManga = localManga.toSManga()

        val rawChapters: List<SChapter> = try {
            source.getChapterList(sManga)
        } catch (e: Exception) {
            logcat(logcat.LogPriority.WARN, e) { "Failed to get chapter list for ${localManga.title} from ${source.name}" }
            emptyList()
        }

        if (rawChapters.isNotEmpty()) {
            try {
                syncChaptersWithSource.await(rawChapters, localManga, source)
            } catch (e: Exception) {
                logcat(logcat.LogPriority.WARN, e) { "Failed to sync manga chapters" }
            }
        }

        val chapters = getChaptersByMangaId.await(localManga.id)
        val items = chapters.map {
            TrackerMediaChapterItem(
                id = it.id,
                name = it.name,
                dateUpload = it.dateUpload,
                readOrWatched = it.read,
                scanlatorOrStudio = it.scanlator,
                number = it.chapterNumber,
                sourceOrder = it.sourceOrder,
            )
        }

        return MatchResult.Success(
            sourceId = sourceId,
            sourceName = source.name,
            entryId = localManga.id,
            isFavorite = localManga.favorite,
            items = items,
        )
    }

    private suspend fun fetchAnime(sourceId: Long, title: String): MatchResult {
        val source = animeSourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: return MatchResult.Error(IllegalArgumentException("Anime source not found: $sourceId"))

        val matchedAnime = animeSearchEngine.deepSearch(source, title)
            ?: return MatchResult.NoMatch

        val localAnime = networkToLocalAnime.await(matchedAnime)
        val sAnime = localAnime.toSAnime()

        val rawEpisodes: List<SEpisode> = try {
            source.getEpisodeList(sAnime)
        } catch (e: Exception) {
            logcat(logcat.LogPriority.WARN, e) { "Failed to get episode list for ${localAnime.title} from ${source.name}" }
            emptyList()
        }

        if (rawEpisodes.isNotEmpty()) {
            try {
                syncEpisodesWithSource.await(rawEpisodes, localAnime, source)
            } catch (e: Exception) {
                logcat(logcat.LogPriority.WARN, e) { "Failed to sync anime episodes" }
            }
        }

        val episodes = getEpisodesByAnimeId.await(localAnime.id)
        val items = episodes.map {
            TrackerMediaChapterItem(
                id = it.id,
                name = it.name,
                dateUpload = it.dateUpload,
                readOrWatched = it.seen,
                scanlatorOrStudio = it.scanlator,
                number = it.episodeNumber,
                sourceOrder = it.sourceOrder,
            )
        }

        return MatchResult.Success(
            sourceId = sourceId,
            sourceName = source.name,
            entryId = localAnime.id,
            isFavorite = localAnime.favorite,
            items = items,
        )
    }

    private suspend fun fetchNovel(sourceId: Long, title: String): MatchResult {
        val source = novelSourceManager.get(sourceId) as? NovelCatalogueSource
            ?: return MatchResult.Error(IllegalArgumentException("Novel source not found: $sourceId"))

        val matchedNovel = novelSearchEngine.deepSearch(source, title)
            ?: return MatchResult.NoMatch

        val localNovel = networkToLocalNovel.await(matchedNovel)
        val sNovel = localNovel.toSNovel()

        val rawChapters: List<SNovelChapter> = try {
            source.getChapterList(sNovel)
        } catch (e: Exception) {
            logcat(logcat.LogPriority.WARN, e) { "Failed to get chapter list for ${localNovel.title} from ${source.name}" }
            emptyList()
        }

        if (rawChapters.isNotEmpty()) {
            try {
                syncNovelChaptersWithSource.await(rawChapters, localNovel, source)
            } catch (e: Exception) {
                logcat(logcat.LogPriority.WARN, e) { "Failed to sync novel chapters" }
            }
        }

        val chapters = getNovelChapters.await(localNovel.id)
        val items = chapters.map {
            TrackerMediaChapterItem(
                id = it.id,
                name = it.name,
                dateUpload = it.dateUpload,
                readOrWatched = it.read,
                scanlatorOrStudio = it.scanlator,
                number = it.chapterNumber,
                sourceOrder = it.sourceOrder,
            )
        }

        return MatchResult.Success(
            sourceId = sourceId,
            sourceName = source.name,
            entryId = localNovel.id,
            isFavorite = localNovel.favorite,
            items = items,
        )
    }
}
// KMK <--
