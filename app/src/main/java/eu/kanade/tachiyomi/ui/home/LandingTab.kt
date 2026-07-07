package eu.kanade.tachiyomi.ui.home

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.more.settings.screen.SettingsHomeScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.lang.toTimestampString
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun landingTab(
    screenModel: LandingScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by screenModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val columnsCount = if (isTabletUi()) 4 else 2

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showSuggestions = uiPreferences.showHomeSuggestions().collectAsState().value
    val showHistory = uiPreferences.showHomeHistory().collectAsState().value
    val showUpdates = uiPreferences.showHomeUpdates().collectAsState().value
    val showLibrary = uiPreferences.showHomeLibrary().collectAsState().value
    val showFeed = uiPreferences.showHomeFeed().collectAsState().value

    return TabContent(
        titleRes = KMR.strings.label_home,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(KMR.strings.pref_home_title),
                icon = Icons.Outlined.Settings,
                onClick = { navigator.push(SettingsHomeScreen) },
            ),
        ),
        content = { paddingValues, _ ->
            PullRefresh(
                refreshing = state.isFeedRefreshing,
                enabled = true,
                onRefresh = {
                    screenModel.triggerBackgroundFeedFetch(force = true)
                    screenModel.loadForgottenFavorites()
                },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 1. Spotlight (Suggestions Carousel)
                    if (showSuggestions && state.suggestions.isNotEmpty()) {
                        item {
                            SpotlightCarousel(
                                suggestions = state.suggestions,
                                tagName = state.suggestionsTagName,
                                onMangaClick = { mangaId -> navigator.push(MangaScreen(mangaId)) },
                            )
                        }
                    }

                    // 2. Continue Reading (History)
                    if (showHistory && state.history.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(KMR.strings.pref_home_section_names_continue_reading),
                                onClickMore = { HomeTab.showSubTab(4) },
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(
                                    items = state.history,
                                    key = { "history-${it.id}" },
                                ) { historyItem ->
                                    val isFirst = state.history.firstOrNull()?.id == historyItem.id
                                    if (isFirst) {
                                        HistoryWideCard(
                                            history = historyItem,
                                            onClick = { navigator.push(MangaScreen(historyItem.mangaId)) },
                                            onResume = {
                                                scope.launch {
                                                    val getNextChapters = Injekt.get<GetNextChapters>()
                                                    val nextChapters = getNextChapters.await(
                                                        historyItem.mangaId,
                                                        historyItem.chapterId,
                                                        onlyUnread = false,
                                                    )
                                                    val chapter = nextChapters.firstOrNull()
                                                    if (chapter != null) {
                                                        val intent = eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(
                                                            context,
                                                            chapter.mangaId,
                                                            chapter.id,
                                                        )
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            },
                                        )
                                    } else {
                                        HistoryCompactCard(
                                            history = historyItem,
                                            onClick = { navigator.push(MangaScreen(historyItem.mangaId)) },
                                        )
                                    }
                                }

                                item {
                                    SeeAllEndCard(width = 80.dp, height = 120.dp, onClick = { HomeTab.showSubTab(4) })
                                }
                            }
                        }
                    }

                    // 3. Fresh Releases (Updates)
                    if (showUpdates && state.updates.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(KMR.strings.pref_home_section_names_fresh_releases),
                                onClickMore = { HomeTab.showSubTab(3) },
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(
                                    items = state.updates,
                                    key = { "update-${it.chapterId}" },
                                ) { updateItem ->
                                    MangaCoverCard(
                                        mangaId = updateItem.mangaId,
                                        coverData = updateItem.coverData,
                                        title = updateItem.mangaTitle,
                                        badgeText = updateItem.chapterName,
                                        onClick = { navigator.push(MangaScreen(updateItem.mangaId)) },
                                    )
                                }

                                item {
                                    SeeAllEndCard(onClick = { HomeTab.showSubTab(3) })
                                }
                            }
                        }
                    }

                    // 4. Forgotten Favorites (Library)
                    if (showLibrary && state.libraryRandom.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(KMR.strings.pref_home_section_names_forgotten_favorites),
                                onClickMore = null,
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(
                                    items = state.libraryRandom,
                                    key = { "forgotten-${it.id}" },
                                ) { manga ->
                                    MangaCoverCard(
                                        mangaId = manga.id,
                                        coverData = manga.asMangaCover(),
                                        title = manga.title,
                                        badgeText = null,
                                        onClick = { navigator.push(MangaScreen(manga.id)) },
                                    )
                                }
                            }
                        }
                    }

                    // 5. Explore Feed
                    if (showFeed) {
                        item {
                            SectionHeader(
                                title = stringResource(KMR.strings.pref_home_section_names_explore_feed),
                                onClickMore = { HomeTab.showSubTab(1) },
                            )
                        }

                        if (state.feed.isEmpty() && state.isFeedRefreshing) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (state.feed.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Pull down to refresh feed.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            val chunkedFeed = state.feed.chunked(columnsCount)
                            items(chunkedFeed) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            FeedItemCard(
                                                item = item,
                                                onClick = { navigator.push(MangaScreen(item.id)) },
                                            )
                                        }
                                    }
                                    if (rowItems.size < columnsCount) {
                                        repeat(columnsCount - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SpotlightCarousel(
    suggestions: List<Suggestion>,
    tagName: String?,
    onMangaClick: (Long) -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val autoplay by uiPreferences.homeSuggestionsAutoplay().collectAsState()
    val autoplayInterval by uiPreferences.homeSuggestionsAutoplayInterval().collectAsState()

    val pagerState = rememberPagerState { suggestions.size }

    // Auto-scroll loop
    LaunchedEffect(suggestions, autoplay, autoplayInterval) {
        while (autoplay && suggestions.size > 1) {
            delay(autoplayInterval * 1000L)
            val next = (pagerState.currentPage + 1) % suggestions.size
            pagerState.animateScrollToPage(next)
        }
    }

    val currentSuggestion = suggestions.getOrNull(pagerState.currentPage)
    val currentManga = currentSuggestion?.manga
    val currentCover = remember(currentManga) { currentManga?.asMangaCover() }
    val vibrantColor = currentCover?.vibrantCoverColor ?: currentCover?.dominantCoverColors?.first
    val glowColor = vibrantColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant

    val animatedGlowColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientGlow",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Text(
            text = if (tagName != null) "Recommended: #$tagName" else "Spotlight",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val suggestion = suggestions[page]
            val manga = suggestion.manga
            val coverData = manga.asMangaCover()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMangaClick(manga.id) },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Blur Background Cover
                    MangaCover.Book(
                        data = coverData,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp),
                    )

                    // Black gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Black.copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                    )

                    // Content Split Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaCover.Book(
                            data = coverData,
                            modifier = Modifier
                                .width(96.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp)),
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = manga.author.orEmpty().ifBlank { "Unknown Author" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = manga.description.orEmpty().ifBlank { "No description available." },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onClickMore: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (onClickMore != null) {
            IconButton(
                onClick = onClickMore,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "See All",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun HistoryWideCard(
    history: HistoryWithRelations,
    onClick: () -> Unit,
    onResume: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier
            .width(320.dp)
            .height(120.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                data = history.coverData,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp)),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = history.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (history.chapterNumber > -1) {
                            "Ch. ${formatChapterNumber(history.chapterNumber)}"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = history.readAt?.toTimestampString() ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    IconButton(
                        onClick = onResume,
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCompactCard(
    history: HistoryWithRelations,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
    ) {
        MangaCover.Book(
            data = history.coverData,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun MangaCoverCard(
    mangaId: Long,
    coverData: tachiyomi.domain.manga.model.MangaCover,
    title: String,
    badgeText: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            MangaCover.Book(
                data = coverData,
                modifier = Modifier.fillMaxSize(),
            )

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SeeAllEndCard(
    width: androidx.compose.ui.unit.Dp = 80.dp,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "See All",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun FeedItemCard(
    item: CachedFeedManga,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val coverData = remember(item.id, item.sourceId, item.favorite, item.thumbnailUrl, item.coverLastModified) {
                tachiyomi.domain.manga.model.MangaCover(
                    mangaId = item.id,
                    sourceId = item.sourceId,
                    isMangaFavorite = item.favorite,
                    ogUrl = item.thumbnailUrl,
                    lastModified = item.coverLastModified,
                )
            }

            MangaCover.Book(
                data = coverData,
                modifier = Modifier.fillMaxSize(),
                alpha = if (item.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
            )

            // Bottom Gradient Overlay for Title
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f),
                            ),
                            startY = 180f,
                        ),
                    ),
            )

            // Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Left: Extension Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        text = item.sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Bottom: Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
