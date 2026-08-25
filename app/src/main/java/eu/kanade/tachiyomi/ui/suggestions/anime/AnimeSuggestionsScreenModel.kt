package eu.kanade.tachiyomi.ui.suggestions.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.data.suggestions.anime.AnimeSuggestionsReport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSuggestionsScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
) : StateScreenModel<AnimeSuggestionsScreenModel.State>(State()) {

    var initialCount: Int = 0

    init {
        loadSuggestions()
    }

    fun loadSuggestions() {
        screenModelScope.launchIO {
            initialCount = state.value.suggestions.size
            AnimeSuggestionsReport.clear()
            mutableState.update { it.copy(isLoading = true, fetchProgress = 0, fetchTotal = 0) }

            val library = getLibraryAnime.await()
            val favoriteUrls = library.map { it.anime.url }.toSet()
            val favoriteTitles = library.map { it.anime.title.lowercase().trim() }.toSet()
            val favoriteIds = library.map { it.anime.id }.toSet()

            // Collect top tags from library
            val topTags = library.flatMap { it.anime.genre.orEmpty() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key }

            val sources = sourceManager.getCatalogueSources()
            val totalTags = if (topTags.isNotEmpty()) topTags.size else 1
            mutableState.update { it.copy(fetchTotal = totalTags) }

            val suggestions = mutableListOf<Anime>()
            var progress = 0

            for (tag in topTags) {
                val jobs = sources.map { source ->
                    async {
                        try {
                            val result = source.getSearchAnime(1, tag, eu.kanade.tachiyomi.animesource.model.AnimeFilterList())
                            val domainAnimes = result.animes.map { it.toDomainAnime(source.id) }
                            val localAnimes = networkToLocalAnime.await(domainAnimes)
                            AnimeSuggestionsReport.fetchedCount.update { it + localAnimes.size }
                            AnimeSuggestionsReport.fetchedBySource.update { map ->
                                val current = map[source.name] ?: 0
                                map + (source.name to current + localAnimes.size)
                            }
                            localAnimes
                        } catch (e: Exception) {
                            AnimeSuggestionsReport.failedCount.update { it + 1 }
                            AnimeSuggestionsReport.failedBySource.update { map ->
                                val current = map[source.name] ?: 0
                                map + (source.name to current + 1)
                            }
                            emptyList()
                        }
                    }
                }
                val batch = jobs.awaitAll().flatten()
                val unadded = batch.filterNot { 
                    it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                }
                val filteredOut = batch.size - unadded.size
                if (filteredOut > 0) {
                    AnimeSuggestionsReport.libraryFilteredCount.update { it + filteredOut }
                }

                synchronized(suggestions) {
                    suggestions.addAll(unadded)
                }

                progress++
                mutableState.update {
                    it.copy(
                        fetchProgress = progress,
                        suggestions = suggestions.distinctBy { a -> a.url }.toImmutableList(),
                    )
                }
            }

            // Fallback to latest updates if suggestions are empty
            if (suggestions.isEmpty()) {
                val jobs = sources.map { source ->
                    async {
                        try {
                            val result = source.getLatestUpdates(1)
                            val domainAnimes = result.animes.map { it.toDomainAnime(source.id) }
                            val localAnimes = networkToLocalAnime.await(domainAnimes)
                            AnimeSuggestionsReport.fetchedCount.update { it + localAnimes.size }
                            AnimeSuggestionsReport.fetchedBySource.update { map ->
                                val current = map[source.name] ?: 0
                                map + (source.name to current + localAnimes.size)
                            }
                            localAnimes
                        } catch (_: Exception) {
                            AnimeSuggestionsReport.failedCount.update { it + 1 }
                            AnimeSuggestionsReport.failedBySource.update { map ->
                                val current = map[source.name] ?: 0
                                map + (source.name to current + 1)
                            }
                            emptyList()
                        }
                    }
                }
                val batch = jobs.awaitAll().flatten()
                val unadded = batch.filterNot { 
                    it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                }
                suggestions.addAll(unadded)
            }

            val finalSuggestions = suggestions.distinctBy { it.url }.shuffled().take(50)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    suggestions = finalSuggestions.toImmutableList(),
                    topTags = topTags.toImmutableList(),
                    favoriteUrls = favoriteUrls,
                    fetchProgress = totalTags,
                )
            }
        }
    }

    fun dismissSuggestion(url: String, title: String) {
        mutableState.update { state ->
            val updated = state.suggestions.filterNot { it.url == url || it.title == title }
            state.copy(suggestions = updated.toImmutableList())
        }
    }

    fun toggleFavorite(anime: Anime, isFavorite: Boolean) {
        screenModelScope.launchIO {
            val isFavoriteNow = !isFavorite
            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetAnimeCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()
                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    anime = anime,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        updateAnime.await(
                            AnimeUpdate(
                                id = anime.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setAnimeCategories = Injekt.get<SetAnimeCategories>()
                        setAnimeCategories.await(anime.id, listOf(defaultCategory.id))
                        mutableState.update { state ->
                            state.copy(favoriteUrls = state.favoriteUrls + anime.url)
                        }
                    }
                    else -> {
                        updateAnime.await(
                            AnimeUpdate(
                                id = anime.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        mutableState.update { state ->
                            state.copy(favoriteUrls = state.favoriteUrls + anime.url)
                        }
                    }
                }
            } else {
                val getCategories = Injekt.get<GetAnimeCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val preselectedIds = getCategories.await(anime.id).map { it.id }
                if (categories.isNotEmpty()) {
                    mutableState.update { state ->
                        state.copy(
                            dialog = State.Dialog.ChangeCategory(
                                anime = anime,
                                initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                            ),
                        )
                    }
                } else {
                    updateAnime.await(
                        AnimeUpdate(
                            id = anime.id,
                            favorite = false,
                            dateAdded = 0L,
                        ),
                    )
                    mutableState.update { state ->
                        state.copy(favoriteUrls = state.favoriteUrls - anime.url)
                    }
                }
            }
        }
    }

    fun setAnimeCategories(anime: Anime, categories: List<Long>) {
        screenModelScope.launchIO {
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setAnimeCategories = Injekt.get<SetAnimeCategories>()
            setAnimeCategories.await(anime.id, categories)
            mutableState.update { state ->
                state.copy(
                    favoriteUrls = state.favoriteUrls + anime.url,
                    dialog = null,
                )
            }
        }
    }

    fun deleteAnime(anime: Anime) {
        screenModelScope.launchIO {
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
            mutableState.update { state ->
                state.copy(
                    favoriteUrls = state.favoriteUrls - anime.url,
                    dialog = null,
                )
            }
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launchIO {
            val createCategory = Injekt.get<CreateAnimeCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val fetchProgress: Int = 0,
        val fetchTotal: Int = 0,
        val suggestions: ImmutableList<Anime> = persistentListOf(),
        val topTags: ImmutableList<String> = persistentListOf(),
        val favoriteUrls: Set<String> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val anime: Anime,
                val initialSelection: ImmutableList<CheckboxState<Category>>,
            ) : Dialog
        }
    }
}
