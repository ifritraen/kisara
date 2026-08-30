package eu.kanade.tachiyomi.ui.browse.search

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.ui.browse.search.components.SearchSourceItem
import eu.kanade.tachiyomi.ui.browse.search.model.AdvancedSearchState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
sealed interface SearchItemResult {
    data object Loading : SearchItemResult
    data class Success(val list: List<Manga>) : SearchItemResult
    data class Error(val throwable: Throwable) : SearchItemResult
}

class SearchScreenModel(
    private val mediaType: UiPreferences.MediaType = UiPreferences.MediaType.MANGA,
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: tachiyomi.domain.entries.anime.source.service.AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: tachiyomi.domain.entries.novel.source.service.NovelSourceManager = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<SearchScreenModel.State>(State()) {

    private var searchJob: Job? = null

    init {
        loadSources()
        loadRecentSearches()
    }

    private fun loadRecentSearches() {
        val recents = uiPreferences.searchRecentQueries().get().toList()
        mutableState.update { it.copy(recentSearches = recents.toPersistentList()) }
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val current = uiPreferences.searchRecentQueries().get().toMutableSet()
        current.remove(query)
        val updated = (listOf(query) + current).take(20).toSet()
        uiPreferences.searchRecentQueries().set(updated)
        mutableState.update { it.copy(recentSearches = updated.toList().toPersistentList()) }
    }

    fun clearRecentSearches() {
        uiPreferences.searchRecentQueries().set(emptySet())
        mutableState.update { it.copy(recentSearches = persistentListOf()) }
    }

    fun loadSources() {
        val prefSources = when (mediaType) {
            UiPreferences.MediaType.MANGA -> uiPreferences.mangaSearchSources().get()
            UiPreferences.MediaType.ANIME -> uiPreferences.animeSearchSources().get()
            UiPreferences.MediaType.NOVEL -> uiPreferences.novelSearchSources().get()
        }

        val allSources = when (mediaType) {
            UiPreferences.MediaType.MANGA -> sourceManager.getCatalogueSources().map {
                SearchSourceItem(
                    id = it.id,
                    name = it.name,
                    lang = it.lang,
                    isEnabled = prefSources.isEmpty() || prefSources.contains(it.id.toString()),
                )
            }
            UiPreferences.MediaType.ANIME -> animeSourceManager.getCatalogueSources().map {
                SearchSourceItem(
                    id = it.id,
                    name = it.name,
                    lang = it.lang,
                    isEnabled = prefSources.isEmpty() || prefSources.contains(it.id.toString()),
                )
            }
            UiPreferences.MediaType.NOVEL -> novelSourceManager.getCatalogueSources().map {
                SearchSourceItem(
                    id = it.id,
                    name = it.name,
                    lang = it.lang,
                    isEnabled = prefSources.isEmpty() || prefSources.contains(it.id.toString()),
                )
            }
        }

        mutableState.update { it.copy(availableSources = allSources.toPersistentList()) }
    }

    fun toggleSource(sourceId: Long, isEnabled: Boolean) {
        val updated = state.value.availableSources.map {
            if (it.id == sourceId) it.copy(isEnabled = isEnabled) else it
        }.toPersistentList()

        mutableState.update { it.copy(availableSources = updated) }
        saveEnabledSources(updated)
    }

    fun selectAllSources() {
        val updated = state.value.availableSources.map { it.copy(isEnabled = true) }.toPersistentList()
        mutableState.update { it.copy(availableSources = updated) }
        saveEnabledSources(updated)
    }

    fun deselectAllSources() {
        val updated = state.value.availableSources.map { it.copy(isEnabled = false) }.toPersistentList()
        mutableState.update { it.copy(availableSources = updated) }
        saveEnabledSources(updated)
    }

    private fun saveEnabledSources(sources: List<SearchSourceItem>) {
        val enabledIds = sources.filter { it.isEnabled }.map { it.id.toString() }.toSet()
        when (mediaType) {
            UiPreferences.MediaType.MANGA -> uiPreferences.mangaSearchSources().set(enabledIds)
            UiPreferences.MediaType.ANIME -> uiPreferences.animeSearchSources().set(enabledIds)
            UiPreferences.MediaType.NOVEL -> uiPreferences.novelSearchSources().set(enabledIds)
        }
    }

    fun updateSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun updateAdvancedState(advancedState: AdvancedSearchState) {
        mutableState.update { it.copy(advancedState = advancedState) }
    }

    fun resetAdvancedFilters() {
        mutableState.update { it.copy(advancedState = AdvancedSearchState()) }
    }

    fun search(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            mutableState.update { it.copy(searchQuery = "", results = persistentMapOf(), isSearching = false) }
            return
        }

        addRecentSearch(cleanQuery)
        mutableState.update { it.copy(searchQuery = cleanQuery, isSearching = true) }

        searchJob?.cancel()
        searchJob = screenModelScope.launch(Dispatchers.IO) {
            val enabledSources = when (mediaType) {
                UiPreferences.MediaType.MANGA -> {
                    val enabledIds = state.value.availableSources.filter { it.isEnabled }.map { it.id }.toSet()
                    sourceManager.getCatalogueSources().filter { enabledIds.contains(it.id) }
                }
                else -> emptyList()
            }

            val initialResults = enabledSources.associateWith { SearchItemResult.Loading as SearchItemResult }.toPersistentMap()
            mutableState.update { it.copy(results = initialResults) }

            enabledSources.map { source ->
                async {
                    try {
                        val page = source.getSearchManga(1, cleanQuery, source.getFilterList())
                        val domainList = page.mangas.map { it.toDomainManga(source.id) }
                        mutableState.update { current ->
                            val updated = current.results.toMutableMap()
                            updated[source] = SearchItemResult.Success(domainList)
                            current.copy(results = updated.toPersistentMap())
                        }
                    } catch (e: Throwable) {
                        mutableState.update { current ->
                            val updated = current.results.toMutableMap()
                            updated[source] = SearchItemResult.Error(e)
                            current.copy(results = updated.toPersistentMap())
                        }
                    }
                }
            }.awaitAll()

            mutableState.update { it.copy(isSearching = false) }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val availableSources: PersistentList<SearchSourceItem> = persistentListOf(),
        val recentSearches: PersistentList<String> = persistentListOf(),
        val results: PersistentMap<CatalogueSource, SearchItemResult> = persistentMapOf(),
        val advancedState: AdvancedSearchState = AdvancedSearchState(),
    )
}
// KMK <--
