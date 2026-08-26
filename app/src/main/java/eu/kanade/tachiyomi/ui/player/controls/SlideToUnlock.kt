package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

private const val UNLOCK_THRESHOLD = 0.85f

@Composable
fun SlideToUnlock(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(KMR.strings.slide_to_unlock),
) {
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var hasTriggeredUnlock by remember { mutableStateOf(false) }

    val trackHeight = 52.dp
    val thumbSize = 44.dp
    val trackPadding = 4.dp

    val density = LocalDensity.current
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val trackPaddingPx = with(density) { trackPadding.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .width(260.dp)
            .height(trackHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxDragDistance = (maxWidthPx - thumbSizePx - trackPaddingPx * 2).coerceAtLeast(1f)
        val progress = (dragOffsetX.value / maxDragDistance).coerceIn(0f, 1f)

        // Track container
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Drag progress fill
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (progress + (thumbSizePx / maxWidthPx)).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.25f),
                                    ),
                                ),
                            ),
                    )
                }

                // Shimmering / fading center text
                Text(
                    text = text,
                    color = Color.White.copy(alpha = (1f - progress * 1.4f).coerceIn(0.15f, 0.9f)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Draggable Thumb
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (trackPaddingPx + dragOffsetX.value).roundToInt(),
                                y = 0,
                            )
                        }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(
                            if (progress >= UNLOCK_THRESHOLD) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            },
                        )
                        .pointerInput(maxDragDistance) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    hasTriggeredUnlock = false
                                },
                                onDragEnd = {
                                    val currentProgress = dragOffsetX.value / maxDragDistance
                                    if (currentProgress >= UNLOCK_THRESHOLD && !hasTriggeredUnlock) {
                                        hasTriggeredUnlock = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onUnlock()
                                        coroutineScope.launch {
                                            dragOffsetX.snapTo(0f)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            dragOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        dragOffsetX.animateTo(0f)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = (dragOffsetX.value + dragAmount).coerceIn(0f, maxDragDistance)
                                    val newProgress = newOffset / maxDragDistance
                                    if (newProgress >= UNLOCK_THRESHOLD && !hasTriggeredUnlock) {
                                        hasTriggeredUnlock = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onUnlock()
                                        coroutineScope.launch {
                                            dragOffsetX.snapTo(0f)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            dragOffsetX.snapTo(newOffset)
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (progress >= UNLOCK_THRESHOLD) {
                            Icons.Filled.LockOpen
                        } else {
                            Icons.Filled.Lock
                        },
                        contentDescription = null,
                        tint = if (progress >= UNLOCK_THRESHOLD) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.Black
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
