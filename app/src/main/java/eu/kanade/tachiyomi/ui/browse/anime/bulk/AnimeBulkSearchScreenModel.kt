package eu.kanade.tachiyomi.ui.browse.anime.bulk

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBulkSearchScreenModel(
    val sourceIds: List<Long>,
    val queries: List<String>,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
) : StateScreenModel<AnimeBulkSearchScreenModel.State>(
    State(
        queryResults = queries.map { AnimeQueryResult(it) }.toImmutableList(),
    ),
) {

    init {
        screenModelScope.launch {
            getLibraryAnime.subscribe().collectLatest { libraryList ->
                val favoriteUrls = libraryList.map { it.anime.url }.toSet()
                mutableState.update { state ->
                    state.copy(favoriteUrls = favoriteUrls)
                }
            }
        }

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
            val sources = sourceManager.getCatalogueSources()
                .filter { sourceIds.contains(it.id) }

            for (index in queries.indices) {
                val query = queries[index]

                updateQueryState(query) { it.copy(isLoading = true, isFailed = false) }

                try {
                    val allResults = mutableListOf<Pair<Anime, Source>>()
                    coroutineScope {
                        val jobs = sources.map { source: AnimeCatalogueSource ->
                            async {
                                try {
                                    val searchResult = source.getSearchAnime(1, query, AnimeFilterList())
                                    val domainSource = Source(
                                        id = source.id,
                                        lang = source.lang,
                                        name = source.name,
                                        supportsLatest = source.supportsLatest,
                                        isStub = false,
                                    )
                                    val animes = searchResult.animes.map { sanime ->
                                        networkToLocalAnime.await(sanime.toDomainAnime(source.id))
                                    }
                                    synchronized(allResults) {
                                        animes.forEach { allResults.add(it to domainSource) }
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

    private fun updateQueryState(query: String, transform: (AnimeQueryResult) -> AnimeQueryResult) {
        mutableState.update { state ->
            state.copy(
                queryResults = state.queryResults.map { qr ->
                    if (qr.query == query) transform(qr) else qr
                }.toImmutableList(),
            )
        }
    }

    fun addAnimesToLibrary(animes: List<Anime>, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            animes.forEach { anime ->
                updateAnime.awaitUpdateFavorite(anime.id, true)
                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    fun toggleFavorite(anime: Anime, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            val isFavorite = anime.favorite
            if (isFavorite) {
                updateAnime.awaitUpdateFavorite(anime.id, false)
                setAnimeCategories.await(anime.id, emptyList())
            } else {
                updateAnime.awaitUpdateFavorite(anime.id, true)
                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    suspend fun getAnimeCategoryIds(animeId: Long): List<Long> {
        return try {
            getCategories.await(animeId).map { it.id }
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
                        AnimeQueryResult(newQuery, isLoading = true)
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
                val allResults = mutableListOf<Pair<Anime, Source>>()
                coroutineScope {
                    val jobs = sources.map { source: AnimeCatalogueSource ->
                        async {
                            try {
                                val searchResult = source.getSearchAnime(1, newQuery, AnimeFilterList())
                                val domainSource = Source(
                                    id = source.id,
                                    lang = source.lang,
                                    name = source.name,
                                    supportsLatest = source.supportsLatest,
                                    isStub = false,
                                )
                                val animes = searchResult.animes.map { sanime ->
                                    networkToLocalAnime.await(sanime.toDomainAnime(source.id))
                                }
                                synchronized(allResults) {
                                    animes.forEach { allResults.add(it to domainSource) }
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
        val queryResults: ImmutableList<AnimeQueryResult> = persistentListOf(),
        val categories: ImmutableList<Category> = persistentListOf(),
        val favoriteUrls: Set<String> = emptySet(),
    )
}

@Immutable
data class AnimeQueryResult(
    val query: String,
    val isLoading: Boolean = false,
    val isFailed: Boolean = false,
    val results: ImmutableList<Pair<Anime, Source>> = persistentListOf(),
)
