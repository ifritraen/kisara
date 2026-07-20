package eu.kanade.tachiyomi.ui.suggestions

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.DisplayOverlaySettingsDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.library.components.SourceIconBadge
import eu.kanade.presentation.library.components.UnreadBadge
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.interactor.GetSuggestionArtists
import tachiyomi.domain.suggestions.interactor.GetSuggestionAuthors
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.service.SuggestionsPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun suggestionsTab(
    screenModel: SuggestionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by screenModel.state.collectAsState()

    val fetchedCount by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.fetchedCount.collectAsState()
    val failedCount by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.failedCount.collectAsState()
    val fetchedBySource by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.fetchedBySource.collectAsState()
    val failedBySource by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.failedBySource.collectAsState()
    val libraryFilteredCount by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.libraryFilteredCount.collectAsState()
    val zeroScoreCount by eu.kanade.tachiyomi.data.suggestions.SuggestionsReport.zeroScoreCount.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }

    val suggestionsPreferences = remember { Injekt.get<SuggestionsPreferences>() }

    val nonBlockedTags = state.tags.filter { !it.isBlocked }
    val top10Tags = nonBlockedTags.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
    val activeTags = nonBlockedTags.filter { top10Tags.contains(it.tag) || it.isUserAdded }
        .sortedWith(compareBy<SuggestionTag> { it.sortOrder }.thenByDescending { it.count })

    val finalTags = if (activeTags.isEmpty()) {
        nonBlockedTags.sortedByDescending { it.count }.take(5)
    } else {
        activeTags
    }

    val nonBlockedSources = state.sources.filter { !it.isBlocked }
    val top5Sources = nonBlockedSources.sortedByDescending { it.count }.take(5).map { it.sourceId }.toSet()
    val activeSources = nonBlockedSources.filter { top5Sources.contains(it.sourceId) || it.isUserAdded }
        .sortedWith(compareBy<SuggestionSource> { it.sortOrder }.thenByDescending { it.count })

    val finalSources = if (activeSources.isEmpty()) {
        nonBlockedSources.sortedByDescending { it.count }.take(5)
    } else {
        activeSources
    }

    val maxTagsToMatch = suggestionsPreferences.maxTagsToMatch().get()
    val topMatchingTags = finalTags.take(maxTagsToMatch)
    val tagWeightsMap = topMatchingTags.mapIndexed { index, tag ->
        tag.tag.lowercase().trim() to ((maxTagsToMatch - index).toDouble() / maxTagsToMatch)
    }.toMap()

    val sourceWeightsMap = finalSources.mapIndexed { index, src ->
        src.sourceId to ((finalSources.size - index).toDouble() / finalSources.size)
    }.toMap()

    val maxSuggestionsToDisplay = suggestionsPreferences.maxSuggestionsToDisplay().get()
    val dynamicSuggestions = remember(state.suggestions, tagWeightsMap, sourceWeightsMap, maxSuggestionsToDisplay) {
        state.suggestions.map { suggestion ->
            val manga = suggestion.manga
            val extWeight = sourceWeightsMap[manga.source] ?: 0.1
            val score = if (manga.initialized && !manga.genre.orEmpty().isEmpty()) {
                var tagSum = 0.0
                manga.genre.orEmpty().forEach { genre ->
                    val clean = SuggestionsWorker.cleanAndFilterGenre(genre)
                    if (clean != null) {
                        tagSum += tagWeightsMap[clean] ?: 0.0
                    }
                }
                extWeight * tagSum
            } else {
                suggestion.relevance
            }
            suggestion.copy(relevance = score)
        }
            .filter { it.relevance > 0.0 }
            .sortedByDescending { it.relevance }
            .take(maxSuggestionsToDisplay)
    }

    var showDisplayOverlayDialog by rememberSaveable { mutableStateOf(false) }

    return TabContent(
        titleRes = KMR.strings.action_suggestions,
        badgeNumber = dynamicSuggestions.size,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { showDisplayOverlayDialog = true },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = { screenModel.triggerRefresh(context) },
            ),
        ),
        content = { paddingValues, _ ->
            if (showDisplayOverlayDialog) {
                DisplayOverlaySettingsDialog(onDismissRequest = { showDisplayOverlayDialog = false })
            }

            var isRefreshing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            var explainingSuggestion by remember { mutableStateOf<Suggestion?>(null) }

            // Change Category Dialog
            val dialog = state.dialog
            if (dialog is SuggestionsScreenModel.State.Dialog.ChangeCategory) {
                val changeCategoryManga = dialog.manga
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = { screenModel.dismissDialog() },
                    onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.CategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setMangaCategories(changeCategoryManga, included)
                    },
                    onDuplicateCheck = {
                        screenModel.dismissDialog()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen(changeCategoryManga.id))
                    },
                )
            }

            PullRefresh(
                refreshing = state.isLoading,
                enabled = true,
                onRefresh = {
                    screenModel.triggerRefresh(context)
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding()),
                ) {
                    // 1. Progress line at the very top
                    if (state.isLoading) {
                        val progress = state.fetchProgress
                        val total = state.fetchTotal
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { progress.toFloat() / total.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 2. Info Row in the same line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            IconButton(
                                onClick = { screenModel.triggerRefresh(context) },
                                enabled = !state.isLoading,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Refresh Suggestions",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.isLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Total: ${dynamicSuggestions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.isLoading && state.fetchProgress > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val newCount = dynamicSuggestions.size - screenModel.initialCount
                                val displayedNew = if (newCount > 0) newCount else 0
                                Text(
                                    text = "New: $displayedNew",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.isLoading) {
                                val left = (state.fetchTotal - state.fetchProgress).coerceAtLeast(0)
                                Text(
                                    text = "Left: $left tags",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { showReportDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.heightIn(max = 28.dp),
                            ) {
                                Text("Report", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // 3. Content area
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (state.isLoading && dynamicSuggestions.isEmpty()) {
                            LoadingScreen(modifier = Modifier.fillMaxSize())
                        } else if (dynamicSuggestions.isEmpty() && !state.isLoading) {
                            EmptyScreen(
                                modifier = Modifier.fillMaxSize(),
                                message = stringResource(KMR.strings.pref_suggestions_summary) + "\n\nPull down or tap Refresh to search sources.",
                            )
                        } else {
                            val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
                            val showDownloadBadge by libraryPreferences.downloadBadge().collectAsState()
                            val showUnreadBadge by libraryPreferences.unreadBadge().collectAsState()
                            val showLocalBadge by libraryPreferences.localBadge().collectAsState()
                            val showLanguageBadge by libraryPreferences.languageBadge().collectAsState()
                            val useLangIcon by libraryPreferences.useLangIcon().collectAsState()
                            val showSourceBadge by libraryPreferences.sourceBadge().collectAsState()

                            val downloadManager = remember { Injekt.get<DownloadManager>() }
                            val sourceManager = remember { Injekt.get<SourceManager>() }
                            val libraryMangaList by remember { Injekt.get<GetLibraryManga>().subscribe() }.collectAsState(initial = emptyList())

                            val orientation = LocalConfiguration.current.orientation
                            val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                            val columnsCount = if (isLandscape) {
                                libraryPreferences.landscapeColumns()
                            } else {
                                libraryPreferences.portraitColumns()
                            }.collectAsState().value
                            val columns = if (columnsCount == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columnsCount)

                            val gridState = rememberLazyGridState()
                            val shouldLoadMore = remember {
                                derivedStateOf {
                                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                                        ?: return@derivedStateOf false
                                    lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 4
                                }
                            }

                            androidx.compose.runtime.LaunchedEffect(shouldLoadMore.value) {
                                if (shouldLoadMore.value && !state.isLoadingNext) {
                                    screenModel.loadNextRank(context)
                                }
                            }

                            LazyVerticalGrid(
                                state = gridState,
                                columns = columns,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = paddingValues.calculateBottomPadding() + 8.dp,
                                    start = 8.dp,
                                    end = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
                                horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
                            ) {
                                items(
                                    items = dynamicSuggestions,
                                    key = { "suggestion-${it.manga.id}" },
                                ) { suggestion ->
                                    val manga = suggestion.manga
                                    MangaCompactGridItem(
                                        title = manga.title,
                                        coverData = MangaCover(
                                            mangaId = manga.id,
                                            sourceId = manga.source,
                                            isMangaFavorite = manga.favorite,
                                            ogUrl = manga.thumbnailUrl,
                                            lastModified = manga.coverLastModified,
                                        ),
                                        onClick = { navigator.push(MangaScreen(manga.id)) },
                                        onLongClick = { screenModel.toggleFavorite(manga) },
                                        manga = manga,
                                        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                                        coverBadgeStart = {
                                            val downloadCount = remember(manga.id) { downloadManager.getDownloadCount(manga).toLong() }
                                            val libraryManga = remember(libraryMangaList, manga.id) { libraryMangaList.firstOrNull { it.id == manga.id } }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (showDownloadBadge && downloadCount > 0) {
                                                    DownloadsBadge(count = downloadCount)
                                                }
                                                if (showUnreadBadge && (libraryManga?.unreadCount ?: 0L) > 0) {
                                                    UnreadBadge(count = libraryManga?.unreadCount ?: 0L)
                                                }
                                                if (manga.favorite) {
                                                    InLibraryBadge(enabled = true)
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(vertical = 4.dp, horizontal = 1.dp)
                                                            .width(14.dp)
                                                            .height(24.dp)
                                                            .background(
                                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                                shape = RoundedCornerShape(4.dp),
                                                            )
                                                            .clickable { screenModel.dismissSuggestion(manga.url, manga.title) },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Close,
                                                            contentDescription = "Dismiss",
                                                            modifier = Modifier.size(10.dp),
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        coverBadgeEnd = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isLocal = sourceManager.get(manga.source)?.isLocal() == true
                                                val lang = sourceManager.get(manga.source)?.lang.orEmpty()
                                                val source = sourceManager.get(manga.source)

                                                if (showLocalBadge && isLocal) {
                                                    Badge(
                                                        imageVector = Icons.Outlined.Folder,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        iconColor = MaterialTheme.colorScheme.onTertiary,
                                                    )
                                                }
                                                if (showLanguageBadge && source != null && !isLocal && lang.isNotEmpty()) {
                                                    LanguageBadge(
                                                        isLocal = false,
                                                        sourceLanguage = lang,
                                                        useLangIcon = useLangIcon,
                                                    )
                                                }
                                                if (showSourceBadge && source != null) {
                                                    val domainSource = remember(source) {
                                                        tachiyomi.domain.source.model.Source(
                                                            id = source.id,
                                                            lang = source.lang,
                                                            name = source.name,
                                                            supportsLatest = source is eu.kanade.tachiyomi.source.CatalogueSource,
                                                            isStub = false,
                                                        )
                                                    }
                                                    SourceIconBadge(source = domainSource)
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .padding(vertical = 4.dp, horizontal = 1.dp)
                                                        .width(14.dp)
                                                        .height(24.dp)
                                                        .background(
                                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                            shape = RoundedCornerShape(4.dp),
                                                        )
                                                        .clickable { explainingSuggestion = suggestion },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Info,
                                                        contentDescription = "Explain relevance score",
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explain Score Dialog
            if (explainingSuggestion != null) {
                val suggestion = explainingSuggestion!!
                val manga = suggestion.manga
                val suggestionsPreferences = remember { Injekt.get<SuggestionsPreferences>() }
                val sourceManager = remember { Injekt.get<SourceManager>() }
                val source = remember(manga.source) { sourceManager.get(manga.source) }

                val getSuggestionAuthors = remember { Injekt.get<GetSuggestionAuthors>() }
                val getSuggestionArtists = remember { Injekt.get<GetSuggestionArtists>() }
                val authors by remember { getSuggestionAuthors.subscribe() }.collectAsState(initial = emptyList())
                val artists by remember { getSuggestionArtists.subscribe() }.collectAsState(initial = emptyList())

                val historyRepository = remember { Injekt.get<tachiyomi.domain.history.repository.HistoryRepository>() }
                val mangaRepository = remember { Injekt.get<MangaRepository>() }
                val recentAuthorsAndArtists by produceState<Pair<Set<String>, Set<String>>>(initialValue = Pair(emptySet(), emptySet())) {
                    val readHistory = mangaRepository.getReadMangaNotInLibrary()
                    val authorsSet = mutableSetOf<String>()
                    val artistsSet = mutableSetOf<String>()
                    val now = System.currentTimeMillis()
                    readHistory.distinctBy { it.id }.forEach { m ->
                        val history = historyRepository.getHistoryByMangaId(m.id)
                        val lastReadTime = history.mapNotNull { it.readAt?.time }.maxOrNull()
                        if (lastReadTime != null) {
                            val diffDays = (now - lastReadTime) / (1000 * 60 * 60 * 24)
                            if (diffDays <= 7) {
                                m.author?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { authorsSet.add(it) }
                                m.artist?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { artistsSet.add(it) }
                            }
                        }
                    }
                    value = Pair(authorsSet, artistsSet)
                }
                val recentAuthors = recentAuthorsAndArtists.first
                val recentArtists = recentAuthorsAndArtists.second

                val nonBlockedTags = state.tags.filter { !it.isBlocked }
                val top10Tags = nonBlockedTags.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
                val activeTags = nonBlockedTags.filter { top10Tags.contains(it.tag) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionTag> { it.sortOrder }.thenByDescending { it.count })

                val finalTags = if (activeTags.isEmpty()) {
                    nonBlockedTags.sortedByDescending { it.count }.take(5)
                } else {
                    activeTags
                }

                val nonBlockedSources = state.sources.filter { !it.isBlocked }
                val top5Sources = nonBlockedSources.sortedByDescending { it.count }.take(5).map { it.sourceId }.toSet()
                val activeSources = nonBlockedSources.filter { top5Sources.contains(it.sourceId) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionSource> { it.sortOrder }.thenByDescending { it.count })

                val finalSources = if (activeSources.isEmpty()) {
                    nonBlockedSources.sortedByDescending { it.count }.take(5)
                } else {
                    activeSources
                }

                val nonBlockedAuthors = authors.filter { !it.isBlocked }
                val top5Authors = nonBlockedAuthors.sortedByDescending { it.count }.take(5).map { it.author }.toSet()
                val activeAuthors = nonBlockedAuthors.filter { top5Authors.contains(it.author) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionAuthor> { it.sortOrder }.thenByDescending { it.count })

                val nonBlockedArtists = artists.filter { !it.isBlocked }
                val top5Artists = nonBlockedArtists.sortedByDescending { it.count }.take(5).map { it.artist }.toSet()
                val activeArtists = nonBlockedArtists.filter { top5Artists.contains(it.artist) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionArtist> { it.sortOrder }.thenByDescending { it.count })

                val favoriteAuthors = nonBlockedAuthors.map { it.author }.toSet()
                val favoriteArtists = nonBlockedArtists.map { it.artist }.toSet()

                val maxTagsToMatch = suggestionsPreferences.maxTagsToMatch().get()
                val topMatchingTags = finalTags.take(maxTagsToMatch)
                val tagWeightsMap = topMatchingTags.mapIndexed { index, tag ->
                    tag.tag.lowercase().trim() to ((maxTagsToMatch - index).toDouble() / maxTagsToMatch)
                }.toMap()

                val sourceWeightsMap = finalSources.mapIndexed { index, src ->
                    src.sourceId to ((finalSources.size - index).toDouble() / finalSources.size)
                }.toMap()

                val extWeight = sourceWeightsMap[manga.source] ?: 0.1
                val matchedTagsInfo = mutableListOf<String>()
                var tagSum = 0.0
                manga.genre.orEmpty().forEach { genre ->
                    val clean = SuggestionsWorker.cleanAndFilterGenre(genre)
                    if (clean != null) {
                        val tagWeight = tagWeightsMap[clean]
                        if (tagWeight != null) {
                            tagSum += tagWeight
                            matchedTagsInfo.add("• $clean: ${String.format("%.2f", tagWeight)}")
                        }
                    }
                }

                val matchedAuthor = manga.author?.lowercase()?.trim()
                val matchedArtist = manga.artist?.lowercase()?.trim()
                if (!matchedAuthor.isNullOrBlank()) {
                    if (recentAuthors.contains(matchedAuthor)) {
                        tagSum += 2.5
                        matchedTagsInfo.add("• Recent Author ($matchedAuthor): 2.50")
                    } else if (favoriteAuthors.contains(matchedAuthor)) {
                        tagSum += 1.5
                        matchedTagsInfo.add("• Favorite Author ($matchedAuthor): 1.50")
                    }
                }
                if (!matchedArtist.isNullOrBlank()) {
                    if (recentArtists.contains(matchedArtist)) {
                        tagSum += 2.5
                        matchedTagsInfo.add("• Recent Artist ($matchedArtist): 2.50")
                    } else if (favoriteArtists.contains(matchedArtist)) {
                        tagSum += 1.5
                        matchedTagsInfo.add("• Favorite Artist ($matchedArtist): 1.50")
                    }
                }

                AlertDialog(
                    onDismissRequest = { explainingSuggestion = null },
                    title = { Text(text = "Why this recommendation?") },
                    text = {
                        Column {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Source Extension Weight:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "• ${source?.name ?: "Unknown Source"}: ${String.format("%.2f", extWeight)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Matching Tags (${matchedTagsInfo.size}):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (matchedTagsInfo.isEmpty()) {
                                Text(
                                    text = "• No matching tags",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                matchedTagsInfo.forEach {
                                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Total Score:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = String.format("%.2f", suggestion.relevance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { explainingSuggestion = null }) {
                            Text("Close")
                        }
                    },
                )
            }

            // Suggestions Generation Report Popup Dialog
            if (showReportDialog) {
                val allSourceNames = (fetchedBySource.keys + failedBySource.keys).sorted()
                AlertDialog(
                    onDismissRequest = { showReportDialog = false },
                    title = { Text("Suggestions Generation Report") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text("Total manga fetched: $fetchedCount", fontWeight = FontWeight.Bold)
                            Text("Total search failures: $failedCount", fontWeight = FontWeight.Bold)
                            Text("Excluded (Already in library/history): $libraryFilteredCount")
                            Text("Excluded (Zero relevance / no matching tags): $zeroScoreCount")
                            Text("Active suggestions (Displayed): ${dynamicSuggestions.size}")
                            HorizontalDivider()
                            if (allSourceNames.isEmpty()) {
                                Text("No extension queries made yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                allSourceNames.forEach { sourceName ->
                                    val fetchedVal = fetchedBySource[sourceName] ?: 0
                                    val failedVal = failedBySource[sourceName] ?: 0
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = sourceName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Fetched: $fetchedVal | Failed: $failedVal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showReportDialog = false }) {
                            Text("Close")
                        }
                    },
                )
            }
        },
    )
}
