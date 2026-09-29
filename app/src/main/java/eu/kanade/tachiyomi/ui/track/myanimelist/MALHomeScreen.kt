package eu.kanade.tachiyomi.ui.track.myanimelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALHomeSection
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMediaItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import tachiyomi.presentation.core.components.material.PullRefresh

// KMK -->
/**
 * MyAnimeList Home subtab with nested subsubtabs (Landing + individual sections).
 */
@Composable
fun MALHomeScreen(
    sections: List<MALHomeSection>,
    isLoading: Boolean,
    onRefresh: () -> Unit = {},
    onItemClick: (MALMediaItem) -> Unit,
    onItemLongClick: (MALMediaItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showTrackSubBarAtTop by uiPreferences.showTrackSubBarAtTop().collectAsState()
    val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
    val activeMediaType by uiPreferences.activeMediaType().collectAsState()

    val subSubTabs = remember(sections, activeMediaType) {
        if (sections.isNotEmpty()) {
            buildList {
                add("Landing")
                sections.forEach { add(it.title) }
            }
        } else {
            buildList {
                add("Landing")
                when (activeMediaType) {
                    MediaType.ANIME -> {
                        add("Top Airing")
                        add("All Time Popular")
                        add("Top Ranked")
                        add("Top Upcoming")
                        add("Most Favorited")
                    }
                    MediaType.NOVEL -> {
                        add("Top Light Novels")
                        add("All Time Popular")
                        add("Top Ranked")
                    }
                    else -> {
                        add("All Time Popular")
                        add("Top Ranked")
                        add("Top Manga")
                        add("Top Light Novels")
                        add("Most Favorited")
                    }
                }
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = eu.kanade.tachiyomi.ui.track.TrackTab.currentSubSubTabIndex.coerceIn(0, (subSubTabs.size - 1).coerceAtLeast(0))) { subSubTabs.size }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(subSubTabs) {
        eu.kanade.tachiyomi.ui.track.TrackTab.currentSubSubTabTitles = subSubTabs
    }

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        eu.kanade.tachiyomi.ui.track.TrackTab.currentSubSubTabIndex = pagerState.currentPage
    }

    androidx.compose.runtime.LaunchedEffect(pagerState.pageCount) {
        launch {
            eu.kanade.tachiyomi.ui.track.TrackTab.nextSubSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.track.TrackTab.prevSubSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage > 0) {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.track.TrackTab.selectSubSubTabEvent.receiveAsFlow().collectLatest { index ->
                if (index in 0 until subSubTabs.size) {
                    pagerState.animateScrollToPage(index)
                }
            }
        }
    }

    PullRefresh(
        refreshing = isLoading,
        enabled = true,
        onRefresh = onRefresh,
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            // Sub-sub-tab Bar (only shown when tabs are at top or floating bar is disabled)
            if (showTrackSubBarAtTop || !floatingBottomBar) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, subSubTabs.size - 1),
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    subSubTabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                if (page == 0) {
                    // Landing Page: Stack of horizontal carousels
                    if (sections.isEmpty() && isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            items(
                                count = sections.size,
                                key = { "mal-section-${sections[it].key}-$it" },
                            ) { index ->
                                val section = sections[index]
                                MALSectionCarousel(
                                    title = section.title,
                                    items = section.items,
                                    onItemClick = onItemClick,
                                    onItemLongClick = onItemLongClick,
                                    onViewAllClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index + 1)
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // Dedicated full grid for section
                    val sectionIndex = page - 1
                    if (sectionIndex in sections.indices) {
                        val section = sections[sectionIndex]
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = section.items.size,
                                key = { "mal-grid-${section.key}-${section.items[it].id}-$it" },
                            ) { index ->
                                val item = section.items[index]
                                MALMediaCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onLongClick = onItemLongClick?.let { { it(item) } },
                                    width = 110,
                                )
                            }
                        }
                    } else if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
