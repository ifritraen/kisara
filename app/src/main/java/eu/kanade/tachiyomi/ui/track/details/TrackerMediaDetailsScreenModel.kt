package eu.kanade.tachiyomi.ui.track.details

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.track.TrackSeriesItem
import eu.kanade.tachiyomi.ui.track.matcher.TrackerMediaChapterItem
import eu.kanade.tachiyomi.ui.track.matcher.TrackerSourceItem
import eu.kanade.tachiyomi.ui.track.matcher.TrackerSourceMatcher
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
data class TrackerMediaDetailsState(
    val isMatching: Boolean = true,
    val activeSourceId: Long? = null,
    val activeSourceName: String? = null,
    val matchedEntryId: Long? = null,
    val chapters: List<TrackerMediaChapterItem> = emptyList(),
    val inLibrary: Boolean = false,
    val matchError: String? = null,
    val prioritizedSourceIds: List<Long> = emptyList(),
    val allInstalledSources: List<TrackerSourceItem> = emptyList(),
    val chapterSearchQuery: String = "",
    val isSortAscending: Boolean = false,
) {
    val filteredChapters: List<TrackerMediaChapterItem>
        get() {
            var list = chapters
            if (chapterSearchQuery.isNotBlank()) {
                val q = chapterSearchQuery.trim().lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) || it.number.toString().contains(q)
                }
            }
            return if (isSortAscending) {
                list.sortedBy { it.number }
            } else {
                list.sortedByDescending { it.number }
            }
        }
}

class TrackerMediaDetailsScreenModel(
    val series: TrackSeriesItem,
    val mediaType: MediaType,
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
) : StateScreenModel<TrackerMediaDetailsState>(TrackerMediaDetailsState()) {

    private val matcher = TrackerSourceMatcher(
        sourceManager = sourceManager,
        animeSourceManager = animeSourceManager,
        novelSourceManager = novelSourceManager,
    )

    init {
        loadSourcesAndMatch()
    }

    private fun getStoredPriorityIds(): List<Long> {
        val prefString = when (mediaType) {
            MediaType.ANIME -> uiPreferences.trackerPrioritizedAnimeSources().get()
            MediaType.NOVEL -> uiPreferences.trackerPrioritizedNovelSources().get()
            MediaType.MANGA -> uiPreferences.trackerPrioritizedMangaSources().get()
        }
        return if (prefString.isNotBlank()) {
            prefString.split(",").mapNotNull { it.trim().toLongOrNull() }
        } else {
            emptyList()
        }
    }

    private fun savePriorityIds(ids: List<Long>) {
        val str = ids.joinToString(",")
        when (mediaType) {
            MediaType.ANIME -> uiPreferences.trackerPrioritizedAnimeSources().set(str)
            MediaType.NOVEL -> uiPreferences.trackerPrioritizedNovelSources().set(str)
            MediaType.MANGA -> uiPreferences.trackerPrioritizedMangaSources().set(str)
        }
    }

    fun loadSourcesAndMatch() {
        screenModelScope.launchIO {
            val installedSources: List<TrackerSourceItem> = when (mediaType) {
                MediaType.ANIME -> animeSourceManager.getCatalogueSources().map {
                    TrackerSourceItem(it.id, it.name, it.lang)
                }
                MediaType.NOVEL -> novelSourceManager.getCatalogueSources().map {
                    TrackerSourceItem(it.id, it.name, it.lang)
                }
                MediaType.MANGA -> sourceManager.getOnlineSources()
                    .filterIsInstance<CatalogueSource>()
                    .map { TrackerSourceItem(it.id, it.name, it.lang) }
            }

            val storedIds = getStoredPriorityIds()
            val priorityIds = if (storedIds.isNotEmpty()) {
                storedIds.filter { id -> installedSources.any { it.id == id } }
            } else {
                installedSources.take(5).map { it.id }
            }

            mutableState.update {
                it.copy(
                    isMatching = true,
                    prioritizedSourceIds = priorityIds,
                    allInstalledSources = installedSources,
                    matchError = null,
                )
            }

            val result = matcher.matchAndFetch(series.title, priorityIds, mediaType)
            when (result) {
                is TrackerSourceMatcher.MatchResult.Success -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = result.sourceId,
                            activeSourceName = result.sourceName,
                            matchedEntryId = result.entryId,
                            chapters = result.items,
                            inLibrary = result.isFavorite,
                            matchError = null,
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.NoMatch -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = null,
                            activeSourceName = null,
                            matchedEntryId = null,
                            chapters = emptyList(),
                            matchError = "No match found in prioritized sources.",
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.Error -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = null,
                            activeSourceName = null,
                            matchedEntryId = null,
                            chapters = emptyList(),
                            matchError = result.error.message ?: "Failed to match source",
                        )
                    }
                }
            }
        }
    }

    fun selectSpecificSource(sourceId: Long) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isMatching = true, matchError = null) }
            val result = matcher.fetchFromSpecificSource(sourceId, series.title, mediaType)
            when (result) {
                is TrackerSourceMatcher.MatchResult.Success -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = result.sourceId,
                            activeSourceName = result.sourceName,
                            matchedEntryId = result.entryId,
                            chapters = result.items,
                            inLibrary = result.isFavorite,
                            matchError = null,
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.NoMatch -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = null,
                            activeSourceName = null,
                            matchedEntryId = null,
                            chapters = emptyList(),
                            matchError = "Series not found on this source.",
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.Error -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSourceId = null,
                            activeSourceName = null,
                            matchedEntryId = null,
                            chapters = emptyList(),
                            matchError = result.error.message ?: "Error fetching from source",
                        )
                    }
                }
            }
        }
    }

    fun updatePriorityOrder(newOrder: List<Long>) {
        savePriorityIds(newOrder)
        mutableState.update { it.copy(prioritizedSourceIds = newOrder) }
        // Re-run matching from top source
        loadSourcesAndMatch()
    }

    fun setChapterSearchQuery(query: String) {
        mutableState.update { it.copy(chapterSearchQuery = query) }
    }

    fun toggleSortDirection() {
        mutableState.update { it.copy(isSortAscending = !it.isSortAscending) }
    }

    fun toggleLibraryBookmark() {
        val entryId = state.value.matchedEntryId ?: return
        val newFav = !state.value.inLibrary
        screenModelScope.launchIO {
            when (mediaType) {
                MediaType.ANIME -> {
                    updateAnime.awaitUpdateFavorite(entryId, newFav)
                    if (newFav) setAnimeCategories.await(entryId, emptyList())
                }
                MediaType.NOVEL -> {
                    updateNovel.awaitUpdateFavorite(entryId, newFav)
                    if (newFav) setNovelCategories.await(entryId, emptyList())
                }
                MediaType.MANGA -> {
                    updateManga.awaitUpdateFavorite(entryId, newFav)
                    if (newFav) setMangaCategories.await(entryId, emptyList())
                }
            }
            mutableState.update { it.copy(inLibrary = newFav) }
        }
    }
}
// KMK <--
