package eu.kanade.presentation.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.presentation.core.components.BadgeGroup

enum class NormalCardStyle(val key: String, val displayName: String) {
    DEFAULT("default", "Default"),
    MODERN("modern", "Modern"),
    EXOTIC("exotic", "Exotic"),
    MINIMAL_EXOTIC("minimal_exotic", "Minimal Exotic"),
    BLUR("blur", "Blur"),
    MATERIAL("material", "Material"),
    LIQUID_GLASS("liquid_glass", "Liquid Glass"),
    ;

    companion object {
        fun fromKey(key: String): NormalCardStyle {
            return entries.find { it.key == key } ?: DEFAULT
        }
    }
}

enum class HomeSectionCardStyle(val key: String, val displayName: String) {
    DEFAULT("default", "Default (Kisara)"),
    REGULAR("regular", "Regular (Anymex)"),
    BLURRED("blurred", "Blurred (Anymex)"),
    BOOTIFUL("bootiful", "Bootiful (Anymex / ShonenX)"),
    ;

    companion object {
        fun fromKey(key: String): HomeSectionCardStyle {
            return entries.find { it.key == key } ?: DEFAULT
        }
    }
}

@Composable
fun KisaraHomeSectionCard(
    style: HomeSectionCardStyle,
    title: String,
    subtitle: String? = null,
    coverData: Any?,
    progress: Float? = null,
    badgeText: String? = null,
    chapterName: String? = null,
    readAtTimestamp: String? = null,
    languageCode: String? = null,
    coverTitleStyle: String = "default",
    onClick: () -> Unit,
    onResume: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (style) {
        HomeSectionCardStyle.REGULAR -> {
            Surface(
                modifier = modifier
                    .width(260.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    MangaCover.Book(
                        data = coverData,
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
        }
        HomeSectionCardStyle.BLURRED -> {
            Box(
                modifier = modifier
                    .width(280.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick),
            ) {
                MangaCover.Book(
                    data = coverData,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    if (badgeText != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        HomeSectionCardStyle.BOOTIFUL -> {
            ElevatedCard(
                modifier = modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    ) {
                        MangaCover.Book(
                            data = coverData,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (onResume != null) {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(22.dp),
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // DEFAULT (Native Kisara Layout for Continue Where You Left)
            val titleParams = remember(coverTitleStyle) { getCoverTitleParams(coverTitleStyle) }
            Card(
                modifier = modifier
                    .width(300.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Blurred cover background
                    MangaCover.Book(
                        data = coverData,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.40f),
                                        Color.Black.copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left thumbnail filling height flush
                        MangaCover.Book(
                            data = coverData,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                // Title (max 2 lines, clean title + ending number, cover title style)
                                Text(
                                    text = title,
                                    fontSize = titleParams.fontSize,
                                    lineHeight = titleParams.lineHeight,
                                    letterSpacing = titleParams.letterSpacing,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (subtitle != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                val lang = languageCode ?: badgeText
                                if (!lang.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            text = lang,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }

                            Column {
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (!chapterName.isNullOrEmpty()) {
                                    Text(
                                        text = chapterName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = readAtTimestamp ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )

                                    if (onResume != null) {
                                        IconButton(
                                            onClick = onResume,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resume",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CoverTitleParams(
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val lineHeight: androidx.compose.ui.unit.TextUnit,
    val letterSpacing: androidx.compose.ui.unit.TextUnit,
)

private fun getCoverTitleParams(styleKey: String): CoverTitleParams {
    return when (styleKey) {
        "compact" -> CoverTitleParams(10.sp, 12.sp, (-0.3).sp)
        "ultra_compact" -> CoverTitleParams(9.sp, 11.sp, (-0.4).sp)
        "moderate" -> CoverTitleParams(10.5.sp, 13.sp, (-0.1).sp)
        else -> CoverTitleParams(11.sp, 16.sp, 0.sp)
    }
}

@Composable
fun KisaraNormalCard(
    style: NormalCardStyle,
    title: String,
    coverData: Any?,
    subtitle: String? = null,
    badgeText: String? = null,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    coverTitleStyle: String = "default",
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val titleParams = remember(coverTitleStyle) { getCoverTitleParams(coverTitleStyle) }
    val subtitleFontSize = remember(titleParams.fontSize) {
        (titleParams.fontSize.value - 2.0f).coerceAtLeast(8.0f).sp
    }

    val numberMatch = remember(title) { Regex("(\\d+)$").find(title) }
    val endingNumber = numberMatch?.groupValues?.get(1)
    val cleanTitleText = remember(title, endingNumber) {
        if (endingNumber != null && title.endsWith(endingNumber)) {
            title.substring(0, title.length - endingNumber.length).trim()
        } else {
            title
        }
    }

    val titleRow: @Composable (Color, Int) -> Unit = { textColor, maxLines ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = cleanTitleText,
                fontSize = titleParams.fontSize,
                lineHeight = titleParams.lineHeight,
                letterSpacing = titleParams.letterSpacing,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (endingNumber != null) {
                Text(
                    text = " $endingNumber",
                    fontSize = titleParams.fontSize,
                    lineHeight = titleParams.lineHeight,
                    letterSpacing = titleParams.letterSpacing,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    val badgesOverlay: @Composable BoxScope.() -> Unit = {
        if (coverBadgeStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = coverBadgeStart,
            )
        }
        if (coverBadgeEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = coverBadgeEnd,
            )
        }
    }

    when (style) {
        NormalCardStyle.MODERN -> {
            Card(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                ),
                            ),
                    )
                    badgesOverlay()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        titleRow(Color.White, 2)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = subtitleFontSize,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        NormalCardStyle.EXOTIC -> {
            Card(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                ),
                            ),
                    )
                    badgesOverlay()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        titleRow(Color.White, 2)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = subtitleFontSize,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        NormalCardStyle.MINIMAL_EXOTIC -> {
            Box(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick),
            ) {
                MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                            ),
                        ),
                )
                badgesOverlay()
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                        titleRow(MaterialTheme.colorScheme.onSurface, 2)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = subtitleFontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        NormalCardStyle.BLUR -> {
            Card(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                    badgesOverlay()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            titleRow(MaterialTheme.colorScheme.onSurface, 2)
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    fontSize = subtitleFontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        NormalCardStyle.MATERIAL -> {
            OutlinedCard(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                        badgesOverlay()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                    ) {
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = subtitleFontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        titleRow(MaterialTheme.colorScheme.onSurface, 2)
                    }
                }
            }
        }
        NormalCardStyle.LIQUID_GLASS -> {
            Box(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.50f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ),
                        ),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onClick),
            ) {
                MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                ),
                            ),
                        ),
                )
                badgesOverlay()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.25f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        titleRow(MaterialTheme.colorScheme.onSurface, 2)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = subtitleFontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick),
            ) {
                MangaCover.Book(data = coverData, modifier = Modifier.fillMaxSize())
                badgesOverlay()
            }
        }
    }
}
