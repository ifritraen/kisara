package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReadingMode

/**
 * Novel Viewer abstraction representing the active novel reader presentation engine and mode.
 */
sealed interface NovelViewer {
    val mode: NovelReadingMode

    data object ContinuousBook : NovelViewer {
        override val mode: NovelReadingMode = NovelReadingMode.BOOK
    }

    data object ChapterByChapter : NovelViewer {
        override val mode: NovelReadingMode = NovelReadingMode.CHAPTERS
    }

    data object PageTurnPager : NovelViewer {
        override val mode: NovelReadingMode = NovelReadingMode.CHAPTERS
    }

    data object WebViewRenderer : NovelViewer {
        override val mode: NovelReadingMode = NovelReadingMode.CHAPTERS
    }

    companion object {
        fun fromReadingMode(
            mode: NovelReadingMode,
            preferPageReader: Boolean = false,
            preferWebView: Boolean = false,
        ): NovelViewer {
            return when {
                mode == NovelReadingMode.BOOK -> ContinuousBook
                preferPageReader -> PageTurnPager
                preferWebView -> WebViewRenderer
                else -> ChapterByChapter
            }
        }
    }
}
