package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.ai.ResourceMonitor

@Composable
fun ResourceBar(
    onFreeRamClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        ResourceMonitor.start()
    }
    val metrics by ResourceMonitor.metrics.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E22))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // CPU Metric
            ResourceItem(
                icon = Icons.Default.Speed,
                label = "CPU",
                value = "${metrics.cpuPercent}%",
                color = when {
                    metrics.cpuPercent > 75 -> Color(0xFFE53935)
                    metrics.cpuPercent > 35 -> Color(0xFFFB8C00)
                    else -> Color(0xFF42A5F5)
                },
            )

            // GPU Metric (Mali)
            ResourceItem(
                icon = Icons.Default.SportsEsports,
                label = "GPU",
                value = if (metrics.gpuPercent >= 0) "${metrics.gpuPercent}%" else "N/A",
                badge = if (metrics.gpuClockMhz > 0) "${metrics.gpuClockMhz}M" else null,
                color = when {
                    metrics.gpuPercent > 70 -> Color(0xFFE53935)
                    metrics.gpuPercent > 20 -> Color(0xFFFB8C00)
                    else -> Color(0xFF66BB6A)
                },
            )

            // RAM Metric
            ResourceItem(
                icon = Icons.Default.Memory,
                label = "RAM",
                value = "${metrics.ramAppMb}M",
                color = when {
                    metrics.ramAppMb > 800 -> Color(0xFFE53935)
                    metrics.ramAppMb > 400 -> Color(0xFFFB8C00)
                    else -> Color(0xFFAB47BC)
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onFreeRamClick != null) {
                    IconButton(
                        onClick = onFreeRamClick,
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Free RAM",
                            tint = Color(0xFFBA68C8),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Root Indicator Dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (metrics.isRootActive) Color(0xFF00E676) else Color(0xFF757575)),
                )
            }
        }
    }
}

@Composable
private fun ResourceItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    badge: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$label:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB0B0B0),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "($badge)",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF9E9E9E),
            )
        }
    }
}