package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,
) {
    // Vertical: tall-narrow Japanese column. Horizontal: wide narration/sfx box.
    val isVertical = block.angle > 85f || (block.height > block.width * 1.3f)

    val padX = block.symWidth * 2
    val padY = block.symHeight

    // Centroid of original block
    val centroidX = block.x + block.width / 2f
    val centroidY = block.y + block.height / 2f

    // For vertical blocks: expand width so horizontal English words fit without letter-splitting
    val rawWidth = if (isVertical) {
        max(block.width + padX, (block.height + padY) * 0.65f)
    } else {
        block.width + padX
    }
    val rawHeight = if (isVertical) {
        // Shrink height proportionally to preserve approximate area
        val area = (block.width + padX) * (block.height + padY)
        max(area / rawWidth, (block.height + padY) * 0.3f)
    } else {
        block.height + padY
    }

    // Anchor expanded box to original centroid
    val xPx = max(centroidX * scaleFactor - rawWidth * scaleFactor / 2f, 0f)
    val yPx = max(centroidY * scaleFactor - rawHeight * scaleFactor / 2f, 0f)
    val width = (rawWidth * scaleFactor).pxToDp()
    val height = (rawHeight * scaleFactor).pxToDp()

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.CenterStart, true)
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(width, height),
    ) {
        val density = LocalDensity.current
        val fontSize = remember { mutableStateOf(16.sp) }
        SubcomposeLayout { constraints ->
            val maxWidthPx = with(density) { width.roundToPx() }
            val maxHeightPx = with(density) { height.roundToPx() }

            // Binary search for optimal font size
            var low = 1
            var high = 100
            var bestSize = low

            while (low <= high) {
                val mid = (low + high) / 2
                val measured = subcompose(mid.sp) {
                    Text(
                        text = block.translation,
                        fontSize = mid.sp,
                        fontFamily = fontFamily,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center,
                        maxLines = Int.MAX_VALUE,
                        softWrap = true,
                        modifier = Modifier
                            .width(width)
                            .rotate(if (isVertical) 0f else block.angle),
                    )
                }[0].measure(Constraints(maxWidth = maxWidthPx))

                if (measured.height <= maxHeightPx) {
                    bestSize = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            fontSize.value = bestSize.sp

            // Render: white outline stroke first, black fill on top
            val textMod = Modifier
                .width(width)
                .rotate(if (isVertical) 0f else block.angle)
                .align(Alignment.Center)

            val outlinePlaceable = subcompose("outline") {
                Text(
                    text = block.translation,
                    fontSize = fontSize.value,
                    fontFamily = fontFamily,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    maxLines = Int.MAX_VALUE,
                    style = TextStyle(
                        drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
                        color = Color.White,
                        fontSize = fontSize.value,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = textMod,
                )
            }[0].measure(constraints)

            val textPlaceable = subcompose(Unit) {
                Text(
                    text = block.translation,
                    fontSize = fontSize.value,
                    fontFamily = fontFamily,
                    color = Color.Black,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    maxLines = Int.MAX_VALUE,
                    modifier = textMod,
                )
            }[0].measure(constraints)

            layout(textPlaceable.width, textPlaceable.height) {
                outlinePlaceable.place(0, 0)
                textPlaceable.place(0, 0)
            }
        }
    }
}
