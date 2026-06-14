package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
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
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
data object HomeTab : Tab {
    private fun readResolve(): Any = HomeTab

    val resumeHistoryEvent = Channel<Unit>()
    private val subTabTargetChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

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
        )
    }
}
// KMK <--
