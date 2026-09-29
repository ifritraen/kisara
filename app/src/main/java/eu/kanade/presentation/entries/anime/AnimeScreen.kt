package eu.kanade.presentation.entries.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.entries.components.EntryPosterBackground
import eu.kanade.presentation.manga.components.TrackerDetailsCard
import eu.kanade.presentation.util.formatChapterNumber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import aniyomi.domain.anime.SeasonAnime
import aniyomi.domain.anime.SeasonDisplayMode
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.anime.model.episodesFiltered
import eu.kanade.domain.entries.anime.model.seasonsFiltered
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.isAuroraStyle
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.anime.components.AnimeActionRow
import eu.kanade.presentation.entries.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.entries.anime.components.AnimeInfoBox
import eu.kanade.presentation.entries.anime.components.AnimeSeasonListItem
import eu.kanade.presentation.entries.anime.components.AnimeSeasonSwitcher
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.ExpandableAnimeDescription
import eu.kanade.presentation.entries.anime.components.NextEpisodeAiringListItem
import eu.kanade.presentation.entries.anime.components.isLikelyEpisodeDescription
import eu.kanade.presentation.entries.anime.components.resolveAnimeSeasonSwitcherItems
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.anime.components.ItemHeader
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.MissingChapterCountListItem as MissingItemCountListItem
// import removed: resolveExternalMetadataCover not needed
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.missingEntriesCount
import tachiyomi.domain.library.service.LibraryPreferences
import eu.kanade.domain.ui.UiPreferences.MetadataSource
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Parses the original title from the description field.
 * Looks for patterns like "Original: Title" or "Оригинал: Title".
 */
private fun parseOriginalTitle(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val patterns = listOf(
        Regex("""Original:\s*([^\n]+)""", RegexOption.IGNORE_CASE),
        Regex("""Оригинал:\s*([^\n]+)""", RegexOption.IGNORE_CASE),
    )

    for (pattern in patterns) {
        val match = pattern.find(description)
        if (match != null) {
            return match.groupValues[1].trim()
        }
    }

    return null
}

