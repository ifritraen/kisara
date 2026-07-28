package eu.kanade.presentation.library.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.MangaCoverHide
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.DebugToggles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

object CommonMangaItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

private val ContinueReadingButtonSizeSmall = 28.dp
private val ContinueReadingButtonSizeLarge = 32.dp

private val ContinueReadingButtonIconSizeSmall = 16.dp
private val ContinueReadingButtonIconSizeLarge = 20.dp

private val ContinueReadingButtonGridPadding = 6.dp
private val ContinueReadingButtonListSpacing = 8.dp

internal const val GRID_SELECTED_COVER_ALPHA = 0.76f

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun MangaCompactGridItem(
    coverData: MangaCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String? = null,
    onClickContinueReading: (() -> Unit)? = null,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    // KMK -->
    libraryColored: Boolean = true,
    // KMK <--
    manga: Manga? = null,
) {
    // KMK -->
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rawTitle = title ?: manga?.title ?: ""
    val parsed = remember(manga, rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(manga, rawTitle) }
    val cleanTitle = parsed.cleanTitle
    val artistAuthorText = remember(parsed) {
        val parts = mutableListOf<String>()
        if (parsed.artist != null) parts.add(parsed.artist)
        if (parsed.author != null) parts.add(parsed.author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    val finalBadgeEnd: @Composable RowScope.() -> Unit = {
        if (coverBadgeEnd != null) {
            coverBadgeEnd()
        } else {
            val lang = parsed.languageCode ?: eu.kanade.tachiyomi.util.MangaTitleParser.getLanguageCode(manga, rawTitle)
            if (lang != null) {
                LanguageBadge(isLocal = false, sourceLanguage = lang)
            }
        }
        val hasColor = parsed.isColorized || eu.kanade.tachiyomi.util.MangaTitleParser.isColorized(manga, rawTitle)
        if (hasColor) {
            ColorizedBadge()
        }
        val hasUncensored = parsed.isUncensored || eu.kanade.tachiyomi.util.MangaTitleParser.isUncensored(manga, rawTitle)
        if (hasUncensored) {
            UncensoredBadge()
        }
    }

    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val normalStyleKey by uiPreferences.normalCardStyle().collectAsState()
    val normalStyle = remember(normalStyleKey) { eu.kanade.presentation.components.cards.NormalCardStyle.fromKey(normalStyleKey) }
    val coverTitleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()

    if (normalStyle != eu.kanade.presentation.components.cards.NormalCardStyle.DEFAULT) {
        eu.kanade.presentation.components.cards.KisaraNormalCard(
            style = normalStyle,
            title = cleanTitle,
            coverData = coverData,
            subtitle = artistAuthorText,
            coverBadgeStart = coverBadgeStart,
            coverBadgeEnd = finalBadgeEnd,
            coverTitleStyle = coverTitleStyleKey,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = manga?.let {
            if (!coverData.isMangaFavorite) {
                { handleMangaDoubleClick(it, context, scope) }
            } else {
                null
            }
        },
    ) {
        MangaGridCover(
            cover = {
                // KMK -->
                if (DebugToggles.HIDE_COVER_IMAGE_ONLY_SHOW_COLOR.enabled) {
                    MangaCoverHide.Book(
                        modifier = Modifier
                            .fillMaxWidth(),
                        bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                        tint = onBgColor,
                    )
                } else {
                    // KMK <--
                    MangaCover.Book(
                        modifier = Modifier
                            // KMK -->
                            // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                            // KMK <--
                            .fillMaxWidth(),
                        data = coverData,
                        // KMK -->
                        alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                        bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                        tint = onBgColor,
                        // KMK <--
                    )
                }
            },
            badgesStart = coverBadgeStart,
            badgesEnd = finalBadgeEnd,
            content = {
                if (title != null) {
                    CoverTextOverlay(
                        title = cleanTitle,
                        artistAuthorText = artistAuthorText,
                        onClickContinueReading = onClickContinueReading,
                    )
                } else if (onClickContinueReading != null) {
                    ContinueReadingButton(
                        size = ContinueReadingButtonSizeLarge,
                        iconSize = ContinueReadingButtonIconSizeLarge,
                        onClick = onClickContinueReading,
                        modifier = Modifier
                            .padding(ContinueReadingButtonGridPadding)
                            .align(Alignment.BottomEnd),
                    )
                }
            },
        )
    }
}

/**
 * Title overlay for [MangaCompactGridItem]
 */
@Composable
private fun BoxScope.CoverTextOverlay(
    title: String,
    artistAuthorText: String? = null,
    onClickContinueReading: (() -> Unit)? = null,
) {
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val titleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()
    val params = remember(titleStyleKey) { getCoverTitleParams(titleStyleKey) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.60f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.15f),
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.88f),
                        Color.Black.copy(alpha = 0.96f),
                    ),
                ),
            )
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = params.paddingHorizontal, vertical = params.paddingVertical),
        ) {
            if (artistAuthorText != null) {
                Text(
                    text = artistAuthorText,
                    fontSize = (params.fontSize.value - 1f).coerceAtLeast(8f).sp,
                    lineHeight = params.lineHeight,
                    letterSpacing = params.letterSpacing,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 6f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                        ),
                    ),
                )
            }
            val numberMatch = remember(title) { Regex("(\\d+)$").find(title) }
            val endingNumber = numberMatch?.groupValues?.get(1)
            val cleanTitleText = remember(title, endingNumber) {
                if (endingNumber != null && title.endsWith(endingNumber)) {
                    title.substring(0, title.length - endingNumber.length).trim()
                } else {
                    title
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = cleanTitleText,
                    fontSize = params.fontSize,
                    lineHeight = params.lineHeight,
                    letterSpacing = params.letterSpacing,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            blurRadius = 8f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                        ),
                    ),
                )
                if (endingNumber != null) {
                    Text(
                        text = " $endingNumber",
                        fontSize = params.fontSize,
                        lineHeight = params.lineHeight,
                        letterSpacing = params.letterSpacing,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        if (onClickContinueReading != null) {
            ContinueReadingButton(
                size = ContinueReadingButtonSizeSmall,
                iconSize = ContinueReadingButtonIconSizeSmall,
                onClick = onClickContinueReading,
                modifier = Modifier.padding(
                    end = ContinueReadingButtonGridPadding,
                    bottom = ContinueReadingButtonGridPadding,
                ),
            )
        }
    }
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun MangaComfortableGridItem(
    coverData: MangaCoverModel,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    titleMaxLines: Int = 2,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    onClickContinueReading: (() -> Unit)? = null,
    // KMK -->
    libraryColored: Boolean = true,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    usePanoramaCover: Boolean,
    fitToPanoramaCover: Boolean = false,
    // KMK <--
    manga: Manga? = null,
) {
    // KMK -->
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rawTitle = title
    val parsed = remember(manga, rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(manga, rawTitle) }
    val cleanTitle = parsed.cleanTitle
    val isNsfw = remember(manga, cleanTitle) { eu.kanade.tachiyomi.util.NsfwDetector.isNsfw(manga, cleanTitle) }
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val blurNsfwCovers by uiPreferences.kisaraBlurNsfwCovers().collectAsState()
    val shouldBlur = isNsfw && blurNsfwCovers
    val artistAuthorText = remember(parsed) {
        val parts = mutableListOf<String>()
        if (parsed.artist != null) parts.add(parsed.artist)
        if (parsed.author != null) parts.add(parsed.author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    val finalBadgeEnd: @Composable RowScope.() -> Unit = {
        if (coverBadgeEnd != null) {
            coverBadgeEnd()
        } else {
            val lang = parsed.languageCode ?: eu.kanade.tachiyomi.util.MangaTitleParser.getLanguageCode(manga, rawTitle)
            if (lang != null) {
                LanguageBadge(isLocal = false, sourceLanguage = lang)
            }
        }
        val hasColor = parsed.isColorized || eu.kanade.tachiyomi.util.MangaTitleParser.isColorized(manga, rawTitle)
        if (hasColor) {
            ColorizedBadge()
        }
        val hasUncensored = parsed.isUncensored || eu.kanade.tachiyomi.util.MangaTitleParser.isUncensored(manga, rawTitle)
        if (hasUncensored) {
            UncensoredBadge()
        }
    }

    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = manga?.let {
            if (!coverData.isMangaFavorite) {
                { handleMangaDoubleClick(it, context, scope) }
            } else {
                null
            }
        },
    ) {
        Column {
            MangaGridCover(
                cover = {
                    // KMK -->
                    if (DebugToggles.HIDE_COVER_IMAGE_ONLY_SHOW_COLOR.enabled) {
                        MangaCoverHide.Book(
                            modifier = Modifier
                                .fillMaxWidth(),
                            bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                            tint = onBgColor,
                        )
                    } else {
                        if (fitToPanoramaCover && usePanoramaCover && coverIsWide) {
                            MangaCover.Panorama(
                                modifier = Modifier
                                    // KMK -->
                                    // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                                    // KMK <--
                                    .fillMaxWidth()
                                    .then(if (shouldBlur) Modifier.blur(16.dp) else Modifier),
                                data = coverData,
                                // KMK -->
                                alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                                bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                                tint = onBgColor,
                                onCoverLoaded = { _, result ->
                                    val image = result.result.image
                                    coverRatio.floatValue = image.height.toFloat() / image.width
                                },
                                // KMK <--
                            )
                        } else {
                            // KMK <--
                            MangaCover.Book(
                                modifier = Modifier
                                    // KMK -->
                                    // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                                    // KMK <--
                                    .fillMaxWidth()
                                    .then(if (shouldBlur) Modifier.blur(16.dp) else Modifier),
                                data = coverData,
                                // KMK -->
                                alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                                bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                                tint = onBgColor,
                                onCoverLoaded = { _, result ->
                                    val image = result.result.image
                                    coverRatio.floatValue = image.height.toFloat() / image.width
                                },
                                scale = if (usePanoramaCover && coverIsWide) {
                                    ContentScale.Fit
                                } else {
                                    ContentScale.Crop
                                },
                                // KMK <--
                            )
                        }
                    }
                },
                // KMK -->
                ratio = if (fitToPanoramaCover && usePanoramaCover && coverIsWide) {
                    MangaCover.Panorama.ratio
                } else {
                    MangaCover.Book.ratio
                },
                // KMK <--
                badgesStart = coverBadgeStart,
                badgesEnd = finalBadgeEnd,
                content = {
                    if (shouldBlur) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = "NSFW Content hidden",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (onClickContinueReading != null) {
                        ContinueReadingButton(
                            size = ContinueReadingButtonSizeLarge,
                            iconSize = ContinueReadingButtonIconSizeLarge,
                            onClick = onClickContinueReading,
                            modifier = Modifier
                                .padding(ContinueReadingButtonGridPadding)
                                .align(Alignment.BottomEnd),
                        )
                    }
                },
            )
            val titleStyleKey by uiPreferences.kisaraCoverTitleStyle().collectAsState()
            val params = remember(titleStyleKey) { getCoverTitleParams(titleStyleKey) }
            Column(modifier = Modifier.padding(horizontal = params.paddingHorizontal, vertical = params.paddingVertical)) {
                if (artistAuthorText != null) {
                    Text(
                        text = artistAuthorText,
                        fontSize = (params.fontSize.value - 1f).coerceAtLeast(8f).sp,
                        lineHeight = params.lineHeight,
                        letterSpacing = params.letterSpacing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GridItemTitle(
                    title = cleanTitle,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = titleMaxLines,
                    titleStyleKey = titleStyleKey,
                )
            }
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun MangaGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    // KMK -->
    ratio: Float = MangaCover.Book.ratio,
    // KMK <--
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }
    }
}

private data class CoverTitleParams(
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val lineHeight: androidx.compose.ui.unit.TextUnit,
    val letterSpacing: androidx.compose.ui.unit.TextUnit,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
)

private fun getCoverTitleParams(styleKey: String): CoverTitleParams {
    return when (styleKey) {
        "compact" -> CoverTitleParams(10.sp, 12.sp, (-0.3).sp, 2.dp, 2.dp)
        "ultra_compact" -> CoverTitleParams(9.sp, 11.sp, (-0.4).sp, 0.dp, 0.dp)
        "moderate" -> CoverTitleParams(10.5.sp, 13.sp, (-0.1).sp, 4.dp, 4.dp)
        else -> CoverTitleParams(11.sp, 16.sp, 0.sp, 8.dp, 8.dp)
    }
}

@Composable
private fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    titleStyleKey: String = "default",
) {
    val params = remember(titleStyleKey) { getCoverTitleParams(titleStyleKey) }
    var displayTitle by remember(title) { mutableStateOf(title) }
    Text(
        modifier = modifier,
        text = displayTitle,
        fontSize = params.fontSize,
        lineHeight = params.lineHeight,
        letterSpacing = params.letterSpacing,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                val numberMatch = Regex("(\\d+)$").find(title)
                if (numberMatch != null) {
                    val numbers = numberMatch.groupValues[1]
                    val lastLineIndex = (textLayoutResult.lineCount - 1).coerceAtMost(maxLines - 1)
                    if (lastLineIndex >= 0) {
                        val lastLineEndIndex = textLayoutResult.getLineEnd(lastLineIndex)
                        val safeCut = (lastLineEndIndex - numbers.length - 3).coerceAtLeast(0)
                        val newTitle = title.substring(0, safeCut) + "…" + numbers
                        if (newTitle != displayTitle) {
                            displayTitle = newTitle
                        }
                    }
                }
            }
        },
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun GridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            )
            .selectedOutline(
                isSelected = isSelected,
                color = MaterialTheme.colorScheme.secondary,
                cornerRadius = 16.dp,
            )
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * @see GridItemSelectable
 */
private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
    cornerRadius: Dp = 16.dp,
) = drawBehind {
    if (isSelected) {
        val strokeWidth = 2.dp.toPx()
        val radius = cornerRadius.toPx() - (strokeWidth / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * Layout of list item.
 */
@Composable
fun MangaListItem(
    coverData: MangaCoverModel,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    badge: @Composable (RowScope.() -> Unit),
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    onClickContinueReading: (() -> Unit)? = null,
    // KMK -->
    libraryColored: Boolean = true,
    // KMK <--
    manga: Manga? = null,
) {
    // KMK -->
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rawTitle = title
    val parsed = remember(manga, rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(manga, rawTitle) }
    val cleanTitle = parsed.cleanTitle
    val isNsfw = remember(manga, cleanTitle) { eu.kanade.tachiyomi.util.NsfwDetector.isNsfw(manga, cleanTitle) }
    val hasUncensored = parsed.isUncensored || eu.kanade.tachiyomi.util.MangaTitleParser.isUncensored(manga, rawTitle)
    val uiPreferences = remember { Injekt.get<eu.kanade.domain.ui.UiPreferences>() }
    val blurNsfwCovers by uiPreferences.kisaraBlurNsfwCovers().collectAsState()
    val shouldBlur = isNsfw && blurNsfwCovers
    val artistAuthorText = remember(parsed) {
        val parts = mutableListOf<String>()
        if (parsed.artist != null) parts.add(parsed.artist)
        if (parsed.author != null) parts.add(parsed.author)
        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    val finalBadge: @Composable RowScope.() -> Unit = {
        badge()
        if (parsed.isColorized) {
            ColorizedBadge()
        }
        if (hasUncensored) {
            UncensoredBadge()
        }
    }

    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = manga?.let {
                    if (!coverData.isMangaFavorite) {
                        { handleMangaDoubleClick(it, context, scope) }
                    } else {
                        null
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK -->
        if (DebugToggles.HIDE_COVER_IMAGE_ONLY_SHOW_COLOR.enabled) {
            MangaCoverHide.Square(
                modifier = Modifier
                    .height(40.dp),
                bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                tint = onBgColor,
            )
        } else {
            // KMK <--
            Box(
                modifier = Modifier.height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                MangaCover.Square(
                    modifier = Modifier
                        // KMK -->
                        // .alpha(coverAlpha)
                        // KMK <--
                        .height(40.dp)
                        .then(if (shouldBlur) Modifier.blur(8.dp) else Modifier),
                    data = coverData,
                    // KMK -->
                    alpha = coverAlpha,
                    bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                    tint = onBgColor,
                    size = MangaCover.Size.Big,
                    // KMK <--
                )
                if (shouldBlur) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = "NSFW Content hidden",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            if (artistAuthorText != null) {
                Text(
                    text = artistAuthorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = cleanTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BadgeGroup(content = finalBadge)
        if (onClickContinueReading != null) {
            ContinueReadingButton(
                size = ContinueReadingButtonSizeSmall,
                iconSize = ContinueReadingButtonIconSizeSmall,
                onClick = onClickContinueReading,
                modifier = Modifier.padding(start = ContinueReadingButtonListSpacing),
            )
        }
    }
}

@Composable
private fun ContinueReadingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilledIconButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer),
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.action_resume),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

fun handleMangaDoubleClick(
    manga: Manga,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    scope.launch {
        try {
            val networkToLocalManga: NetworkToLocalManga = Injekt.get()
            val updateManga: UpdateManga = Injekt.get()
            val getCategories: GetCategories = Injekt.get()
            val setMangaCategories: SetMangaCategories = Injekt.get()
            val libraryPreferences: LibraryPreferences = Injekt.get()

            val localManga = withContext(Dispatchers.IO) {
                networkToLocalManga(manga)
            }
            val result = withContext(Dispatchers.IO) {
                updateManga.awaitUpdateFavorite(localManga.id, true)
            }
            if (result) {
                val categories = withContext(Dispatchers.IO) {
                    getCategories.await()
                }
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                if (defaultCategory != null) {
                    withContext(Dispatchers.IO) {
                        setMangaCategories.await(localManga.id, listOf(defaultCategory.id))
                    }
                }
                withContext(Dispatchers.Main) {
                    context.toast(context.stringResource(MR.strings.manga_added_library))
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Double tap favoriting failed: " + e.stackTraceToString() }
        }
    }
}
