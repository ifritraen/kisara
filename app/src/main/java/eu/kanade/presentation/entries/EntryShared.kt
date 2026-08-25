package eu.kanade.presentation.entries

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.kanade.presentation.manga.DownloadAction

enum class EntryScreenItem {
    INFO_BOX,
    ACTION_ROW,
    METADATA_INFO,
    TRACKER_DETAILS,
    DESCRIPTION_WITH_TAG,
    INFO_BUTTONS,
    CHAPTER_PREVIEW_LOADING,
    CHAPTER_PREVIEW_ROW,
    CHAPTER_PREVIEW_MORE,
    CHAPTER_HEADER,
    CHAPTER,
    RELATED_MANGAS,
    EXTERNAL_METADATA,
    AIRING_TIME,
}

data class TitleFastScrollOverlayAccumulator(
    val prevIndex: Int = 0,
    val prevOffset: Int = 0,
    val revealed: Boolean = false,
)

fun reduceTitleFastScrollOverlayAccumulator(
    current: TitleFastScrollOverlayAccumulator,
    isExpandedList: Boolean,
    isScrolling: Boolean,
    index: Int,
    offset: Int,
): TitleFastScrollOverlayAccumulator {
    if (!isScrolling) return current
    val isScrollingUp = index < current.prevIndex || (index == current.prevIndex && offset < current.prevOffset)
    val revealed = isScrollingUp && isExpandedList
    return TitleFastScrollOverlayAccumulator(
        prevIndex = index,
        prevOffset = offset,
        revealed = revealed,
    )
}

fun shouldShowTitleFastScrollFloatingActionButton(
    shouldShowFab: Boolean,
    isThumbFastScrolling: Boolean,
): Boolean {
    return shouldShowFab && !isThumbFastScrolling
}

fun shouldShowTitleFastScrollOverlayChrome(
    isThumbDragged: Boolean,
    isExpandedList: Boolean,
    isReverseScrolling: Boolean,
): Boolean {
    return isThumbDragged || (isExpandedList && isReverseScrolling)
}

data class TitleListFastScrollSpec(
    val enabled: Boolean = true,
    val thumbAllowed: Boolean = true,
    val topPaddingPx: Int = 0,
)

fun resolveTitleListFastScrollSpec(
    itemCount: Int,
): TitleListFastScrollSpec {
    return TitleListFastScrollSpec(enabled = itemCount > 10, thumbAllowed = itemCount > 10)
}

fun resolveTitleListFastScrollSpec(
    baseTopPaddingPx: Int,
    firstVisibleItemIndex: Int,
    blockStartIndex: Int,
    blockStartOffsetPx: Int?,
): TitleListFastScrollSpec {
    val thumbAllowed = firstVisibleItemIndex >= blockStartIndex
    val topPaddingPx = if (thumbAllowed && blockStartOffsetPx != null) {
        maxOf(baseTopPaddingPx, blockStartOffsetPx)
    } else {
        baseTopPaddingPx
    }
    return TitleListFastScrollSpec(
        enabled = true,
        thumbAllowed = thumbAllowed,
        topPaddingPx = topPaddingPx,
    )
}

fun resolveEntryAutoJumpTargetIndex(
    enabled: Boolean,
    targetIndex: Int,
    restoredScrollIndex: Int,
): Int? {
    if (!enabled) return null
    return if (restoredScrollIndex <= 0 && targetIndex >= 0) targetIndex else null
}

fun resolveEntryAutoJumpTargetIndex(
    currentIndex: Int,
    itemCount: Int,
): Int? {
    val next = currentIndex + 1
    return if (next in 0 until itemCount) next else null
}