@Composable
fun AnimeScreen(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (episode: Episode, alt: Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onDuplicateClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,

    // Dubbing selection
    onDubbingClicked: (() -> Unit)? = null,
    selectedDubbing: String? = null,
    onDownloadLongClick: ((Episode) -> Unit)? = null,

    // Metadata retry (Anilist/Shikimori)
    onRetryMetadata: () -> Unit,

    onClickEditInfo: (() -> Unit)? = null,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()
    val metadataSource by uiPreferences.metadataSource().collectAsState()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextAnime().collectAsState()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            KMR.strings.action_disable_auto_jump_next_episode
        } else {
            KMR.strings.action_enable_auto_jump_next_episode
        },
    )
    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextAnime().set(!autoJumpToNextEnabled)
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(AnimeSourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableAnimeSource }

    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    if (!isTabletUi) {
        AnimeScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onDuplicateClicked = onDuplicateClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onDubbingClicked = onDubbingClicked,
            selectedDubbing = selectedDubbing,
            isAutoJumpToNextEnabled = autoJumpToNextEnabled,
            autoJumpToNextLabel = autoJumpToNextLabel,
            onToggleAutoJumpToNext = onToggleAutoJumpToNext,
            onClickEditInfo = onClickEditInfo,
        )
    } else {
        AnimeScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onDuplicateClicked = onDuplicateClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            onDubbingClicked = onDubbingClicked,
            selectedDubbing = selectedDubbing,
            isAutoJumpToNextEnabled = autoJumpToNextEnabled,
            autoJumpToNextLabel = autoJumpToNextLabel,
            onToggleAutoJumpToNext = onToggleAutoJumpToNext,
            onClickEditInfo = onClickEditInfo,
            onSuggestionClick = onSuggestionClick,
            onRetrySuggestions = onRetrySuggestions,
            onOpenSuggestions = onOpenSuggestions,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeScreenSmallImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onDuplicateClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,
    onClickEditInfo: (() -> Unit)?,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,

    // Dubbing selection
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onSaveScrollPosition: (Int, Int) -> Unit = { _, _ -> },
) {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val metadataSource by uiPreferences.metadataSource().collectAsState()
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val originalTitle = remember(state.anime.displayDescription) {
        parseOriginalTitle(state.anime.displayDescription)
    }
    val displayTitle = if (showOriginalTitle && originalTitle != null) {
        "${state.anime.displayTitle} ($originalTitle)"
    } else {
        state.anime.displayTitle
    }

    val resolvedCoverUrl = remember(
        state.anime,
        state.isMetadataLoading,
        metadataSource,
    ) {
        resolveCoverUrl(state, metadataSource)
    }
    val refererUrl = remember(state.source) {
        (state.source as? HttpSource)?.baseUrl
    }

    val density = LocalDensity.current
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val contentListState = rememberLazyListState()
    val episodeListState = rememberLazyListState()

    // Save scroll position when it changes
    LaunchedEffect(contentListState.firstVisibleItemIndex, contentListState.firstVisibleItemScrollOffset) {
        onSaveScrollPosition(
            contentListState.firstVisibleItemIndex,
            contentListState.firstVisibleItemScrollOffset,
        )
    }

    // Restore saved scroll position or auto-scroll to target episode
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.scrollIndex, state.targetEpisodeIndex) {
        if (!hasScrolledToTarget) {
            hasScrolledToTarget = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetEpisodeIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                episodeListState.animateScrollToItem(targetIndex)
            }
        }
    }

    val seasons = remember(state) { state.processedSeasons }
    val seasonSwitcherItems = remember(seasons) {
        resolveAnimeSeasonSwitcherItems(
            currentAnimeId = state.anime.id,
            seasons = seasons.map { it.seasonAnime },
        )
    }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }
    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered(state.downloadedOnly)
            FetchType.Episodes -> state.anime.episodesFiltered(state.downloadedOnly)
        }
    }

    var toolbarHeight by remember { mutableIntStateOf(0) }
    var isSheetExpanded by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val nextUnseenEpisode = remember(episodes, state.anime, state.downloadedOnly) {
        episodes.getNextUnseen(state.anime, state.downloadedOnly)?.let { ep ->
            episodes.firstOrNull { it.episode.id == ep.id }
        }
    }
    val nextUnseenIndex = remember(listItem, nextUnseenEpisode) {
        if (nextUnseenEpisode != null) {
            listItem.indexOfFirst {
                it is EpisodeList.Item && it.episode.id == nextUnseenEpisode.episode.id
            }
        } else {
            -1
        }
    }
    var hasScrolledToUnseen by remember(state.anime.id) { mutableStateOf(false) }
    LaunchedEffect(state.anime.id, nextUnseenIndex) {
        if (!hasScrolledToUnseen && nextUnseenIndex >= 0) {
            episodeListState.scrollToItem(nextUnseenIndex)
            hasScrolledToUnseen = true
        }
    }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else if (isSheetExpanded) {
            isSheetExpanded = false
        } else {
            navigateUp()
        }
    })

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = maxHeight
        val minHeightDp by uiPreferences.chapterSheetMinHeightDp().collectAsState()
        val maxHeightPct by uiPreferences.chapterSheetMaxHeightPct().collectAsState()
        val targetMaxHeight = maxHeight * (maxHeightPct / 100f)
        val estimatedContentHeight = 64.dp + (episodes.size * 56).dp
        val calculatedExpandedHeight = if (episodes.isEmpty()) minHeightDp.dp else minOf(targetMaxHeight, maxOf(minHeightDp.dp, estimatedContentHeight))

        val sheetHeight by animateDpAsState(
            targetValue = if (isSheetExpanded) calculatedExpandedHeight else minHeightDp.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "Episodes Sheet Height",
        )

        Scaffold(
            topBar = {
                val selectedEpisodeCount: Int = remember(episodes) {
                    episodes.count { it.selected }
                }
                val isFirstItemVisible by remember {
                    derivedStateOf { contentListState.firstVisibleItemIndex == 0 }
                }
                val isFirstItemScrolled by remember {
                    derivedStateOf { contentListState.firstVisibleItemScrollOffset > 0 }
                }
                val titleAlpha by animateFloatAsState(
                    if (!isFirstItemVisible) 1f else 0f,
                    label = "Top Bar Title",
                )
                val backgroundAlpha by animateFloatAsState(
                    if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                    label = "Top Bar Background",
                )
                EntryToolbar(
                    title = displayTitle,
                    hasFilters = hasFilters,
                    navigateUp = navigateUp,
                    onClickFilter = onFilterClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = onSettingsClicked,
                    onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                    autoJumpToNextLabel = autoJumpToNextLabel,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedEpisodeCount,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                    isManga = false,
                    onClickEditInfo = onClickEditInfo,
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height },
                )
            },
            bottomBar = {
                val selectedEpisodes = remember(episodes) {
                    episodes.filter { it.selected }
                }
                SharedAnimeBottomActionMenu(
                    selected = selectedEpisodes,
                    onEpisodeClicked = onEpisodeClicked,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiFillermarkClicked = onMultiFillermarkClicked,
                    onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                    onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                    onDownloadEpisode = onDownloadEpisode,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 1f,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()

            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(top = topPadding),
            ) {
                val layoutDirection = LocalLayoutDirection.current

                Box(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen dynamic blurred poster backdrop
                    EntryPosterBackground(
                        coverData = state.anime.thumbnailUrl ?: state.anime,
                        scrollOffset = contentListState.firstVisibleItemScrollOffset,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Main Content Details
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                        state = contentListState,
                        contentPadding = PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            bottom = contentPadding.calculateBottomPadding() + minHeightDp.dp + 16.dp,
                        ),
                    ) {
                        item(key = EntryScreenItem.INFO_BOX) {
                            AnimeInfoBox(
                                isTabletUi = false,
                                appBarPadding = topPadding,
                                anime = state.anime,
                                sourceName = remember { state.source.getNameForAnimeInfo() },
                                isStubSource = remember { state.source is StubAnimeSource },
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                                resolvedCoverUrl = resolvedCoverUrl,
                                resolvedCoverUrlFallback = null,
                                refererUrl = refererUrl,
                            )
                        }

                        item(key = EntryScreenItem.ACTION_ROW) {
                            AnimeActionRow(
                                favorite = state.anime.favorite,
                                trackingCount = state.trackingCount,
                                nextUpdate = nextUpdate,
                                isUserIntervalMode = state.anime.fetchInterval < 0,
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onWebViewLongClicked = onWebViewLongClicked,
                                onTrackingClicked = onTrackingClicked,
                                onEditIntervalClicked = onEditIntervalClicked,
                                onEditCategory = onEditCategoryClicked,
                                onDubbingClicked = onDubbingClicked,
                                selectedDubbing = selectedDubbing,
                                duplicateCount = state.duplicateCount,
                                onDuplicateClicked = onDuplicateClicked,
                            )
                        }

                        if (state.trackerDetails != null) {
                            item(key = "anime_tracker_details") {
                                TrackerDetailsCard(trackDetails = state.trackerDetails)
                            }
                        }

                        item(key = EntryScreenItem.DESCRIPTION_WITH_TAG) {
                            ExpandableAnimeDescription(
                                defaultExpandState = state.isFromSource,
                                description = state.anime.displayDescription,
                                tagsProvider = { state.anime.displayGenre },
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                            )
                        }

                        if (seasonSwitcherItems.size > 1) {
                            item(key = "season_switcher") {
                                AnimeSeasonSwitcher(
                                    items = seasonSwitcherItems,
                                    onSeasonClicked = onSeasonClicked,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
                                )
                            }
                        }

                        if (state.airingTime > 0L) {
                            item(key = EntryScreenItem.AIRING_TIME) {
                                NextEpisodeAiringListItem(
                                    title = stringResource(
                                        KMR.strings.display_mode_episode,
                                        formatEpisodeNumber(state.airingEpisodeNumber),
                                    ),
                                    date = formatTime(state.airingTime, useDayFormat = true),
                                )
                            }
                        }
                    }

                    // Floating frosted glass Episode Sheet
                    val nestedScrollConnection = remember(isSheetExpanded) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (isSheetExpanded && available.y > 0f) {
                                    if (episodeListState.firstVisibleItemIndex == 0 && episodeListState.firstVisibleItemScrollOffset == 0) {
                                        isSheetExpanded = false
                                        return available
                                    }
                                }
                                return Offset.Zero
                            }
                        }
                    }

                    GlassSurface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp)
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .nestedScroll(nestedScrollConnection)
                            .pointerInput(isSheetExpanded) {
                                if (!isSheetExpanded) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount < -10f) {
                                            isSheetExpanded = true
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        style = GlassDefaults.regularStyle(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSheetExpanded = !isSheetExpanded }
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            if (dragAmount > 10f) {
                                                isSheetExpanded = false
                                            } else if (dragAmount < -10f) {
                                                isSheetExpanded = true
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    val dragIcon = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                                    Icon(
                                        imageVector = dragIcon,
                                        contentDescription = "Expand/Collapse",
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "${episodes.size} Episodes",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (nextUnseenEpisode != null) {
                                            val ep = nextUnseenEpisode.episode
                                            val epTitle = if (state.anime.displayMode == 0L) {
                                                ep.name
                                            } else {
                                                "Episode ${formatChapterNumber(ep.episodeNumber)}"
                                            }
                                            val progressText = if (ep.lastSecondSeen > 0 && ep.totalSeconds > 0) {
                                                val curMin = ep.lastSecondSeen / 60
                                                val curSec = ep.lastSecondSeen % 60
                                                val totMin = ep.totalSeconds / 60
                                                val totSec = ep.totalSeconds % 60
                                                " • %d:%02d / %d:%02d".format(curMin, curSec, totMin, totSec)
                                            } else {
                                                ""
                                            }
                                            Text(
                                                text = "$epTitle$progressText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    IconButton(
                                        onClick = onFilterClicked,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    // Resume Button
                                    val isWatching = remember(episodes) {
                                        episodes.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0 }
                                    }
                                    IconButton(
                                        onClick = {
                                            nextUnseenEpisode?.let { ep ->
                                                onEpisodeClicked(ep.episode, alwaysUseExternalPlayer)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = stringResource(
                                                if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                                            ),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            LazyColumn(
                                state = episodeListState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = listItem,
                                    key = { item ->
                                        when (item) {
                                            is EpisodeList.MissingCount -> "missing-${item.id}"
                                            is EpisodeList.Item -> "episode-${item.episode.id}"
                                        }
                                    },
                                ) { item ->
                                    when (item) {
                                        is EpisodeList.MissingCount -> {
                                            MissingItemCountListItem(
                                                count = item.count,
                                            )
                                        }
                                        is EpisodeList.Item -> {
                                            AnimeEpisodeListItem(
                                                title = if (state.anime.displayMode == 0L) item.episode.name else "Episode ${formatChapterNumber(item.episode.episodeNumber)}",
                                                date = item.episode.dateUpload.takeIf { it > 0 }?.let { relativeDateTimeText(it) },
                                                watchProgress = null,
                                                scanlator = item.episode.scanlator,
                                                summary = item.episode.summary,
                                                previewUrl = item.episode.previewUrl,
                                                seen = item.episode.seen,
                                                bookmark = item.episode.bookmark,
                                                fillermark = item.episode.fillermark,
                                                selected = item.selected,
                                                isAnyEpisodeSelected = isAnySelected,
                                                downloadIndicatorEnabled = onDownloadEpisode != null,
                                                downloadStateProvider = { item.downloadState },
                                                downloadProgressProvider = { item.downloadProgress },
                                                episodeSwipeStartAction = episodeSwipeStartAction,
                                                episodeSwipeEndAction = episodeSwipeEndAction,
                                                onClick = { onEpisodeClicked(item.episode, false) },
                                                onLongClick = { onEpisodeSelected(item, !item.selected, true, true) },
                                                onDownloadClick = { action -> onDownloadEpisode?.invoke(listOf(item), action) },
                                                onEpisodeSwipe = { action -> onEpisodeSwipe(item, action) },
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

internal fun resolveAnimeClassicFastScrollBlockStartIndex(
    fetchType: FetchType,
    hasAiringTimeItem: Boolean,
): Int {
    val baseIndex = 4
    return if (fetchType == FetchType.Episodes && hasAiringTimeItem) baseIndex + 1 else baseIndex
}

private val ANIME_CLASSIC_FAST_SCROLL_ITEM_TOP_INSET = 5.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreenLargeImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onDuplicateClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,
    onClickEditInfo: (() -> Unit)?,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For swipe actions
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,

    // Dubbing selection
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,

    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onRetrySuggestions: () -> Unit,
    onOpenSuggestions: () -> Unit,
) {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val entrySuggestionsEnabled by sourcePreferences.entrySuggestionsEnabled().collectAsState()
    val entrySuggestionsExpandInline by uiPreferences.entrySuggestionsExpandInline().collectAsState()
    val entrySuggestionsInOverflow by uiPreferences.entrySuggestionsInOverflow().collectAsState()
    val metadataSource by uiPreferences.metadataSource().collectAsState()
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val originalTitle = remember(state.anime.displayDescription) {
        parseOriginalTitle(state.anime.displayDescription)
    }
    val displayTitle = if (showOriginalTitle && originalTitle != null) {
        "${state.anime.displayTitle} ($originalTitle)"
    } else {
        state.anime.displayTitle
    }

    val resolvedCoverUrl = remember(
        state.anime,
        state.isMetadataLoading,
        metadataSource,
    ) {
        resolveCoverUrl(state, metadataSource)
    }
    val refererUrl = remember(state.source) {
        (state.source as? HttpSource)?.baseUrl
    }

    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val seasons = remember(state) { state.processedSeasons }
    val seasonSwitcherItems = remember(seasons) {
        resolveAnimeSeasonSwitcherItems(
            currentAnimeId = state.anime.id,
            seasons = seasons.map { it.seasonAnime },
        )
    }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val itemListState = rememberLazyGridState()

    // Auto-scroll to target episode on initial load (large impl)
    var hasScrolledToTargetLarge: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetEpisodeIndex) {
        if (!hasScrolledToTargetLarge) {
            hasScrolledToTargetLarge = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetEpisodeIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                itemListState.animateScrollToItem(targetIndex)
            }
        }
    }

    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered(state.downloadedOnly)
            FetchType.Episodes -> state.anime.episodesFiltered(state.downloadedOnly)
        }
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        Scaffold(
            topBar = {
                val selectedChapterCount = remember(episodes) {
                    episodes.count { it.selected }
                }
                EntryToolbar(
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    title = displayTitle,
                    hasFilters = hasFilters,
                    navigateUp = navigateUp,
                    onClickFilter = onFilterButtonClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = onSettingsClicked,
                    onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                    autoJumpToNextLabel = autoJumpToNextLabel,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedChapterCount,
                    onCancelActionMode = { onAllEpisodeSelected(false) },
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { 1f },
                    backgroundAlphaProvider = { 1f },
                    isManga = false,
                    onClickEditInfo = onClickEditInfo,
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    val selectedEpisodes = remember(episodes) {
                        episodes.filter { it.selected }
                    }
                    SharedAnimeBottomActionMenu(
                        selected = selectedEpisodes,
                        onEpisodeClicked = onEpisodeClicked,
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        onMultiFillermarkClicked = onMultiFillermarkClicked,
                        onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                        onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                        onDownloadEpisode = onDownloadEpisode,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
                        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(
                    start = insetPadding.calculateStartPadding(layoutDirection),
                    top = with(density) { topBarHeight.toDp() },
                    end = insetPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                TwoPanelBox(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
                    startContent = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            AnimeInfoBox(
                                isTabletUi = true,
                                appBarPadding = contentPadding.calculateTopPadding(),
                                anime = state.anime,
                                sourceName = remember { state.source.getNameForAnimeInfo() },
                                isStubSource = remember { state.source is StubAnimeSource },
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                                resolvedCoverUrl = resolvedCoverUrl,
                                resolvedCoverUrlFallback = null,
                                refererUrl = refererUrl,
                            )
                            AnimeActionRow(
                                favorite = state.anime.favorite,
                                trackingCount = state.trackingCount,
                                nextUpdate = nextUpdate,
                                isUserIntervalMode = state.anime.fetchInterval < 0,
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onWebViewLongClicked = onWebViewLongClicked,
                                onTrackingClicked = onTrackingClicked,
                                onEditIntervalClicked = onEditIntervalClicked,
                                onEditCategory = onEditCategoryClicked,
                                onDubbingClicked = onDubbingClicked,
                                selectedDubbing = selectedDubbing,
                                duplicateCount = state.duplicateCount,
                                onDuplicateClicked = onDuplicateClicked,
                            )
                            if (state.trackerDetails != null) {
                                TrackerDetailsCard(trackDetails = state.trackerDetails)
                            }
                            ExpandableAnimeDescription(
                                defaultExpandState = true,
                                description = state.anime.displayDescription,
                                tagsProvider = { state.anime.displayGenre },
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                            )
                            if (entrySuggestionsEnabled) {
                                if (entrySuggestionsExpandInline) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow(
                                        state = state.suggestions,
                                        onSuggestionClick = onSuggestionClick,
                                        onOpenSuggestions = onOpenSuggestions,
                                        onRetryClick = onRetrySuggestions,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else if (!entrySuggestionsInOverflow) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                    ),
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clickable(onClick = onOpenSuggestions)
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(KMR.strings.suggestions_similar_titles),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    endContent = {
                        FastScrollLazyVerticalGrid(
                            modifier = Modifier.fillMaxHeight(),
                            state = itemListState,
                            columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                            contentPadding = PaddingValues(
                                start = GRID_PADDING,
                                end = GRID_PADDING,
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = EntryScreenItem.CHAPTER_HEADER,
                                contentType = EntryScreenItem.CHAPTER_HEADER,
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                val missingEpisodesCount = remember(episodes) {
                                    episodes.map { it.episode.episodeNumber }.missingEntriesCount()
                                }
                                val missingSeasonsCount = remember(seasons) {
                                    seasons.map { it.seasonAnime.anime.seasonNumber }.missingEntriesCount()
                                }
                                ItemHeader(
                                    enabled = !isAnySelected,
                                    itemCount = when (state.anime.fetchType) {
                                        FetchType.Seasons -> seasons.size
                                        FetchType.Episodes -> episodes.size
                                    },
                                    missingItemsCount = maxOf(missingEpisodesCount, missingSeasonsCount),
                                    onClick = onFilterButtonClicked,
                                    isManga = false,
                                    fetchType = state.anime.fetchType,
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                            }

                            when (state.anime.fetchType) {
                                FetchType.Seasons -> {
                                    if (seasonSwitcherItems.size > 1) {
                                        item(
                                            key = "season_switcher",
                                            contentType = "season_switcher",
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            AnimeSeasonSwitcher(
                                                items = seasonSwitcherItems,
                                                onSeasonClicked = onSeasonClicked,
                                                modifier = Modifier
                                                    .ignorePadding(offsetGridPaddingPx)
                                                    .padding(
                                                        horizontal = MaterialTheme.padding.medium,
                                                        vertical = 4.dp,
                                                    ),
                                            )
                                        }
                                    }
                                    sharedSeasons(
                                        anime = state.anime,
                                        seasons = seasons,
                                        containerHeight = containerHeightPx - topBarHeight,
                                        onSeasonClicked = onSeasonClicked,
                                        onClickContinueWatching = onClickContinueWatching,
                                        listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                }

                                FetchType.Episodes -> {
                                    if (state.airingTime > 0L) {
                                        item(
                                            key = EntryScreenItem.AIRING_TIME,
                                            contentType = EntryScreenItem.AIRING_TIME,
                                        ) {
                                            // Handles the second by second countdown reseting
                                            var timer by remember { mutableLongStateOf(state.airingTime) }
                                            LaunchedEffect(key1 = timer) {
                                                if (timer > 0L) {
                                                    delay(1000L)
                                                    timer -= 1000L
                                                }
                                            }
                                            if (timer > 0L &&
                                                showNextEpisodeAirTime &&
                                                state.anime.displayStatus.toInt() != SAnime.COMPLETED
                                            ) {
                                                NextEpisodeAiringListItem(
                                                    title = stringResource(
                                                        KMR.strings.display_mode_episode,
                                                        formatEpisodeNumber(state.airingEpisodeNumber),
                                                    ),
                                                    date = formatTime(state.airingTime, useDayFormat = true),
                                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                )
                                            }
                                        }
                                    }

                                    sharedEpisodeItems(
                                        anime = state.anime,
                                        episodes = listItem,
                                        isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                        showSummaries = state.showSummaries,
                                        showPreviews = state.showPreviews,
                                        episodeSwipeStartAction = episodeSwipeStartAction,
                                        episodeSwipeEndAction = episodeSwipeEndAction,
                                        onEpisodeClicked = onEpisodeClicked,
                                        onDownloadEpisode = onDownloadEpisode,
                                        onEpisodeSelected = onEpisodeSelected,
                                        onEpisodeSwipe = onEpisodeSwipe,
                                        itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedAnimeBottomActionMenu(
    selected: List<EpisodeList.Item>,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.bookmark } },
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.fillermark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.seen } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0L } },
        onMarkPreviousAsViewedClicked = {
            onMarkPreviousAsSeenClicked(selected[0].episode)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadEpisode!!(selected.toList(), EpisodeDownloadAction.START)
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState != AnimeDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.episode })
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState == AnimeDownload.State.DOWNLOADED }
        },
        onExternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
        isManga = false,
    )
}

private fun LazyGridScope.sharedSeasons(
    anime: Anime,
    seasons: List<AnimeSeasonItem>,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    listItemModifier: Modifier = Modifier,
) {
    items(
        items = seasons,
        key = { season -> season.seasonAnime.anime },
        span = { GridItemSpan(if (anime.seasonDisplayGridMode == SeasonDisplayMode.List) maxLineSpan else 1) },
    ) { item ->
        AnimeSeasonListItem(
            anime = anime,
            item = item,
            containerHeight = containerHeight,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onClickContinueWatching,
            listItemModifier = listItemModifier,
        )
    }
}

private fun LazyGridScope.sharedEpisodeItems(
    anime: Anime,
    episodes: List<EpisodeList>,
    isAnyEpisodeSelected: Boolean,
    showSummaries: Boolean,
    showPreviews: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    itemModifier: Modifier = Modifier,
) {
    items(
        items = episodes,
        key = { episodeItem ->
            when (episodeItem) {
                is EpisodeList.MissingCount -> "missing-count-${episodeItem.id}"
                is EpisodeList.Item -> "episode-${episodeItem.id}"
            }
        },
        contentType = { EntryScreenItem.CHAPTER },
        span = { GridItemSpan(maxLineSpan) },
    ) { episodeItem ->
        val haptic = LocalHapticFeedback.current

        when (episodeItem) {
            is EpisodeList.MissingCount -> {
                MissingItemCountListItem(
                    count = episodeItem.count,
                    modifier = itemModifier,
                )
            }
            is EpisodeList.Item -> {
                AnimeEpisodeListItem(
                    title = if (anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            KMR.strings.display_mode_episode,
                            formatEpisodeNumber(episodeItem.episode.episodeNumber),
                        )
                    } else {
                        episodeItem.episode.name
                    },
                    date = relativeDateTimeText(episodeItem.episode.dateUpload),
                    watchProgress = episodeItem.episode.lastSecondSeen
                        .takeIf { !episodeItem.episode.seen && it > 0L }
                        ?.let {
                            stringResource(
                                KMR.strings.episode_progress,
                                formatTime(it),
                                formatTime(episodeItem.episode.totalSeconds),
                            )
                        },
                    scanlator = episodeItem.episode.scanlator
                        .takeIf { !it.isNullOrBlank() && !it.isLikelyEpisodeDescription() },
                    summary = episodeItem.episode.summary.takeIf { !it.isNullOrBlank() && showSummaries }
                        ?: episodeItem.episode.scanlator
                            .takeIf { !it.isNullOrBlank() && showSummaries && it.isLikelyEpisodeDescription() },
                    previewUrl = episodeItem.episode.previewUrl.takeIf { !it.isNullOrBlank() && showPreviews },
                    seen = episodeItem.episode.seen,
                    bookmark = episodeItem.episode.bookmark,
                    fillermark = episodeItem.episode.fillermark,
                    selected = episodeItem.selected,
                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                    downloadIndicatorEnabled = !isAnyEpisodeSelected && !anime.isLocal(),
                    downloadStateProvider = { episodeItem.downloadState },
                    downloadProgressProvider = { episodeItem.downloadProgress },
                    episodeSwipeStartAction = episodeSwipeStartAction,
                    episodeSwipeEndAction = episodeSwipeEndAction,
                    onLongClick = {
                        onEpisodeSelected(episodeItem, !episodeItem.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onEpisodeItemClick(
                            episodeItem = episodeItem,
                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                            onToggleSelection = { onEpisodeSelected(episodeItem, !episodeItem.selected, true, false) },
                            onEpisodeClicked = onEpisodeClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadEpisode != null) {
                        { onDownloadEpisode(listOf(episodeItem), it) }
                    } else {
                        null
                    },
                    onEpisodeSwipe = {
                        onEpisodeSwipe(episodeItem, it)
                    },
                    modifier = itemModifier,
                )
            }
        }
    }
}

private fun onEpisodeItemClick(
    episodeItem: EpisodeList.Item,
    isAnyEpisodeSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
) {
    when {
        episodeItem.selected -> onToggleSelection(false)
        isAnyEpisodeSelected -> onToggleSelection(true)
        else -> onEpisodeClicked(episodeItem.episode, false)
    }
}

private fun resolveCoverUrl(
    state: AnimeScreenModel.State.Success,
    metadataSource: MetadataSource,
): String? {
    val baseCoverUrl = state.anime.thumbnailUrl.orEmpty()
    return baseCoverUrl.takeIf { it.isNotBlank() }
}

fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    return if (useDayFormat) {
        String.format(
            "Airing in %02dd %02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toDays(milliseconds),
            TimeUnit.MILLISECONDS.toHours(milliseconds) -
                TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds)),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else if (milliseconds > 3600000L) {
        String.format(
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}

private val GRID_PADDING = 14.dp
private fun Modifier.ignorePadding(gridPadding: Int) = layout { measurable, constraints ->
    val looseConstraints = constraints.offset(gridPadding * 2, 0)
    val placeable = measurable.measure(looseConstraints)

    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
