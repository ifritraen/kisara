package eu.kanade.tachiyomi.ui.suggestions

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.suggestions.interactor.GetSuggestions
import tachiyomi.domain.suggestions.model.Suggestion
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuggestionsScreenModel(
    private val getSuggestions: GetSuggestions = Injekt.get(),
) : StateScreenModel<SuggestionsScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getSuggestions.subscribe().collectLatest { list ->
                mutableState.update { it.copy(isLoading = false, suggestions = list) }
            }
        }
    }

    fun triggerRefresh(context: android.content.Context) {
        mutableState.update { it.copy(isLoading = true) }
        val request = androidx.work.OneTimeWorkRequestBuilder<SuggestionsWorker>().build()
        androidx.work.WorkManager.getInstance(context).enqueue(request)
    }

    data class State(
        val isLoading: Boolean = true,
        val suggestions: List<Suggestion> = emptyList(),
    )
}
