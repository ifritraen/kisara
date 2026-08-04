package eu.kanade.tachiyomi.ui.browse.bulk

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BulkSearchScreenModel(
    val sourceIds: List<Long>,
    val queries: List<String>,
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateScreenModel<BulkSearchScreenModel.State>(
    State(
        queryResults = queries.map { QueryResult(it) }.toImmutableList(),
    ),
) {

    init {
        // Collect library/favorites updates to dynamically update library status on items
        screenModelScope.launch {
            getLibraryManga.subscribe().collectLatest { libraryList ->
                val favoriteUrls = libraryList.map { it.manga.url }.toSet()
                mutableState.update { state ->
                    state.copy(favoriteUrls = favoriteUrls)
                }
            }
        }

        // Collect categories
        screenModelScope.launch {
            try {
                val cats = getCategories.await()
                mutableState.update { it.copy(categories = cats.toImmutableList()) }
            } catch (e: Exception) {
                // ignore
            }
        }

        startBulkSearch()
    }

    private fun startBulkSearch() {
        screenModelScope.launchIO {
            val sources = sourceManager.getVisibleCatalogueSources()
                .filter { sourceIds.contains(it.id) }

            for (index in queries.indices) {
                val query = queries[index]

                updateQueryState(query) { it.copy(isLoading = true, isFailed = false) }

                try {
                    val allResults = mutableListOf<Pair<Manga, Source>>()
                    coroutineScope {
                        val jobs = sources.map { source ->
                            async {
                                try {
                                    val searchResult = source.getSearchManga(1, query, eu.kanade.tachiyomi.source.model.FilterList())
                                    val domainSource = Source(
                                        id = source.id,
                                        lang = source.lang,
                                        name = source.name,
                                        supportsLatest = source.supportsLatest,
                                        isStub = false,
                                    )
                                    val filterMangaByBlockedContent = Injekt.get<tachiyomi.domain.suggestions.interactor.FilterMangaByBlockedContent>()
                                    val rawMangas = searchResult.mangas.map { smanga ->
                                        networkToLocalManga(smanga.toDomainManga(source.id))
                                    }
                                    val mangas = filterMangaByBlockedContent.filterList(rawMangas)
                                    synchronized(allResults) {
                                        mangas.forEach { allResults.add(it to domainSource) }
                                    }
                                } catch (e: Exception) {
                                    // Ignore failures on individual sources
                                }
                            }
                        }
                        jobs.awaitAll()
                    }

                    // Keep top results or all results
                    updateQueryState(query) {
                        it.copy(
                            isLoading = false,
                            isFailed = allResults.isEmpty(),
                            results = allResults.toImmutableList(),
                        )
                    }
                } catch (e: Exception) {
                    updateQueryState(query) { it.copy(isLoading = false, isFailed = true) }
                }

                // Add a small delay between queries
                if (index < queries.size - 1) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    private fun updateQueryState(query: String, transform: (QueryResult) -> QueryResult) {
        mutableState.update { state ->
            state.copy(
                queryResults = state.queryResults.map { qr ->
                    if (qr.query == query) transform(qr) else qr
                }.toImmutableList(),
            )
        }
    }

    fun addMangasToLibrary(mangas: List<Manga>, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            mangas.forEach { manga ->
                updateManga.awaitUpdateFavorite(manga.id, true)
                setMangaCategories.await(manga.id, categoryIds)
                if (categoryIds.isNotEmpty()) {
                    Injekt.get<eu.kanade.domain.track.interactor.TrackOnCategorySet>().execute(manga)
                }
            }
        }
    }

    fun toggleFavorite(manga: Manga, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            val isFavorite = manga.favorite
            if (isFavorite) {
                updateManga.awaitUpdateFavorite(manga.id, false)
                setMangaCategories.await(manga.id, emptyList())
            } else {
                updateManga.awaitUpdateFavorite(manga.id, true)
                setMangaCategories.await(manga.id, categoryIds)
                if (categoryIds.isNotEmpty()) {
                    Injekt.get<eu.kanade.domain.track.interactor.TrackOnCategorySet>().execute(manga)
                }
            }
        }
    }

    fun editQuery(oldQuery: String, newQuery: String) {
        if (oldQuery == newQuery || newQuery.isBlank()) return

        mutableState.update { state ->
            val updatedResults = state.queryResults.map { qr ->
                if (qr.query == oldQuery) {
                    QueryResult(query = newQuery, isLoading = true, isFailed = false)
                } else {
                    qr
                }
            }.toImmutableList()
            state.copy(queryResults = updatedResults)
        }

        screenModelScope.launchIO {
            val sources = sourceManager.getVisibleCatalogueSources()
                .filter { sourceIds.contains(it.id) }
            try {
                val allResults = mutableListOf<Pair<Manga, Source>>()
                coroutineScope {
                    val jobs = sources.map { source ->
                        async {
                            try {
                                val searchResult = source.getSearchManga(1, newQuery, eu.kanade.tachiyomi.source.model.FilterList())
                                val domainSource = Source(
                                    id = source.id,
                                    lang = source.lang,
                                    name = source.name,
                                    supportsLatest = source.supportsLatest,
                                    isStub = false,
                                )
                                val mangas = searchResult.mangas.map { smanga ->
                                    networkToLocalManga(smanga.toDomainManga(source.id))
                                }
                                synchronized(allResults) {
                                    mangas.forEach { allResults.add(it to domainSource) }
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                updateQueryState(newQuery) {
                    it.copy(
                        isLoading = false,
                        isFailed = allResults.isEmpty(),
                        results = allResults.toImmutableList(),
                    )
                }
            } catch (e: Exception) {
                updateQueryState(newQuery) { it.copy(isLoading = false, isFailed = true) }
            }
        }
    }

    suspend fun getMangaCategoryIds(mangaId: Long): List<Long> {
        return getCategories.await(mangaId).map { it.id }
    }

    @Immutable
    data class QueryResult(
        val query: String,
        val isLoading: Boolean = true,
        val isFailed: Boolean = false,
        val results: ImmutableList<Pair<Manga, Source>> = emptyList<Pair<Manga, Source>>().toImmutableList(),
    )

    data class State(
        val queryResults: ImmutableList<QueryResult> = emptyList<QueryResult>().toImmutableList(),
        val favoriteUrls: Set<String> = emptySet(),
        val categories: ImmutableList<Category> = emptyList<Category>().toImmutableList(),
    )
}
