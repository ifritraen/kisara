package eu.kanade.tachiyomi.ui.track.matcher

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class TrackerSourceMatcher(
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) {
    private val searchEngine = SmartSourceSearchEngine(null)

    sealed class MatchResult {
        data class Success(
            val source: CatalogueSource,
            val manga: Manga,
            val chapters: List<Chapter>,
            val sourceId: Long,
        ) : MatchResult()

        data object NoMatch : MatchResult()
        data class Error(val error: Throwable) : MatchResult()
    }

    suspend fun matchAndFetch(
        title: String,
        sourceIds: List<Long>,
    ): MatchResult = withContext(Dispatchers.IO) {
        if (title.isBlank() || sourceIds.isEmpty()) {
            return@withContext MatchResult.NoMatch
        }

        for (sourceId in sourceIds) {
            val source = sourceManager.get(sourceId) as? CatalogueSource ?: continue
            try {
                val matchedManga = searchEngine.deepSearch(source, title)
                if (matchedManga != null) {
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
                            logcat(logcat.LogPriority.WARN, e) { "Failed to sync chapters" }
                        }
                    }

                    val chapters = getChaptersByMangaId.await(localManga.id)
                    return@withContext MatchResult.Success(
                        source = source,
                        manga = localManga,
                        chapters = chapters,
                        sourceId = sourceId,
                    )
                }
            } catch (e: Exception) {
                logcat(logcat.LogPriority.WARN, e) { "Error searching source ${source.name} for $title" }
                // Cascade down to next source
            }
        }

        MatchResult.NoMatch
    }

    suspend fun fetchFromSpecificSource(
        sourceId: Long,
        title: String,
    ): MatchResult = withContext(Dispatchers.IO) {
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: return@withContext MatchResult.Error(IllegalArgumentException("Source not found: $sourceId"))

        try {
            val matchedManga = searchEngine.deepSearch(source, title)
                ?: return@withContext MatchResult.NoMatch

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
                    logcat(logcat.LogPriority.WARN, e) { "Failed to sync chapters" }
                }
            }

            val chapters = getChaptersByMangaId.await(localManga.id)
            MatchResult.Success(
                source = source,
                manga = localManga,
                chapters = chapters,
                sourceId = sourceId,
            )
        } catch (e: Exception) {
            logcat(logcat.LogPriority.ERROR, e) { "Error fetching from source ${source.name}" }
            MatchResult.Error(e)
        }
    }
}
// KMK <--
