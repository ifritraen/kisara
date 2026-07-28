@file:Suppress("PropertyName")

package eu.kanade.presentation.manga.components

import androidx.annotation.ColorInt
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import eu.kanade.presentation.manga.components.MangaCover.Companion.COVER_TEMPLATE_SIZE_BIG
import eu.kanade.presentation.manga.components.MangaCover.Companion.COVER_TEMPLATE_SIZE_MEDIUM
import eu.kanade.presentation.manga.components.MangaCover.Companion.COVER_TEMPLATE_SIZE_NORMAL
import eu.kanade.presentation.manga.components.MangaCover.Size
import eu.kanade.tachiyomi.R
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.model.MangaCover as DomainMangaCover

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),

    // KMK -->
    Panorama(3f / 2f),
    // KMK <--
    ;

    enum class Size {
        Normal,
        Medium,
        Big,
    }

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.medium,
        onClick: (() -> Unit)? = null,
        // KMK -->
        alpha: Float = 1f,
        bgColor: Color? = null,
        @ColorInt tint: Int? = null,
        /** Perform action when cover loaded, specifically generating color map. If the cover doesn't update, it won't be called */
        onCoverLoaded: ((DomainMangaCover, result: AsyncImagePainter.State.Success) -> Unit)? = null,
        size: Size = Size.Normal,
        scale: ContentScale = ContentScale.Crop,
        // KMK <--
    ) {
        var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        val succeed = state is AsyncImagePainter.State.Success

        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 1200,
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
            ),
            label = "shimmerTranslate",
        )

        val shimmerBrush = remember(translateAnim) {
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    Color(0x1F888888),
                    Color(0x3DFFFFFF),
                    Color(0x1F888888),
                ),
                start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, translateAnim - 300f),
                end = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim),
            )
        }

        val baseModifier = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .alpha(if (succeed) alpha else 1f)

        val modifierColored = if (!succeed && state !is AsyncImagePainter.State.Error) {
            baseModifier.background(shimmerBrush)
        } else {
            baseModifier.background(bgColor ?: CoverPlaceholderColor)
        }.then(
            if (onClick != null) {
                Modifier.clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )

        Box(modifier = modifierColored) {
            coil3.compose.AsyncImage(
                model = data,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = scale,
                onState = { newState ->
                    state = newState
                    if (newState is AsyncImagePainter.State.Success && onCoverLoaded != null) {
                        when (data) {
                            is Manga -> onCoverLoaded(data.asMangaCover(), newState)
                            is DomainMangaCover -> onCoverLoaded(data, newState)
                        }
                    }
                },
            )

            if (state is AsyncImagePainter.State.Error) {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(
                            when (size) {
                                Size.Big -> COVER_TEMPLATE_SIZE_BIG
                                Size.Medium -> COVER_TEMPLATE_SIZE_MEDIUM
                                else -> COVER_TEMPLATE_SIZE_NORMAL
                            },
                        )
                        .align(Alignment.Center),
                    colorFilter = ColorFilter.tint(
                        tint?.let { Color(it) } ?: CoverPlaceholderOnBgColor,
                    ),
                )
            }
        }
    }

    companion object {
        val COVER_TEMPLATE_SIZE_BIG = 16.dp
        val COVER_TEMPLATE_SIZE_MEDIUM = 24.dp
        val COVER_TEMPLATE_SIZE_NORMAL = 32.dp
    }
}

enum class MangaCoverHide(private val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.medium,
        onClick: (() -> Unit)? = null,
        // KMK -->
        /** background color, which used for loading/error indicator */
        bgColor: Color? = CoverPlaceholderColor,
        /** onBackground color, which used for loading/error indicator */
        @ColorInt tint: Int? = null,
        size: Size = Size.Normal,
    ) {
        val modifierColored = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .background(bgColor ?: CoverPlaceholderColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )

        Box(
            modifier = modifierColored,
        ) {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_menu_book_24),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(
                        when (size) {
                            Size.Big -> COVER_TEMPLATE_SIZE_BIG
                            Size.Medium -> COVER_TEMPLATE_SIZE_MEDIUM
                            else -> COVER_TEMPLATE_SIZE_NORMAL
                        },
                    )
                    .align(Alignment.Center),
                colorFilter = ColorFilter.tint(
                    tint?.let { Color(it) } ?: CoverPlaceholderOnBgColor,
                ),
            )
        }
    }
}

internal const val RatioSwitchToPanorama = 0.75f

internal val CoverPlaceholderColor = Color(0x1F888888)
internal val CoverPlaceholderOnBgColor = Color(0x8F888888)
