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
import androidx.compose.material.icons.filled.Movie
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
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.library.components.ColorizedBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.library.components.UncensoredBadge
import eu.kanade.tachiyomi.ui.home.anime.AnimeLandingScreenModel
import eu.kanade.tachiyomi.ui.home.anime.CachedFeedAnime
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.MangaTitleParser
import eu.kanade.tachiyomi.util.lang.toTimestampString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AnimeSpotlightCarousel(
    animes: List<Anime>,
    tagName: String? = null,
    onAnimeClick: (Long) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
    onDismissAnime: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (animes.isEmpty()) return

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val autoplay by uiPreferences.homeSuggestionsAutoplay().collectAsState()
    val autoplayInterval by uiPreferences.homeSuggestionsAutoplayInterval().collectAsState()

    val pagerState = rememberPagerState { animes.size }

    LaunchedEffect(animes.size) {
        if (animes.isNotEmpty() && pagerState.currentPage >= animes.size) {
            pagerState.scrollToPage((animes.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(animes, autoplay, autoplayInterval) {
        while (autoplay && animes.size > 1) {
            delay(autoplayInterval * 1000L)
            if (!pagerState.isScrollInProgress && animes.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % animes.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    val glowColor = MaterialTheme.colorScheme.primary

    val animatedGlowColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientGlowAnime",
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
                    onClick = { navigator.push(ExtensionNsfwScreen(1)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(KMR.strings.extension_nsfw_configure_tooltip),
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
            val anime = animes.getOrNull(page) ?: return@HorizontalPager
            val coverData = anime.asAnimeCover()
            val parsed = remember(anime) {
                MangaTitleParser.parse(anime.title)
            }
            val authorText = remember(anime.author, anime.artist, parsed.author, parsed.artist) {
                val author = anime.author ?: parsed.author
                val artist = anime.artist ?: parsed.artist
                listOfNotNull(author, artist).distinct().joinToString(" • ").ifBlank { "Unknown" }
            }
            val tags = remember(anime.genre) {
                anime.genre.orEmpty()
            }
            val endingNumber = remember(anime.title) {
                Regex("(\\d+)$").find(anime.title)?.groupValues?.get(1)
            }
            val titleFontSize = 15.sp

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { onAnimeClick(anime.id) },
                        onLongClick = { onAnimeLongClick(anime) },
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
                                IconButton(
                                    onClick = { onDismissAnime(anime) },
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
fun AnimeLandingContent(
    paddingValues: PaddingValues,
    screenModel: eu.kanade.tachiyomi.ui.home.anime.AnimeLandingScreenModel,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showSuggestions by uiPreferences.showHomeSuggestions().collectAsState()
    val showHistory by uiPreferences.showHomeHistory().collectAsState()
    val showUpdates by uiPreferences.showHomeUpdates().collectAsState()
    val showLibrary by uiPreferences.showHomeLibrary().collectAsState()
    val showFeed by uiPreferences.showHomeFeed().collectAsState()
    val coverTitleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()

    val dialog = state.dialog
    if (dialog is AnimeLandingScreenModel.State.Dialog.ChangeCategory) {
        val changeCategoryAnime = dialog.anime
        ChangeCategoryDialog(
            initialSelection = dialog.initialSelection,
            onDismissRequest = { screenModel.dismissDialog() },
            onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen()) },
            onConfirm = { included, _ ->
                screenModel.setAnimeCategories(changeCategoryAnime, included)
            },
            onDuplicateCheck = {
                screenModel.dismissDialog()
                navigator.push(eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen(changeCategoryAnime.id))
            },
            onDelete = {
                screenModel.toggleFavorite(changeCategoryAnime.id, true)
            },
            anime = changeCategoryAnime,
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
            // 1. Spotlight
            if (showSuggestions && state.spotlightAnime.isNotEmpty()) {
                item {
                    AnimeSpotlightCarousel(
                        animes = state.spotlightAnime,
                        tagName = state.spotlightTagName,
                        onAnimeClick = { animeId -> navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(animeId)) },
                        onAnimeLongClick = { anime ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            screenModel.toggleFavorite(anime.id, anime.favorite)
                        },
                        onDismissAnime = { anime -> screenModel.dismissSpotlight(anime) },
                    )
                }
            }

            // 2. Continue Watching (History)
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
                            key = { "anime-history-${it.id}" },
                        ) { historyItem ->
                            val onResume: () -> Unit = {
                                val intent = PlayerActivity.newIntent(context, historyItem.animeId, historyItem.episodeId)
                                context.startActivity(intent)
                            }

                            val epSubtitle = if (historyItem.episodeNumber > -1) {
                                "Ep. ${eu.kanade.presentation.util.formatChapterNumber(historyItem.episodeNumber)}"
                            } else {
                                ""
                            }

                            KisaraHomeSectionCard(
                                style = HomeSectionCardStyle.DEFAULT,
                                title = historyItem.title,
                                subtitle = epSubtitle,
                                coverData = historyItem.coverData,
                                progress = 0.5f,
                                chapterName = epSubtitle,
                                readAtTimestamp = historyItem.seenAt?.toTimestampString(),
                                coverTitleStyle = coverTitleStyleKey,
                                onClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(historyItem.animeId)) },
                                onResume = onResume,
                            )
                        }

                        item {
                            SeeAllEndCard(width = 80.dp, height = 120.dp, onClick = { HomeTab.showSubTab(4) })
                        }
                    }
                }
            }

            // 2b. Continue from Tracker
            if (state.trackerContinue.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(KMR.strings.pref_home_section_names_continue_tracker),
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
                            key = { "anime-tracker-continue-${it.id}" },
                        ) { trackerItem ->
                            val epSubtitle = if (trackerItem.lastWatchedEpisode != null && trackerItem.lastWatchedEpisode > 0) {
                                val maxEp = if (trackerItem.totalEpisodes != null && trackerItem.totalEpisodes > 0) " / ${trackerItem.totalEpisodes}" else ""
                                "Ep. ${trackerItem.lastWatchedEpisode.toInt()}$maxEp"
                            } else {
                                trackerItem.status ?: "Watching"
                            }

                            KisaraHomeSectionCard(
                                style = HomeSectionCardStyle.DEFAULT,
                                title = trackerItem.title,
                                subtitle = trackerItem.sourceName,
                                coverData = trackerItem.coverUrl,
                                progress = if (trackerItem.totalEpisodes != null && trackerItem.totalEpisodes > 0 && trackerItem.lastWatchedEpisode != null) {
                                    (trackerItem.lastWatchedEpisode / trackerItem.totalEpisodes).coerceIn(0f, 1f)
                                } else null,
                                chapterName = epSubtitle,
                                coverTitleStyle = coverTitleStyleKey,
                                onClick = {
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen(
                                            series = trackerItem.toTrackSeriesItem(MediaType.ANIME),
                                            mediaType = MediaType.ANIME,
                                        ),
                                    )
                                },
                                onResume = {
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen(
                                            series = trackerItem.toTrackSeriesItem(MediaType.ANIME),
                                            mediaType = MediaType.ANIME,
                                        ),
                                    )
                                },
                            )
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
                            key = { "anime-update-${it.episodeId}" },
                        ) { updateItem ->
                            AnimeCoverCard(
                                animeId = updateItem.animeId,
                                coverData = updateItem.coverData,
                                title = updateItem.animeTitle,
                                badgeText = updateItem.episodeName,
                                onClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(updateItem.animeId)) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    screenModel.toggleFavorite(updateItem.animeId, updateItem.coverData.isAnimeFavorite)
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
                    SectionHeader(
                        title = stringResource(KMR.strings.pref_home_section_names_forgotten_favorites),
                        onClickMore = null,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = libraryPairs,
                            key = { pair -> "anime-lib-pair-${pair.first().id}" },
                        ) { pair ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                for (anime in pair) {
                                    AnimeCoverCard(
                                        animeId = anime.id,
                                        coverData = AnimeCover(
                                            animeId = anime.id,
                                            sourceId = anime.source,
                                            isAnimeFavorite = anime.favorite,
                                            url = anime.thumbnailUrl,
                                            lastModified = anime.coverLastModified,
                                        ),
                                        title = anime.title,
                                        badgeText = "",
                                        onClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(anime.id)) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            screenModel.toggleFavorite(anime.id, anime.favorite)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recommended For You (AniList / Tracker Recommendations)
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
                            key = { "anime-rec-${it.sourceName}-${it.id}-${it.title}" },
                        ) { rec ->
                            val genreText = rec.genres.take(2).joinToString(" • ").ifBlank { rec.status ?: rec.sourceName }
                            KisaraHomeSectionCard(
                                style = HomeSectionCardStyle.DEFAULT,
                                title = rec.title,
                                subtitle = genreText,
                                coverData = rec.coverUrl,
                                progress = 0f,
                                coverTitleStyle = coverTitleStyleKey,
                                badgeText = if (rec.score != null && rec.score > 0.0) "★ ${String.format(java.util.Locale.US, "%.1f", rec.score)}" else rec.sourceName,
                                onClick = {
                                    val sourceManager = Injekt.get<AnimeSourceManager>()
                                    val sourceIds = sourceManager.getCatalogueSources().map { it.id }
                                    navigator.push(
                                        eu.kanade.tachiyomi.ui.browse.anime.bulk.AnimeBulkSearchScreen(
                                            sourceIds = sourceIds,
                                            queries = listOf(rec.title),
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
                        title = stringResource(KMR.strings.feed),
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
                                text = stringResource(KMR.strings.feed_empty),
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
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    AnimeFeedCard(
                                        item = item,
                                        coverTitleStyleKey = coverTitleStyleKey,
                                        onClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(item.id)) },
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
private fun AnimeFeedCard(
    item: CachedFeedAnime,
    coverTitleStyleKey: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    KisaraNormalCard(
        style = NormalCardStyle.DEFAULT,
        title = item.title,
        coverData = AnimeCover(
            animeId = item.id,
            sourceId = item.sourceId,
            isAnimeFavorite = item.favorite,
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
private fun AnimeCoverCard(
    animeId: Long,
    coverData: AnimeCover,
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
            ItemCover.Book(
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
