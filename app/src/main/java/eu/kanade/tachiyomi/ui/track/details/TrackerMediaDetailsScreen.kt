package eu.kanade.tachiyomi.ui.track.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.track.TrackSeriesItem
import eu.kanade.tachiyomi.util.system.openInBrowser

// KMK -->
class TrackerMediaDetailsScreen(
    private val series: TrackSeriesItem,
    private val mediaType: MediaType = MediaType.MANGA,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            TrackerMediaDetailsScreenModel(series = series, mediaType = mediaType)
        }
        val state by screenModel.state.collectAsState()
        var showPrioritySheet by remember { mutableStateOf(false) }
        var isSynopsisExpanded by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = series.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Bookmark / Library
                        if (state.matchedManga != null) {
                            IconButton(onClick = { screenModel.toggleLibraryBookmark() }) {
                                Icon(
                                    imageVector = if (state.inLibrary) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Library",
                                    tint = if (state.inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Search in Sources
                        IconButton(onClick = { navigator.push(GlobalSearchScreen(series.title)) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search in Sources")
                        }

                        // Open in Browser
                        series.trackingUrl?.let { url ->
                            IconButton(onClick = { context.openInBrowser(url) }) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open in Browser")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Header & Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (!series.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = series.coverUrl,
                                    contentDescription = series.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = series.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    series.rating?.let { rating ->
                                        Text(
                                            text = "★ $rating",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    series.type?.let { type ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                                        )
                                    }
                                }

                                series.status?.let { status ->
                                    Text(
                                        text = "Status: $status",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                series.year?.let { year ->
                                    Text(
                                        text = "Year: $year",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                series.authors?.let { authors ->
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

                        // Genres
                        if (series.genres.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                series.genres.forEach { genre ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }

                        // Synopsis
                        if (!series.description.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = series.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded },
                            )
                            Text(
                                text = if (isSynopsisExpanded) "Show less" else "Read more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                    .padding(top = 2.dp),
                            )
                        }
                    }
                }

                // Source Hierarchy Selector Bar
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Source",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = state.activeSource?.name ?: if (state.isMatching) "Searching extensions..." else "No source matched",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.activeSource != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(onClick = { showPrioritySheet = true }) {
                                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sources (${state.prioritizedSourceIds.size})")
                                }

                                IconButton(onClick = { screenModel.loadSourcesAndMatch() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
                                }
                            }
                        }
                    }
                }

                // Chapter Header & Search/Sort Filter
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Chapters (${state.filteredChapters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        IconButton(onClick = { screenModel.toggleSortDirection() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort Direction",
                                tint = if (state.isSortAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.chapterSearchQuery,
                        onValueChange = { screenModel.setChapterSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text("Filter chapters/episodes...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )
                }

                // Chapter / Episode State Views
                if (state.isMatching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Auto-searching top priority extensions...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (state.matchError != null || state.matchedManga == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = state.matchError ?: "No match found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { showPrioritySheet = true }) {
                                        Text("Reorder / Pick Source")
                                    }
                                    OutlinedButton(onClick = { navigator.push(GlobalSearchScreen(series.title)) }) {
                                        Text("Manual Search")
                                    }
                                }
                            }
                        }
                    }
                } else if (state.filteredChapters.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No chapters found for this query.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Chapter Items List
                    items(state.filteredChapters, key = { it.id }) { chapter ->
                        val manga = state.matchedManga ?: return@items
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        ReaderActivity.newIntent(context, manga.id, chapter.id)
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (chapter.read) Icons.Outlined.CheckCircle else Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    tint = if (chapter.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )

                                Column {
                                    Text(
                                        text = chapter.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (!chapter.read) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (chapter.dateUpload > 0) {
                                        Text(
                                            text = relativeDateText(chapter.dateUpload),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }

                            if (chapter.scanlator != null) {
                                Text(
                                    text = chapter.scanlator.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }

        if (showPrioritySheet) {
            TrackerSourcePrioritySheet(
                prioritizedSourceIds = state.prioritizedSourceIds,
                allInstalledSources = state.allInstalledSources,
                activeSourceId = state.activeSource?.id,
                onSavePriorityList = { newOrder ->
                    screenModel.updatePriorityOrder(newOrder)
                },
                onDirectSelectSource = { sourceId ->
                    screenModel.selectSpecificSource(sourceId)
                },
                onDismissRequest = { showPrioritySheet = false },
            )
        }
    }
}
// KMK <--
