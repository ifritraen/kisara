package eu.kanade.presentation.library.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
// KMK -->
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
// KMK <--
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.components.BadgeGroup

object CommonEntryItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

@Composable
fun EntryComfortableGridItem(
    title: String,
    coverData: Any?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            // KMK --> ponytail: reuse last decode as placeholder for zero-flicker scrolling
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverData)
                    .placeholderMemoryCacheKey(MemoryCache.Key(coverData.toString()))
                    .build(),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(coverAlpha),
                contentScale = ContentScale.Crop,
            )
            if (coverBadgeStart != null || coverBadgeEnd != null) {
                BadgeGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                ) {
                    coverBadgeStart?.invoke(this)
                    coverBadgeEnd?.invoke(this)
                }
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun EntryCompactGridItem(
    title: String?,
    coverData: Any?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
    ) {
        // KMK --> ponytail: reuse last decode as placeholder for zero-flicker scrolling
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverData)
                .placeholderMemoryCacheKey(MemoryCache.Key(coverData.toString()))
                .build(),
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .alpha(coverAlpha),
            contentScale = ContentScale.Crop,
        )
        if (coverBadgeStart != null || coverBadgeEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {
                coverBadgeStart?.invoke(this)
                coverBadgeEnd?.invoke(this)
            }
        }
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun EntryListItem(
    title: String,
    coverData: Any?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    badge: @Composable (RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    entries: Any? = null,
    containerHeight: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK --> ponytail: reuse last decode as placeholder for zero-flicker scrolling
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverData)
                .placeholderMemoryCacheKey(MemoryCache.Key(coverData.toString()))
                .build(),
            contentDescription = title,
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .alpha(coverAlpha),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (badge != null) {
                BadgeGroup(modifier = Modifier.padding(top = 4.dp)) {
                    badge()
                }
            }
        }
    }
}
