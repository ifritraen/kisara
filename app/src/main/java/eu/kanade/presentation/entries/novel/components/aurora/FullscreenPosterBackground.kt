package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.AuroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBlur
import eu.kanade.presentation.entries.components.aurora.buildAuroraPosterBackgroundRequest
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterBackgroundPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraPosterScrimBrush
import eu.kanade.presentation.entries.components.aurora.shouldDrawAuroraPosterBlurOverlay
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fixed fullscreen poster background with scroll-based dimming and blur effects.
 *
 * @param novel Novel object containing cover information
 * @param scrollOffset Current scroll offset for dynamic dimming
 * @param firstVisibleItemIndex Current first visible item index from LazyListState
 * @param resolvedCoverUrl Resolved cover URL to display (null to skip loading)
 */
@Composable
fun FullscreenPosterBackground(
    novel: Novel,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    modifier: Modifier = Modifier,
    resolvedCoverUrl: String? = null,
    resolvedCoverUrlFallback: String? = null,
    refererUrl: String? = null,
    sourceHeaders: Map<String, String>? = null,
    minimumBlurOverlayAlpha: Float = 0f,
    onPosterLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val coverCache = remember { Injekt.get<NovelCoverCache>() }
    // Issue #154: surface the user-set custom cover on the details poster,
    // matching what the library grid already shows.
    val customCoverFile = remember(novel.id, novel.coverLastModified) {
        coverCache.getCustomCoverFile(novel.id).takeIf { it.exists() }
    }
    val posterRequest = remember(
        resolvedCoverUrl,
        resolvedCoverUrlFallback,
        refererUrl,
        sourceHeaders,
        novel.thumbnailUrl,
        customCoverFile,
        novel.coverLastModified,
    ) {
        AuroraPosterRequest(
            primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
            fallbackUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: novel.thumbnailUrl,
            refererUrl = refererUrl?.takeIf { it.isNotBlank() },
            headers = sourceHeaders,
            customCoverFile = customCoverFile,
            coverLastModified = novel.coverLastModified,
        )
    }
    val posterModel = posterRequest.primaryUrl ?: posterRequest.fallbackUrl ?: posterRequest.customCoverFile?.path
    val posterColorFilter = rememberAuroraPosterColorFilter()

    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

    val rawDim = if (hasScrolledAway) 0.7f else (scrollOffset / 100f).coerceIn(0f, 0.7f)
    val rawBlur = if (hasScrolledAway) {
        1f
    } else {
        (scrollOffset / 100f).coerceIn(minimumBlurOverlayAlpha, 1f)
    }

    val dimAlpha by animateFloatAsState(
        targetValue = rawDim,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (hasScrolledAway) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "dimAlpha",
    )
    val blurOverlayAlpha by animateFloatAsState(
        targetValue = rawBlur,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (hasScrolledAway) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "blurOverlayAlpha",
    )
    val containerWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val containerHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val placeholderPosterUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: novel.thumbnailUrl
    val placeholderCover = remember(
        novel.id,
        novel.source,
        novel.favorite,
        novel.coverLastModified,
        placeholderPosterUrl,
    ) {
        novel.asNovelCover().copy(url = placeholderPosterUrl)
    }
    var previousSuccessfulBackgroundSpec by remember(novel.id) {
        mutableStateOf<AuroraPosterBackgroundSpec?>(null)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onPosterLongPress != null) {
                    Modifier.pointerInput(onPosterLongPress) {
                        detectTapGestures(
                            onLongPress = { onPosterLongPress() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val colors = AuroraTheme.colors

        if (posterModel != null) {
            val backgroundSpec = remember(
                novel.id,
                novel.coverLastModified,
                posterRequest,
                containerWidthPx,
            ) {
                val baseCacheKey = "novel-bg;${novel.id};${novel.coverLastModified};" +
                    posterRequest.primaryUrl.orEmpty()
                auroraPosterBackgroundSpec(
                    baseCacheKey = baseCacheKey,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                )
            }
            val backgroundRequest = remember(
                posterRequest,
                placeholderCover,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
                backgroundSpec.memoryCacheKey,
                containerWidthPx,
                containerHeightPx,
            ) {
                buildAuroraPosterBackgroundRequest(
                    context = context,
                    data = posterRequest,
                    spec = backgroundSpec,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    placeholderData = previousSuccessfulBackgroundSpec
                        ?.takeIf { it.memoryCacheKey != backgroundSpec.memoryCacheKey }
                        ?: placeholderCover,
                )
            }
            val backgroundPainter = rememberAuroraPosterBackgroundPainter(
                request = backgroundRequest,
                placeholderPainter = placeholderPainter,
            )
            LaunchedEffect(
                posterRequest.primaryUrl,
                placeholderPosterUrl,
                backgroundSpec.memoryCacheKey,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
            ) {
                val fallbackKey = "novel;${novel.id};$placeholderPosterUrl;${novel.coverLastModified}"
                val debugMessage = "request poster=${previewTitleCoverUrl(posterRequest.primaryUrl)} " +
                    "placeholder=${previewTitleCoverUrl(placeholderPosterUrl)} " +
                    "memoryKey=${backgroundSpec.memoryCacheKey} " +
                    "placeholderKey=${previousSuccessfulBackgroundSpec?.memoryCacheKey ?: fallbackKey}"
                debugTitleCoverFlow(
                    scope = "novel-bg",
                    message = debugMessage,
                )
            }
            LaunchedEffect(backgroundPainter, backgroundSpec) {
                backgroundPainter.state.collectLatest { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        previousSuccessfulBackgroundSpec = backgroundSpec
                    }
                    debugTitleCoverFlow(
                        scope = "novel-bg",
                        message = "painterState=${state::class.simpleName} poster=${previewTitleCoverUrl(
                            posterRequest.primaryUrl,
                        )} memoryKey=${backgroundSpec.memoryCacheKey}",
                    )
                }
            }

            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            val shouldApplyBlurLayer by remember {
                derivedStateOf {
                    blurOverlayAlpha > 0.08f &&
                        shouldDrawAuroraPosterBlurOverlay(blurOverlayAlpha)
                }
            }
            if (shouldApplyBlurLayer) {
                Image(
                    painter = backgroundPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = posterColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = blurOverlayAlpha
                        }
                        .auroraPosterBlur(if (colors.isDark) 20.dp else 32.dp),
                )
            }
        } else {
            Image(
                painter = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(resolveAuroraPosterScrimBrush(colors)),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val color = if (colors.isDark) Color.Black else colors.background
                    val factor = if (colors.isDark) 1f else 0.60f
                    onDrawBehind {
                        drawRect(color = color, alpha = dimAlpha * factor)
                    }
                },
        )
    }
}
