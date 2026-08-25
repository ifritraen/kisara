package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

private data class NovelBookFlipAnimationSpeedOption(
    val value: NovelBookFlipAnimationSpeed,
    val labelRes: StringResource,
)

@Composable
internal fun novelPageTransitionStyleEntries(): ImmutableMap<NovelPageTransitionStyle, String> {
    return persistentMapOf(
        NovelPageTransitionStyle.INSTANT to
            stringResource(KMR.strings.novel_reader_page_transition_style_instant),
        NovelPageTransitionStyle.SLIDE to
            stringResource(KMR.strings.novel_reader_page_transition_style_slide),
        NovelPageTransitionStyle.DEPTH to
            stringResource(KMR.strings.novel_reader_page_transition_style_depth),
        NovelPageTransitionStyle.CURL to
            stringResource(KMR.strings.novel_reader_page_transition_style_curl),
        NovelPageTransitionStyle.BOOK_FLIP to
            stringResource(KMR.strings.novel_reader_page_transition_style_book_flip),
    )
}

@Composable
internal fun novelPageTransitionStyleSubtitle(
    style: NovelPageTransitionStyle,
    entries: Map<NovelPageTransitionStyle, String>,
): String {
    val normalizedStyle = if (style == NovelPageTransitionStyle.BOOK) {
        NovelPageTransitionStyle.CURL
    } else {
        style
    }
    return entries[normalizedStyle].orEmpty()
}

private val novelBookFlipAnimationSpeedSliderOptions = listOf(
    NovelBookFlipAnimationSpeedOption(
        value = NovelBookFlipAnimationSpeed.SLOW,
        labelRes = KMR.strings.novel_reader_page_turn_speed_slow,
    ),
    NovelBookFlipAnimationSpeedOption(
        value = NovelBookFlipAnimationSpeed.NORMAL,
        labelRes = KMR.strings.novel_reader_page_turn_speed_normal,
    ),
    NovelBookFlipAnimationSpeedOption(
        value = NovelBookFlipAnimationSpeed.FAST,
        labelRes = KMR.strings.novel_reader_page_turn_speed_fast,
    ),
)

internal fun novelBookFlipAnimationSpeedSliderIndex(speed: NovelBookFlipAnimationSpeed): Int {
    return novelBookFlipAnimationSpeedSliderOptions.indexOfFirst { it.value == speed }.coerceAtLeast(0)
}

internal fun resolveNovelBookFlipAnimationSpeedSliderValue(index: Int): NovelBookFlipAnimationSpeed {
    return novelBookFlipAnimationSpeedSliderOptions[
        index.coerceIn(
            0,
            novelBookFlipAnimationSpeedSliderOptions.lastIndex,
        ),
    ].value
}

@Composable
internal fun novelBookFlipAnimationSpeedEntries(): ImmutableMap<NovelBookFlipAnimationSpeed, String> {
    return persistentMapOf(
        NovelBookFlipAnimationSpeed.SLOW to stringResource(KMR.strings.novel_reader_page_turn_speed_slow),
        NovelBookFlipAnimationSpeed.NORMAL to stringResource(KMR.strings.novel_reader_page_turn_speed_normal),
        NovelBookFlipAnimationSpeed.FAST to stringResource(KMR.strings.novel_reader_page_turn_speed_fast),
    )
}
