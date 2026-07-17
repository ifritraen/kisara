package tachiyomi.presentation.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 2.dp)
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    textColor: Color = MaterialTheme.colorScheme.onSecondary,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val badgeBgColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        else -> color.copy(alpha = 0.85f)
    }
    val badgeTextColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> textColor
    }

    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(badgeBgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = badgeTextColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
    )
}

@Composable
fun Badge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    iconColor: Color = MaterialTheme.colorScheme.onSecondary,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val iconContentPlaceholder = "[icon]"
    val text = buildAnnotatedString {
        appendInlineContent(iconContentPlaceholder)
    }
    val inlineContent = persistentMapOf(
        Pair(
            iconContentPlaceholder,
            InlineTextContent(
                Placeholder(
                    width = MaterialTheme.typography.bodySmall.fontSize,
                    height = MaterialTheme.typography.bodySmall.fontSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Icon(
                    imageVector = imageVector,
                    tint = iconColor,
                    contentDescription = null,
                )
            },
        ),
    )

    val badgeBgColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        else -> color.copy(alpha = 0.85f)
    }
    val badgeTextColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> iconColor
    }

    Text(
        text = text,
        inlineContent = inlineContent,
        modifier = modifier
            .clip(shape)
            .background(badgeBgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = badgeTextColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
    )
}

// KMK -->
@Composable
fun Badge(
    painter: Painter,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    tint: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val badgeBgColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        else -> color.copy(alpha = 0.85f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(badgeBgColor),
    ) {
        Icon(
            painter = painter,
            tint = tint,
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Composable
fun Badge(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val badgeBgColor = when (color) {
        MaterialTheme.colorScheme.secondary -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        MaterialTheme.colorScheme.tertiary -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        else -> color.copy(alpha = 0.85f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(badgeBgColor),
    ) {
        Image(
            bitmap = imageBitmap,
            colorFilter = tint?.let { ColorFilter.tint(it) },
            contentDescription = null,
            modifier = modifier,
        )
    }
}
// KMK <--
