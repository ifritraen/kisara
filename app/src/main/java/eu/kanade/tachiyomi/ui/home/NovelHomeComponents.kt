package eu.kanade.tachiyomi.ui.home

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import eu.kanade.tachiyomi.data.coil.staticBlur
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionNsfwScreen
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.toTrackSeriesItem
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.cards.HomeSectionCardStyle
import eu.kanade.presentation.components.cards.KisaraHomeSectionCard
import eu.kanade.presentation.components.cards.KisaraNormalCard
import eu.kanade.presentation.components.cards.NormalCardStyle
import eu.kanade.presentation.library.components.ColorizedBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.library.components.UncensoredBadge
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.home.novel.CachedFeedNovel
import eu.kanade.tachiyomi.ui.home.novel.NovelLandingScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.util.MangaTitleParser
import eu.kanade.tachiyomi.util.lang.toTimestampString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelSpotlightCarousel(
    novels: List<Novel>,
    tagName: String? = null,
    onNovelClick: (Long) -> Unit,
    onNovelLongClick: (Novel) -> Unit,
    onDismissNovel: ((Novel) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (novels.isEmpty()) return

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val autoplay by uiPreferences.homeSuggestionsAutoplay().collectAsState()
    val autoplayInterval by uiPreferences.homeSuggestionsAutoplayInterval().collectAsState()

    val pagerState = rememberPagerState { novels.size }

    LaunchedEffect(novels.size) {
        if (novels.isNotEmpty() && pagerState.currentPage >= novels.size) {
            pagerState.scrollToPage((novels.size - 1).coerceAtLeast(0))
        }
    }

    // Auto-scroll loop
    LaunchedEffect(novels, autoplay, autoplayInterval) {
        while (autoplay && novels.size > 1) {
            delay(autoplayInterval * 1000L)
            if (!pagerState.isScrollInProgress && novels.isNotEmpty()) {
                val next = (pagerState.currentPage + 1) % novels.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    val glowColor = MaterialTheme.colorScheme.primary

    val animatedGlowColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 800),
        label = "novelGlowColor",
    )

    Column(
        modifier = modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (tagName != null) {
                    val cleanTag = tagName.removePrefix("#").trim().replaceFirstChar { it.uppercase() }
                    "Recommended: #$cleanTag"
                } else {
                    "Spotlight"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            val hideNsfwPref = remember { uiPreferences.kisaraHideNsfwSuggestions() }
            val isNsfwBlocked by hideNsfwPref.collectAsState()
            val navigator = LocalNavigator.currentOrThrow

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SpotlightNsfwChip(
                    isBlocked = isNsfwBlocked,
                    onClick = { hideNsfwPref.set(!isNsfwBlocked) },
                )

                IconButton(
                    onClick = { navigator.push(ExtensionNsfwScreen(2)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = tachiyomi.presentation.core.i18n.stringResource(KMR.strings.extension_nsfw_configure_tooltip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val novel = novels.getOrNull(page) ?: return@HorizontalPager
            val coverData = novel.asNovelCover()
            val parsed = remember(novel) {
                MangaTitleParser.parse(novel.title)
            }
            val authorText = remember(novel.author, parsed.author, parsed.artist) {
                val author = novel.author ?: parsed.author
                val artist = parsed.artist
                listOfNotNull(author, artist).distinct().joinToString(" • ").ifBlank { "Unknown" }
            }
            val tags = remember(novel.genre) {
                novel.genre.orEmpty()
            }
            val endingNumber = remember(novel.title) {
                Regex("(\\d+)$").find(novel.title)?.groupValues?.get(1)
            }
            val titleFontSize = 15.sp

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { onNovelClick(novel.id) },
                        onLongClick = { onNovelLongClick(novel) },
                    ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Blurred background cover
                    MangaCover.Book(
                        data = coverData,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp),
                    )

                    // Black gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.82f),
                                    ),
                                ),
                            ),
                    )

                    // Content Split Row
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Thumbnail (Full height flush aspect ratio 2:3)
                        MangaCover.Book(
                            data = coverData,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right Info Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // 1. Title + Dismiss button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Text(
                                        text = parsed.cleanTitle,
                                        fontSize = titleFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (endingNumber != null) {
                                        Text(
                                            text = " $endingNumber",
                                            fontSize = titleFontSize,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (onDismissNovel != null) {
                                    IconButton(
                                        onClick = { onDismissNovel(novel) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Not Interested",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 2. Artist & Author Name
                            Text(
                                text = authorText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 3. Badges Row (Language, Color, Uncensored)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val targetLang = parsed.languageCode
                                if (!targetLang.isNullOrEmpty()) {
                                    LanguageBadge(isLocal = false, sourceLanguage = targetLang)
                                }
                                if (parsed.isColorized) {
                                    ColorizedBadge()
                                }
                                if (parsed.isUncensored) {
                                    UncensoredBadge()
                                }
                            }

                            // 4. Tags List Chips (3 rows horizontally scrollable)
                            if (tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(items = tags) { tag ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NovelContinueReadingShelf(
    historyList: List<NovelHistoryWithRelations>,
    onNovelClick: (Long) -> Unit,
    onResumeChapter: (NovelHistoryWithRelations) -> Unit,
    onClickMore: (() -> Unit)? = null,
    coverTitleStyle: String = "default",
    modifier: Modifier = Modifier,
) {
    if (historyList.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(
            title = tachiyomi.presentation.core.i18n.stringResource(KMR.strings.pref_home_section_names_continue_reading),
            onClickMore = onClickMore,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                items = historyList,
                key = { "novel-history-${it.id}" },
            ) { item ->
                val parsed = remember(item.title) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(item.title) }
                val numberMatch = remember(item.title) { Regex("(\\d+)$").find(item.title) }
                val endingNumber = numberMatch?.groupValues?.get(1)
                val cleanTitle = remember(parsed.cleanTitle, endingNumber) {
                    if (endingNumber != null && !parsed.cleanTitle.endsWith(endingNumber)) {
                        "${parsed.cleanTitle} $endingNumber"
                    } else {
                        parsed.cleanTitle
                    }
                }

                val authorArtist = remember(parsed.author, parsed.artist) {
                    listOfNotNull(parsed.author, parsed.artist).distinct().joinToString(" • ").ifBlank { null }
                }

                val chapterSubtitle = if (item.chapterNumber > -1) {
                    "Ch. ${formatChapterNumber(item.chapterNumber)}"
                } else {
                    ""
                }

                KisaraHomeSectionCard(
                    style = HomeSectionCardStyle.DEFAULT,
                    title = cleanTitle,
                    subtitle = authorArtist,
                    coverData = item.coverData,
                    progress = 0.5f,
                    chapterName = chapterSubtitle,
                    readAtTimestamp = item.readAt?.toTimestampString(),
                    languageCode = parsed.languageCode,
                    coverTitleStyle = coverTitleStyle,
                    onClick = { onNovelClick(item.novelId) },
                    onResume = { onResumeChapter(item) },
                )
            }

            if (onClickMore != null) {
                item {
                    SeeAllEndCard(width = 80.dp, height = 120.dp, onClick = onClickMore)
                }
            }
        }
    }
}

@Composable
fun NovelLandingContent(
    paddingValues: PaddingValues,
    screenModel: eu.kanade.tachiyomi.ui.home.novel.NovelLandingScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiPreferences = remember { Injekt.get<UiPreferences>() }

    val showSpotlight by uiPreferences.showHomeSuggestions().collectAsState()
    val showContinueReading by uiPreferences.showHomeHistory().collectAsState()
    val showUpdates by uiPreferences.showHomeUpdates().collectAsState()
    val showLibrary by uiPreferences.showHomeLibrary().collectAsState()
    val showFeed by uiPreferences.showHomeFeed().collectAsState()
    val coverTitleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()

    val dialog = state.dialog
    if (dialog is NovelLandingScreenModel.State.Dialog.ChangeCategory) {
        val changeCategoryNovel = dialog.novel
        ChangeCategoryDialog(
            initialSelection = dialog.initialSelection,
            onDismissRequest = { screenModel.dismissDialog() },
            onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.novel.NovelCategoryScreen()) },
            onConfirm = { included, _ ->
                screenModel.setNovelCategories(changeCategoryNovel, included)
            },
            onDuplicateCheck = {
                screenModel.dismissDialog()
                navigator.push(eu.kanade.tachiyomi.ui.browse.novel.duplicate.DuplicateNovelScreen(changeCategoryNovel.id))
            },
            onDelete = {
                screenModel.toggleFavorite(changeCategoryNovel.id, true)
            },
            novel = changeCategoryNovel,
            onCreateCategory = screenModel::createCategory,
        )
    }

    PullRefresh(
        refreshing = state.isFeedRefreshing,
        enabled = true,
        onRefresh = {
            screenModel.triggerBackgroundFeedFetch(force = true)
            screenModel.loadTrackerRecommendations(force = true)
            screenModel.loadTrackerContinue(force = true)
            screenModel.loadSpotlightSuggestions()
        },
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Spotlight Carousel
            if (showSpotlight && state.spotlightNovel.isNotEmpty()) {
                item {
                    NovelSpotlightCarousel(
                        novels = state.spotlightNovel,
                        tagName = state.spotlightTagName,
                        onNovelClick = { novelId ->
                            navigator.push(NovelScreen(novelId))
                        },
                        onNovelLongClick = { novel ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            screenModel.toggleFavorite(novel.id, novel.favorite)
                        },
                        onDismissNovel = { novel ->
                            screenModel.dismissSpotlight(novel)
                        },
                    )
                }
            }

            // 2. Continue Reading Shelf
            if (showContinueReading && state.history.isNotEmpty()) {
                item {
                    NovelContinueReadingShelf(
                        historyList = state.history,
                        onNovelClick = { novelId ->
                            navigator.push(NovelScreen(novelId))
                        },
                        onResumeChapter = { historyItem ->
                            navigator.push(NovelReaderScreen(historyItem.chapterId))
                        },
                        onClickMore = {
                            HomeTab.showSubTab(4)
                        },
                        coverTitleStyle = coverTitleStyleKey,
                    )
                }
            }

            // 2b. Continue from Tracker
            if (state.trackerContinue.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = tachiyomi.presentation.core.i18n.stringResource(KMR.strings.pref_home_section_names_continue_tracker),
                        onClickMore = { HomeTab.showSubTab(2) },
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(
                            items = state.trackerContinue,
                            key = { "novel-tracker-continue-${it.id}" },
                        ) { trackerItem ->
                            val chapterSubtitle = if (trackerItem.lastReadChapter != null && trackerItem.lastReadChapter > 0) {
                                val maxCh = if (trackerItem.totalChapters != null && trackerItem.totalChapters > 0) " / ${trackerItem.totalChapters}" else ""
                                "Ch. ${trackerItem.lastReadChapter.toInt()}$maxCh"
                            } else {
                                trackerItem.status ?: "Reading"
                            }

                            KisaraHomeSectionCard(
                                style = HomeSectionCardStyle.DEFAULT,
                                title = trackerItem.title,
                                subtitle = trackerItem.sourceName,
                                coverData = trackerItem.coverUrl,
                                progress = if (trackerItem.totalChapters != null && trackerItem.totalChapters > 0 && trackerItem.lastReadChapter != null) {
                                    (trackerItem.lastReadChapter / trackerItem.totalChapters).coerceIn(0f, 1f)
                                } else null,
                                chapterName = chapterSubtitle,
                                coverTitleStyle = coverTitleStyleKey,
                                onClick = {
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen(
                                            series = trackerItem.toTrackSeriesItem(MediaType.NOVEL),
                                            mediaType = MediaType.NOVEL,
                                        ),
                                    )
                                },
                                onResume = {
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen(
                                            series = trackerItem.toTrackSeriesItem(MediaType.NOVEL),
                                            mediaType = MediaType.NOVEL,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // 3. Fresh Novel Updates
            if (showUpdates && state.updates.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = tachiyomi.presentation.core.i18n.stringResource(KMR.strings.pref_home_section_names_fresh_releases),
                        onClickMore = { HomeTab.showSubTab(3) },
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.updates,
                            key = { "novel-update-${it.novelId}-${it.chapterId}" },
                        ) { updateItem ->
                            NovelCoverCard(
                                novelId = updateItem.novelId,
                                coverData = updateItem.coverData,
                                title = updateItem.novelTitle,
                                badgeText = "Ch. ${updateItem.chapterName}",
                                onClick = { navigator.push(NovelScreen(updateItem.novelId)) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    screenModel.toggleFavorite(updateItem.novelId, updateItem.coverData.isNovelFavorite)
                                },
                            )
                        }

                        item {
                            SeeAllEndCard(onClick = { HomeTab.showSubTab(3) })
                        }
                    }
                }
            }

            // 4. Library Favorites (2-Row Chunked Layout)
            if (showLibrary && state.libraryRandom.isNotEmpty()) {
                item {
                    val libraryPairs = remember(state.libraryRandom) { state.libraryRandom.chunked(2) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tachiyomi.presentation.core.i18n.stringResource(KMR.strings.pref_home_section_names_forgotten_favorites),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = libraryPairs,
                            key = { pair -> "novel-lib-pair-${pair.first().id}" },
                        ) { pair ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                for (novel in pair) {
                                    NovelCoverCard(
                                        novelId = novel.id,
                                        coverData = NovelCover(
                                            novelId = novel.id,
                                            sourceId = novel.source,
                                            isNovelFavorite = novel.favorite,
                                            url = novel.thumbnailUrl,
                                            lastModified = novel.coverLastModified,
                                        ),
                                        title = novel.title,
                                        badgeText = "",
                                        onClick = { navigator.push(NovelScreen(novel.id)) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            screenModel.toggleFavorite(novel.id, novel.favorite)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recommended For You (AniList Recommendations)
            if (state.trackerRecommendations.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recommended For You",
                        onClickMore = null,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(
                            items = state.trackerRecommendations,
                            key = { "novel-rec-${it.sourceName}-${it.id}-${it.title}" },
                        ) { rec ->
                            val genreText = rec.genres.take(2).joinToString(" • ").ifBlank { rec.status ?: rec.sourceName }
                            KisaraHomeSectionCard(
                                style = HomeSectionCardStyle.DEFAULT,
                                title = rec.title,
                                subtitle = genreText,
                                coverData = rec.coverUrl,
                                progress = 0f,
                                chapterName = if (rec.score != null && rec.score > 0.0) "★ ${String.format(java.util.Locale.US, "%.1f", rec.score)}" else rec.sourceName,
                                coverTitleStyle = coverTitleStyleKey,
                                onClick = {
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen(
                                            searchQuery = rec.title,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // 6. Explore Feed
            if (showFeed) {
                item {
                    SectionHeader(
                        title = "Explore Feed",
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
                    val chunkedFeed = state.feed.chunked(2)
                    items(chunkedFeed) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    NovelFeedItemCard(
                                        item = item,
                                        coverTitleStyleKey = coverTitleStyleKey,
                                        onClick = { navigator.push(NovelScreen(item.id)) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            screenModel.toggleFavorite(item.id, item.favorite)
                                        },
                                    )
                                }
                            }
                            if (rowItems.size < 2) {
                                repeat(2 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelFeedItemCard(
    item: CachedFeedNovel,
    coverTitleStyleKey: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    KisaraNormalCard(
        style = NormalCardStyle.DEFAULT,
        title = item.title,
        coverData = NovelCover(
            novelId = item.id,
            sourceId = item.sourceId,
            isNovelFavorite = item.favorite,
            url = item.thumbnailUrl,
            lastModified = item.coverLastModified,
        ),
        coverTitleStyle = coverTitleStyleKey,
        badgeText = item.sourceName,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun NovelCoverCard(
    novelId: Long,
    coverData: NovelCover,
    title: String,
    badgeText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            MangaCover.Book(
                data = coverData,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (badgeText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
