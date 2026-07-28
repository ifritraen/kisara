package eu.kanade.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TabbedScreen(
    titleRes: StringResource,
    tabs: ImmutableList<TabContent>,
    state: PagerState = rememberPagerState { tabs.size },
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    showAppBar: Boolean = true,
    showTabs: Boolean = true,
    // KMK -->
    feedScreenModel: FeedScreenModel,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    // KMK <--
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // KMK -->
    val feedState by feedScreenModel.state.collectAsState()
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    // KMK <--

    Scaffold(
        topBar = {
            if (showAppBar || searchQuery != null) {
                val tab = tabs[state.currentPage]
                val searchEnabled = tab.searchEnabled
                // KMK -->
                if (bulkFavoriteState.selectionMode) {
                    BulkSelectionToolbar(
                        selectedCount = bulkFavoriteState.selection.size,
                        isRunning = bulkFavoriteState.isRunning,
                        onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                        onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                        onSelectAll = {
                            feedState.items?.let { result ->
                                result.mapNotNull { it.results }
                                    .flatten()
                                    .forEach { bulkFavoriteScreenModel.select(it) }
                            }
                        },
                        onReverseSelection = {
                            feedState.items?.let { result ->
                                result.mapNotNull { it.results }
                                    .flatten()
                                    .let { bulkFavoriteScreenModel.reverseSelection(it) }
                            }
                        },
                    )
                } else {
                    // KMK <--
                    SearchToolbar(
                        titleContent = { AppBarTitle(stringResource(titleRes)) },
                        searchEnabled = searchEnabled,
                        searchQuery = if (searchEnabled) searchQuery else null,
                        onChangeSearchQuery = onChangeSearchQuery,
                        actions = { AppBarActions(tab.actions) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                )
                .then(
                    if (showAppBar || searchQuery != null) {
                        Modifier.padding(top = contentPadding.calculateTopPadding())
                    } else {
                        Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    },
                ),
        ) {
            AnimatedVisibility(
                visible = showTabs,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                val tabRowContent = @Composable {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.currentPage == index,
                            onClick = { scope.launch { state.animateScrollToPage(index) } },
                            text = { TabText(text = stringResource(tab.titleRes), badgeCount = tab.badgeNumber) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(36.dp),
                        )
                    }
                }
                if (tabs.size > 4) {
                    androidx.compose.material3.PrimaryScrollableTabRow(
                        selectedTabIndex = state.currentPage,
                        edgePadding = 0.dp,
                        modifier = Modifier.zIndex(1f).height(36.dp),
                        tabs = tabRowContent,
                    )
                } else {
                    PrimaryTabRow(
                        selectedTabIndex = state.currentPage,
                        modifier = Modifier.zIndex(1f).height(36.dp),
                        tabs = tabRowContent,
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val uiPreferences = remember { Injekt.get<UiPreferences>() }
                val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
                val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
                val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
                val standardBottomBarHeight by uiPreferences.standardBottomBarHeight().collectAsState()
                val standardBottomBarBottomMargin by uiPreferences.standardBottomBarBottomMargin().collectAsState()

                val totalBottomInset = if (floatingBottomBar) {
                    (bottomBarHeight + bottomBarBottomMargin + 24).dp
                } else {
                    (standardBottomBarHeight + standardBottomBarBottomMargin + 24).dp
                }

                val bottomPadding = contentPadding.calculateBottomPadding() + totalBottomInset

                tabs[page].content(
                    PaddingValues(bottom = bottomPadding),
                    snackbarHostState,
                )
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)
