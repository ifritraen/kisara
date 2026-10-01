package eu.kanade.presentation.entries.novel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import eu.kanade.presentation.manga.ChapterSheetExpansion
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import eu.kanade.presentation.entries.components.ItemCover
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import eu.kanade.domain.description.DescriptionEngine
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.isAuroraStyle
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.DescriptionBlocks
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.TrackerDetailsCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.PillActionButton
import eu.kanade.presentation.entries.components.EntryPosterBackground
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.novel.components.NovelChapterActionButton
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.novel.buildNovelCoverImageRequest
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.coil.staticBlur
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionIconState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterActionUiState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterDisplayRow
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.OmniBuilderScreen
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelChapterDisplayData
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelChapterRowIndex
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelVisibleChapterRows
import eu.kanade.tachiyomi.ui.entries.novel.resolveNovelVolumeChapterDisplayData
import eu.kanade.tachiyomi.ui.entries.novel.shouldGroupNovelChaptersByVolume
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel

internal const val NOVEL_CHAPTERS_PAGE_SIZE = 120

@Composable
fun NovelScreen(
    state: NovelScreenModel.State.Success,
    isFromSource: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartReading: (() -> Unit)?,
    isReading: Boolean,
    onToggleFavorite: () -> Unit,
    onEditCategoryClicked: (() -> Unit)? = null,
    onEditNotesClicked: (() -> Unit)? = null,
    onClickEditInfo: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onSuggestionClick: (Any) -> Unit = {},
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,
    onPosterLongClicked: (() -> Unit)? = null,
    onToggleAllChaptersRead: () -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onSourceSettings: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onDuplicateClicked: (() -> Unit)? = null,
    onOpenBatchDownloadDialog: (() -> Unit)?,
    onOpenTranslatedDownloadDialog: (() -> Unit)?,
    onOpenEpubExportDialog: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterTranslateClick: (Long) -> Unit,
    onChapterTranslateLongClick: (Long) -> Unit,
    onChapterTranslatedDownloadClick: (Long) -> Unit,
    onChapterTranslatedDownloadLongClick: (Long) -> Unit,
    onChapterTranslatedDownloadOpenFolder: (Long) -> Unit,
    onChapterReadToggle: (Long) -> Unit,
    onChapterBookmarkToggle: (Long) -> Unit,
    onChapterDownloadToggle: (Long) -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (Long, LibraryPreferences.NovelSwipeAction) -> Unit,
    onFilterButtonClicked: () -> Unit,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    chapterPageEnabled: Boolean,
    chapterPageCurrent: Int,
    chapterPageTotal: Int,
    chapterPageLoading: Boolean,
    onChapterPageChange: (Int) -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
    onMultiDownloadClicked: () -> Unit,
    onMultiDeleteClicked: () -> Unit,
    onSaveScrollPosition: (Int, Int) -> Unit = { _, _ -> },
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onMakeBookClicked: (() -> Unit)? = null,
    onAppendBookClicked: (() -> Unit)? = null,
    onDeleteBookSourceChaptersClicked: (() -> Unit)? = null,
    onDeleteBookClicked: (() -> Unit)? = null,
    onToggleReadAsBook: ((Boolean) -> Unit)? = null,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val entrySuggestionsEnabled by sourcePreferences.entrySuggestionsEnabled().collectAsState()
    val entrySuggestionsExpandInline by uiPreferences.entrySuggestionsExpandInline().collectAsState()
    val entrySuggestionsInOverflow by uiPreferences.entrySuggestionsInOverflow().collectAsState()
    val theme by uiPreferences.appTheme().collectAsState()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextNovel().collectAsState()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            KMR.strings.action_disable_auto_jump_next_chapter
        } else {
            KMR.strings.action_enable_auto_jump_next_chapter
        },
    )

    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextNovel().set(!autoJumpToNextEnabled)
    }

    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current

    // Standard implementation
    val chapters = state.processedChapters
    val groupedByChapter = false
    val groupedByVolume = remember(chapters, selectedScanlator) { shouldGroupNovelChaptersByVolume(chapters) }
    val chapterGroups = remember(chapters, groupedByChapter, selectedScanlator) {
        if (groupedByChapter) {
            resolveNovelChapterDisplayData(
                chapters = chapters,
                groupedByChapter = true,
                expandedGroupKeys = emptySet(),
            ).chapterGroups
        } else {
            emptyList()
        }
    }
    val volumeGroups = remember(chapters, groupedByVolume, selectedScanlator) {
        if (groupedByVolume) {
            resolveNovelVolumeChapterDisplayData(
                chapters = chapters,
                expandedVolumeKeys = emptySet(),
            ).volumeGroups
        } else {
            emptyList()
        }
    }
    val allGroupKeys = remember(chapterGroups, volumeGroups, groupedByVolume) {
        if (groupedByVolume) {
            volumeGroups.mapTo(mutableSetOf()) { it.groupKey }
        } else {
            chapterGroups.mapTo(mutableSetOf()) { it.groupKey }
        }
    }
    val initialExpandedGroupKeys =
        remember(chapters, selectedScanlator, state.targetChapterIndex, groupedByVolume) {
            when {
                groupedByVolume -> {
                    val targetChapterId = chapters.getOrNull(state.targetChapterIndex)?.id
                    volumeGroups.firstOrNull { group -> group.chapters.any { it.id == targetChapterId } }
                        ?.groupKey
                        ?.let(::setOf)
                        .orEmpty()
                }
                groupedByChapter -> {
                    chapters.getOrNull(state.targetChapterIndex)
                        ?.chapterNumber
                        ?.toBits()
                        ?.let(::setOf)
                        .orEmpty()
                }
                else -> emptySet()
            }
        }
    var expandedGroupKeys by remember(chapters, selectedScanlator, groupedByVolume) {
        mutableStateOf(initialExpandedGroupKeys)
    }
    val chapterDisplayData =
        remember(chapters, selectedScanlator, expandedGroupKeys, groupedByChapter, groupedByVolume) {
            if (groupedByVolume) {
                resolveNovelVolumeChapterDisplayData(
                    chapters = chapters,
                    expandedVolumeKeys = expandedGroupKeys,
                )
            } else {
                resolveNovelChapterDisplayData(
                    chapters = chapters,
                    groupedByChapter = groupedByChapter,
                    expandedGroupKeys = expandedGroupKeys,
                )
            }
        }
    val displayRows = chapterDisplayData.displayRows
    val totalChapterCount = if (state.chapterPageEnabled) {
        maxOf(state.chapters.size, state.chapterPageEstimatedTotal)
    } else if (groupedByVolume) {
        volumeGroups.size
    } else if (groupedByChapter) {
        chapterGroups.size
    } else {
        chapters.size
    }
    val selectedIds = state.selectedChapterIds
    val selectedCount = selectedIds.size
    val isAnySelected = selectedCount > 0
    val selectedChapters = chapters.filter { it.id in selectedIds }
    val downloadedChapterIds = state.downloadedChapterIds
    val visibleTopLevelCount = when {
        groupedByVolume -> volumeGroups.size
        groupedByChapter -> chapterGroups.size
        else -> chapters.size
    }
    var visibleChapterCount by remember(chapters, selectedScanlator, groupedByVolume) {
        mutableIntStateOf(
            initialVisibleChapterCount(
                totalCount = visibleTopLevelCount,
                pageSize = NOVEL_CHAPTERS_PAGE_SIZE,
            ),
        )
    }
    val visibleRows = remember(displayRows, visibleChapterCount, groupedByChapter, groupedByVolume) {
        resolveNovelVisibleChapterRows(
            rows = displayRows,
            visibleTopLevelCount = visibleChapterCount,
            groupedByChapter = groupedByChapter || groupedByVolume,
        )
    }
    val contentListState = rememberLazyListState()
    val chapterListState = rememberLazyListState()
    // KMK -->
    var sheetExpansion by remember { mutableStateOf(ChapterSheetExpansion.COLLAPSED) }

    BackHandler(onBack = {
        if (sheetExpansion == ChapterSheetExpansion.FULL) {
            sheetExpansion = ChapterSheetExpansion.PARTIAL
        } else if (sheetExpansion == ChapterSheetExpansion.PARTIAL) {
            sheetExpansion = ChapterSheetExpansion.COLLAPSED
        } else {
            onBack()
        }
    })
    // KMK <--
    val hazeState = remember { HazeState() }

    val nextUnreadChapter = remember(chapters, state.bookState) {
        eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter(chapters, null, state.bookState)
    }
    val nextUnreadIndex = remember(visibleRows, nextUnreadChapter) {
        if (nextUnreadChapter != null) {
            visibleRows.indexOfFirst {
                (it as? NovelChapterDisplayRow.BranchChapter)?.chapter?.id == nextUnreadChapter.id ||
                    (it as? NovelChapterDisplayRow.ChapterVariant)?.chapter?.id == nextUnreadChapter.id ||
                    (it as? NovelChapterDisplayRow.VolumeChapter)?.chapter?.id == nextUnreadChapter.id ||
                    (it as? NovelChapterDisplayRow.ChapterGroup)?.chapters?.any { ch -> ch.id == nextUnreadChapter.id } == true
            }
        } else {
            -1
        }
    }
    var hasScrolledToUnread by remember(state.novel.id) { mutableStateOf(false) }
    LaunchedEffect(state.novel.id, nextUnreadIndex) {
        if (!hasScrolledToUnread && nextUnreadIndex >= 0) {
            chapterListState.scrollToItem(nextUnreadIndex)
            hasScrolledToUnread = true
        }
    }

    // Save scroll position when it changes
    LaunchedEffect(contentListState.firstVisibleItemIndex, contentListState.firstVisibleItemScrollOffset) {
        onSaveScrollPosition(
            contentListState.firstVisibleItemIndex,
            contentListState.firstVisibleItemScrollOffset,
        )
    }

    // Restore saved scroll position or auto-scroll to target chapter
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.scrollIndex, state.targetChapterIndex, expandedGroupKeys, groupedByChapter, groupedByVolume) {
        if (!hasScrolledToTarget) {
            hasScrolledToTarget = true
            val targetChapterId = chapters.getOrNull(state.targetChapterIndex)?.id
            val targetIndex = targetChapterId?.let {
                resolveNovelChapterRowIndex(displayRows, it)
            }?.takeIf { it >= 0 }
                ?.let { rowIndex ->
                    resolveEntryAutoJumpTargetIndex(
                        enabled = autoJumpToNextEnabled,
                        targetIndex = rowIndex,
                        restoredScrollIndex = state.scrollIndex,
                    )
                }
            if (targetIndex != null) {
                chapterListState.animateScrollToItem(targetIndex)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = maxHeight
        val minHeightDp by uiPreferences.chapterSheetMinHeightDp().collectAsState()
        val maxHeightPct by uiPreferences.chapterSheetMaxHeightPct().collectAsState()
        val targetMaxHeight = maxHeight * (maxHeightPct / 100f)
        val density = LocalDensity.current
        var novelSheetHeaderHeightDp by remember { mutableStateOf(110.dp) }
        val singleNovelChapterHeight = if (visibleRows.isNotEmpty()) 72.dp else 0.dp
        val collapsedSheetHeight = remember(novelSheetHeaderHeightDp, visibleRows.size) {
            novelSheetHeaderHeightDp + singleNovelChapterHeight
        }
        val estimatedContentHeight = novelSheetHeaderHeightDp + (visibleRows.size * 56).dp
        val calculatedExpandedHeight = if (visibleRows.isEmpty()) collapsedSheetHeight else minOf(targetMaxHeight, maxOf(collapsedSheetHeight, estimatedContentHeight))

        Scaffold(
            topBar = {
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
                val isBookBuilt = state.bookState != null
                val isBookBuilding = state.bookBuildProgress != null
                val appendableChapterCount = (state.chapters.size - (state.bookState?.chapterCount ?: 0)).coerceAtLeast(0)
                val readAsBook = state.bookState?.enabled == true
                val bookModeMenu = resolveNovelBookModeMenu(readAsBook)

                val bookTitle = stringResource(
                    when {
                        isBookBuilding -> KMR.strings.novel_book_building
                        isBookBuilt -> KMR.strings.novel_book_rebuild
                        else -> KMR.strings.novel_book_make
                    },
                )
                val appendTitle = stringResource(KMR.strings.novel_book_append_available, appendableChapterCount)
                val toggleTitle = stringResource(
                    if (bookModeMenu.current == NovelBookReadingMode.BOOK) {
                        KMR.strings.novel_book_read_as_book
                    } else {
                        KMR.strings.novel_book_read_as_chapters
                    },
                )
                val deleteTitle = stringResource(KMR.strings.novel_book_delete)
                val deleteSourceChaptersTitle = stringResource(KMR.strings.novel_book_cleanup_source_chapters)
                val outdatedTitle = stringResource(KMR.strings.novel_book_outdated)

                val overflowActions = remember(
                    bookTitle,
                    appendTitle,
                    toggleTitle,
                    deleteTitle,
                    deleteSourceChaptersTitle,
                    outdatedTitle,
                    isBookBuilt,
                    isBookBuilding,
                    appendableChapterCount,
                    readAsBook,
                    state.bookIsStale,
                    onMakeBookClicked,
                    onAppendBookClicked,
                    onToggleReadAsBook,
                    onDeleteBookClicked,
                    onDeleteBookSourceChaptersClicked,
                ) {
                    persistentListOf<AppBar.OverflowAction>().builder().apply {
                        if (onMakeBookClicked != null) {
                            add(
                                AppBar.OverflowAction(
                                    title = bookTitle,
                                    enabled = !isBookBuilding,
                                    onClick = { if (!isBookBuilding) onMakeBookClicked() },
                                ),
                            )
                        }
                        if (onAppendBookClicked != null && isBookBuilt && appendableChapterCount > 0) {
                            add(
                                AppBar.OverflowAction(
                                    title = appendTitle,
                                    enabled = !isBookBuilding,
                                    onClick = { if (!isBookBuilding) onAppendBookClicked() },
                                ),
                            )
                        }
                        if (isBookBuilt && state.bookIsStale) {
                            add(
                                AppBar.OverflowAction(
                                    title = outdatedTitle,
                                    enabled = false,
                                    onClick = {},
                                ),
                            )
                        }
                        if (onToggleReadAsBook != null && isBookBuilt) {
                            add(
                                AppBar.OverflowAction(
                                    title = toggleTitle,
                                    onClick = {
                                        onToggleReadAsBook(bookModeMenu.target == NovelBookReadingMode.BOOK)
                                    },
                                ),
                            )
                        }
                        if (onDeleteBookClicked != null && isBookBuilt) {
                            add(
                                AppBar.OverflowAction(
                                    title = deleteTitle,
                                    onClick = onDeleteBookClicked,
                                ),
                            )
                        }
                        if (onDeleteBookSourceChaptersClicked != null && isBookBuilt) {
                            add(
                                AppBar.OverflowAction(
                                    title = deleteSourceChaptersTitle,
                                    onClick = onDeleteBookSourceChaptersClicked,
                                ),
                            )
                        }
                    }.build()
                }

                EntryToolbar(
                    title = state.novel.displayTitle,
                    hasFilters = state.filterActive,
                    navigateUp = onBack,
                    onClickFilter = onFilterButtonClicked,
                    onClickShare = onShare,
                    onClickDownload = onOpenBatchDownloadDialog?.let { openDialog ->
                        { _ -> openDialog() }
                    },
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = null,
                    onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                    autoJumpToNextLabel = autoJumpToNextLabel,
                    actionModeCounter = selectedCount,
                    onCancelActionMode = { onAllChapterSelected(false) },
                    onSelectAll = { onAllChapterSelected(true) },
                    onInvertSelection = onInvertSelection,
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                    overflowActions = overflowActions,
                    isManga = false,
                    onClickEditInfo = onClickEditInfo,
                )
            },
            bottomBar = {
                EntryBottomActionMenu(
                    visible = selectedChapters.isNotEmpty(),
                    isManga = true,
                    onBookmarkClicked = {
                        onMultiBookmarkClicked(true)
                    }.takeIf { selectedChapters.any { !it.bookmark } },
                    onRemoveBookmarkClicked = {
                        onMultiBookmarkClicked(false)
                    }.takeIf { selectedChapters.isNotEmpty() && selectedChapters.all { it.bookmark } },
                    onMarkAsViewedClicked = {
                        onMultiMarkAsReadClicked(true)
                    }.takeIf { selectedChapters.any { !it.read } },
                    onMarkAsUnviewedClicked = {
                        onMultiMarkAsReadClicked(false)
                    }.takeIf { selectedChapters.any { it.read || it.lastPageRead > 0L } },
                    onMarkPreviousAsViewedClicked = {
                        onMarkPreviousAsReadClicked(selectedChapters.first())
                    }.takeIf { selectedChapters.size == 1 },
                    onDownloadClicked = {
                        onMultiDownloadClicked()
                    }.takeIf {
                        selectedChapters.any { chapter -> chapter.id !in downloadedChapterIds }
                    },
                    onTranslationBatchClicked = if (state.geminiEnabled) {
                        {
                            selectedChapters.firstOrNull()?.let { chapter ->
                                onChapterTranslateLongClick(chapter.id)
                            }
                        }
                    } else {
                        null
                    },
                    onDeleteClicked = {
                        onMultiDeleteClicked()
                    }.takeIf {
                        selectedChapters.any { chapter -> chapter.id in downloadedChapterIds }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Fullscreen dynamic blurred poster backdrop
                EntryPosterBackground(
                    coverData = state.novel.thumbnailUrl ?: state.novel,
                    scrollOffset = contentListState.firstVisibleItemScrollOffset,
                    modifier = Modifier.fillMaxSize(),
                )

                // Main Content Details
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .padding(paddingValues),
                    state = contentListState,
                    contentPadding = PaddingValues(
                        bottom = collapsedSheetHeight + 16.dp,
                    ),
                ) {
                    // 1. Hero Header Box
                    item(key = "novel_hero_box") {
                        val context = LocalContext.current
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            style = GlassDefaults.prominentStyle(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ItemCover.Book(
                                    data = buildNovelCoverImageRequest(context, state.novel) {
                                        crossfade(true)
                                    },
                                    modifier = Modifier
                                        .size(width = 104.dp, height = 148.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = { onPosterLongClicked?.invoke() }),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = state.novel.displayTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    state.novel.displayAuthor?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = state.source.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        novelStatusText(state.novel.displayStatus)?.let { statusText ->
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(6.dp),
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                        Text(
                                            text = pluralStringResource(
                                                MR.plurals.manga_num_chapters,
                                                totalChapterCount,
                                                totalChapterCount,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(6.dp),
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Action Buttons Box
                    item(key = "novel_actions_box") {
                        val favorite = state.novel.favorite
                        val defaultActionButtonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            style = GlassDefaults.prominentStyle(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NovelActionButton(
                                    title = stringResource(if (favorite) MR.strings.in_library else MR.strings.add_to_library),
                                    icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    color = if (favorite) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
                                    onClick = onToggleFavorite,
                                    onLongClick = onEditCategoryClicked,
                                )
                                NovelActionButton(
                                    title = if (trackingCount == 0) {
                                        stringResource(MR.strings.manga_tracking_tab)
                                    } else {
                                        pluralStringResource(MR.plurals.num_trackers, trackingCount, trackingCount)
                                    },
                                    icon = if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                                    color = if (trackingCount == 0) defaultActionButtonColor else MaterialTheme.colorScheme.primary,
                                    onClick = onTrackingClicked,
                                )
                                if (onOpenBatchDownloadDialog != null) {
                                    NovelActionButton(
                                        title = stringResource(MR.strings.manga_download),
                                        icon = Icons.Outlined.Download,
                                        color = defaultActionButtonColor,
                                        onClick = onOpenBatchDownloadDialog,
                                    )
                                }
                                if (onOpenEpubExportDialog != null) {
                                    NovelActionButton(
                                        title = stringResource(KMR.strings.novel_epub_short),
                                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                                        color = defaultActionButtonColor,
                                        onClick = onOpenEpubExportDialog,
                                    )
                                }
                                if (onWebView != null) {
                                    NovelActionButton(
                                        title = stringResource(MR.strings.action_web_view),
                                        icon = Icons.Outlined.Public,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = onWebView,
                                    )
                                }
                                if (state.duplicateCount > 0 && onDuplicateClicked != null) {
                                    NovelActionButton(
                                        title = stringResource(KMR.strings.label_duplicate),
                                        icon = Icons.Outlined.ContentCopy,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = onDuplicateClicked,
                                    )
                                }
                            }
                        }
                    }

                    // 3. Information & Metadata Box
                    item(key = "novel_info_box") {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            style = GlassDefaults.prominentStyle(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Information",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MetadataInfoRow(label = "Source", value = state.source.name)
                                    state.novel.displayAuthor?.takeIf { it.isNotBlank() }?.let {
                                        MetadataInfoRow(label = "Author", value = it)
                                    }
                                    novelStatusText(state.novel.displayStatus)?.let {
                                        MetadataInfoRow(label = "Status", value = it)
                                    }
                                    MetadataInfoRow(label = "Format", value = "Novel")
                                    MetadataInfoRow(
                                        label = "Chapters",
                                        value = "$totalChapterCount (${state.chapters.count { it.read }} read)",
                                    )
                                }
                            }
                        }
                    }

                    // 4. Tracker Info Box
                    if (state.trackerDetails != null) {
                        item(key = "novel_tracker_details") {
                            TrackerDetailsCard(trackDetails = state.trackerDetails)
                        }
                    } else if (trackingCount > 0) {
                        item(key = "novel_tracker_box") {
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(24.dp),
                                style = GlassDefaults.prominentStyle(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onTrackingClicked)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sync,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = pluralStringResource(MR.plurals.num_trackers, trackingCount, trackingCount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // 5. Description Box
                    state.novel.displayDescription?.takeIf { it.isNotBlank() }?.let { description ->
                        item(key = "novel_description_box") {
                            var isDescriptionExpanded by remember { mutableStateOf(false) }
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(24.dp),
                                style = GlassDefaults.prominentStyle(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Description",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    SelectionContainer {
                                        val blocks = remember(description) { DescriptionEngine.beautify(description) }
                                        Column(
                                            modifier = Modifier.animateContentSize(),
                                        ) {
                                            DescriptionBlocks(
                                                blocks = blocks,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = stringResource(if (isDescriptionExpanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Icon(
                                                    imageVector = if (isDescriptionExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Tags Box
                    val genres = state.novel.genre
                    if (!genres.isNullOrEmpty()) {
                        item(key = "novel_tags_box") {
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(24.dp),
                                style = GlassDefaults.prominentStyle(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sell,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Genres",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        genres.forEach { genre ->
                                            Text(
                                                text = genre,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                    .clickable { onGenreClick?.invoke(genre) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. Suggestions Box
                    if (entrySuggestionsEnabled) {
                        if (entrySuggestionsExpandInline) {
                            item(key = "suggestions_row") {
                                eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow(
                                    state = state.suggestions,
                                    onSuggestionClick = onSuggestionClick,
                                    onOpenSuggestions = onOpenSuggestions,
                                    onRetryClick = onRetrySuggestions,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else if (!entrySuggestionsInOverflow) {
                            item(key = "suggestions_button") {
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    style = GlassDefaults.prominentStyle(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onOpenSuggestions)
                                            .padding(vertical = 14.dp, horizontal = 16.dp),
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
                    }
                }

                // Floating frosted glass Chapter Sheet
                val nestedScrollConnection = remember(sheetExpansion) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y > 0f) {
                                if (chapterListState.firstVisibleItemIndex == 0 && chapterListState.firstVisibleItemScrollOffset == 0) {
                                    if (sheetExpansion == ChapterSheetExpansion.FULL) {
                                        sheetExpansion = ChapterSheetExpansion.PARTIAL
                                        return available
                                    } else if (sheetExpansion == ChapterSheetExpansion.PARTIAL) {
                                        sheetExpansion = ChapterSheetExpansion.COLLAPSED
                                        return available
                                    }
                                }
                            }
                            return Offset.Zero
                        }
                    }
                }

                val sheetBottomInset = paddingValues.calculateBottomPadding()
                val targetFullHeight = (maxHeight - paddingValues.calculateTopPadding()).coerceAtLeast(calculatedExpandedHeight + sheetBottomInset)

                val sheetHeight by animateDpAsState(
                    targetValue = when (sheetExpansion) {
                        ChapterSheetExpansion.COLLAPSED -> collapsedSheetHeight + sheetBottomInset
                        ChapterSheetExpansion.PARTIAL -> calculatedExpandedHeight + sheetBottomInset
                        ChapterSheetExpansion.FULL -> targetFullHeight
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "Chapters Sheet Height",
                )

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(sheetHeight)
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(sheetExpansion) {
                            if (sheetExpansion == ChapterSheetExpansion.COLLAPSED) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -10f) {
                                        sheetExpansion = ChapterSheetExpansion.PARTIAL
                                    }
                                }
                            }
                        },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    style = GlassDefaults.prominentStyle(),
                    isStandardSurface = true,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    novelSheetHeaderHeightDp = with(density) { size.height.toDp() }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sheetExpansion = when (sheetExpansion) {
                                            ChapterSheetExpansion.COLLAPSED -> ChapterSheetExpansion.PARTIAL
                                            ChapterSheetExpansion.PARTIAL -> ChapterSheetExpansion.FULL
                                            ChapterSheetExpansion.FULL -> ChapterSheetExpansion.COLLAPSED
                                        }
                                    }
                                    .pointerInput(sheetExpansion) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            if (dragAmount > 10f) {
                                                sheetExpansion = when (sheetExpansion) {
                                                    ChapterSheetExpansion.FULL -> ChapterSheetExpansion.PARTIAL
                                                    ChapterSheetExpansion.PARTIAL -> ChapterSheetExpansion.COLLAPSED
                                                    ChapterSheetExpansion.COLLAPSED -> ChapterSheetExpansion.COLLAPSED
                                                }
                                            } else if (dragAmount < -10f) {
                                                sheetExpansion = when (sheetExpansion) {
                                                    ChapterSheetExpansion.COLLAPSED -> ChapterSheetExpansion.PARTIAL
                                                    ChapterSheetExpansion.PARTIAL -> ChapterSheetExpansion.FULL
                                                    ChapterSheetExpansion.FULL -> ChapterSheetExpansion.FULL
                                                }
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
                                    val dragIcon = when (sheetExpansion) {
                                        ChapterSheetExpansion.FULL -> Icons.Default.KeyboardArrowDown
                                        ChapterSheetExpansion.PARTIAL -> Icons.Default.KeyboardArrowUp
                                        ChapterSheetExpansion.COLLAPSED -> Icons.Default.KeyboardArrowUp
                                    }
                                    Icon(
                                        imageVector = dragIcon,
                                        contentDescription = "Expand/Collapse",
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "$totalChapterCount Chapters",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (nextUnreadChapter != null) {
                                            val chTitle = if (state.novel.displayMode == 0L) {
                                                nextUnreadChapter.name
                                            } else {
                                                "Chapter ${formatChapterNumber(nextUnreadChapter.chapterNumber)}"
                                            }
                                            val progressText = if (nextUnreadChapter.lastPageRead > 0) {
                                                " • Page ${nextUnreadChapter.lastPageRead}"
                                            } else {
                                                ""
                                            }
                                            Text(
                                                text = "$chTitle$progressText",
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
                                        onClick = onFilterButtonClicked,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FilterList,
                                            contentDescription = "Filter",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    // Resume Button
                                    val isReading = remember(chapters) {
                                        chapters.any { it.read || it.lastPageRead > 0 }
                                    }
                                    FilledIconButton(
                                        onClick = {
                                            val target = nextUnreadChapter ?: chapters.lastOrNull() ?: chapters.firstOrNull()
                                            if (target != null) {
                                                onChapterClick(target.id)
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = stringResource(
                                                if (isReading) MR.strings.action_resume else MR.strings.action_start,
                                            ),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }

                            if (state.showScanlatorSelector) {
                                ScanlatorBranchSelector(
                                    scanlatorChapterCounts = scanlatorChapterCounts,
                                    selectedScanlator = selectedScanlator,
                                    onScanlatorSelected = onScanlatorSelected,
                                    showAllOption = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }

                            if (chapterPageEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    IconButton(
                                        onClick = { onChapterPageChange(chapterPageCurrent - 1) },
                                        enabled = !chapterPageLoading && chapterPageCurrent > 1,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronLeft,
                                            contentDescription = stringResource(MR.strings.spen_previous_page),
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "$chapterPageCurrent / $chapterPageTotal",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        if (chapterPageLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onChapterPageChange(chapterPageCurrent + 1) },
                                        enabled = !chapterPageLoading && chapterPageCurrent < chapterPageTotal,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = stringResource(MR.strings.spen_next_page),
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                        }

                        val targetMiniChapterIndex = remember(chapters, nextUnreadIndex) {
                            val lastRead = chapters.indexOfLast { it.chapter.read }
                            when {
                                lastRead >= 0 -> lastRead
                                nextUnreadIndex >= 0 -> nextUnreadIndex
                                else -> 0
                            }
                        }

                        LaunchedEffect(sheetExpansion, targetMiniChapterIndex) {
                            if (sheetExpansion == ChapterSheetExpansion.COLLAPSED && targetMiniChapterIndex in chapters.indices) {
                                chapterListState.scrollToItem(targetMiniChapterIndex)
                            }
                        }

                        LazyColumn(
                            state = chapterListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = if (sheetExpansion == ChapterSheetExpansion.COLLAPSED) 0.dp else (paddingValues.calculateBottomPadding() + 16.dp)),
                            userScrollEnabled = sheetExpansion != ChapterSheetExpansion.COLLAPSED,
                        ) {
                            items(
                                items = visibleRows,
                                key = { row ->
                                    when (row) {
                                        is NovelChapterDisplayRow.BranchChapter -> "branch-${row.chapter.id}"
                                        is NovelChapterDisplayRow.ChapterGroup -> "group-${row.groupKey}"
                                        is NovelChapterDisplayRow.ChapterVariant -> "variant-${row.chapter.id}"
                                        is NovelChapterDisplayRow.VolumeGroup -> "volume-${row.groupKey}"
                                        is NovelChapterDisplayRow.VolumeChapter -> "volume-chapter-${row.chapter.id}"
                                    }
                                },
                            ) { row ->
                                when (row) {
                                    is NovelChapterDisplayRow.BranchChapter -> {
                                        val chapter = row.chapter
                                        NovelClassicChapterRow(
                                            chapter = chapter,
                                            displayNumber = row.displayNumber,
                                            selected = chapter.id in selectedIds,
                                            chapterActionState = state.chapterActionStates[chapter.id],
                                            downloaded = chapter.id in state.downloadedChapterIds,
                                            downloading = chapter.id in state.downloadingChapterIds,
                                            selectionMode = isAnySelected,
                                            displayMode = state.novel.displayMode,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            onClick = { onChapterClick(chapter.id) },
                                            onLongClick = { onChapterLongClick(chapter.id) },
                                            onTranslateClick = { onChapterTranslateClick(chapter.id) },
                                            onTranslateLongClick = { onChapterTranslateLongClick(chapter.id) },
                                            onTranslatedDownloadClick = { onChapterTranslatedDownloadClick(chapter.id) },
                                            onTranslatedDownloadLongClick = { onChapterTranslatedDownloadLongClick(chapter.id) },
                                            onTranslatedDownloadOpenFolder = { onChapterTranslatedDownloadOpenFolder(chapter.id) },
                                            onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                            onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                            onToggleRead = { onChapterReadToggle(chapter.id) },
                                            chapterSwipeStartAction = chapterSwipeStartAction,
                                            chapterSwipeEndAction = chapterSwipeEndAction,
                                            onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                                        )
                                    }
                                    is NovelChapterDisplayRow.ChapterGroup -> {
                                        val primaryChapter = row.chapters.first()
                                        val isExpanded = row.groupKey in expandedGroupKeys
                                        NovelClassicChapterGroup(
                                            title = stringResource(
                                                MR.strings.display_mode_chapter,
                                                formatChapterNumber(row.displayNumber.toDouble()),
                                            ),
                                            count = row.chapters.size,
                                            expanded = isExpanded,
                                            singleItemGroup = row.chapters.size == 1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            onClick = {
                                                if (row.chapters.size == 1) {
                                                    onChapterClick(primaryChapter.id)
                                                } else {
                                                    expandedGroupKeys = if (isExpanded) {
                                                        expandedGroupKeys - row.groupKey
                                                    } else {
                                                        expandedGroupKeys + row.groupKey
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (row.chapters.size == 1) {
                                                    onChapterLongClick(primaryChapter.id)
                                                }
                                            },
                                        )
                                    }
                                    is NovelChapterDisplayRow.ChapterVariant -> {
                                        val chapter = row.chapter
                                        val chapterTitle = chapter.scanlator
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { scanlator ->
                                                val baseName = chapter.name.ifBlank {
                                                    stringResource(
                                                        MR.strings.display_mode_chapter,
                                                        formatChapterNumber(row.displayNumber.toDouble()),
                                                    )
                                                }
                                                "$scanlator · $baseName"
                                            }
                                        NovelClassicChapterRow(
                                            chapter = chapter,
                                            displayNumber = row.displayNumber,
                                            selected = chapter.id in selectedIds,
                                            chapterActionState = state.chapterActionStates[chapter.id],
                                            downloaded = chapter.id in state.downloadedChapterIds,
                                            downloading = chapter.id in state.downloadingChapterIds,
                                            selectionMode = isAnySelected,
                                            displayMode = state.novel.displayMode,
                                            titleOverride = chapterTitle,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 24.dp,
                                                    end = 16.dp,
                                                    top = 2.dp,
                                                    bottom = 4.dp,
                                                ),
                                            onClick = { onChapterClick(chapter.id) },
                                            onLongClick = { onChapterLongClick(chapter.id) },
                                            onTranslateClick = { onChapterTranslateClick(chapter.id) },
                                            onTranslateLongClick = { onChapterTranslateLongClick(chapter.id) },
                                            onTranslatedDownloadClick = { onChapterTranslatedDownloadClick(chapter.id) },
                                            onTranslatedDownloadLongClick = { onChapterTranslatedDownloadLongClick(chapter.id) },
                                            onTranslatedDownloadOpenFolder = { onChapterTranslatedDownloadOpenFolder(chapter.id) },
                                            onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                            onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                            onToggleRead = { onChapterReadToggle(chapter.id) },
                                            chapterSwipeStartAction = chapterSwipeStartAction,
                                            chapterSwipeEndAction = chapterSwipeEndAction,
                                            onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
                                        )
                                    }
                                    is NovelChapterDisplayRow.VolumeGroup -> {
                                        val primaryChapter = row.chapters.first()
                                        val isExpanded = row.groupKey in expandedGroupKeys
                                        NovelClassicChapterGroup(
                                            title = row.title,
                                            count = row.chapters.size,
                                            expanded = isExpanded,
                                            singleItemGroup = row.chapters.size == 1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            onClick = {
                                                if (row.chapters.size == 1) {
                                                    onChapterClick(primaryChapter.id)
                                                } else {
                                                    expandedGroupKeys = if (isExpanded) {
                                                        expandedGroupKeys - row.groupKey
                                                    } else {
                                                        expandedGroupKeys + row.groupKey
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (row.chapters.size == 1) {
                                                    onChapterLongClick(primaryChapter.id)
                                                }
                                            },
                                        )
                                    }
                                    is NovelChapterDisplayRow.VolumeChapter -> {
                                        val chapter = row.chapter
                                        NovelClassicChapterRow(
                                            chapter = chapter,
                                            displayNumber = row.displayNumber,
                                            selected = chapter.id in selectedIds,
                                            chapterActionState = state.chapterActionStates[chapter.id],
                                            downloaded = chapter.id in state.downloadedChapterIds,
                                            downloading = chapter.id in state.downloadingChapterIds,
                                            selectionMode = isAnySelected,
                                            displayMode = state.novel.displayMode,
                                            titleOverride = row.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 24.dp,
                                                    end = 16.dp,
                                                    top = 2.dp,
                                                    bottom = 4.dp,
                                                ),
                                            onClick = { onChapterClick(chapter.id) },
                                            onLongClick = { onChapterLongClick(chapter.id) },
                                            onTranslateClick = { onChapterTranslateClick(chapter.id) },
                                            onTranslateLongClick = { onChapterTranslateLongClick(chapter.id) },
                                            onTranslatedDownloadClick = { onChapterTranslatedDownloadClick(chapter.id) },
                                            onTranslatedDownloadLongClick = { onChapterTranslatedDownloadLongClick(chapter.id) },
                                            onTranslatedDownloadOpenFolder = { onChapterTranslatedDownloadOpenFolder(chapter.id) },
                                            onToggleDownload = { onChapterDownloadToggle(chapter.id) },
                                            onToggleBookmark = { onChapterBookmarkToggle(chapter.id) },
                                            onToggleRead = { onChapterReadToggle(chapter.id) },
                                            chapterSwipeStartAction = chapterSwipeStartAction,
                                            chapterSwipeEndAction = chapterSwipeEndAction,
                                            onChapterSwipe = { action -> onChapterSwipe(chapter.id, action) },
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

internal fun initialVisibleChapterCount(totalCount: Int, pageSize: Int): Int {
    if (totalCount <= 0 || pageSize <= 0) return 0
    return minOf(totalCount, pageSize)
}

@Composable
private fun NovelClassicChapterRow(
    chapter: tachiyomi.domain.items.novelchapter.model.NovelChapter,
    displayNumber: Int,
    selected: Boolean,
    chapterActionState: NovelChapterActionUiState?,
    downloaded: Boolean,
    downloading: Boolean,
    selectionMode: Boolean,
    displayMode: Long,
    titleOverride: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onTranslateLongClick: () -> Unit,
    onTranslatedDownloadClick: () -> Unit,
    onTranslatedDownloadLongClick: () -> Unit,
    onTranslatedDownloadOpenFolder: () -> Unit,
    onToggleDownload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (LibraryPreferences.NovelSwipeAction) -> Unit,
) {
    val chapterCard: @Composable () -> Unit = {
        Card(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val chapterTitle = titleOverride ?: when (displayMode) {
                        DomainNovel.CHAPTER_DISPLAY_NUMBER -> {
                            stringResource(
                                MR.strings.display_mode_chapter,
                                formatChapterNumber(displayNumber.toDouble()),
                            )
                        }
                        else -> {
                            chapter.name.ifBlank {
                                stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(displayNumber.toDouble()),
                                )
                            }
                        }
                    }
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (chapter.read) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    novelChapterDateText(
                        chapter = chapter,
                        parsedDateText = if (chapter.dateUpload > 0L) {
                            relativeDateTimeText(chapter.dateUpload)
                        } else {
                            null
                        },
                    )?.let { dateText ->
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!selectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (chapterActionState?.showGeminiRow == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val translateState = chapterActionState.translateState
                                NovelChapterActionButton(
                                    icon = Icons.Rounded.Translate,
                                    iconTint = when (translateState) {
                                        NovelChapterActionIconState.Active -> MaterialTheme.colorScheme.primary
                                        NovelChapterActionIconState.InProgress -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = onTranslateClick,
                                    onLongClick = onTranslateLongClick,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                    showProgress = translateState == NovelChapterActionIconState.InProgress,
                                    progressColor = MaterialTheme.colorScheme.primary,
                                    size = 36.dp,
                                    iconSize = 20.dp,
                                    contentDescription = stringResource(
                                        KMR.strings.novel_reader_selected_text_translation_action_translate,
                                    ),
                                )
                                val translatedDownloadState = chapterActionState.downloadTranslatedState
                                NovelChapterActionButton(
                                    icon = Icons.Outlined.Download,
                                    iconTint = when (translatedDownloadState) {
                                        NovelChapterActionIconState.Active -> MaterialTheme.colorScheme.primary
                                        NovelChapterActionIconState.InProgress -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    onClick = if (translatedDownloadState == NovelChapterActionIconState.Active) {
                                        onTranslatedDownloadOpenFolder
                                    } else {
                                        onTranslatedDownloadClick
                                    },
                                    onLongClick = onTranslatedDownloadLongClick,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                    showProgress = translatedDownloadState == NovelChapterActionIconState.InProgress,
                                    progressColor = MaterialTheme.colorScheme.primary,
                                    size = 36.dp,
                                    iconSize = 20.dp,
                                    contentDescription = stringResource(MR.strings.manga_download),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NovelChapterActionButton(
                                icon = when {
                                    downloading -> Icons.Outlined.FileDownloadOff
                                    downloaded -> Icons.Outlined.Download
                                    else -> Icons.Outlined.Download
                                },
                                iconTint = when {
                                    downloading -> MaterialTheme.colorScheme.tertiary
                                    downloaded -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleDownload,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                showProgress = downloading,
                                progressColor = MaterialTheme.colorScheme.primary,
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                            NovelChapterActionButton(
                                icon = Icons.Outlined.Bookmark,
                                iconTint = if (chapter.bookmark) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleBookmark,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                            NovelChapterActionButton(
                                icon = Icons.Outlined.CheckCircle,
                                iconTint = if (chapter.read) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = onToggleRead,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f),
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (!selectionMode) {
        val startSwipeAction = novelSwipeAction(
            action = chapterSwipeStartAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
        )
        val endSwipeAction = novelSwipeAction(
            action = chapterSwipeEndAction,
            read = chapter.read,
            bookmark = chapter.bookmark,
            downloaded = downloaded,
            downloading = downloading,
            background = MaterialTheme.colorScheme.primaryContainer,
            onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
        )
        SwipeableActionsBox(
            modifier = Modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = novelSwipeActionThreshold,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            chapterCard()
        }
    } else {
        chapterCard()
    }
}

@Composable
private fun NovelClassicChapterGroup(
    title: String,
    count: Int,
    expanded: Boolean,
    singleItemGroup: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 1) {
                        "$title ($count)"
                    } else {
                        title
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ChevronLeft
                } else {
                    Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun nextVisibleChapterCount(currentCount: Int, totalCount: Int, step: Int): Int {
    if (totalCount <= 0 || step <= 0) return 0
    if (currentCount <= 0) return minOf(totalCount, step)
    return minOf(totalCount, currentCount + step)
}

internal fun resolveNovelFastScrollVisibleChapterCount(
    currentVisibleCount: Int,
    loadedChapterCount: Int,
): Int {
    if (loadedChapterCount <= 0) return 0
    return maxOf(currentVisibleCount, loadedChapterCount)
}

private const val NOVEL_CLASSIC_CHAPTERS_HEADER_KEY = "novel-classic-chapters-header"
private val NOVEL_CLASSIC_FAST_SCROLL_ITEM_TOP_INSET = 6.dp

internal fun resolveNovelClassicFastScrollBlockStartIndex(
    showScanlatorSelector: Boolean,
    chapterPageEnabled: Boolean,
): Int {
    return 3 + listOf(showScanlatorSelector, chapterPageEnabled).count { it }
}

@Composable
private fun novelStatusText(status: Long): String? {
    return when (status) {
        SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
        SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
        SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
        SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
        SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
        SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
        else -> null
    }
}

@Composable
private fun RowScope.NovelActionButton(
    title: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    PillActionButton(
        title = title,
        icon = icon,
        color = color,
        onClick = onClick,
        onLongClick = onLongClick,
        active = color == MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MetadataInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

