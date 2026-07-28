package eu.kanade.tachiyomi.ui.more.logbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.logbook.LogbookScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.logbook.interactor.ClearLogbook
import tachiyomi.domain.logbook.interactor.GetLogbookEntries
import tachiyomi.domain.logbook.model.LogbookActionType
import tachiyomi.domain.logbook.model.LogbookEntry
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LogbookTabScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LogbookScreenModel() }
        val state by screenModel.state.collectAsState()

        LogbookScreen(
            isLoading = state.isLoading,
            entries = state.entries,
            selectedType = state.selectedType,
            searchQuery = state.searchQuery,
            onSelectType = screenModel::selectType,
            onSearchQueryChange = screenModel::setSearchQuery,
            onClearAll = screenModel::clearAll,
            onNavigateBack = { navigator.pop() },
            onNavigateToManga = { mangaId -> navigator.push(MangaScreen(mangaId)) },
            onNavigateToCategory = { navigator.push(CategoryScreen()) },
            onNavigateToSettings = { _ ->
                navigator.push(SettingsScreen())
            },
            onNavigateToExtensions = {
                // Navigate back and select extension tab
                navigator.pop()
            },
        )
    }
}

data class LogbookState(
    val isLoading: Boolean = true,
    val entries: List<LogbookEntry> = emptyList(),
    val selectedType: LogbookActionType? = null,
    val searchQuery: String = "",
)

class LogbookScreenModel(
    private val getLogbookEntries: GetLogbookEntries = Injekt.get(),
    private val clearLogbook: ClearLogbook = Injekt.get(),
) : StateScreenModel<LogbookState>(LogbookState()) {

    @Suppress("ktlint:standard:backing-property-naming")
    private val _selectedType = MutableStateFlow<LogbookActionType?>(null)

    @Suppress("ktlint:standard:backing-property-naming")
    private val _searchQuery = MutableStateFlow("")

    init {
        screenModelScope.launchIO {
            kotlinx.coroutines.flow.combine(
                _selectedType,
                _searchQuery,
            ) { type, query -> Pair(type, query) }
                .flatMapLatest { (type, query) ->
                    getLogbookEntries.subscribe(type, query)
                }
                .collectLatest { entries ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        entries = entries,
                    )
                }
        }
    }

    fun selectType(type: LogbookActionType?) {
        _selectedType.value = type
        mutableState.value = mutableState.value.copy(selectedType = type)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun clearAll() {
        screenModelScope.launchIO {
            clearLogbook.await()
        }
    }
}
