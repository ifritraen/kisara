package eu.kanade.presentation.entries.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

import eu.kanade.tachiyomi.data.coil.staticBlur

@Composable
fun EntryPosterBackground(
    coverData: Any?,
    scrollOffset: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val scrimColor = MaterialTheme.colorScheme.background

    val hasScrolledAway = scrollOffset > 100
    val rawDim = if (hasScrolledAway) 0.45f else (0.20f + (scrollOffset / 400f).coerceIn(0f, 0.20f))
    val dimAlpha by animateFloatAsState(
        targetValue = rawDim,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "posterDimAlpha",
    )

    Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
        if (coverData != null) {
            val imageRequest = remember(coverData) {
                ImageRequest.Builder(context)
                    .data(coverData)
                    .crossfade(true)
                    .staticBlur(24)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Frosted glassy tint scrim overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scrimColor.copy(alpha = 0.20f),
                                scrimColor.copy(alpha = 0.35f),
                                scrimColor.copy(alpha = 0.60f),
                            ),
                        ),
                    ),
            )

            // Dynamic dimming layer on scroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = dimAlpha)),
            )
        }
    }
}
