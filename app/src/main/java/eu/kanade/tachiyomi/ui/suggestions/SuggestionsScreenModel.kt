package eu.kanade.tachiyomi.ui.suggestions

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.suggestions.interactor.GetSuggestionSources
import tachiyomi.domain.suggestions.interactor.GetSuggestionTags
import tachiyomi.domain.suggestions.interactor.GetSuggestions
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuggestionsScreenModel(
    private val getSuggestions: GetSuggestions = Injekt.get(),
    private val getSuggestionTags: GetSuggestionTags = Injekt.get(),
    private val getSuggestionSources: GetSuggestionSources = Injekt.get(),
) : StateScreenModel<SuggestionsScreenModel.State>(State()) {

    var initialCount: Int = 0

    init {
        screenModelScope.launch {
            getSuggestions.subscribe().collectLatest { list ->
                mutableState.update { state ->
                    state.copy(
                        suggestions = list,
                        isLoading = list.isEmpty() && state.isLoading,
                    )
                }
            }
        }
        screenModelScope.launch {
            getSuggestionTags.subscribe().collectLatest { list ->
                mutableState.update { it.copy(tags = list) }
            }
        }
        screenModelScope.launch {
            getSuggestionSources.subscribe().collectLatest { list ->
                mutableState.update { it.copy(sources = list) }
            }
        }
        val app = Injekt.get<android.app.Application>()
        screenModelScope.launch {
            androidx.work.WorkManager.getInstance(app)
                .getWorkInfosForUniqueWorkFlow("SuggestionsSessionWork")
                .collectLatest { workInfos ->
                    val activeWork = workInfos.find { !it.state.isFinished }
                    val isRunning = activeWork != null
                    val progressData = activeWork?.progress
                    val progress = progressData?.getInt("progress", 0) ?: 0
                    val total = progressData?.getInt("total", 0) ?: 0
                    
                    mutableState.update { state ->
                        state.copy(
                            isLoading = isRunning,
                            fetchProgress = progress,
                            fetchTotal = total,
                        )
                    }
                }
        }
    }

    fun triggerRefresh(context: android.content.Context) {
        val isRunning = SuggestionsWorker.isUpdateRunning(context)
        if (!isRunning) {
            initialCount = state.value.suggestions.size
            
            val request = androidx.work.OneTimeWorkRequestBuilder<SuggestionsWorker>()
                .setInputData(androidx.work.workDataOf("is_manual" to true))
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "SuggestionsSessionWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun loadNextRank(context: android.content.Context) {
        // Do nothing in session-based suggestions mode
    }

    fun dismissSuggestion(mangaUrl: String, title: String) {
        screenModelScope.launch {
            val repository = Injekt.get<tachiyomi.domain.suggestions.repository.SuggestionRepository>()
            repository.dismiss(mangaUrl, title)
            mutableState.update { state ->
                state.copy(suggestions = state.suggestions.filter { it.manga.url != mangaUrl })
            }
        }
    }

    fun toggleFavorite(manga: tachiyomi.domain.manga.model.Manga) {
        screenModelScope.launch {
            val updateManga = Injekt.get<eu.kanade.domain.manga.interactor.UpdateManga>()
            val isFavoriteNow = !manga.favorite

            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultCategory().get()

                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }
                when {
                    // Choose a category (Always ask)
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()

                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    manga = manga,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }

                    // Specific default category set
                    defaultCategory != null -> {
                        updateManga.awaitUpdateFavorite(manga.id, true)
                        val setMangaCategories = Injekt.get<SetMangaCategories>()
                        setMangaCategories.await(manga.id, listOf(defaultCategory.id))
                        updateLocalFavoriteState(manga.id, true)
                    }

                    // Uncategorized / Default
                    else -> {
                        updateManga.awaitUpdateFavorite(manga.id, true)
                        updateLocalFavoriteState(manga.id, true)
                    }
                }
            } else {
                updateManga.awaitUpdateFavorite(manga.id, false)
                updateLocalFavoriteState(manga.id, false)
            }
        }
    }

    private fun updateLocalFavoriteState(mangaId: Long, favorite: Boolean) {
        mutableState.update { state ->
            state.copy(
                suggestions = state.suggestions.map { suggestion ->
                    if (suggestion.manga.id == mangaId) {
                        suggestion.copy(manga = suggestion.manga.copy(favorite = favorite))
                    } else {
                        suggestion
                    }
                },
            )
        }
    }

    fun setMangaCategories(manga: tachiyomi.domain.manga.model.Manga, categoryIds: List<Long>) {
        screenModelScope.launch {
            val updateManga = Injekt.get<eu.kanade.domain.manga.interactor.UpdateManga>()
            updateManga.awaitUpdateFavorite(manga.id, true)
            val setMangaCategories = Injekt.get<SetMangaCategories>()
            setMangaCategories.await(manga.id, categoryIds)
            updateLocalFavoriteState(manga.id, true)
            dismissDialog()
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun getLastError(context: android.content.Context): String? {
        return context.getSharedPreferences("suggestions_prefs", android.content.Context.MODE_PRIVATE)
            .getString("last_error", null)
    }

    data class State(
        val isLoading: Boolean = true,
        val isLoadingNext: Boolean = false,
        val loadedRanks: Int = 1,
        val suggestions: List<Suggestion> = emptyList(),
        val tags: List<SuggestionTag> = emptyList(),
        val sources: List<SuggestionSource> = emptyList(),
        val dialog: Dialog? = null,
        val fetchProgress: Int = 0,
        val fetchTotal: Int = 0,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val manga: tachiyomi.domain.manga.model.Manga,
                val initialSelection: kotlinx.collections.immutable.ImmutableList<CheckboxState<tachiyomi.domain.category.model.Category>>,
            ) : Dialog
        }
    }
}
