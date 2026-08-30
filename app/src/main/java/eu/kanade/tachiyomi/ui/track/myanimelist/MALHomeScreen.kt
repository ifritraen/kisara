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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALHomeSection
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMediaItem
import kotlinx.coroutines.launch

// KMK -->
/**
 * MyAnimeList Home subtab with nested subsubtabs (Landing + individual sections).
 */
@Composable
fun MALHomeScreen(
    sections: List<MALHomeSection>,
    isLoading: Boolean,
    onItemClick: (MALMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading && sections.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (sections.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No content available. Pull to refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val subSubTabs = buildList {
        add("Landing")
        sections.forEach { add(it.title) }
    }

    val pagerState = rememberPagerState { subSubTabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Sub-sub-tab Bar
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            if (page == 0) {
                // Landing Page: Stack of horizontal carousels
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(sections.size, key = { sections[it].key }) { index ->
                        val section = sections[index]
                        MALSectionCarousel(
                            title = section.title,
                            items = section.items,
                            onItemClick = onItemClick,
                            onViewAllClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index + 1)
                                }
                            },
                        )
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
                        items(section.items, key = { it.id }) { item ->
                            MALMediaCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                width = 110,
                            )
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
