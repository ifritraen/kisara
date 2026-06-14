package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val LocalHazeState = staticCompositionLocalOf { HazeState() }

@Immutable
data class GlassStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val tonalElevation: Dp,
    val shadowElevation: Dp,
)

object GlassDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    @Composable
    fun subtleStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.72f,
        borderAlpha = 0.18f,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    )

    @Composable
    fun regularStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.82f,
        borderAlpha = 0.24f,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    )

    @Composable
    fun prominentStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.88f,
        borderAlpha = 0.30f,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.regularStyle(),
    shape: Shape = GlassDefaults.shape,
    dialogSurface: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val isGlassEnabled by uiPreferences.kisaraFrostedGlass().collectAsState()
    val hazeOpacity by uiPreferences.bottomBarOpacity().collectAsState()
    val hazeBlur by uiPreferences.bottomBarBlur().collectAsState()

    val hazeState = LocalHazeState.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f

    val preferenceAlpha = (hazeOpacity.coerceIn(0, 100)) / 100f
    val effectiveContainerAlpha = (preferenceAlpha * style.containerAlpha).coerceIn(0f, 1f)

    // Base color formula from Kototoro
    val baseColor = when {
        effectiveContainerAlpha >= 0.86f -> colorScheme.surfaceContainerHigh
        effectiveContainerAlpha >= 0.80f -> colorScheme.surfaceContainer
        else -> colorScheme.surfaceContainerLow
    }.let { candidate ->
        if (isDarkTheme) lerp(candidate, colorScheme.surfaceBright, 0.16f) else candidate
    }
    val containerColor = baseColor.copy(alpha = effectiveContainerAlpha)

    // Base blur radius formula from Kototoro
    val baseBlurRadius = when {
        style.shadowElevation >= 10.dp -> 28.dp
        style.shadowElevation >= 6.dp -> 24.dp
        else -> 18.dp
    }
    val blurRadius = hazeBlur.coerceIn(0, 80).dp
        .takeIf { it > 0.dp }
        ?: baseBlurRadius

    // Tint alpha from Kototoro
    val tintAlpha = ((preferenceAlpha * 0.22f) + (style.containerAlpha * 0.14f)).coerceIn(0.18f, 0.38f)
        .let { alpha ->
            if (isDarkTheme) (alpha + 0.10f).coerceAtMost(0.50f) else alpha
        }
    val baseTintColor = baseColor.copy(alpha = tintAlpha)

    val border = BorderStroke(
        width = 1.dp,
        color = colorScheme.outlineVariant.copy(
            alpha = if (isDarkTheme) style.borderAlpha.coerceIn(0.16f, 0.28f) else style.borderAlpha.coerceAtMost(0.18f),
        ),
    )

    val useRuntimeHaze = isGlassEnabled

    val hazeStyle = remember(blurRadius, baseTintColor, containerColor, dialogSurface) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeDefaults.tint(if (dialogSurface) Color.Transparent else baseTintColor),
            blurRadius = blurRadius,
            noiseFactor = 0.03f, // premium frosted feel noise
        )
    }

    val hazeBackgroundColor = if (dialogSurface) Color.Transparent else containerColor
    val surfaceColor = if (useRuntimeHaze && !dialogSurface) Color.Transparent else containerColor

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Surface(
            modifier = if (useRuntimeHaze) {
                modifier
                    .clip(shape)
                    .hazeChild(hazeState, hazeStyle) {
                        backgroundColor = hazeBackgroundColor
                        blurredEdgeTreatment = BlurredEdgeTreatment(shape)
                        clipToAreasBounds = true
                        expandLayerBounds = !dialogSurface
                        forceInvalidateOnPreDraw = true
                    }
            } else {
                modifier
            },
            shape = shape,
            color = surfaceColor,
            contentColor = colorScheme.onSurface,
            tonalElevation = style.tonalElevation,
            shadowElevation = style.shadowElevation,
            border = border,
        ) {
            Box(content = content)
        }
    }
}
