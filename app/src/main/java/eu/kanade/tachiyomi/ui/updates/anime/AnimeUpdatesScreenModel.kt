package eu.kanade.tachiyomi.ui.updates.anime

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.updates.anime.AnimeUpdatesUiModel
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AnimeUpdatesItem(
    val update: AnimeUpdatesWithRelations,
    val selected: Boolean = false,
)

class AnimeUpdatesScreenModel(
    private val getAnimeUpdates: GetAnimeUpdates = Injekt.get(),
) : StateScreenModel<AnimeUpdatesScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val calendar = Instant.now().minus(30, ChronoUnit.DAYS)
            getAnimeUpdates.subscribe(calendar)
                .catch { logcat(LogPriority.ERROR, it) }
                .flowOn(Dispatchers.IO)
                .collectLatest { list ->
                    val items = list.map { AnimeUpdatesItem(it) }
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = items.toPersistentList(),
                        )
                    }
                }
        }
    }

    fun toggleSelection(item: AnimeUpdatesItem, selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                if (it.update.episodeId == item.update.episodeId) it.copy(selected = selected) else it
            }
            state.copy(items = newItems.toPersistentList())
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map { it.copy(selected = selected) }
            state.copy(items = newItems.toPersistentList())
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map { it.copy(selected = !it.selected) }
            state.copy(items = newItems.toPersistentList())
        }
    }

    fun updateLibrary(context: Context) {
        eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob.startNow(context)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: PersistentList<AnimeUpdatesItem> = persistentListOf(),
    ) {
        val selected: List<AnimeUpdatesItem>
            get() = items.filter { it.selected }

        val selectionMode: Boolean
            get() = items.any { it.selected }

        fun getUiModel(): List<AnimeUpdatesUiModel> {
            val map = items.groupBy { item ->
                Instant.ofEpochMilli(item.update.dateFetch)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            val uiModels = mutableListOf<AnimeUpdatesUiModel>()
            for ((date, updateItems) in map) {
                uiModels.add(AnimeUpdatesUiModel.Header(date))
                updateItems.forEach { uiModels.add(AnimeUpdatesUiModel.Item(it)) }
            }
            return uiModels
        }
    }
}
