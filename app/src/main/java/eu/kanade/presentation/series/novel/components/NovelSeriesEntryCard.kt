package eu.kanade.presentation.series.novel.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.aurora.AURORA_DIMMED_ITEM_ALPHA
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.series.isSeriesEntryCompleted
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.library.novel.LibraryNovel

@Composable
fun NovelSeriesEntryCard(
    novel: LibraryNovel,
    ordinalLabel: String?,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isCompleted = isSeriesEntryCompleted(novel.readCount, novel.totalChapters)
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "series_entry_card_scale",
    )

    GlassmorphismCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .alpha(if (isCompleted) AURORA_DIMMED_ITEM_ALPHA else 1f)
            .clickable(onClick = onClick),
        cornerRadius = 14.dp,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        innerPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Box(
                modifier = dragHandleModifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = colors.textPrimary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Ordinal badge / Completed check
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) {
                            colors.accent.copy(alpha = 0.2f)
                        } else {
                            colors.textPrimary.copy(alpha = 0.08f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = ordinalLabel ?: "${novel.novel.id}",
                        color = colors.textPrimary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Cover
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                MangaCover.Book(
                    data = novel.novel.asNovelCover(),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.novel.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${novel.readCount.toInt()} / ${novel.totalChapters.toInt()}",
                    color = colors.textPrimary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.textPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
