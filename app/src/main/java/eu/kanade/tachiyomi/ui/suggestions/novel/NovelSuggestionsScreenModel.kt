package eu.kanade.tachiyomi.ui.suggestions.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSuggestionsScreenModel(
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
) : StateScreenModel<NovelSuggestionsScreenModel.State>(State()) {

    init {
        loadSuggestions()
    }

    fun loadSuggestions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }
            val library = getLibraryNovel.await()
            val favoriteUrls = library.map { it.novel.url }.toSet()
            val favoriteTitles = library.map { it.novel.title.lowercase().trim() }.toSet()
            val favoriteIds = library.map { it.novel.id }.toSet()

            // Collect top tags from library
            val topTags = library.flatMap { it.novel.genre.orEmpty() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

            val sources = sourceManager.getCatalogueSources()

            val suggestions = mutableListOf<Novel>()
            for (tag in topTags) {
                val jobs = sources.map { source ->
                    async {
                        try {
                            val result = source.getSearchNovels(1, tag, source.getFilterList())
                            val domainNovels = result.novels.map { it.toDomainNovel(source.id) }
                            networkToLocalNovel.await(domainNovels)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                val batch = jobs.awaitAll().flatten()
                synchronized(suggestions) {
                    suggestions.addAll(batch.filterNot { 
                        it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                    })
                }
            }

            // If empty, fetch latest from sources
            if (suggestions.isEmpty()) {
                val jobs = sources.map { source ->
                    async {
                        try {
                            val result = source.getLatestUpdates(1)
                            val domainNovels = result.novels.map { it.toDomainNovel(source.id) }
                            networkToLocalNovel.await(domainNovels)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                val batch = jobs.awaitAll().flatten()
                suggestions.addAll(batch.filterNot { 
                    it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                })
            }

            val finalSuggestions = suggestions.distinctBy { it.url }.shuffled().take(40)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    suggestions = finalSuggestions.toImmutableList(),
                    topTags = topTags.toImmutableList(),
                    favoriteUrls = favoriteUrls,
                )
            }
        }
    }

    fun toggleFavorite(novel: Novel, currentFavorite: Boolean) {
        screenModelScope.launchIO {
            val isFavoriteNow = !currentFavorite
            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetNovelCategories>()
                val categories = getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        hidden = it.hidden,
                        parentId = it.parentId,
                    )
                }.filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultNovelCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()
                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    novel = novel,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        updateNovel.await(
                            NovelUpdate(
                                id = novel.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setNovelCategories = Injekt.get<SetNovelCategories>()
                        setNovelCategories.await(novel.id, listOf(defaultCategory.id))
                        mutableState.update { state ->
                            state.copy(favoriteUrls = state.favoriteUrls + novel.url)
                        }
                    }
                    else -> {
                        updateNovel.await(
                            NovelUpdate(
                                id = novel.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        mutableState.update { state ->
                            state.copy(favoriteUrls = state.favoriteUrls + novel.url)
                        }
                    }
                }
            } else {
                val getCategories = Injekt.get<GetNovelCategories>()
                val categories = getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        hidden = it.hidden,
                        parentId = it.parentId,
                    )
                }.filterNot { it.isSystemCategory }
                val preselectedIds = getCategories.await(novel.id).map { it.id }
                if (categories.isNotEmpty()) {
                    mutableState.update { state ->
                        state.copy(
                            dialog = State.Dialog.ChangeCategory(
                                novel = novel,
                                initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                            ),
                        )
                    }
                } else {
                    updateNovel.await(
                        NovelUpdate(
                            id = novel.id,
                            favorite = false,
                            dateAdded = 0L,
                        ),
                    )
                    mutableState.update { state ->
                        state.copy(favoriteUrls = state.favoriteUrls - novel.url)
                    }
                }
            }
        }
    }

    fun setNovelCategories(novel: Novel, categories: List<Long>) {
        screenModelScope.launchIO {
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setNovelCategories = Injekt.get<SetNovelCategories>()
            setNovelCategories.await(novel.id, categories)
            mutableState.update { state ->
                state.copy(
                    favoriteUrls = state.favoriteUrls + novel.url,
                    dialog = null,
                )
            }
        }
    }

    fun deleteNovel(novel: Novel) {
        screenModelScope.launchIO {
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
            mutableState.update { state ->
                state.copy(
                    favoriteUrls = state.favoriteUrls - novel.url,
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
            val createCategory = Injekt.get<CreateNovelCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val suggestions: ImmutableList<Novel> = persistentListOf(),
        val topTags: ImmutableList<String> = persistentListOf(),
        val favoriteUrls: Set<String> = emptySet(),
        val dialog: Dialog? = null,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val novel: Novel,
                val initialSelection: ImmutableList<CheckboxState<Category>>,
            ) : Dialog
        }
    }
}
