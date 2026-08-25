package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.QueryTransformer
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class AnimeSearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<AnimeSearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledAnimeSources().get()
    protected val pinnedSources = sourcePreferences.pinnedAnimeSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: AnimeSourceFilter? = null

    private val sortComparator = { map: Map<AnimeCatalogueSource, AnimeSearchItemResult> ->
        compareBy<AnimeCatalogueSource>(
            { (map[it] as? AnimeSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { showOnlyWithResults ->
                mutableState.update { it.copy(onlyShowHasResults = showOnlyWithResults) }
            }
        }
        screenModelScope.launch {
            preferences.searchClean().changes().collectLatest { v ->
                mutableState.update { it.copy(searchClean = v) }
            }
        }
        screenModelScope.launch {
            preferences.searchFormat().changes().collectLatest { v ->
                mutableState.update { it.copy(searchFormat = v) }
            }
        }
        screenModelScope.launch {
            preferences.searchFuzzy().changes().collectLatest { v ->
                mutableState.update { it.copy(searchFuzzy = v) }
            }
        }
    }

    fun toggleSearchClean() {
        preferences.searchClean().set(!state.value.searchClean)
    }
    fun toggleSearchFormat() {
        preferences.searchFormat().set((state.value.searchFormat + 1) % 3)
    }
    fun toggleSearchFuzzy() {
        preferences.searchFuzzy().set(!state.value.searchFuzzy)
    }

    @Composable
    fun getAnime(initialAnime: Anime): androidx.compose.runtime.State<Anime> {
        return produceState(initialValue = initialAnime) {
            getAnime.subscribe(initialAnime.url, initialAnime.source)
                .filterNotNull()
                .collectLatest { anime ->
                    value = anime
                }
        }
    }

    open fun getEnabledSources(): List<AnimeCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun getFilterSources(): List<AnimeCatalogueSource> {
        val sources = getEnabledSources()
        return when (state.value.sourceFilter) {
            AnimeSourceFilter.PinnedOnly -> sources.filter { "${it.id}" in pinnedSources }
            AnimeSourceFilter.All -> sources
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: AnimeSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return
        if (query == lastQuery && sourceFilter == lastSourceFilter) return

        lastQuery = query
        lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getFilterSources()

        mutableState.update {
            it.copy(
                items = sources.associateWith { AnimeSearchItemResult.Loading }
                    .toPersistentMap(),
                total = sources.size,
                progress = 0,
            )
        }

        ioCoroutineScope.launch {
            try {
                val allCategories = getCategories.await()
                val libraryAnimeList = getLibraryAnime.await()

                val cleanQuery = query.trim()
                val matches = libraryAnimeList.filter { item ->
                    item.anime.title.contains(cleanQuery, ignoreCase = true) ||
                        (item.anime.author?.contains(cleanQuery, ignoreCase = true) == true) ||
                        (item.anime.artist?.contains(cleanQuery, ignoreCase = true) == true)
                }.map { item ->
                    val cat = allCategories.find { it.id == item.category }
                    val catName = if (cat != null) {
                        if (cat.parentId != null) {
                            val parent = allCategories.find { it.id == cat.parentId }
                            if (parent != null) "${parent.name} / ${cat.name}" else cat.name
                        } else {
                            if (cat.isSystemCategory) "Default" else cat.name
                        }
                    } else {
                        "Default"
                    }

                    LibrarySearchResult(
                        anime = item.anime,
                        categoryNames = catName,
                    )
                }

                mutableState.update { it.copy(libraryResults = matches.toImmutableList()) }
            } catch (_: Throwable) {
                mutableState.update { it.copy(libraryResults = persistentListOf()) }
            }
        }

        searchJob = ioCoroutineScope.launch {
            val s = state.value
            val transformedQuery = QueryTransformer.transform(query, s.searchClean, s.searchFormat)
            val intermediateItems = mutableState.value.items.toMutableMap()

            sources.map { source ->
                async {
                    if (!isActive) return@async
                    try {
                        val searchResult = withContext(coroutineDispatcher) {
                            source.getSearchAnime(1, transformedQuery.sanitize(), AnimeFilterList())
                        }
                        val domainAnimes = searchResult.animes.map { it.toDomainAnime(source.id) }
                        val rawAnimes = networkToLocalAnime.await(domainAnimes)
                        val result = if (rawAnimes.isEmpty()) {
                            AnimeSearchItemResult.Empty
                        } else {
                            AnimeSearchItemResult.Success(rawAnimes)
                        }
                        if (isActive) {
                            withContext(coroutineDispatcher) {
                                intermediateItems[source] = result
                                mutableState.update {
                                    it.copy(
                                        items = intermediateItems
                                            .toSortedMap(sortComparator(intermediateItems))
                                            .toPersistentMap(),
                                        progress = it.progress + 1,
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        if (isActive) {
                            withContext(coroutineDispatcher) {
                                intermediateItems[source] = AnimeSearchItemResult.Error(e)
                                mutableState.update {
                                    it.copy(
                                        items = intermediateItems
                                            .toSortedMap(sortComparator(intermediateItems))
                                            .toPersistentMap(),
                                        progress = it.progress + 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    data class LibrarySearchResult(
        val anime: Anime,
        val categoryNames: String,
    )

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val sourceFilter: AnimeSourceFilter = AnimeSourceFilter.All,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult> = persistentMapOf(),
        val libraryResults: ImmutableList<LibrarySearchResult> = persistentListOf(),
        val searchClean: Boolean = false,
        val searchFormat: Int = 0,
        val searchFuzzy: Boolean = false,
        val total: Int = 0,
        val progress: Int = 0,
    ) {
        val progressText: String
            get() = "$progress/$total"

        val filteredItems: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult>
            get() = if (onlyShowHasResults) {
                items.filter { (_, result) ->
                    result is AnimeSearchItemResult.Success && !result.isEmpty
                }.toPersistentMap()
            } else {
                items
            }
    }
}

sealed interface AnimeSearchItemResult {
    data object Loading : AnimeSearchItemResult
    data object Empty : AnimeSearchItemResult
    data class Success(val result: List<Anime>) : AnimeSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }
    data class Error(val throwable: Throwable) : AnimeSearchItemResult
}

enum class AnimeSourceFilter {
    All,
    PinnedOnly,
}
