package eu.kanade.tachiyomi.ui.browse.novel.bulk

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBulkSearchScreenModel(
    val sourceIds: List<Long>,
    val queries: List<String>,
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
) : StateScreenModel<NovelBulkSearchScreenModel.State>(
    State(
        queryResults = queries.map { NovelQueryResult(it) }.toImmutableList(),
    ),
) {

    init {
        // Collect library/favorites updates
        screenModelScope.launch {
            getLibraryNovel.subscribe().collectLatest { libraryList ->
                val favoriteUrls = libraryList.map { it.novel.url }.toSet()
                mutableState.update { state ->
                    state.copy(favoriteUrls = favoriteUrls)
                }
            }
        }

        // Collect categories
        screenModelScope.launch {
            try {
                val cats = getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        parentId = it.parentId,
                        hidden = it.hidden,
                    )
                }
                mutableState.update { it.copy(categories = cats.toImmutableList()) }
            } catch (e: Exception) {
                // ignore
            }
        }

        startBulkSearch()
    }

    private fun startBulkSearch() {
        screenModelScope.launchIO {
            val sources = sourceManager.getCatalogueSources()
                .filter { sourceIds.contains(it.id) }

            for (index in queries.indices) {
                val query = queries[index]

                updateQueryState(query) { it.copy(isLoading = true, isFailed = false) }

                try {
                    val allResults = mutableListOf<Pair<Novel, Source>>()
                    coroutineScope {
                        val jobs = sources.map { source: NovelCatalogueSource ->
                            async {
                                try {
                                    val page = source.getSearchNovels(1, query, source.getFilterList())
                                    val domainSource = Source(
                                        id = source.id,
                                        lang = source.lang,
                                        name = source.name,
                                        supportsLatest = source.supportsLatest,
                                        isStub = false,
                                    )
                                    val novels = page.novels.map { snovel ->
                                        networkToLocalNovel.await(snovel.toDomainNovel(source.id))
                                    }
                                    synchronized(allResults) {
                                        novels.forEach { allResults.add(it to domainSource) }
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                        jobs.awaitAll()
                    }

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

                if (index < queries.size - 1) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    private fun updateQueryState(query: String, transform: (NovelQueryResult) -> NovelQueryResult) {
        mutableState.update { state ->
            state.copy(
                queryResults = state.queryResults.map { qr ->
                    if (qr.query == query) transform(qr) else qr
                }.toImmutableList(),
            )
        }
    }

    fun addNovelsToLibrary(novels: List<Novel>, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            novels.forEach { novel ->
                updateNovel.awaitUpdateFavorite(novel.id, true)
                setNovelCategories.await(novel.id, categoryIds)
            }
        }
    }

    fun toggleFavorite(novel: Novel, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            val isFavorite = novel.favorite
            if (isFavorite) {
                updateNovel.awaitUpdateFavorite(novel.id, false)
                setNovelCategories.await(novel.id, emptyList())
            } else {
                updateNovel.awaitUpdateFavorite(novel.id, true)
                setNovelCategories.await(novel.id, categoryIds)
            }
        }
    }

    suspend fun getNovelCategoryIds(novelId: Long): List<Long> {
        return try {
            getCategories.await(novelId).map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun editQuery(oldQuery: String, newQuery: String) {
        if (oldQuery == newQuery || newQuery.isBlank()) return

        mutableState.update { state ->
            state.copy(
                queryResults = state.queryResults.map { qr ->
                    if (qr.query == oldQuery) {
                        NovelQueryResult(newQuery, isLoading = true)
                    } else {
                        qr
                    }
                }.toImmutableList(),
            )
        }

        screenModelScope.launchIO {
            val sources = sourceManager.getCatalogueSources()
                .filter { sourceIds.contains(it.id) }

            try {
                val allResults = mutableListOf<Pair<Novel, Source>>()
                coroutineScope {
                    val jobs = sources.map { source: NovelCatalogueSource ->
                        async {
                            try {
                                val page = source.getSearchNovels(1, newQuery, source.getFilterList())
                                val domainSource = Source(
                                    id = source.id,
                                    lang = source.lang,
                                    name = source.name,
                                    supportsLatest = source.supportsLatest,
                                    isStub = false,
                                )
                                val novels = page.novels.map { snovel ->
                                    networkToLocalNovel.await(snovel.toDomainNovel(source.id))
                                }
                                synchronized(allResults) {
                                    novels.forEach { allResults.add(it to domainSource) }
                                }
                            } catch (e: Exception) {
                                // Ignore
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

    @Immutable
    data class State(
        val queryResults: ImmutableList<NovelQueryResult> = persistentListOf(),
        val categories: ImmutableList<Category> = persistentListOf(),
        val favoriteUrls: Set<String> = emptySet(),
    )
}

@Immutable
data class NovelQueryResult(
    val query: String,
    val isLoading: Boolean = false,
    val isFailed: Boolean = false,
    val results: ImmutableList<Pair<Novel, Source>> = persistentListOf(),
)
