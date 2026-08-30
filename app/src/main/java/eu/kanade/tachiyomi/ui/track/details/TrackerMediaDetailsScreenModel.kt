package eu.kanade.tachiyomi.ui.track.details

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.track.TrackSeriesItem
import eu.kanade.tachiyomi.ui.track.matcher.TrackerSourceMatcher
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetMangaDefaultCategory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
data class TrackerMediaDetailsState(
    val isMatching: Boolean = true,
    val activeSource: CatalogueSource? = null,
    val matchedManga: Manga? = null,
    val chapters: List<Chapter> = emptyList(),
    val inLibrary: Boolean = false,
    val matchError: String? = null,
    val prioritizedSourceIds: List<Long> = emptyList(),
    val allInstalledSources: List<CatalogueSource> = emptyList(),
    val chapterSearchQuery: String = "",
    val isSortAscending: Boolean = false,
) {
    val filteredChapters: List<Chapter>
        get() {
            var list = chapters
            if (chapterSearchQuery.isNotBlank()) {
                val q = chapterSearchQuery.trim().lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) || it.chapterNumber.toString().contains(q)
                }
            }
            return if (isSortAscending) {
                list.sortedBy { it.chapterNumber }
            } else {
                list.sortedByDescending { it.chapterNumber }
            }
        }
}

class TrackerMediaDetailsScreenModel(
    val series: TrackSeriesItem,
    val mediaType: MediaType,
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaDefaultCategory: SetMangaDefaultCategory = Injekt.get(),
) : StateScreenModel<TrackerMediaDetailsState>(TrackerMediaDetailsState()) {

    private val matcher = TrackerSourceMatcher()

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
            val installed = sourceManager.getOnlineSources()
                .filterIsInstance<CatalogueSource>()

            val storedIds = getStoredPriorityIds()
            val priorityIds = if (storedIds.isNotEmpty()) {
                storedIds.filter { id -> installed.any { it.id == id } }
            } else {
                installed.take(5).map { it.id }
            }

            mutableState.update {
                it.copy(
                    isMatching = true,
                    prioritizedSourceIds = priorityIds,
                    allInstalledSources = installed,
                    matchError = null,
                )
            }

            val result = matcher.matchAndFetch(series.title, priorityIds)
            when (result) {
                is TrackerSourceMatcher.MatchResult.Success -> {
                    val isFav = result.manga.favorite
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = result.source,
                            matchedManga = result.manga,
                            chapters = result.chapters,
                            inLibrary = isFav,
                            matchError = null,
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.NoMatch -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = null,
                            matchedManga = null,
                            chapters = emptyList(),
                            matchError = "No match found in prioritized sources.",
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.Error -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = null,
                            matchedManga = null,
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
            val result = matcher.fetchFromSpecificSource(sourceId, series.title)
            when (result) {
                is TrackerSourceMatcher.MatchResult.Success -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = result.source,
                            matchedManga = result.manga,
                            chapters = result.chapters,
                            inLibrary = result.manga.favorite,
                            matchError = null,
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.NoMatch -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = null,
                            matchedManga = null,
                            chapters = emptyList(),
                            matchError = "Series not found on this source.",
                        )
                    }
                }
                is TrackerSourceMatcher.MatchResult.Error -> {
                    mutableState.update {
                        it.copy(
                            isMatching = false,
                            activeSource = null,
                            matchedManga = null,
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
        val manga = state.value.matchedManga ?: return
        val newFav = !state.value.inLibrary
        screenModelScope.launchIO {
            if (newFav) {
                updateManga.awaitUpdateFavorite(manga.id, true)
                setMangaDefaultCategory.await(manga.id)
            } else {
                updateManga.awaitUpdateFavorite(manga.id, false)
            }
            mutableState.update { it.copy(inLibrary = newFav) }
        }
    }
}
// KMK <--
