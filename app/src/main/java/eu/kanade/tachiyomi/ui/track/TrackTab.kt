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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.category.novel.NovelCategoryScreen
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import tachiyomi.i18n.MR
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
    val selectSubSubTabEvent = kotlinx.coroutines.channels.Channel<Int>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val switchTrackerServiceEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val openSectionFilterEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val refreshEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val searchEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var currentPageIndex by mutableIntStateOf(0)
        private set
    var currentSubSubTabIndex by mutableIntStateOf(0)
    var currentSubSubTabTitles by mutableStateOf<List<String>>(emptyList())

    fun showSubTab(index: Int) {
        currentPageIndex = index
        selectSubTabEvent.trySend(index)
    }

    fun showSubSubTab(index: Int) {
        currentSubSubTabIndex = index
        selectSubSubTabEvent.trySend(index)
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
                UiPreferences.TrackTabService.MANGA_UPDATES -> {
                    if (activeMediaType == MediaType.NOVEL) {
                        screenModel.loadNovelReleases()
                        screenModel.loadNovelRecommended()
                    } else {
                        screenModel.loadNewReleases()
                        screenModel.loadRecommended()
                    }
                }
            }
        }

        val subTabs = remember(activeMediaType, activeTrackerService) {
            when (activeTrackerService) {
                UiPreferences.TrackTabService.MANGA_UPDATES -> {
                    persistentListOf(
                        SubTabItem("Releases", Icons.Outlined.NewReleases),
                        SubTabItem("Recommended", Icons.Outlined.AutoAwesome),
                        SubTabItem("Search", Icons.Outlined.Search),
                        SubTabItem("Reviews", Icons.Outlined.RateReview),
                    )
                }
                else -> {
                    persistentListOf(
                        SubTabItem("Home", Icons.Outlined.AutoAwesome),
                        SubTabItem("My List", Icons.AutoMirrored.Outlined.List),
                        SubTabItem("Search", Icons.Outlined.Search),
                        SubTabItem("Profile", Icons.Outlined.AccountCircle),
                    )
                }
            }
        }

        val pagerState = rememberPagerState(initialPage = currentPageIndex.coerceIn(0, subTabs.size - 1)) { subTabs.size }
        var showAnilistSectionFilterSheet by remember { mutableStateOf(false) }
        var showTrackerServiceDialog by remember { mutableStateOf(false) }
        val enabledAnilistSections by uiPreferences.anilistHomeEnabledSections().collectAsState()

        LaunchedEffect(pagerState.currentPage) {
            currentPageIndex = pagerState.currentPage
        }

        LaunchedEffect(Unit) {
            launch {
                selectSubTabEvent.receiveAsFlow().collectLatest { index ->
                    if (index in 0 until subTabs.size) {
                        if (kotlin.math.abs(index - pagerState.currentPage) <= 1) {
                            pagerState.animateScrollToPage(index)
                        } else {
                            pagerState.scrollToPage(index)
                        }
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
            launch {
                switchTrackerServiceEvent.receiveAsFlow().collectLatest {
                    showTrackerServiceDialog = true
                }
            }
            launch {
                openSectionFilterEvent.receiveAsFlow().collectLatest {
                    showAnilistSectionFilterSheet = true
                }
            }
            launch {
                refreshEvent.receiveAsFlow().collectLatest {
                    when (activeTrackerService) {
                        UiPreferences.TrackTabService.ANILIST -> {
                            screenModel.loadAnilistHome(activeMediaType, forceRefresh = true)
                            screenModel.loadAnilistUserList(activeMediaType, forceRefresh = true)
                            screenModel.loadAnilistStats()
                        }
                        UiPreferences.TrackTabService.MAL -> {
                            screenModel.loadMALHome(activeMediaType, forceRefresh = true)
                            screenModel.loadMALUserList(activeMediaType, forceRefresh = true)
                            screenModel.loadMALProfile()
                        }
                        UiPreferences.TrackTabService.MANGA_UPDATES -> {
                            if (activeMediaType == MediaType.NOVEL) {
                                screenModel.loadNovelReleases()
                                screenModel.loadNovelRecommended()
                            } else {
                                screenModel.loadNewReleases()
                                screenModel.loadRecommended()
                            }
                        }
                    }
                }
            }
            launch {
                searchEvent.receiveAsFlow().collectLatest {
                    showSubTab(2)
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
                            scope.launch {
                                if (kotlin.math.abs(index - pagerState.currentPage) <= 1) {
                                    pagerState.animateScrollToPage(index)
                                } else {
                                    pagerState.scrollToPage(index)
                                }
                            }
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

        Scaffold { paddingValues ->
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
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (activeTrackerService) {
                        UiPreferences.TrackTabService.MAL -> {
                            when (page) {
                                0 -> MALHomeScreen(
                                    sections = state.malHomeSections,
                                    isLoading = state.isLoadingMALHome,
                                    onRefresh = { screenModel.loadMALHome(activeMediaType, forceRefresh = true) },
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                        UiPreferences.TrackTabService.MANGA_UPDATES -> {
                            when (page) {
                                0 -> MangaUpdatesReleasesScreen(
                                    releases = if (activeMediaType == MediaType.NOVEL) state.novelReleases else state.newReleases,
                                    isLoading = if (activeMediaType == MediaType.NOVEL) state.isLoadingNovelReleases else state.isLoadingReleases,
                                    onItemClick = { title ->
                                        navigator.push(TrackerMediaDetailsScreen(TrackSeriesItem(title = title, mediaType = activeMediaType), activeMediaType))
                                    },
                                )
                                1 -> MangaUpdatesRecommendedScreen(
                                    items = if (activeMediaType == MediaType.NOVEL) state.novelRecommendedSeries else state.recommendedSeries,
                                    isLoading = if (activeMediaType == MediaType.NOVEL) state.isLoadingNovelRecommended else state.isLoadingRecommended,
                                    activeMediaType = activeMediaType,
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
                                    },
                                )
                                2 -> MangaUpdatesSearchScreen(
                                    results = if (activeMediaType == MediaType.NOVEL) state.novelSearchResults else state.muSearchResults,
                                    isSearching = if (activeMediaType == MediaType.NOVEL) state.isSearchingNovels else false,
                                    activeMediaType = activeMediaType,
                                    onSearch = { query ->
                                        if (activeMediaType == MediaType.NOVEL) screenModel.searchNovels(query) else screenModel.searchSeries(query)
                                    },
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
                                    },
                                )
                                3 -> MangaUpdatesReviewsScreen(
                                    reviews = state.reviews,
                                    isLoading = state.isLoadingReviews,
                                    onSearch = screenModel::loadReviews,
                                )
                                else -> Box(Modifier.fillMaxSize())
                            }
                        }
                        else -> {
                            when (page) {
                                0 -> AnilistHomeScreen(
                                    sections = state.homeSections,
                                    isLoading = state.isLoadingHome,
                                    onRefresh = { screenModel.loadAnilistHome(activeMediaType, forceRefresh = true) },
                                    onItemClick = { item ->
                                        navigator.push(TrackerMediaDetailsScreen(item.toTrackSeriesItem(activeMediaType), activeMediaType))
                                    },
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                                    onItemLongClick = { item ->
                                        screenModel.showSetCategoryDialog(item.toTrackSeriesItem(activeMediaType), activeMediaType)
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
                    }
                }
            }
        }

        state.selectedSeries?.let { series ->
            TrackSeriesDetailsSheet(
                item = series,
                onDismiss = { screenModel.selectSeries(null) },
                onAddToLocalLibrary = {
                    screenModel.showSetCategoryDialog(series, activeMediaType)
                },
                onAddToTrackerLibrary = {
                    screenModel.showTrackerStatusPicker(series, isAniList = activeTrackerService == UiPreferences.TrackTabService.ANILIST)
                },
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

        val currentDialog = state.dialog
        when (currentDialog) {
            is TrackScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = currentDialog.initialSelection,
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setMangaCategories(currentDialog.manga, included)
                    },
                    manga = currentDialog.manga,
                )
            }
            is TrackScreenModel.Dialog.ChangeAnimeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = currentDialog.initialSelection,
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(AnimeCategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setAnimeCategories(currentDialog.anime, included)
                    },
                    anime = currentDialog.anime,
                )
            }
            is TrackScreenModel.Dialog.ChangeNovelCategory -> {
                ChangeCategoryDialog(
                    initialSelection = currentDialog.initialSelection,
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(NovelCategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setNovelCategories(currentDialog.novel, included)
                    },
                    novel = currentDialog.novel,
                )
            }
            is TrackScreenModel.Dialog.TrackerStatusPicker -> {
                TrackerStatusPickerDialog(
                    item = currentDialog.item,
                    isAniList = currentDialog.isAniList,
                    onSelectStatus = { status ->
                        screenModel.updateTrackerEntryStatus(currentDialog.item, status, currentDialog.isAniList)
                    },
                    onDismissRequest = screenModel::dismissDialog,
                )
            }
            null -> {}
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

        if (showTrackerServiceDialog) {
            TrackerServiceSelectorDialog(
                activeMediaType = activeMediaType,
                currentService = activeTrackerService,
                onSelectService = { selectedService ->
                    when (activeMediaType) {
                        MediaType.ANIME -> uiPreferences.trackTabAnimeService().set(selectedService)
                        MediaType.MANGA -> uiPreferences.trackTabMangaService().set(selectedService)
                        MediaType.NOVEL -> uiPreferences.trackTabNovelService().set(selectedService)
                    }
                },
                onDismissRequest = { showTrackerServiceDialog = false },
            )
        }
    }
}

@Composable
private fun TrackerServiceSelectorDialog(
    activeMediaType: MediaType,
    currentService: UiPreferences.TrackTabService,
    onSelectService: (UiPreferences.TrackTabService) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val availableServices = when (activeMediaType) {
        MediaType.ANIME -> listOf(
            UiPreferences.TrackTabService.ANILIST to "AniList",
            UiPreferences.TrackTabService.MAL to "MyAnimeList (MAL)",
        )
        MediaType.MANGA, MediaType.NOVEL -> listOf(
            UiPreferences.TrackTabService.ANILIST to "AniList",
            UiPreferences.TrackTabService.MAL to "MyAnimeList (MAL)",
            UiPreferences.TrackTabService.MANGA_UPDATES to "MangaUpdates",
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Select Tracker Service (${activeMediaType.name.lowercase().replaceFirstChar { it.uppercase() }})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableServices.forEach { (service, name) ->
                    val isSelected = service == currentService
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectService(service)
                                onDismissRequest()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectService(service)
                                onDismissRequest()
                            },
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
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
    val trackerId: Long? = null,
    val mediaType: MediaType = MediaType.MANGA,
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
        trackerId = id,
        mediaType = activeMediaType,
    )
}

fun MALMediaItem.toTrackSeriesItem(activeMediaType: MediaType = MediaType.MANGA): TrackSeriesItem {
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
        trackerId = id,
        mediaType = if (isAnime) MediaType.ANIME else activeMediaType,
    )
}

fun MURecord.toTrackSeriesItem(activeMediaType: MediaType = MediaType.MANGA): TrackSeriesItem {
    return TrackSeriesItem(
        title = title ?: "",
        coverUrl = image?.url?.original,
        type = type?.uppercase() ?: if (activeMediaType == MediaType.NOVEL) "NOVEL" else "MANGA",
        status = status,
        rating = bayesianRating?.let { String.format("%.2f", it) },
        score = bayesianRating?.times(10),
        description = description,
        trackingUrl = url,
        year = year?.toString(),
        authors = authors?.joinToString(", ") { it.name ?: "" },
        genres = genres?.mapNotNull { it.genre } ?: emptyList(),
        trackerId = seriesId,
        mediaType = activeMediaType,
    )
}

@Composable
private fun TrackerStatusPickerDialog(
    item: TrackSeriesItem,
    isAniList: Boolean,
    onSelectStatus: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val serviceName = if (isAniList) "AniList" else "MyAnimeList"
    val isAnime = item.mediaType == MediaType.ANIME
    val statusOptions = if (isAniList) {
        listOf(
            "PLANNING" to ("Plan to " + if (isAnime) "Watch" else "Read"),
            "CURRENT" to (if (isAnime) "Watching" else "Reading"),
            "COMPLETED" to "Completed",
            "PAUSED" to "Paused / On Hold",
            "DROPPED" to "Dropped",
            "REPEATING" to (if (isAnime) "Rewatching" else "Rereading"),
        )
    } else {
        listOf(
            (if (isAnime) "plan_to_watch" else "plan_to_read") to ("Plan to " + if (isAnime) "Watch" else "Read"),
            (if (isAnime) "watching" else "reading") to (if (isAnime) "Watching" else "Reading"),
            "completed" to "Completed",
            "on_hold" to "On Hold",
            "dropped" to "Dropped",
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Add to $serviceName Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                statusOptions.forEach { (statusKey, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectStatus(statusKey)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
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
    onAddToLocalLibrary: () -> Unit,
    onAddToTrackerLibrary: () -> Unit,
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

            // Quick Actions: Local Library & Tracker Library
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onAddToLocalLibrary,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Local Library")
                }

                FilledTonalButton(
                    onClick = onAddToTrackerLibrary,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Tracker")
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
// MANGAUPDATES SCREENS
// ==========================================
@Composable
fun MangaUpdatesReleasesScreen(
    releases: List<eu.kanade.tachiyomi.ui.track.MUReleaseItem>,
    isLoading: Boolean,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading && releases.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(releases) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item.title?.let(onItemClick) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.title ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        item.chapter?.let { ch ->
                            Text(
                                text = "Ch. $ch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        item.releaseDate?.let { date ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!item.groups.isNullOrEmpty()) {
                        Text(
                            text = item.groups,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MangaUpdatesRecommendedScreen(
    items: List<MURecord>,
    isLoading: Boolean,
    activeMediaType: MediaType,
    onItemClick: (MURecord) -> Unit,
    onItemLongClick: (MURecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading && items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { record ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onItemClick(record) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = record.image?.url?.original,
                        contentDescription = record.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(6.dp),
                    ) {
                        Text(
                            text = record.title ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MangaUpdatesSearchScreen(
    results: List<MURecord>,
    isSearching: Boolean,
    activeMediaType: MediaType,
    onSearch: (String) -> Unit,
    onItemClick: (MURecord) -> Unit,
    onItemLongClick: (MURecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            placeholder = { Text("Search MangaUpdates...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
        )

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onItemClick(record) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = record.image?.url?.original,
                                contentDescription = record.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .padding(6.dp),
                            ) {
                                Text(
                                    text = record.title ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
fun MangaUpdatesReviewsScreen(
    reviews: List<MUReviewRecord>,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            placeholder = { Text("Search reviews by title...") },
            leadingIcon = { Icon(Icons.Outlined.RateReview, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
        )

        if (isLoading && reviews.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(reviews) { review ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = review.title ?: "Review",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            review.score?.let { r ->
                                Text(
                                    text = "★ $r / 10",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            review.body?.let { body ->
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
