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
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
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
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.anime.feed.animeFeedTab
import eu.kanade.tachiyomi.ui.browse.novel.feed.novelFeedTab
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.historyTab
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.suggestions.SuggestionsScreenModel
import eu.kanade.tachiyomi.ui.suggestions.anime.animeSuggestionsTab
import eu.kanade.tachiyomi.ui.suggestions.novel.novelSuggestionsTab
import eu.kanade.tachiyomi.ui.suggestions.suggestionsTab
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.novel.novelUpdatesTab
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
    val nextSubTabEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val prevSubTabEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    var currentPageIndex by mutableStateOf(0)

    val addFeedEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val sortFeedEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val bulkSelectEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val suggestionsRefreshEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val updatesFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val updatesUpdateLibraryEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val updatesCalendarEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val historySearchEvent = Channel<String?>(1, BufferOverflow.DROP_OLDEST)
    val historyFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val historyChecklistEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showSubTab(index: Int) {
        currentPageIndex = index
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
        val context = androidx.compose.ui.platform.LocalContext.current

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val activeMediaType by uiPreferences.activeMediaType().collectAsStateWithLifecycle()

        val landingScreenModel = rememberScreenModel { LandingScreenModel() }

        val feedScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { FeedScreenModel() } else null
        val bulkFavoriteScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { BulkFavoriteScreenModel() } else null
        val suggestionsScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { SuggestionsScreenModel() } else null
        val updatesScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { UpdatesScreenModel() } else null
        val updatesSettingsScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { UpdatesSettingsScreenModel() } else null
        val historyScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { HistoryScreenModel() } else null
        val historySettingsScreenModel = if (activeMediaType == MediaType.MANGA) rememberScreenModel { HistorySettingsScreenModel() } else null
        val historyState by (historyScreenModel?.state ?: kotlinx.coroutines.flow.MutableStateFlow(HistoryScreenModel.State())).collectAsStateWithLifecycle()

        val animeFeedScreenModel = if (activeMediaType == MediaType.ANIME) rememberScreenModel { eu.kanade.tachiyomi.ui.browse.anime.feed.AnimeFeedScreenModel() } else null
        val animeSuggestionsScreenModel = if (activeMediaType == MediaType.ANIME) rememberScreenModel { eu.kanade.tachiyomi.ui.suggestions.anime.AnimeSuggestionsScreenModel() } else null
        val animeUpdatesScreenModel = if (activeMediaType == MediaType.ANIME) rememberScreenModel { eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel() } else null
        val animeHistoryScreenModel = if (activeMediaType == MediaType.ANIME) rememberScreenModel { eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel() } else null

        val novelFeedScreenModel = if (activeMediaType == MediaType.NOVEL) rememberScreenModel { eu.kanade.tachiyomi.ui.browse.novel.feed.NovelFeedScreenModel() } else null
        val novelSuggestionsScreenModel = if (activeMediaType == MediaType.NOVEL) rememberScreenModel { eu.kanade.tachiyomi.ui.suggestions.novel.NovelSuggestionsScreenModel() } else null
        val novelUpdatesScreenModel = if (activeMediaType == MediaType.NOVEL) rememberScreenModel { eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesScreenModel() } else null
        val novelHistoryScreenModel = if (activeMediaType == MediaType.NOVEL) rememberScreenModel { eu.kanade.tachiyomi.ui.history.novel.NovelHistoryScreenModel() } else null

        val tabs = when (activeMediaType) {
            MediaType.NOVEL -> persistentListOf(
                landingTab(landingScreenModel),
                this.novelFeedTab(),
                if (novelSuggestionsScreenModel != null) this.novelSuggestionsTab(novelSuggestionsScreenModel) else this.novelSuggestionsTab(),
                if (novelUpdatesScreenModel != null) this.novelUpdatesTab(context, fromMore = false, novelUpdatesScreenModel) else this.novelUpdatesTab(context, fromMore = false),
                if (novelHistoryScreenModel != null) this.novelHistoryTab(context, fromMore = false, novelHistoryScreenModel) else this.novelHistoryTab(context, fromMore = false),
                eu.kanade.tachiyomi.ui.home.favorite.favoritesTab(),
            )
            MediaType.ANIME -> persistentListOf(
                landingTab(landingScreenModel),
                if (animeFeedScreenModel != null) this.animeFeedTab(animeFeedScreenModel) else this.animeFeedTab(),
                if (animeSuggestionsScreenModel != null) this.animeSuggestionsTab(animeSuggestionsScreenModel) else this.animeSuggestionsTab(),
                if (animeUpdatesScreenModel != null) this.animeUpdatesTab(context, fromMore = false, animeUpdatesScreenModel) else this.animeUpdatesTab(context, fromMore = false),
                if (animeHistoryScreenModel != null) this.animeHistoryTab(context, fromMore = false, animeHistoryScreenModel) else this.animeHistoryTab(context, fromMore = false),
                eu.kanade.tachiyomi.ui.home.favorite.favoritesTab(),
            )
            MediaType.MANGA -> persistentListOf(
                landingTab(landingScreenModel),
                feedTab(feedScreenModel!!, bulkFavoriteScreenModel!!),
                suggestionsTab(suggestionsScreenModel!!),
                updatesTab(updatesScreenModel!!, updatesSettingsScreenModel!!),
                historyTab(historyScreenModel!!, historySettingsScreenModel!!),
                eu.kanade.tachiyomi.ui.home.favorite.favoritesTab(),
            )
        }

        val state = rememberPagerState(initialPage = currentPageIndex) { tabs.size }

        LaunchedEffect(Unit) {
            launch {
                subTabTargetChannel.receiveAsFlow().collectLatest {
                    if (kotlin.math.abs(it - state.currentPage) <= 1 && state.currentPageOffsetFraction == 0f) {
                        state.animateScrollToPage(it)
                    } else {
                        state.scrollToPage(it)
                    }
                }
            }
            launch {
                nextSubTabEvent.receiveAsFlow().collectLatest {
                    val totalTabs = tabs.size
                    if (totalTabs > 1) {
                        val next = (state.currentPage + 1).coerceAtMost(totalTabs - 1)
                        state.animateScrollToPage(next)
                    }
                }
            }
            launch {
                prevSubTabEvent.receiveAsFlow().collectLatest {
                    val totalTabs = tabs.size
                    if (totalTabs > 1) {
                        val prev = (state.currentPage - 1).coerceAtLeast(0)
                        state.animateScrollToPage(prev)
                    }
                }
            }
        }

        LaunchedEffect(state) {
            snapshotFlow { state.settledPage }.collect {
                currentPageIndex = it
            }
        }

        val showingFeedOrderScreen = remember { mutableStateOf(false) }

        LaunchedEffect(activeMediaType) {
            launch {
                addFeedEvent.receiveAsFlow().collectLatest {
                    when (activeMediaType) {
                        MediaType.ANIME -> animeFeedScreenModel?.openAddSourceDialog()
                        MediaType.NOVEL -> novelFeedScreenModel?.openAddSourceDialog()
                        MediaType.MANGA -> feedScreenModel?.openAddDialog()
                    }
                }
            }
            launch {
                sortFeedEvent.receiveAsFlow().collectLatest {
                    when (activeMediaType) {
                        MediaType.ANIME -> animeFeedScreenModel?.toggleReordering()
                        MediaType.NOVEL -> novelFeedScreenModel?.toggleReordering()
                        MediaType.MANGA -> showingFeedOrderScreen.value = !showingFeedOrderScreen.value
                    }
                }
            }
            launch {
                bulkSelectEvent.receiveAsFlow().collectLatest {
                    bulkFavoriteScreenModel?.toggleSelectionMode()
                }
            }
            launch {
                suggestionsRefreshEvent.receiveAsFlow().collectLatest {
                    if (activeMediaType == MediaType.ANIME) {
                        animeSuggestionsScreenModel?.loadSuggestions()
                    } else if (activeMediaType == MediaType.NOVEL) {
                        novelSuggestionsScreenModel?.loadSuggestions()
                    } else {
                        suggestionsScreenModel?.triggerRefresh(context)
                    }
                }
            }
            launch {
                updatesFilterEvent.receiveAsFlow().collectLatest {
                    updatesScreenModel?.showFilterDialog()
                }
            }
            launch {
                updatesUpdateLibraryEvent.receiveAsFlow().collectLatest {
                    if (activeMediaType == MediaType.ANIME) {
                        animeUpdatesScreenModel?.updateLibrary(context)
                    } else if (activeMediaType == MediaType.NOVEL) {
                        novelUpdatesScreenModel?.updateLibrary()
                    } else {
                        updatesScreenModel?.updateLibrary()
                    }
                }
            }
            launch {
                updatesCalendarEvent.receiveAsFlow().collectLatest {
                    navigator.push(UpcomingScreen())
                }
            }
            launch {
                historySearchEvent.receiveAsFlow().collectLatest { query ->
                    when (activeMediaType) {
                        MediaType.ANIME -> animeHistoryScreenModel?.search(query ?: "")
                        MediaType.NOVEL -> novelHistoryScreenModel?.search(query ?: "")
                        MediaType.MANGA -> historyScreenModel?.updateSearchQuery(query ?: "")
                    }
                }
            }
            launch {
                historyFilterEvent.receiveAsFlow().collectLatest {
                    historyScreenModel?.showFilterDialog()
                }
            }
            launch {
                historyChecklistEvent.receiveAsFlow().collectLatest {
                    if (activeMediaType == MediaType.ANIME) {
                        animeHistoryScreenModel?.setDialog(eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel.Dialog.DeleteAll)
                    } else if (activeMediaType == MediaType.NOVEL) {
                        novelHistoryScreenModel?.setDialog(eu.kanade.tachiyomi.ui.history.novel.NovelHistoryScreenModel.Dialog.DeleteAll)
                    } else {
                        historyScreenModel?.toggleSelectionMode()
                    }
                }
            }
        }

        val scope = rememberCoroutineScope()
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsStateWithLifecycle()
        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsStateWithLifecycle()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsStateWithLifecycle()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsStateWithLifecycle()
        val hazeState = LocalHazeState.current

        val bottomBarVisible by HomeScreen.showBottomNavFlow.collectAsStateWithLifecycle()

        val animeHistorySearchQuery = animeHistoryScreenModel?.state?.collectAsStateWithLifecycle()?.value?.searchQuery
        val novelHistorySearchQuery = novelHistoryScreenModel?.state?.collectAsStateWithLifecycle()?.value?.searchQuery

        val activeHistorySearchQuery = if (state.currentPage == 4) {
            when (activeMediaType) {
                MediaType.ANIME -> animeHistorySearchQuery
                MediaType.NOVEL -> novelHistorySearchQuery
                MediaType.MANGA -> historyState.searchQuery
            }
        } else null

        val onActiveHistorySearchQueryChange: (String?) -> Unit = { query ->
            if (state.currentPage == 4) {
                when (activeMediaType) {
                    MediaType.ANIME -> animeHistoryScreenModel?.search(query)
                    MediaType.NOVEL -> novelHistoryScreenModel?.search(query)
                    MediaType.MANGA -> historyScreenModel?.updateSearchQuery(query)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier),
        ) {
            TabbedScreen(
                titleRes = KMR.strings.label_home,
                tabs = tabs,
                state = state,
                searchQuery = activeHistorySearchQuery,
                onChangeSearchQuery = onActiveHistorySearchQueryChange,
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

        val updatesState = updatesScreenModel?.state?.collectAsStateWithLifecycle()?.value
        if (updatesState?.dialog is UpdatesScreenModel.Dialog.FilterSheet && updatesSettingsScreenModel != null && updatesScreenModel != null) {
            eu.kanade.presentation.updates.UpdatesFilterDialog(
                onDismissRequest = { updatesScreenModel.setDialog(null) },
                screenModel = updatesSettingsScreenModel,
            )
        }

        if (historyState.dialog is HistoryScreenModel.Dialog.FilterSheet && historySettingsScreenModel != null && historyScreenModel != null) {
            eu.kanade.presentation.history.components.HistoryFilterDialog(
                onDismissRequest = { historyScreenModel.setDialog(null) },
                screenModel = historySettingsScreenModel,
            )
        }
    }
}
// KMK <--
