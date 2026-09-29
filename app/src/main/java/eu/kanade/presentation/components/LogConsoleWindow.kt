package eu.kanade.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.ai.AppLogger
import eu.kanade.tachiyomi.data.ai.LogEntry
import eu.kanade.tachiyomi.data.ai.LogLevel

@Composable
fun LogConsoleWindow(
    modifier: Modifier = Modifier,
) {
    val logs by AppLogger.logs.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val maxAvailableHeightDp = (configuration.screenHeightDp * 0.50f).coerceAtLeast(200f)

    var currentHeightDp by remember { mutableFloatStateOf(180f) }

    val activeHeight = when {
        isFullScreen -> maxAvailableHeightDp.dp
        else -> currentHeightDp.dp
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF141416),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val deltaDp = -dragAmount / density
                            if (!isExpanded && deltaDp > 10) {
                                isExpanded = true
                            }
                            if (isExpanded) {
                                val next = (currentHeightDp + deltaDp).coerceIn(120f, maxAvailableHeightDp)
                                currentHeightDp = next
                                if (deltaDp < -35 && currentHeightDp <= 130f) {
                                    isExpanded = false
                                }
                            }
                        }
                    }
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF424242))
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Engine Console",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2C2C2E))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${logs.size}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB0B0B0),
                            )
                        }

                        if (!isExpanded && logs.isNotEmpty()) {
                            val lastLog = logs.last()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lastLog.message,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when (lastLog.level) {
                                    LogLevel.AI_STEP -> Color(0xFF40C4FF)
                                    LogLevel.SUCCESS -> Color(0xFF00E676)
                                    LogLevel.WARN -> Color(0xFFFFD54F)
                                    LogLevel.ERROR -> Color(0xFFFF5252)
                                    LogLevel.INFO -> Color(0xFF9E9E9E)
                                },
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExpanded) {
                            IconButton(
                                onClick = {
                                    val text = logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Engine Logs", text))
                                    Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Logs",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(12.dp),
                                )
                            }

                            IconButton(
                                onClick = { AppLogger.clear() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = "Clear Logs",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(14.dp),
                                )
                            }

                            IconButton(
                                onClick = { isFullScreen = !isFullScreen },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(activeHeight)
                        .background(Color(0xFF0D0D0E))
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No logs yet. Colorization passes will stream here.",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF555555),
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            items(logs) { entry ->
                                LogRowItem(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRowItem(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.AI_STEP -> Color(0xFF40C4FF)
        LogLevel.SUCCESS -> Color(0xFF00E676)
        LogLevel.WARN -> Color(0xFFFFD54F)
        LogLevel.ERROR -> Color(0xFFFF5252)
        LogLevel.INFO -> Color(0xFFB0BEC5)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = entry.timestamp,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF555555),
            modifier = Modifier.padding(end = 5.dp),
        )

        Box(
            modifier = Modifier
                .padding(end = 5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(levelColor.copy(alpha = 0.15f))
                .padding(horizontal = 3.dp, vertical = 0.5.dp)
        ) {
            Text(
                text = entry.level.name,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = levelColor,
            )
        }

        Text(
            text = entry.message,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE0E0E0),
        )
    }
}