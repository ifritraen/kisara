package eu.kanade.tachiyomi.ui.suggestions

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.model.Suggestion
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

    val dynamicSuggestions = remember(state.suggestions, tagWeightsMap, sourceWeightsMap) {
        state.suggestions.map { suggestion ->
            val manga = suggestion.manga
            val extWeight = sourceWeightsMap[manga.source] ?: 0.1
            var tagSum = 0.0
            manga.genre.orEmpty().forEach { genre ->
                val clean = SuggestionsWorker.cleanAndFilterGenre(genre)
                if (clean != null) {
                    tagSum += tagWeightsMap[clean] ?: 0.0
                }
            }
            val score = extWeight * tagSum
            suggestion.copy(relevance = score)
        }
            .filter { it.relevance > 0.0 }
            .sortedByDescending { it.relevance }
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
                )
            }

            PullRefresh(
                refreshing = isRefreshing,
                enabled = true,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        screenModel.triggerRefresh(context)
                        isRefreshing = false
                    }
                },
            ) {
                if (state.isLoading) {
                    LoadingScreen(modifier = Modifier.padding(paddingValues))
                } else if (dynamicSuggestions.isEmpty()) {
                    EmptyScreen(
                        modifier = Modifier.padding(paddingValues),
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
                            top = paddingValues.calculateTopPadding() + 8.dp,
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
                                            IconButton(
                                                onClick = { screenModel.dismissSuggestion(manga.url, manga.title) },
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                        shape = CircleShape,
                                                    ),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = "Dismiss",
                                                    modifier = Modifier.size(16.dp),
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
                                            SourceIconBadge(source = source)
                                        }

                                        IconButton(
                                            onClick = { explainingSuggestion = suggestion },
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                    shape = CircleShape,
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = "Explain Score",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        if (state.isLoadingNext) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                BrowseSourceLoadingItem()
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
        },
    )
}
