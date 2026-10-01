package eu.kanade.tachiyomi.ui.browse.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.anime.bulk.animeBulkSearchTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.bulk.bulkSearchTab
import eu.kanade.tachiyomi.ui.browse.novel.bulk.novelBulkSearchTab
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.search.components.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
object SearchTabEvents {
    val nextSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val prevSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val selectSubTabEvent = kotlinx.coroutines.channels.Channel<Int>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var currentPageIndex = 0
}

fun Screen.searchTab(
    mediaType: MediaType = MediaType.MANGA,
): TabContent {
    return TabContent(
        titleRes = KMR.strings.bulk_search,
        searchEnabled = false,
        content = { contentPadding, _ ->
            SearchTabContent(
                screen = this,
                mediaType = mediaType,
                contentPadding = contentPadding,
            )
        },
    )
}

@Composable
private fun SearchTabContent(
    screen: Screen,
    mediaType: MediaType,
    contentPadding: PaddingValues,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenModel = screen.rememberScreenModel { SearchScreenModel(mediaType = mediaType) }
    val state by screenModel.state.collectAsState()

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
    val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()
    val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
    val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
    val standardBottomBarHeight by uiPreferences.standardBottomBarHeight().collectAsState()
    val standardBottomBarBottomMargin by uiPreferences.standardBottomBarBottomMargin().collectAsState()
    val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsState()

    val bottomBarGap = ((if (floatingBottomBar) (bottomBarHeight + bottomBarBottomMargin) else (standardBottomBarHeight + standardBottomBarBottomMargin)) + subTabsBottomMargin).coerceAtLeast(0).dp

    LaunchedEffect(Unit) {
        eu.kanade.tachiyomi.ui.browse.search.model.TagDictionary.initialize(context)
    }

    val tabTitles = persistentListOf(
        stringResource(KMR.strings.tab_global_search),
        stringResource(KMR.strings.tab_bulk_search),
        stringResource(KMR.strings.tab_advanced_search),
    )
    val pagerState = rememberPagerState(initialPage = SearchTabEvents.currentPageIndex.coerceIn(0, tabTitles.size - 1)) { tabTitles.size }

    LaunchedEffect(pagerState.currentPage) {
        SearchTabEvents.currentPageIndex = pagerState.currentPage
    }

    LaunchedEffect(pagerState.pageCount) {
        launch {
            SearchTabEvents.nextSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
        launch {
            SearchTabEvents.prevSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage > 0) {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
        }
        launch {
            SearchTabEvents.selectSubTabEvent.receiveAsFlow().collectLatest { index ->
                if (index in 0 until tabTitles.size) {
                    pagerState.animateScrollToPage(index)
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val subTabContentPadding = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + bottomBarGap + 48.dp,
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            key(mediaType, pageIndex) {
                when (pageIndex) {
                    // Page 0: Global Search
                    0 -> {
                        when (mediaType) {
                            MediaType.ANIME -> GlobalAnimeSearchScreen().Content()
                            MediaType.NOVEL -> GlobalNovelSearchScreen().Content()
                            MediaType.MANGA -> GlobalSearchScreen().Content()
                        }
                    }

                    // Page 1: Bulk Search
                    1 -> {
                        when (mediaType) {
                            MediaType.ANIME -> screen.animeBulkSearchTab().content(subTabContentPadding, snackbarHostState)
                            MediaType.NOVEL -> screen.novelBulkSearchTab().content(subTabContentPadding, snackbarHostState)
                            MediaType.MANGA -> screen.bulkSearchTab().content(subTabContentPadding, snackbarHostState)
                        }
                    }

                    // Page 2: Advanced Search
                    2 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentPadding.calculateTopPadding(), bottom = bottomBarGap + 48.dp),
                        ) {
                            AdvancedSearchScreen(
                                state = state.advancedState,
                                onStateChange = screenModel::updateAdvancedState,
                                onExecuteSearch = { compiledQuery ->
                                    when (mediaType) {
                                        MediaType.ANIME -> navigator.push(GlobalAnimeSearchScreen(compiledQuery))
                                        MediaType.NOVEL -> navigator.push(GlobalNovelSearchScreen(compiledQuery))
                                        MediaType.MANGA -> navigator.push(GlobalSearchScreen(compiledQuery))
                                    }
                                },
                                onResetFilters = screenModel::resetAdvancedFilters,
                            )
                        }
                    }
                }
            }
        }

        // Sub-subtab bar positioned at the bottom if floating subtabs are not enabled or if subtabs are at the top
        if (!floatingBottomBar || showTopTabBar) {
            GlassSurface(
                shape = RoundedCornerShape(16.dp),
                style = GlassDefaults.regularStyle(),
                isStandardSurface = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarGap + 8.dp)
                    .zIndex(2f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
