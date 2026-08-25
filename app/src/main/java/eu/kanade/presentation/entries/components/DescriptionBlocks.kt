package eu.kanade.presentation.entries.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.description.DescriptionBlock
import eu.kanade.presentation.theme.AuroraColors

data class DescriptionBlockStyle(
    val textColor: Color = Color.Unspecified,
    val headerColor: Color = Color.Unspecified,
    val keyColor: Color = Color.Unspecified,
    val valueColor: Color = Color.Unspecified,
)

@Composable
fun defaultDescriptionBlockStyle(): DescriptionBlockStyle {
    return DescriptionBlockStyle(
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        headerColor = MaterialTheme.colorScheme.onSurface,
        keyColor = MaterialTheme.colorScheme.primary,
        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

fun auroraDescriptionBlockStyle(colors: AuroraColors): DescriptionBlockStyle {
    return DescriptionBlockStyle(
        textColor = colors.textPrimary,
        headerColor = colors.textPrimary,
        keyColor = colors.accent,
        valueColor = colors.textSecondary,
    )
}

@Composable
fun DescriptionBlocks(
    blocks: List<DescriptionBlock>,
    modifier: Modifier = Modifier,
    style: DescriptionBlockStyle = defaultDescriptionBlockStyle(),
) {
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is DescriptionBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        color = style.textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is DescriptionBlock.Header -> {
                    Text(
                        text = block.text,
                        color = style.headerColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                is DescriptionBlock.KeyValue -> {
                    Text(
                        text = "${block.key}: ${block.value}",
                        color = style.textColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableDescriptionBlocks(
    blocks: List<DescriptionBlock>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    style: DescriptionBlockStyle = defaultDescriptionBlockStyle(),
    collapsedLines: Int = 5,
    onOverflowChanged: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .animateContentSize()
            .clickable { onToggle() },
    ) {
        val fullText = blocks.joinToString("\n\n") {
            when (it) {
                is DescriptionBlock.Paragraph -> it.text
                is DescriptionBlock.Header -> it.text
                is DescriptionBlock.KeyValue -> "${it.key}: ${it.value}"
            }
        }
        Text(
            text = fullText,
            color = style.textColor,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (!expanded) {
                    onOverflowChanged(textLayoutResult.hasVisualOverflow)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
