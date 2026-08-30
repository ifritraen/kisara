package eu.kanade.tachiyomi.ui.track

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.KisaraBottomSheet
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnime
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserAnime
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUAuthorRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGenreItem
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGroupRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUPublisherRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MURecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUReviewRecord
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMediaItem
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.track.anilist.AnilistHomeScreen
import eu.kanade.tachiyomi.ui.track.anilist.AnilistMyListScreen
import eu.kanade.tachiyomi.ui.track.anilist.AnilistProfileScreen
import eu.kanade.tachiyomi.ui.track.anilist.AnilistSearchScreen
import eu.kanade.tachiyomi.ui.track.anilist.AnilistSectionFilterSheet
import eu.kanade.tachiyomi.ui.track.details.TrackerMediaDetailsScreen
import eu.kanade.tachiyomi.ui.track.myanimelist.MALHomeScreen
import eu.kanade.tachiyomi.ui.track.myanimelist.MALMyListScreen
import eu.kanade.tachiyomi.ui.track.myanimelist.MALProfileScreen
import eu.kanade.tachiyomi.ui.track.myanimelist.MALSearchScreen
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TrackTab : Tab {

    private val selectSubTabEvent = kotlinx.coroutines.channels.Channel<Int>()
    val nextSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val prevSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val nextSubSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val prevSubSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var currentPageIndex by mutableIntStateOf(0)
        private set

    fun showSubTab(index: Int) {
        currentPageIndex = index
        selectSubTabEvent.trySend(index)
    }

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(KMR.strings.label_track_tab)
            val icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Outlined.TrackChanges)
            return remember {
                TabOptions(
                    index = 2u,
                    title = title,
                    icon = icon,
                )
            }
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { TrackScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val activeMediaType by uiPreferences.activeMediaType().collectAsState()
        val showTrackSubBarAtTop by uiPreferences.showTrackSubBarAtTop().collectAsState()

        val mangaService by uiPreferences.trackTabMangaService().collectAsState()
        val animeService by uiPreferences.trackTabAnimeService().collectAsState()
        val novelService by uiPreferences.trackTabNovelService().collectAsState()

        val activeTrackerService = when (activeMediaType) {
            MediaType.ANIME -> animeService
            MediaType.NOVEL -> novelService
            MediaType.MANGA -> mangaService
        }

        LaunchedEffect(activeMediaType, activeTrackerService) {
            when (activeTrackerService) {
                UiPreferences.TrackTabService.ANILIST -> {
                    screenModel.loadAnilistHome(activeMediaType)
                    screenModel.loadAnilistUserList(activeMediaType)
                    screenModel.loadAnilistStats()
                }
                UiPreferences.TrackTabService.MAL -> {
                    screenModel.loadMALHome(activeMediaType)
                    screenModel.loadMALUserList(activeMediaType)
                    screenModel.loadMALProfile()
                }
                else -> {}
            }
        }

        val subTabs = remember(activeMediaType, activeTrackerService) {
            when (activeTrackerService) {
                UiPreferences.TrackTabService.ANILIST, UiPreferences.TrackTabService.MAL -> {
                    persistentListOf(
                        SubTabItem("Home", Icons.Outlined.AutoAwesome),
                        SubTabItem("My List", Icons.AutoMirrored.Outlined.List),
                        SubTabItem("Search", Icons.Outlined.Search),
                        SubTabItem("Profile", Icons.Outlined.AccountCircle),
                    )
                }
                else -> {
                    when (activeMediaType) {
                        MediaType.ANIME -> persistentListOf(
                            SubTabItem("Trending", Icons.Outlined.AutoAwesome),
                            SubTabItem("This Season", Icons.Outlined.NewReleases),
                            SubTabItem("Top 100", Icons.Outlined.Visibility),
                            SubTabItem("Search", Icons.Outlined.Search),
                            SubTabItem("Genres & Tags", Icons.Outlined.Category),
                            SubTabItem("Studios", Icons.Outlined.Domain),
                            SubTabItem("My Anime List", Icons.AutoMirrored.Outlined.List),
                            SubTabItem("Profile", Icons.Outlined.AccountCircle),
                        )
                        MediaType.NOVEL -> persistentListOf(
                            SubTabItem("Novel Releases", Icons.Outlined.NewReleases),
                            SubTabItem("Top Novels", Icons.Outlined.AutoAwesome),
                            SubTabItem("Novel Directory", Icons.Outlined.Info),
                            SubTabItem("Novel Search", Icons.Outlined.Search),
                            SubTabItem("Novel Genres", Icons.Outlined.Category),
                            SubTabItem("Publishers", Icons.Outlined.Domain),
                            SubTabItem("Novel Reviews", Icons.Outlined.RateReview),
                            SubTabItem("My Novel Lists", Icons.AutoMirrored.Outlined.List),
                            SubTabItem("User CP", Icons.Outlined.AccountCircle),
                        )
                        else -> persistentListOf(
                            SubTabItem("New Releases", Icons.Outlined.NewReleases),
                            SubTabItem("Recommended", Icons.Outlined.AutoAwesome),
                            SubTabItem("Releases", Icons.Outlined.Visibility),
                            SubTabItem("Series Info", Icons.Outlined.Info),
                            SubTabItem("Scanlators", Icons.Outlined.Group),
                            SubTabItem("Mangaka", Icons.Outlined.Person),
                            SubTabItem("Publishers", Icons.Outlined.Domain),
                            SubTabItem("Reviews", Icons.Outlined.RateReview),
                            SubTabItem("Genres", Icons.Outlined.Category),
                            SubTabItem("Search", Icons.Outlined.Search),
                            SubTabItem("My Lists", Icons.AutoMirrored.Outlined.List),
                            SubTabItem("User CP", Icons.Outlined.AccountCircle),
                        )
                    }
                }
            }
        }

        val pagerState = rememberPagerState(initialPage = currentPageIndex.coerceIn(0, subTabs.size - 1)) { subTabs.size }
        var showAnilistSectionFilterSheet by remember { mutableStateOf(false) }
        val enabledAnilistSections by uiPreferences.anilistHomeEnabledSections().collectAsState()

        LaunchedEffect(pagerState.currentPage) {
            currentPageIndex = pagerState.currentPage
        }

        LaunchedEffect(Unit) {
            launch {
                selectSubTabEvent.receiveAsFlow().collectLatest { index ->
                    if (index in 0 until subTabs.size) {
                        pagerState.animateScrollToPage(index)
                    }
                }
            }
            launch {
                nextSubTabEvent.receiveAsFlow().collectLatest {
                    val totalTabs = subTabs.size
                    if (totalTabs > 1) {
                        val next = (pagerState.currentPage + 1).coerceAtMost(totalTabs - 1)
                        pagerState.animateScrollToPage(next)
                    }
                }
            }
            launch {
                prevSubTabEvent.receiveAsFlow().collectLatest {
                    val totalTabs = subTabs.size
                    if (totalTabs > 1) {
                        val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                        pagerState.animateScrollToPage(prev)
                    }
                }
            }
        }

        val subTabBar: @Composable () -> Unit = {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, subTabs.size - 1),
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
                subTabs.forEachIndexed { index, item ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        },
                    )
                }
            }
        }

        Scaffold(
            floatingActionButton = {
                if (activeTrackerService == UiPreferences.TrackTabService.ANILIST && pagerState.currentPage == 0) {
                    FloatingActionButton(
                        onClick = { showAnilistSectionFilterSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Filter Landing Sections")
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (showTrackSubBarAtTop) {
                    subTabBar()
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    when (activeTrackerService) {
                        UiPreferences.TrackTabService.ANILIST -> {
                            when (page) {
                                0 -> AnilistHomeScreen(
                                    sections = state.homeSections,
                                    isLoading = state.isLoadingHome,
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                )
                                1 -> AnilistMyListScreen(
                                    isLoggedIn = screenModel.isAniListLoggedIn(),
                                    entries = state.userList,
                                    isLoading = state.isLoadingUserList,
                                    selectedStatus = state.userListStatus,
                                    activeMediaType = activeMediaType,
                                    onStatusSelected = { status ->
                                        screenModel.setAnilistStatusFilter(status)
                                    },
                                    onLoginClick = {
                                        scope.launch { pagerState.animateScrollToPage(3) }
                                    },
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                )
                                2 -> AnilistSearchScreen(
                                    query = state.searchQuery,
                                    onQueryChange = { q ->
                                        screenModel.searchAnilist(q, activeMediaType)
                                    },
                                    onSearch = {
                                        screenModel.searchAnilist(state.searchQuery, activeMediaType)
                                    },
                                    results = state.searchResults,
                                    isSearching = state.isSearching,
                                    genres = state.filterGenres,
                                    selectedGenre = state.selectedGenre,
                                    onGenreSelected = { genre ->
                                        screenModel.setAnilistGenre(genre)
                                    },
                                    selectedSort = state.selectedSort,
                                    onSortSelected = { sort ->
                                        screenModel.setAnilistSort(sort)
                                    },
                                    activeMediaType = activeMediaType,
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                )
                                3 -> AnilistProfileScreen(
                                    isLoggedIn = screenModel.isAniListLoggedIn(),
                                    userStats = state.userStats,
                                    isLoading = state.isLoadingStats,
                                    onLoginToken = { token ->
                                        screenModel.loginAniList(token)
                                    },
                                    onLogout = {
                                        screenModel.logoutAniList()
                                    },
                                    onRefresh = {
                                        screenModel.loadAnilistStats()
                                    },
                                )
                                else -> Box(Modifier.fillMaxSize())
                            }
                        }
                        UiPreferences.TrackTabService.MAL -> {
                            when (page) {
                                0 -> MALHomeScreen(
                                    sections = state.malHomeSections,
                                    isLoading = state.isLoadingMALHome,
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(), activeMediaType))
                                    },
                                )
                                1 -> MALMyListScreen(
                                    isLoggedIn = screenModel.isMALLoggedIn(),
                                    entries = state.malUserList,
                                    isLoading = state.isLoadingMALUserList,
                                    selectedStatus = state.malUserListStatus,
                                    activeMediaType = activeMediaType,
                                    onStatusSelected = { status ->
                                        screenModel.setMALStatusFilter(status)
                                    },
                                    onLoginClick = {
                                        scope.launch { pagerState.animateScrollToPage(3) }
                                    },
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(), activeMediaType))
                                    },
                                )
                                2 -> MALSearchScreen(
                                    query = state.malSearchQuery,
                                    onQueryChange = { q ->
                                        screenModel.searchMAL(q, activeMediaType)
                                    },
                                    onSearch = {
                                        screenModel.searchMAL(state.malSearchQuery, activeMediaType)
                                    },
                                    results = state.malSearchResults,
                                    isSearching = state.isSearchingMAL,
                                    activeMediaType = activeMediaType,
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(), activeMediaType))
                                    },
                                )
                                3 -> MALProfileScreen(
                                    isLoggedIn = screenModel.isMALLoggedIn(),
                                    userProfile = state.malUserProfile,
                                    isLoading = state.isLoadingMALProfile,
                                    onLogout = {
                                        screenModel.logoutMAL()
                                    },
                                    onRefresh = {
                                        screenModel.loadMALProfile()
                                    },
                                )
                                else -> Box(Modifier.fillMaxSize())
                            }
                        }
                        else -> {
                            when (activeMediaType) {
                                MediaType.ANIME -> when (page) {
                                    0 -> AniListTrendingFeed(screenModel)
                                    1 -> AniListSeasonalFeed(screenModel)
                                    2 -> AniListTopRatedFeed(screenModel)
                                    3 -> AniListSearchSection(screenModel)
                                    4 -> AniListGenresSection(screenModel)
                                    5 -> AniListStudiosSection(screenModel)
                                    6 -> AniListMyListSection(screenModel)
                                    7 -> AniListProfileSection(screenModel)
                                    else -> AniListTrendingFeed(screenModel)
                                }
                                MediaType.NOVEL -> when (page) {
                                    0 -> MangaUpdatesNovelReleaseFeed(screenModel)
                                    1 -> MangaUpdatesNovelRecommended(screenModel)
                                    2 -> MangaUpdatesSearchSection(screenModel, "releases", isNovel = true)
                                    3 -> MangaUpdatesNovelSearchSection(screenModel)
                                    4 -> MangaUpdatesGenresSection(screenModel)
                                    5 -> MangaUpdatesPublishersSection(screenModel)
                                    6 -> MangaUpdatesReviewsSection(screenModel)
                                    7 -> MangaUpdatesMyLists(screenModel)
                                    8 -> MangaUpdatesUserCP(screenModel)
                                    else -> MangaUpdatesNovelReleaseFeed(screenModel)
                                }
                                else -> when (page) {
                                    0 -> MangaUpdatesReleaseFeed(screenModel)
                                    1 -> MangaUpdatesRecommended(screenModel)
                                    2 -> MangaUpdatesSearchSection(screenModel, "releases")
                                    3 -> MangaUpdatesSeriesDirectory(screenModel)
                                    4 -> MangaUpdatesGroupsSection(screenModel)
                                    5 -> MangaUpdatesAuthorsSection(screenModel)
                                    6 -> MangaUpdatesPublishersSection(screenModel)
                                    7 -> MangaUpdatesReviewsSection(screenModel)
                                    8 -> MangaUpdatesGenresSection(screenModel)
                                    9 -> MangaUpdatesSearchSection(screenModel, "search")
                                    10 -> MangaUpdatesMyLists(screenModel)
                                    11 -> MangaUpdatesUserCP(screenModel)
                                    else -> MangaUpdatesReleaseFeed(screenModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        state.selectedSeries?.let { series ->
            TrackSeriesDetailsSheet(
                item = series,
                onDismiss = { screenModel.selectSeries(null) },
                onSearchInSources = {
                    screenModel.selectSeries(null)
                    navigator.push(GlobalSearchScreen(series.title))
                },
                onOpenInBrowser = {
                    series.trackingUrl?.let { url ->
                        context.openInBrowser(url)
                    }
                },
            )
        }

        if (showAnilistSectionFilterSheet) {
            AnilistSectionFilterSheet(
                enabledSections = enabledAnilistSections,
                onToggleSection = screenModel::toggleAnilistHomeSection,
                onSelectAll = screenModel::selectAllAnilistHomeSections,
                onDeselectAll = screenModel::deselectAllAnilistHomeSections,
                onDismissRequest = { showAnilistSectionFilterSheet = false },
            )
        }
    }
}

private data class SubTabItem(val title: String, val icon: ImageVector)

data class TrackSeriesItem(
    val title: String,
    val coverUrl: String? = null,
    val type: String? = null,
    val status: String? = null,
    val rating: String? = null,
    val score: Double? = null,
    val description: String? = null,
    val trackingUrl: String? = null,
    val year: String? = null,
    val authors: String? = null,
    val genres: List<String> = emptyList(),
)

fun ALSearchItem.toTrackSeriesItem(activeMediaType: MediaType): TrackSeriesItem {
    return TrackSeriesItem(
        title = title.userPreferred,
        coverUrl = coverImage.large,
        type = format ?: if (activeMediaType == MediaType.ANIME) "ANIME" else "MANGA",
        status = status,
        rating = averageScore?.let { "$it%" },
        score = averageScore?.toDouble(),
        description = description,
        trackingUrl = if (activeMediaType == MediaType.ANIME) AnilistApi.animeUrl(id) else AnilistApi.mangaUrl(id),
        year = startDate?.year?.toString(),
        authors = staff?.edges?.mapNotNull { it.node.name() }?.joinToString(", ")
            ?: studios?.edges?.filter { it.isMain }?.map { it.node.name }?.joinToString(", "),
        genres = genres ?: emptyList(),
    )
}

fun MALMediaItem.toTrackSeriesItem(): TrackSeriesItem {
    return TrackSeriesItem(
        title = title,
        coverUrl = coverUrl,
        type = format?.uppercase() ?: if (isAnime) "ANIME" else "MANGA",
        status = status?.replace("_", " ")?.uppercase(),
        rating = score?.let { String.format("%.2f", it) },
        score = score?.times(10),
        description = synopsis,
        trackingUrl = if (isAnime) "https://myanimelist.net/anime/$id" else "https://myanimelist.net/manga/$id",
        year = startDate?.take(4),
        authors = authors,
        genres = genres,
    )
}

// ==========================================
// SERIES DETAILS BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrackSeriesDetailsSheet(
    item: TrackSeriesItem,
    onDismiss: () -> Unit,
    onSearchInSources: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    KisaraBottomSheet(
        onDismissRequest = onDismiss,
        title = item.title,
        subtitle = item.type,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!item.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    item.rating?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    item.status?.let { status ->
                        Text(
                            text = "Status: $status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item.year?.let { year ->
                        Text(
                            text = "Year: $year",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item.authors?.let { authors ->
                        Text(
                            text = authors,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (item.genres.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.genres.forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onSearchInSources,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(KMR.strings.action_search_in_sources))
                }

                if (!item.trackingUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(KMR.strings.action_open_in_browser))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// ANILIST ANIME COMPOSABLES
// ==========================================
@Composable
private fun AniListTrendingFeed(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadTrendingAnime()
    }

    if (state.isLoadingTrendingAnime) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AniListAnimeGrid(
            items = state.trendingAnime,
            onItemClick = { anime ->
                screenModel.selectSeries(
                    TrackSeriesItem(
                        title = anime.title,
                        coverUrl = anime.largeImageUrl,
                        type = "Anime (${anime.format})",
                        status = anime.publishingStatus,
                        rating = if (anime.averageScore > 0) "${anime.averageScore}%" else null,
                        score = anime.averageScore.toDouble(),
                        description = anime.description,
                        trackingUrl = "https://anilist.co/anime/${anime.remoteId}",
                        genres = anime.genres,
                        authors = anime.studios.edges.firstOrNull { it.isMain }?.node?.name
                            ?: anime.studios.edges.firstOrNull()?.node?.name,
                    ),
                )
            },
        )
    }
}

@Composable
private fun AniListSeasonalFeed(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadSeasonalAnime()
    }

    if (state.isLoadingSeasonalAnime) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AniListAnimeGrid(
            items = state.seasonalAnime,
            onItemClick = { anime ->
                screenModel.selectSeries(
                    TrackSeriesItem(
                        title = anime.title,
                        coverUrl = anime.largeImageUrl,
                        type = "Anime (${anime.format})",
                        status = anime.publishingStatus,
                        rating = if (anime.averageScore > 0) "${anime.averageScore}%" else null,
                        score = anime.averageScore.toDouble(),
                        description = anime.description,
                        trackingUrl = "https://anilist.co/anime/${anime.remoteId}",
                        genres = anime.genres,
                    ),
                )
            },
        )
    }
}

@Composable
private fun AniListTopRatedFeed(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadTopRatedAnime()
    }

    if (state.isLoadingTopAnime) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AniListAnimeGrid(
            items = state.topAnime,
            onItemClick = { anime ->
                screenModel.selectSeries(
                    TrackSeriesItem(
                        title = anime.title,
                        coverUrl = anime.largeImageUrl,
                        type = "Anime (${anime.format})",
                        status = anime.publishingStatus,
                        rating = if (anime.averageScore > 0) "${anime.averageScore}%" else null,
                        score = anime.averageScore.toDouble(),
                        description = anime.description,
                        trackingUrl = "https://anilist.co/anime/${anime.remoteId}",
                        genres = anime.genres,
                    ),
                )
            },
        )
    }
}

@Composable
private fun AniListSearchSection(screenModel: TrackScreenModel) {
    var query by remember { mutableStateOf("") }
    val state by screenModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Anime (AniList)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.searchAnime(query) }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isSearchingAnime) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AniListAnimeGrid(
                items = state.animeSearchResults,
                onItemClick = { anime ->
                    screenModel.selectSeries(
                        TrackSeriesItem(
                            title = anime.title,
                            coverUrl = anime.largeImageUrl,
                            type = "Anime (${anime.format})",
                            status = anime.publishingStatus,
                            rating = if (anime.averageScore > 0) "${anime.averageScore}%" else null,
                            score = anime.averageScore.toDouble(),
                            description = anime.description,
                            trackingUrl = "https://anilist.co/anime/${anime.remoteId}",
                            genres = anime.genres,
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AniListGenresSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadAnimeGenres()
    }

    if (state.isLoadingAnimeGenres) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Anime Genres & Tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (genre in state.animeGenres) {
                        AssistChip(
                            onClick = {
                                screenModel.searchAnime(genre)
                            },
                            label = { Text(genre) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AniListStudiosSection(screenModel: TrackScreenModel) {
    var query by remember { mutableStateOf("") }
    val state by screenModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Studios (e.g. Mappa, Ufotable)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.loadAnimeStudios(query) }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoadingAnimeStudios) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.animeStudios) { studio ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                screenModel.searchAnime(studio.name)
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = studio.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniListMyListSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    var selectedStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedStatus) {
        if (screenModel.isAniListLoggedIn()) {
            screenModel.loadUserAnimeList(selectedStatus)
        }
    }

    if (!screenModel.isAniListLoggedIn()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "AniList Account Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(KMR.strings.al_login_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "ALL" to null,
                    "Watching" to "CURRENT",
                    "Completed" to "COMPLETED",
                    "Paused" to "PAUSED",
                    "Dropped" to "DROPPED",
                    "Planning" to "PLANNING",
                    "Rewatching" to "REPEATING",
                ).forEach { (label, status) ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status },
                        label = { Text(label) },
                    )
                }
            }

            if (state.isLoadingUserAnimeList) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.userAnimeList) { userItem ->
                        val anime = userItem.anime
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    screenModel.selectSeries(
                                        TrackSeriesItem(
                                            title = anime.title,
                                            coverUrl = anime.largeImageUrl,
                                            type = "Anime (${anime.format})",
                                            status = userItem.listStatus,
                                            rating = if (userItem.scoreRaw > 0) "${userItem.scoreRaw}%" else null,
                                            score = userItem.scoreRaw.toDouble(),
                                            description = anime.description,
                                            trackingUrl = "https://anilist.co/anime/${anime.remoteId}",
                                            genres = anime.genres,
                                        ),
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = anime.largeImageUrl,
                                    contentDescription = anime.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(60.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = anime.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Progress: ${userItem.episodesSeen} / ${if (anime.totalEpisodes > 0) anime.totalEpisodes else '?'}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (userItem.scoreRaw > 0) {
                                        Text(
                                            text = "Score: ${userItem.scoreRaw}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
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

@Composable
private fun AniListProfileSection(screenModel: TrackScreenModel) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    val isLoggedIn = screenModel.isAniListLoggedIn()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "AniList Integration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isLoggedIn) {
                        "Connected to AniList. Anime tracking and episode progress are active."
                    } else {
                        "Log in with your AniList token to enable full tracker synchronization for Anime."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isLoggedIn) {
                    FilledTonalButton(
                        onClick = { screenModel.logoutAniList() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log Out from AniList")
                    }
                } else {
                    Button(
                        onClick = {
                            context.openInBrowser("https://anilist.co/api/v2/oauth/authorize?client_id=16801&response_type=token")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Get AniList Login Token")
                    }

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Paste AniList Access Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            if (tokenInput.isNotBlank()) {
                                screenModel.loginAniList(tokenInput.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect Account")
                    }
                }
            }
        }
    }
}

@Composable
private fun AniListAnimeGrid(
    items: List<ALAnime>,
    onItemClick: (ALAnime) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { anime ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onItemClick(anime) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    AsyncImage(
                        model = anime.largeImageUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (anime.averageScore > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "${anime.averageScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ==========================================
// NOVEL & MANGAUPDATES COMPOSABLES
// ==========================================
@Composable
private fun MangaUpdatesNovelReleaseFeed(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadNovelReleases()
    }

    if (state.isLoadingNovelReleases) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.novelReleases) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            screenModel.selectSeries(
                                TrackSeriesItem(
                                    title = item.title.orEmpty(),
                                    type = "Light Novel",
                                    status = "Recent Release",
                                ),
                            )
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Ch. ${item.chapter ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = item.groups.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
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
private fun MangaUpdatesNovelRecommended(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadNovelRecommended()
    }

    if (state.isLoadingNovelRecommended) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.novelRecommendedSeries) { series ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            screenModel.selectSeries(
                                TrackSeriesItem(
                                    title = series.title.orEmpty(),
                                    coverUrl = series.image?.url?.original,
                                    type = series.type ?: "Novel",
                                    status = series.status,
                                    rating = series.bayesianRating?.let { "$it" },
                                    score = series.bayesianRating,
                                    description = series.description,
                                    trackingUrl = series.url,
                                    year = series.year,
                                    genres = series.genres?.mapNotNull { it.genre } ?: emptyList(),
                                ),
                            )
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = series.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            series.bayesianRating?.let { rating ->
                                if (rating > 0.0) {
                                    Text(
                                        text = "★ $rating",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        series.description?.let { desc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
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
private fun MangaUpdatesNovelSearchSection(screenModel: TrackScreenModel) {
    var query by remember { mutableStateOf("") }
    val state by screenModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Novels (MangaUpdates)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.searchNovels(query) }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isSearchingNovels) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.novelSearchResults) { record ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                screenModel.selectSeries(
                                    TrackSeriesItem(
                                        title = record.title.orEmpty(),
                                        coverUrl = record.image?.url?.original,
                                        type = record.type ?: "Novel",
                                        status = record.status,
                                        rating = record.bayesianRating?.let { "$it" },
                                        score = record.bayesianRating,
                                        description = record.description,
                                        trackingUrl = record.url,
                                        year = record.year,
                                        genres = record.genres?.mapNotNull { it.genre } ?: emptyList(),
                                    ),
                                )
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = record.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            record.bayesianRating?.let { rating ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "★ Rating: $rating",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// MANGA MANGAUPDATES COMPOSABLES
// ==========================================
@Composable
private fun MangaUpdatesReleaseFeed(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadNewReleases()
    }

    if (state.isLoadingReleases) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.newReleases) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            screenModel.selectSeries(
                                TrackSeriesItem(
                                    title = item.title.orEmpty(),
                                    type = "Manga Release",
                                    status = "Recent Release",
                                ),
                            )
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Ch. ${item.chapter ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = item.groups.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
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
private fun MangaUpdatesRecommended(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    LaunchedEffect(Unit) {
        screenModel.loadRecommended()
    }

    if (state.isLoadingRecommended) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.recommendedSeries) { series ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            screenModel.selectSeries(
                                TrackSeriesItem(
                                    title = series.title.orEmpty(),
                                    coverUrl = series.image?.url?.original,
                                    type = series.type ?: "Manga",
                                    status = series.status,
                                    rating = series.bayesianRating?.let { "$it" },
                                    score = series.bayesianRating,
                                    description = series.description,
                                    trackingUrl = series.url,
                                    year = series.year,
                                    genres = series.genres?.mapNotNull { it.genre } ?: emptyList(),
                                ),
                            )
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = series.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            series.bayesianRating?.let { rating ->
                                if (rating > 0.0) {
                                    Text(
                                        text = "★ $rating",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        series.description?.let { desc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
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
private fun MangaUpdatesSearchSection(screenModel: TrackScreenModel, mode: String, isNovel: Boolean = false) {
    var query by remember { mutableStateOf("") }
    val state by screenModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (isNovel) "Search Novels (MangaUpdates)" else "Search MangaUpdates") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (isNovel) screenModel.searchNovels(query) else screenModel.searchSeries(query)
            }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val isSearching = if (isNovel) state.isSearchingNovels else state.isSearching
        val results = if (isNovel) state.novelSearchResults else state.muSearchResults

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results) { record ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                screenModel.selectSeries(
                                    TrackSeriesItem(
                                        title = record.title.orEmpty(),
                                        coverUrl = record.image?.url?.original,
                                        type = record.type ?: if (isNovel) "Novel" else "Manga",
                                        status = record.status,
                                        rating = record.bayesianRating?.let { "$it" },
                                        score = record.bayesianRating,
                                        description = record.description,
                                        trackingUrl = record.url,
                                        year = record.year,
                                        genres = record.genres?.mapNotNull { it.genre } ?: emptyList(),
                                    ),
                                )
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = record.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            record.bayesianRating?.let { rating ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "★ Rating: $rating",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaUpdatesSeriesDirectory(screenModel: TrackScreenModel) {
    MangaUpdatesSearchSection(screenModel, "directory")
}

@Composable
private fun MangaUpdatesGroupsSection(screenModel: TrackScreenModel) {
    var query by remember { mutableStateOf("") }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Scanlation Groups") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.loadGroups(query) }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoadingGroups) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.groups) { group ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                group.name?.ifBlank { null }?.let {
                                    navigator.push(GlobalSearchScreen(it))
                                }
                            },
                    ) {
                        Text(
                            text = group.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaUpdatesAuthorsSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        screenModel.loadAuthors("")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Authors / Mangaka") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.loadAuthors(query) }) {
                Text("Search")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoadingAuthors) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.authors) { author ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                author.name?.ifBlank { null }?.let {
                                    navigator.push(GlobalSearchScreen(it))
                                }
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = author.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            author.birthplace?.let { place ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Birthplace: $place",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaUpdatesPublishersSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        screenModel.loadPublishers("")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Publishers") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.loadPublishers(query) }) {
                Text("Search")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoadingPublishers) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.publishers) { publisher ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                publisher.name?.ifBlank { null }?.let {
                                    navigator.push(GlobalSearchScreen(it))
                                }
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = publisher.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            publisher.type?.let { type ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Type: $type",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaUpdatesReviewsSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        screenModel.loadReviews("")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Reviews") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.loadReviews(query) }) {
                Text("Search")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoadingReviews) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.reviews) { review ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                review.title?.ifBlank { null }?.let {
                                    navigator.push(GlobalSearchScreen(it))
                                }
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = review.title.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                review.score?.let { score ->
                                    Text(
                                        text = "★ $score",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            review.body?.let { body ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaUpdatesGenresSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

    LaunchedEffect(Unit) {
        screenModel.loadGenres()
    }

    if (state.isLoadingGenres) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.genres) { genre ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            genre.genre?.ifBlank { null }?.let {
                                navigator.push(GlobalSearchScreen(it))
                            }
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = genre.genre.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            genre.stats?.series?.let { count ->
                                Text(
                                    text = "$count Series",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        genre.description?.let { desc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
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
private fun MangaUpdatesMyLists(screenModel: TrackScreenModel) {
    val isLoggedIn = screenModel.isMangaUpdatesLoggedIn()
    if (!isLoggedIn) {
        MangaUpdatesLoginCard(screenModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "MangaUpdates Account Connected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Your reading lists and tracker sync are actively connected to MangaUpdates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { screenModel.logoutMangaUpdates() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun MangaUpdatesUserCP(screenModel: TrackScreenModel) {
    val isLoggedIn = screenModel.isMangaUpdatesLoggedIn()
    if (!isLoggedIn) {
        MangaUpdatesLoginCard(screenModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "User Control Panel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Connected as MangaUpdates Member.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { screenModel.logoutMangaUpdates() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun MangaUpdatesLoginCard(screenModel: TrackScreenModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "MangaUpdates Login",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Log in to view your lists and sync tracked manga.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        isLoggingIn = true
                        screenModel.loginMangaUpdates(username, password) {
                            isLoggingIn = false
                        }
                    },
                    enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Log In")
                    }
                }
            }
        }
    }
}
