package eu.kanade.presentation.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.browse.local.LocalMangaItem
import eu.kanade.tachiyomi.ui.browse.local.LocalScreenModel
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.UnreadBadge
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun LibraryLocalContent(
    contentPadding: PaddingValues,
    onClickManga: (Long) -> Unit,
    categoryNamesByMangaId: Map<Long, List<String>> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val screenModel = remember { LocalScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val bottomPadding = contentPadding.calculateBottomPadding()
    val startPadding = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = startPadding, end = endPadding),
    ) {
        // Dual Sub-tabs: Local and Downloaded
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "${stringResource(MR.strings.label_local)}${if (state.localManga.isNotEmpty()) " (${state.localManga.size})" else ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "${stringResource(MR.strings.label_downloaded)}${if (state.downloadedManga.isNotEmpty()) " (${state.downloadedManga.size})" else ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }

        // Search Bar row for filtering within local/downloaded
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = screenModel::search,
            placeholder = { Text(stringResource(MR.strings.action_search_hint), fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { screenModel.search("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        PullRefresh(
            refreshing = state.isLoading,
            enabled = true,
            onRefresh = screenModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "localSubTabTransition",
                modifier = Modifier.fillMaxSize(),
            ) { tabIndex ->
                val items: List<LocalMangaItem> = if (tabIndex == 0) state.filteredLocalManga else state.filteredDownloadedManga
                val isTabLoading = if (tabIndex == 0) state.isLocalLoading else state.isDownloadedLoading

                when {
                    isTabLoading && items.isEmpty() -> {
                        LoadingScreen()
                    }
                    items.isEmpty() -> {
                        EmptyScreen(
                            message = if (state.searchQuery.isNotEmpty()) {
                                stringResource(MR.strings.no_results_found)
                            } else {
                                stringResource(MR.strings.empty_screen)
                            },
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = bottomPadding + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = items,
                                key = { "${tabIndex}_${it.manga.id}" },
                            ) { item ->
                                val badges = if (tabIndex == 1) {
                                    item.categoryBadges.ifEmpty { categoryNamesByMangaId[item.manga.id].orEmpty() }
                                } else {
                                    item.categoryBadges
                                }
                                MangaComfortableGridItem(
                                    coverData = item.manga.asMangaCover(),
                                    title = item.manga.title,
                                    manga = item.manga,
                                    coverBadgeStart = {
                                        if (item.downloadCount > 0) {
                                            DownloadsBadge(count = item.downloadCount.toLong())
                                        }
                                        if (item.unreadCount > 0) {
                                            UnreadBadge(count = item.unreadCount)
                                        }
                                    },
                                    onClick = { onClickManga(item.manga.id) },
                                    onLongClick = { onClickManga(item.manga.id) },
                                    usePanoramaCover = false,
                                    categoryBadges = badges,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
