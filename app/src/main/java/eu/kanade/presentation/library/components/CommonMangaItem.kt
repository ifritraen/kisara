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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val parsed = remember(rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(rawTitle) }
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
        } else if (parsed.languageCode != null) {
            LanguageBadge(isLocal = false, sourceLanguage = parsed.languageCode)
        }
        if (parsed.isColorized) {
            ColorizedBadge()
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
                if (parsed.isUncensored) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.BottomEnd)
                            .then(
                                if (onClickContinueReading != null) {
                                    Modifier.padding(bottom = 32.dp)
                                } else if (title != null) {
                                    Modifier.padding(bottom = 48.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        UncensoredBadge()
                    }
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.70f),
                        Color.Black.copy(alpha = 0.85f),
                    ),
                ),
            )
            .fillMaxHeight(0.45f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
        ) {
            if (artistAuthorText != null) {
                Text(
                    text = artistAuthorText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f,
                        ),
                    ),
                )
            }
            GridItemTitle(
                title = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f,
                    ),
                ),
                minLines = 1,
            )
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
    val parsed = remember(rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(rawTitle) }
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
        } else if (parsed.languageCode != null) {
            LanguageBadge(isLocal = false, sourceLanguage = parsed.languageCode)
        }
        if (parsed.isColorized) {
            ColorizedBadge()
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
                                    .fillMaxWidth(),
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
                                    .fillMaxWidth(),
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
                    if (parsed.isUncensored) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .align(Alignment.BottomEnd)
                                .then(
                                    if (onClickContinueReading != null) {
                                        Modifier.padding(bottom = 32.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            UncensoredBadge()
                        }
                    }
                },
            )
            Column(modifier = Modifier.padding(4.dp)) {
                if (artistAuthorText != null) {
                    Text(
                        text = artistAuthorText,
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

@Composable
private fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
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
    val parsed = remember(rawTitle) { eu.kanade.tachiyomi.util.MangaTitleParser.parse(rawTitle) }
    val cleanTitle = parsed.cleanTitle
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
        if (parsed.isUncensored) {
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
            MangaCover.Square(
                modifier = Modifier
                    // KMK -->
                    // .alpha(coverAlpha)
                    // KMK <--
                    .height(40.dp),
                data = coverData,
                // KMK -->
                alpha = coverAlpha,
                bgColor = bgColor ?: MaterialTheme.colorScheme.surface.takeIf { isSelected },
                tint = onBgColor,
                size = MangaCover.Size.Big,
                // KMK <--
            )
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
