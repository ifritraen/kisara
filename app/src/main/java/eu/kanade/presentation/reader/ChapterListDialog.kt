package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
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
import tachiyomi.i18n.MR
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
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val context = LocalContext.current
    val state = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val downloadQueueState by downloadManager.queueState.collectAsState()

    val tabTitles = remember {
        persistentListOf(MR.strings.chapters)
    }
    val pagerState = rememberPagerState { tabTitles.size }
    val mappedTabTitles = tabTitles.map { stringResource(it) }.toImmutableList()

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = mappedTabTitles,
        pagerState = pagerState,
        actions = {
            val scope = rememberCoroutineScope()
            IconButton(
                onClick = {
                    scope.launch {
                        val index = chapters.indexOfFirst { it.isCurrent }
                        if (index >= 0) {
                            state.animateScrollToItem(index)
                        }
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(MR.strings.action_resume),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
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
                LazyColumn(
                    state = state,
                    modifier = Modifier.heightIn(min = 200.dp, max = 450.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    items(
                        items = chapters,
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
    }
}
