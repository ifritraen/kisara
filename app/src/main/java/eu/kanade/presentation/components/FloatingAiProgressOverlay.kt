package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

@Composable
fun FloatingAiProgressOverlay(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val translationManager = remember { Injekt.get<eu.kanade.translation.TranslationManager>() }
    val colorizerManager = remember { Injekt.get<eu.kanade.translation.ColorizerManager>() }
    val superResolutionManager = remember { Injekt.get<eu.kanade.translation.SuperResolutionManager>() }

    val translationProgress by translationManager.progressState.collectAsState()
    val colorizerProgress by colorizerManager.progressState.collectAsState()
    val superResolutionProgress by superResolutionManager.progressState.collectAsState()

    val activeAiProgress = when {
        translationProgress != null -> {
            val p = translationProgress!!
            Triple(
                "TRANSLATION",
                p.step to "${p.currentPage}/${p.totalPages}",
                Triple(
                    if (p.totalPages > 0) p.currentPage.toFloat() / p.totalPages.toFloat() else 0f,
                    {
                        val active = translationManager.getQueuedTranslationOrNull(p.chapterId)
                        if (active != null) translationManager.cancelQueuedTranslation(active)
                    },
                    Icons.Default.Translate,
                ),
            )
        }
        colorizerProgress != null -> {
            val p = colorizerProgress!!
            Triple(
                "COLORIZER",
                p.step to if (p.totalPages > 0) "Page ${p.currentPage}/${p.totalPages}" else "",
                Triple(
                    p.percent / 100f,
                    {
                        val active = colorizerManager.getQueuedColorizerOrNull(p.chapterId)
                        if (active != null) {
                            colorizerManager.cancelQueuedColorizer(active)
                        } else {
                            colorizerManager.cancelActiveColorizer()
                        }
                    },
                    Icons.Default.Palette,
                ),
            )
        }
        superResolutionProgress != null -> {
            val p = superResolutionProgress!!
            Triple(
                "SUPER_RES",
                p.step to if (p.totalPages > 0) "${p.currentPage}/${p.totalPages}" else "",
                Triple(
                    p.percent / 100f,
                    {
                        val active = superResolutionManager.getQueuedSuperResolutionOrNull(p.chapterId)
                        if (active != null) {
                            superResolutionManager.cancelQueuedSuperResolution(active)
                        } else {
                            superResolutionManager.cancelActiveSuperResolution()
                        }
                    },
                    Icons.Default.AutoAwesome,
                ),
            )
        }
        else -> null
    }

    activeAiProgress?.let { (type, stepPair, actionTriple) ->
        val (stepText, pageText) = stepPair
        val (progressFraction, onCancelAction, iconVector) = actionTriple

        var isMinimized by remember { mutableStateOf(false) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isMinimized) {
                GlassSurface(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y
                            }
                        }
                        .widthIn(max = 440.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    style = GlassDefaults.prominentStyle(),
                ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 1. TOP: Hardware Resource Monitor + Minimize Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ResourceBar(
                            onFreeRamClick = {
                                System.gc()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { isMinimized = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize AI Card",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. MIDDLE: AI Progress Details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (pageText.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• $pageText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = stepText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        IconButton(
                            onClick = onCancelAction,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel AI Process",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. PROGRESS BAR
                    if (progressFraction > 0f) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4. EMBEDDED ENGINE CONSOLE LOGS
                    LogConsoleWindow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            }
        } else {
            // Minimized Floating Pill
            GlassSurface(
                modifier = modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y
                        }
                    }
                    .clickable { isMinimized = false },
                shape = CircleShape,
                style = GlassDefaults.prominentStyle(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                        CircularProgressIndicator(
                            progress = { if (progressFraction > 0f) progressFraction.coerceIn(0f, 1f) else 0.5f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pageText.isNotBlank()) pageText else type,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
}
