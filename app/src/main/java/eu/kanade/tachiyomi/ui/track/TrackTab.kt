package eu.kanade.tachiyomi.ui.track

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUAuthorRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGenreItem
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGroupRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUPublisherRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MURecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUReviewRecord
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TrackTab : Tab {

    private val selectSubTabEvent = kotlinx.coroutines.channels.Channel<Int>()
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
        val screenModel = rememberScreenModel { TrackScreenModel() }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val subTabs = persistentListOf(
            SubTabItem(stringResource(KMR.strings.label_mu_new_releases), Icons.Outlined.NewReleases),
            SubTabItem(stringResource(KMR.strings.label_mu_recommended), Icons.Outlined.AutoAwesome),
            SubTabItem(stringResource(KMR.strings.label_mu_releases), Icons.Outlined.Visibility),
            SubTabItem(stringResource(KMR.strings.label_mu_series_info), Icons.Outlined.Info),
            SubTabItem(stringResource(KMR.strings.label_mu_scanlators), Icons.Outlined.Group),
            SubTabItem(stringResource(KMR.strings.label_mu_mangaka), Icons.Outlined.Person),
            SubTabItem(stringResource(KMR.strings.label_mu_publishers), Icons.Outlined.Domain),
            SubTabItem(stringResource(KMR.strings.label_mu_reviews), Icons.Outlined.RateReview),
            SubTabItem(stringResource(KMR.strings.label_mu_genres), Icons.Outlined.Category),
            SubTabItem(stringResource(KMR.strings.label_mu_search), Icons.Outlined.Search),
            SubTabItem(stringResource(KMR.strings.label_mu_my_lists), Icons.AutoMirrored.Outlined.List),
            SubTabItem(stringResource(KMR.strings.label_mu_user_cp), Icons.Outlined.AccountCircle),
        )

        val pagerState = rememberPagerState(initialPage = currentPageIndex) { subTabs.size }

        LaunchedEffect(pagerState.currentPage) {
            currentPageIndex = pagerState.currentPage
        }

        LaunchedEffect(Unit) {
            selectSubTabEvent.receiveAsFlow().collectLatest { index ->
                pagerState.scrollToPage(index)
            }
        }

        val uiPreferences = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
        val showTrackSubBarAtTop by uiPreferences.showTrackSubBarAtTop().collectAsState()

        val subTabBar: @Composable () -> Unit = {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
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
                ) { page ->
                    when (page) {
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

private data class SubTabItem(val title: String, val icon: ImageVector)

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
                    modifier = Modifier.fillMaxWidth(),
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
                        item.releaseDate?.let { date ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                    modifier = Modifier.fillMaxWidth(),
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
                        series.type?.let { type ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
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
private fun MangaUpdatesSearchSection(screenModel: TrackScreenModel, mode: String) {
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
                label = { Text("Search MangaUpdates") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { screenModel.searchSeries(query) }) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.searchResults) { record ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
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
    val state by screenModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        screenModel.loadGroups("")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Groups / Scanlators") },
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.groups) { group ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = group.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            group.url?.let { url ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
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
private fun MangaUpdatesAuthorsSection(screenModel: TrackScreenModel) {
    val state by screenModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

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
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
    val isLoggedIn = screenModel.isLoggedIn()
    if (!isLoggedIn) {
        MangaUpdatesLoginCard(screenModel)
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("MangaUpdates Account Connected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MangaUpdatesUserCP(screenModel: TrackScreenModel) {
    val isLoggedIn = screenModel.isLoggedIn()
    if (!isLoggedIn) {
        MangaUpdatesLoginCard(screenModel)
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("User Control Panel Connected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
                        screenModel.login(username, password) {
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

class TrackScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(TrackState())
    val state = _state.asStateFlow()

    fun isLoggedIn(): Boolean {
        return trackerManager.mangaUpdates.isLoggedIn
    }

    fun login(u: String, p: String, onDone: () -> Unit) {
        screenModelScope.launch {
            try {
                trackerManager.mangaUpdates.login(u, p)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log in to MangaUpdates" }
            }
            onDone()
        }
    }

    fun loadNewReleases() {
        if (_state.value.newReleases.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingReleases = true) }
            try {
                val results = trackerManager.mangaUpdates.api.getRecentReleases()
                val mapped = results.map {
                    MUReleaseItem(
                        title = it.title,
                        chapter = it.chapter,
                        groups = it.groups?.mapNotNull { g -> g.name }?.joinToString(", "),
                        releaseDate = it.releaseDate,
                    )
                }
                _state.update { it.copy(newReleases = mapped, isLoadingReleases = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates new releases" }
                _state.update { it.copy(isLoadingReleases = false) }
            }
        }
    }

    fun loadRecommended() {
        if (_state.value.recommendedSeries.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingRecommended = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search("a")
                _state.update { it.copy(recommendedSeries = results, isLoadingRecommended = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates recommendations" }
                _state.update { it.copy(isLoadingRecommended = false) }
            }
        }
    }

    fun searchSeries(query: String) {
        val q = query.trim().ifBlank { "a" }
        screenModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search(q)
                _state.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to search MangaUpdates series" }
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadGenres() {
        if (_state.value.genres.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingGenres = true) }
            try {
                val results = trackerManager.mangaUpdates.api.getGenres()
                _state.update { it.copy(genres = results, isLoadingGenres = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates genres" }
                _state.update { it.copy(isLoadingGenres = false) }
            }
        }
    }

    fun loadGroups(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingGroups = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchGroups(query)
                _state.update { it.copy(groups = results, isLoadingGroups = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates groups" }
                _state.update { it.copy(isLoadingGroups = false) }
            }
        }
    }

    fun loadAuthors(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingAuthors = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchAuthors(query)
                _state.update { it.copy(authors = results, isLoadingAuthors = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates authors" }
                _state.update { it.copy(isLoadingAuthors = false) }
            }
        }
    }

    fun loadPublishers(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingPublishers = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchPublishers(query)
                _state.update { it.copy(publishers = results, isLoadingPublishers = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates publishers" }
                _state.update { it.copy(isLoadingPublishers = false) }
            }
        }
    }

    fun loadReviews(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingReviews = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchReviews(query)
                _state.update { it.copy(reviews = results, isLoadingReviews = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates reviews" }
                _state.update { it.copy(isLoadingReviews = false) }
            }
        }
    }
}

data class TrackState(
    val isLoadingReleases: Boolean = false,
    val newReleases: List<MUReleaseItem> = emptyList(),
    val isLoadingRecommended: Boolean = false,
    val recommendedSeries: List<MURecord> = emptyList(),
    val isSearching: Boolean = false,
    val searchResults: List<MURecord> = emptyList(),
    val isLoadingGenres: Boolean = false,
    val genres: List<MUGenreItem> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val groups: List<MUGroupRecord> = emptyList(),
    val isLoadingAuthors: Boolean = false,
    val authors: List<MUAuthorRecord> = emptyList(),
    val isLoadingPublishers: Boolean = false,
    val publishers: List<MUPublisherRecord> = emptyList(),
    val isLoadingReviews: Boolean = false,
    val reviews: List<MUReviewRecord> = emptyList(),
)

data class MUReleaseItem(
    val title: String?,
    val chapter: String?,
    val groups: String?,
    val releaseDate: String? = null,
)
