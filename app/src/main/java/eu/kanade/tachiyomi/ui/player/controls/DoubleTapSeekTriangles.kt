package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun DoubleTapSeekTriangles(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "double_tap_seek_chevrons")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 0 using FastOutSlowInEasing
                1.0f at 200 using FastOutSlowInEasing
                0.2f at 400 using FastOutSlowInEasing
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "chevron_1_alpha",
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 0
                0.2f at 150 using FastOutSlowInEasing
                1.0f at 350 using FastOutSlowInEasing
                0.2f at 550 using FastOutSlowInEasing
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "chevron_2_alpha",
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.2f at 0
                0.2f at 300 using FastOutSlowInEasing
                1.0f at 500 using FastOutSlowInEasing
                0.2f at 700 using FastOutSlowInEasing
                0.2f at 800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "chevron_3_alpha",
    )

    val rotation = if (isForward) 0f else 180f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = modifier.rotate(rotation),
    ) {
        DoubleTapArrow(alpha1)
        DoubleTapArrow(alpha2)
        DoubleTapArrow(alpha3)
    }
}

@Composable
private fun DoubleTapArrow(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.ic_play_seek_triangle),
        contentDescription = null,
        modifier = modifier
            .size(width = 16.dp, height = 20.dp)
            .alpha(alpha),
        tint = Color.White,
    )
}
