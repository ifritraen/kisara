package eu.kanade.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ScanlatorTabRow
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.PageBookmarksSection
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.chapter.deduplicateChapters
import eu.kanade.tachiyomi.util.lang.toRelativeString
import exh.metadata.MetadataUtil
import exh.source.isEhBasedManga
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.pagebookmark.model.PageBookmark
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun ChapterListDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
    onDownloadAction: ((Chapter, ChapterDownloadAction) -> Unit)? = null,
    isHttpSource: Boolean,
    onBrowserClick: (() -> Unit)?,
    // KMK -->
    pageBookmarks: List<PageBookmark> = emptyList(),
    onClickBookmarkPage: ((chapterId: Long, pageNumber: Int) -> Unit)? = null,
    onDeleteBookmarkPage: ((bookmarkId: Long) -> Unit)? = null,
    onResumeClick: (() -> Unit)? = null,
    onToggleCurrentPageBookmark: (() -> Unit)? = null,
    currentPageNumber: Int = 1,
    isCurrentPageBookmarked: Boolean = false,
    // KMK <--
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val context = LocalContext.current
    val state = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val downloadQueueState by downloadManager.queueState.collectAsState()

    val tabTitles = remember {
        // KMK -->
        persistentListOf(MR.strings.chapters, KMR.strings.page_bookmarks)
        // KMK <--
    }
    val pagerState = rememberPagerState { tabTitles.size }
    val mappedTabTitles = tabTitles.map { stringResource(it) }.toImmutableList()

    val availableScanlators = remember(chapters) {
        chapters.mapNotNull { it.chapter.scanlator?.trim()?.ifBlank { null } }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val scanlatorExtraHeight = if (availableScanlators.size > 1) 36.dp else 0.dp

    // KMK --> Expandable bottom sheet: Partial (~58% / default) -> Full (92%)
    var isFullExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val partialMaxHeight = (screenHeight * 0.58f).coerceIn(340.dp, 480.dp) + scanlatorExtraHeight
    val fullMaxHeight = (screenHeight * 0.92f).coerceAtLeast(partialMaxHeight) + scanlatorExtraHeight

    val contentMaxHeight by animateDpAsState(
        targetValue = if (isFullExpanded) fullMaxHeight else partialMaxHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ReaderChapterListHeight",
    )

    BackHandler(enabled = isFullExpanded) {
        isFullExpanded = false
    }

    val nestedScrollConnection = remember(isFullExpanded, state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Swiping/sliding up in partial mode expands the sheet to full mode
                if (!isFullExpanded && available.y < -5f && source == NestedScrollSource.UserInput) {
                    isFullExpanded = true
                }
                // Dragging down at the top of the list in full mode collapses back to partial
                if (isFullExpanded && available.y > 0f && source == NestedScrollSource.UserInput) {
                    if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                        isFullExpanded = false
                        return available
                    }
                }
                return Offset.Zero
            }
        }
    }

    val dragGestureModifier = Modifier.pointerInput(isFullExpanded) {
        detectVerticalDragGestures { _, dragAmount ->
            if (dragAmount < -15f && !isFullExpanded) {
                isFullExpanded = true
            } else if (dragAmount > 15f && isFullExpanded) {
                isFullExpanded = false
            }
        }
    }
    // KMK <--

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = mappedTabTitles,
        pagerState = pagerState,
        modifier = dragGestureModifier.nestedScroll(nestedScrollConnection),
        actions = {
            // KMK --> Toggle fullscreen expand
            IconButton(
                onClick = { isFullExpanded = !isFullExpanded },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isFullExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullExpanded) "Collapse" else "Expand full",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            // KMK <--
            IconButton(
                onClick = {
                    onDismissRequest()
                    onResumeClick?.invoke()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(MR.strings.action_resume),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (isHttpSource && onBrowserClick != null) {
                IconButton(onClick = onBrowserClick) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = stringResource(MR.strings.action_open_in_browser),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { page ->
        when (page) {
            0 -> {
                var selectedScanlator by remember { mutableStateOf<String?>(null) }
                val currentChapterItem = remember(chapters) { chapters.find { it.isCurrent } }
                val displayedChapters = remember(chapters, selectedScanlator) {
                    if (selectedScanlator == null) {
                        chapters.deduplicateChapters(
                            currentChapterId = currentChapterItem?.chapter?.id,
                            preferredScanlator = currentChapterItem?.chapter?.scanlator,
                            getChapter = { it.chapter },
                        )
                    } else {
                        chapters.filter { it.chapter.scanlator.equals(selectedScanlator, ignoreCase = true) }
                    }
                }

                Column(
                    modifier = Modifier.heightIn(min = 200.dp, max = contentMaxHeight),
                ) {
                    if (availableScanlators.size > 1) {
                        ScanlatorTabRow(
                            scanlators = availableScanlators,
                            selectedScanlator = selectedScanlator,
                            onSelectScanlator = { selectedScanlator = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    LazyColumn(
                        state = state,
                        modifier = Modifier
                            .nestedScroll(nestedScrollConnection)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        items(
                            items = displayedChapters,
                            key = { "chapter-list-${it.chapter.id}" },
                        ) { chapterItem ->
                            val activeDownload = downloadQueueState.find { it.chapter.id == chapterItem.chapter.id }
                            val progress = activeDownload?.let {
                                downloadManager.progressFlow()
                                    .filter { it.chapter.id == chapterItem.chapter.id }
                                    .map { it.progress }
                                    .collectAsState(0).value
                            } ?: 0
                            val downloaded = if (chapterItem.manga.isLocal()) {
                                true
                            } else {
                                downloadManager.isChapterDownloaded(
                                    chapterItem.chapter.name,
                                    chapterItem.chapter.scanlator,
                                    chapterItem.chapter.url,
                                    chapterItem.manga.ogTitle,
                                    chapterItem.manga.source,
                                )
                            }
                            val downloadState = when {
                                activeDownload != null -> activeDownload.status
                                downloaded -> Download.State.DOWNLOADED
                                else -> Download.State.NOT_DOWNLOADED
                            }
                            MangaChapterListItem(
                                title = chapterItem.chapter.name,
                                date = chapterItem.chapter.dateUpload
                                    .takeIf { it > 0L }
                                    ?.let {
                                        if (manga?.isEhBasedManga() == true) {
                                            MetadataUtil.EX_DATE_FORMAT
                                                .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()))
                                        } else {
                                            LocalDate.ofInstant(
                                                Instant.ofEpochMilli(it),
                                                ZoneId.systemDefault(),
                                            ).toRelativeString(context, dateRelativeTime, chapterItem.dateFormat)
                                        }
                                    },
                                readProgress = null,
                                scanlator = chapterItem.chapter.scanlator,
                                sourceName = null,
                                read = chapterItem.chapter.read,
                                bookmark = chapterItem.chapter.bookmark,
                                selected = chapterItem.isCurrent,
                                downloadIndicatorEnabled = onDownloadAction != null,
                                downloadStateProvider = { downloadState },
                                downloadProgressProvider = { progress },
                                chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                                chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
                                onLongClick = { /*TODO*/ },
                                onClick = { onClickChapter(chapterItem.chapter) },
                                onDownloadClick = if (onDownloadAction != null) {
                                    { action -> onDownloadAction(chapterItem.chapter, action) }
                                } else {
                                    null
                                },
                                onChapterSwipe = {
                                    onBookmark(chapterItem.chapter)
                                },
                            )
                        }
                    }
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .heightIn(min = 200.dp, max = contentMaxHeight)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    PageBookmarksSection(
                        bookmarks = pageBookmarks,
                        chapters = remember(chapters) { chapters.map { it.chapter } },
                        onClickBookmark = { chapterId, pageNumber ->
                            onClickBookmarkPage?.invoke(chapterId, pageNumber)
                            onDismissRequest()
                        },
                        onDeleteBookmark = { bookmarkId ->
                            onDeleteBookmarkPage?.invoke(bookmarkId)
                        },
                        onBookmarkCurrentPage = onToggleCurrentPageBookmark,
                        currentPageNumber = currentPageNumber,
                        isCurrentPageBookmarked = isCurrentPageBookmarked,
                    )
                }
            }
        }
    }
}
