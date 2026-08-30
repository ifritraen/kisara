package eu.kanade.tachiyomi.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.anischedule.AniScheduleFeedItem
import eu.kanade.tachiyomi.data.anischedule.AniScheduleMedia
import eu.kanade.tachiyomi.ui.track.TrackSeriesItem
import eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class AnimeScheduleScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { AnimeScheduleScreenModel() }
        AnimeScheduleScreenContent(screenModel = screenModel)
    }
}

@Composable
fun AnimeScheduleScreenContent(
    screenModel: AnimeScheduleScreenModel,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Feed Type Switcher Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimeFeedType.entries.forEach { feedType ->
                if (feedType == AnimeFeedType.HENTAI_FEED && !state.isAdultEnabled) return@forEach

                val isSelected = state.selectedFeedType == feedType
                FilterChip(
                    selected = isSelected,
                    onClick = { screenModel.setFeedType(feedType) },
                    label = {
                        Text(
                            text = feedType.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        when (feedType) {
                            AnimeFeedType.SCHEDULE -> Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            AnimeFeedType.SUB_FEED -> Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            AnimeFeedType.DUB_FEED -> Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            AnimeFeedType.HENTAI_FEED -> Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { screenModel.loadData(forceRefresh = true) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedFeedType) {
                    AnimeFeedType.SCHEDULE -> {
                        ScheduleSection(
                            state = state,
                            onSelectDay = screenModel::setSelectedDay,
                            onClickMedia = { media ->
                                val series = media.toTrackSeriesItem()
                                navigator.push(TrackerMediaDetailsScreen(series = series, mediaType = MediaType.ANIME))
                            },
                        )
                    }
                    AnimeFeedType.SUB_FEED -> {
                        EpisodeFeedSection(
                            feedList = state.subFeed,
                            mediaMap = state.mediaMap,
                            title = "Recent Sub Episodes",
                            onClickItem = { item, media ->
                                val series = item.toTrackSeriesItem(media)
                                navigator.push(TrackerMediaDetailsScreen(series = series, mediaType = MediaType.ANIME))
                            },
                        )
                    }
                    AnimeFeedType.DUB_FEED -> {
                        EpisodeFeedSection(
                            feedList = state.dubFeed,
                            mediaMap = state.mediaMap,
                            title = "Recent Dub Episodes",
                            onClickItem = { item, media ->
                                val series = item.toTrackSeriesItem(media)
                                navigator.push(TrackerMediaDetailsScreen(series = series, mediaType = MediaType.ANIME))
                            },
                        )
                    }
                    AnimeFeedType.HENTAI_FEED -> {
                        EpisodeFeedSection(
                            feedList = state.hentaiFeed,
                            mediaMap = state.mediaMap,
                            title = "18+ Adult Releases",
                            onClickItem = { item, media ->
                                val series = item.toTrackSeriesItem(media)
                                navigator.push(TrackerMediaDetailsScreen(series = series, mediaType = MediaType.ANIME))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSection(
    state: AnimeScheduleScreenModel.State,
    onSelectDay: (DayOfWeek) -> Unit,
    onClickMedia: (AniScheduleMedia) -> Unit,
) {
    val days = remember { DayOfWeek.entries.toList() }
    val today = remember { LocalDate.now().dayOfWeek }
    val scheduleItems = remember(state.schedule, state.selectedDay, state.searchQuery) {
        state.getScheduleForSelectedDay()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Weekday Tabs
        ScrollableTabRow(
            selectedTabIndex = days.indexOf(state.selectedDay),
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[days.indexOf(state.selectedDay)]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            days.forEach { day ->
                val isSelected = state.selectedDay == day
                val isToday = day == today
                Tab(
                    selected = isSelected,
                    onClick = { onSelectDay(day) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isToday) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    },
                )
            }
        }

        // Schedule List
        if (scheduleItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No airing anime scheduled for ${state.selectedDay.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(scheduleItems, key = { it.media.id }) { item ->
                    ScheduleCard(item = item, onClick = { onClickMedia(item.media) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(
    item: AiringScheduleCardData,
    onClick: () -> Unit,
) {
    val media = item.media
    val title = media.title?.getBestTitle() ?: "Unknown Title"
    val coverUrl = media.coverImage?.getUrl()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Poster
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Episode & Countdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.nextEpisode != null) {
                        Text(
                            text = "Episode ${item.nextEpisode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = item.countdownText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Format & Genres
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    media.format?.let { fmt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = fmt,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    media.genres.take(3).forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = genre,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeFeedSection(
    feedList: List<AniScheduleFeedItem>,
    mediaMap: Map<Long, AniScheduleMedia>,
    title: String,
    onClickItem: (AniScheduleFeedItem, AniScheduleMedia?) -> Unit,
) {
    if (feedList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No recent episodes found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(feedList, key = { "${it.id}-${it.episode?.aired}" }) { item ->
                val media = mediaMap[item.id]
                EpisodeFeedCard(
                    item = item,
                    media = media,
                    onClick = { onClickItem(item, media) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeFeedCard(
    item: AniScheduleFeedItem,
    media: AniScheduleMedia?,
    onClick: () -> Unit,
) {
    val title = media?.title?.getBestTitle() ?: "Anime ID: ${item.id}"
    val coverUrl = media?.coverImage?.getUrl()
    val episodeNumber = item.episode?.aired

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (episodeNumber != null) {
                        Text(
                            text = "Episode $episodeNumber Released",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    item.format?.let { fmt ->
                        Text(
                            text = "• $fmt",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun AniScheduleMedia.toTrackSeriesItem(): TrackSeriesItem {
    return TrackSeriesItem(
        title = title?.getBestTitle() ?: "Unknown",
        coverUrl = coverImage?.getUrl(),
        type = format ?: "ANIME",
        status = "AIRING",
        rating = null,
        score = null,
        description = null,
        trackingUrl = "https://anilist.co/anime/$id",
        year = seasonYear?.toString(),
        authors = null,
        genres = genres,
    )
}

private fun AniScheduleFeedItem.toTrackSeriesItem(scheduleMedia: AniScheduleMedia?): TrackSeriesItem {
    return TrackSeriesItem(
        title = scheduleMedia?.title?.getBestTitle() ?: "Anime ID: $id",
        coverUrl = scheduleMedia?.coverImage?.getUrl(),
        type = format ?: scheduleMedia?.format ?: "ANIME",
        status = "AIRING",
        rating = null,
        score = null,
        description = null,
        trackingUrl = "https://anilist.co/anime/$id",
        year = scheduleMedia?.seasonYear?.toString(),
        authors = null,
        genres = scheduleMedia?.genres ?: emptyList(),
    )
}
