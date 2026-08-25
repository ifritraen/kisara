package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.novel.OmniSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.QueryTransformer
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class NovelSearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<NovelSearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledNovelSources().get()
    protected val pinnedSources = sourcePreferences.pinnedNovelSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: NovelSourceFilter? = null

    private val sortComparator = { map: Map<NovelCatalogueSource, NovelSearchItemResult> ->
        compareBy<NovelCatalogueSource>(
            { (map[it] as? NovelSearchItemResult.Success)?.isEmpty ?: true },
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
            preferences.enabledLanguages().changes().collectLatest { languages ->
                mutableState.update { it.copy(availableLanguages = languages.toImmutableSet()) }
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

    @Composable
    fun getNovel(initialNovel: Novel): androidx.compose.runtime.State<Novel> {
        return produceState(initialValue = initialNovel) {
            getNovel.subscribe(initialNovel.url, initialNovel.source)
                .filterNotNull()
                .collectLatest { novel ->
                    value = novel
                }
        }
    }

    open fun getEnabledSources(): List<NovelCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: NovelSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun setLanguageFilter(languages: Set<String>) {
        mutableState.update { it.copy(languageFilter = languages.toImmutableSet()) }
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
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

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val isUrl = query.trim().toHttpUrlOrNull() != null
        val sources = if (isUrl) {
            val omni = sourceManager.get(OmniSource.OMNI_SOURCE_ID) as? NovelCatalogueSource
            if (omni != null) listOf(omni) else emptyList()
        } else {
            getEnabledSources()
                .filter { sourceFilter != NovelSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
        }

        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: NovelSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { NovelSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        ioCoroutineScope.launch {
            try {
                val allCategories = getCategories.await()
                val libraryNovelList = getLibraryNovel.await()

                val cleanQuery = query.trim()
                val matches = libraryNovelList.filter { item ->
                    item.novel.title.contains(cleanQuery, ignoreCase = true) ||
                        (item.novel.author?.contains(cleanQuery, ignoreCase = true) == true)
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
                        novel = item.novel,
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

            sources.map { source ->
                async {
                    if (state.value.items[source] !is NovelSearchItemResult.Loading) {
                        return@async
                    }
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchNovels(1, transformedQuery.sanitize(), source.getFilterList())
                        }

                        val titles = page.novels.map {
                            networkToLocalNovel.await(it.toDomainNovel(source.id))
                        }

                        if (isActive) {
                            updateItem(source, NovelSearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, NovelSearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: PersistentMap<NovelCatalogueSource, NovelSearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: NovelCatalogueSource, result: NovelSearchItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    data class LibrarySearchResult(
        val novel: Novel,
        val categoryNames: String,
    )

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val sourceFilter: NovelSourceFilter = NovelSourceFilter.All,
        val onlyShowHasResults: Boolean = false,
        val searchClean: Boolean = false,
        val searchFormat: Int = 0,
        val searchFuzzy: Boolean = false,
        val languageFilter: ImmutableSet<String> = persistentSetOf(),
        val availableLanguages: ImmutableSet<String> = persistentSetOf(),
        val items: PersistentMap<NovelCatalogueSource, NovelSearchItemResult> = persistentMapOf(),
        val libraryResults: ImmutableList<LibrarySearchResult> = persistentListOf(),
        val total: Int = 0,
        val progress: Int = 0,
    ) {
        val progressText: String
            get() = "$progress/$total"

        val filteredItems: Map<NovelCatalogueSource, NovelSearchItemResult>
            get() {
                var filtered = items.asIterable()
                if (onlyShowHasResults) {
                    filtered = filtered.filter { (_, result) ->
                        result is NovelSearchItemResult.Success && !result.isEmpty
                    }
                }
                if (languageFilter.isNotEmpty()) {
                    filtered = filtered.filter { (source, _) ->
                        source.lang in languageFilter
                    }
                }
                return filtered.associate { it.key to it.value }
            }
    }
}

sealed interface NovelSearchItemResult {
    data object Loading : NovelSearchItemResult
    data class Success(val result: List<Novel>) : NovelSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }
    data class Error(val throwable: Throwable) : NovelSearchItemResult
}

enum class NovelSourceFilter {
    All,
    PinnedOnly,
}
