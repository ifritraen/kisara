package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import eu.kanade.tachiyomi.ui.history.historyTab
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import eu.kanade.tachiyomi.ui.updates.updatesTab
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
data object HomeTab : Tab {
    private fun readResolve(): Any = HomeTab

    val resumeHistoryEvent = Channel<Unit>()
    private val subTabTargetChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    var currentPageIndex by mutableStateOf(0)

    val addFeedEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val sortFeedEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val bulkSelectEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val updatesFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val updatesUpdateLibraryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val updatesCalendarEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val historySearchEvent = Channel<String?>(1, BufferOverflow.DROP_OLDEST)
    val historyFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val historyChecklistEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showSubTab(index: Int) {
        subTabTargetChannel.trySend(index)
    }

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            return TabOptions(
                index = 0u,
                title = stringResource(KMR.strings.label_home),
                icon = rememberVectorPainter(if (isSelected) Icons.Filled.Home else Icons.Outlined.Home),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeHistoryEvent.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val feedScreenModel = rememberScreenModel { FeedScreenModel() }
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }

        val updatesScreenModel = rememberScreenModel { UpdatesScreenModel() }
        val updatesSettingsScreenModel = rememberScreenModel { UpdatesSettingsScreenModel() }

        val historyScreenModel = rememberScreenModel { HistoryScreenModel() }
        val historySettingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
        val historyState by historyScreenModel.state.collectAsState()

        val tabs = persistentListOf(
            feedTab(feedScreenModel, bulkFavoriteScreenModel),
            updatesTab(updatesScreenModel, updatesSettingsScreenModel),
            historyTab(historyScreenModel, historySettingsScreenModel),
        )

        val state = rememberPagerState { tabs.size }

        LaunchedEffect(Unit) {
            subTabTargetChannel.receiveAsFlow().collectLatest {
                state.scrollToPage(it)
            }
        }

        LaunchedEffect(state.currentPage) {
            currentPageIndex = state.currentPage
        }

        val showingFeedOrderScreen = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch {
                addFeedEvent.receiveAsFlow().collectLatest {
                    feedScreenModel.openAddDialog()
                }
            }
            launch {
                sortFeedEvent.receiveAsFlow().collectLatest {
                    showingFeedOrderScreen.value = !showingFeedOrderScreen.value
                }
            }
            launch {
                bulkSelectEvent.receiveAsFlow().collectLatest {
                    bulkFavoriteScreenModel.toggleSelectionMode()
                }
            }
            launch {
                updatesFilterEvent.receiveAsFlow().collectLatest {
                    updatesScreenModel.showFilterDialog()
                }
            }
            launch {
                updatesUpdateLibraryEvent.receiveAsFlow().collectLatest {
                    updatesScreenModel.updateLibrary()
                }
            }
            launch {
                updatesCalendarEvent.receiveAsFlow().collectLatest {
                    navigator.push(UpcomingScreen())
                }
            }
            launch {
                historySearchEvent.receiveAsFlow().collectLatest {
                    historyScreenModel.updateSearchQuery("")
                }
            }
            launch {
                historyFilterEvent.receiveAsFlow().collectLatest {
                    historyScreenModel.showFilterDialog()
                }
            }
            launch {
                historyChecklistEvent.receiveAsFlow().collectLatest {
                    historyScreenModel.toggleSelectionMode()
                }
            }
        }

        val scope = rememberCoroutineScope()
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsState()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsState()
        val hazeState = LocalHazeState.current

        var bottomBarVisible by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (delta < -10f) {
                        if (bottomBarVisible) {
                            bottomBarVisible = false
                            scope.launch { HomeScreen.showBottomNav(false) }
                        }
                    } else if (delta > 10f) {
                        if (!bottomBarVisible) {
                            bottomBarVisible = true
                            scope.launch { HomeScreen.showBottomNav(true) }
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier)
                .nestedScroll(nestedScrollConnection),
        ) {
            TabbedScreen(
                titleRes = KMR.strings.label_home,
                tabs = tabs,
                state = state,
                searchQuery = if (state.currentPage == 2) historyState.searchQuery else null,
                onChangeSearchQuery = { query ->
                    if (state.currentPage == 2) {
                        historyScreenModel.updateSearchQuery(query)
                    }
                },
                feedScreenModel = feedScreenModel,
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                showAppBar = false,
                showTabs = if (showTopTabBar) {
                    if (hideTopBarOnScroll) bottomBarVisible else true
                } else {
                    false
                },
            )
        }
    }
}
// KMK <--
