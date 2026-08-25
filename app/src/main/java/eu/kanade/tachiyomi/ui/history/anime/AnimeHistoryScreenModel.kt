package eu.kanade.tachiyomi.ui.history.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.history.anime.AnimeHistoryUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId

class AnimeHistoryScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val removeAnimeHistory: RemoveAnimeHistory = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
) : StateScreenModel<AnimeHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    getAnimeHistory.subscribe(query ?: "")
                        .distinctUntilChanged()
                        .catch {
                            logcat(LogPriority.ERROR, it)
                            _events.send(Event.InternalError)
                        }
                        .map { toUiModels(it) }
                        .flowOn(Dispatchers.IO)
                }
                .collectLatest { list ->
                    mutableState.update { it.copy(list = list.toImmutableList()) }
                }
        }
    }

    private fun toUiModels(historyList: List<AnimeHistoryWithRelations>): List<AnimeHistoryUiModel> {
        val map = historyList.groupBy { history ->
            history.seenAt?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.MIN
        }

        val uiModels = mutableListOf<AnimeHistoryUiModel>()
        for ((date, items) in map) {
            uiModels.add(AnimeHistoryUiModel.Header(date))
            items.forEach { uiModels.add(AnimeHistoryUiModel.Item(it)) }
        }
        return uiModels
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun removeFromHistory(history: AnimeHistoryWithRelations) {
        screenModelScope.launchIO {
            removeAnimeHistory.await(history)
        }
    }

    fun removeAllFromHistory(animeId: Long) {
        screenModelScope.launchIO {
            removeAnimeHistory.await(animeId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val success = removeAnimeHistory.awaitAll()
            if (success) {
                _events.send(Event.HistoryCleared)
            } else {
                _events.send(Event.InternalError)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    suspend fun getNextEpisode(animeId: Long, episodeId: Long): tachiyomi.domain.items.episode.model.Episode? {
        val episodes = getNextEpisodes.await(animeId, episodeId, onlyUnseen = false)
        return episodes.firstOrNull()
    }

    sealed interface Dialog {
        data class Delete(val history: AnimeHistoryWithRelations) : Dialog
        data object DeleteAll : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data object HistoryCleared : Event
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: ImmutableList<AnimeHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )
}
