package eu.kanade.translation.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max

fun getBubbleBackgroundColor(bitmap: android.graphics.Bitmap?, block: TranslationBlock): Color {
    if (bitmap == null) return Color.White
    try {
        val w = bitmap.width
        val h = bitmap.height

        val points = listOf(
            Pair((block.x + 2).toInt(), (block.y + 2).toInt()),
            Pair((block.x + block.width - 3).toInt(), (block.y + 2).toInt()),
            Pair((block.x + 2).toInt(), (block.y + block.height - 3).toInt()),
            Pair((block.x + block.width - 3).toInt(), (block.y + block.height - 3).toInt()),
            Pair((block.x + block.width / 2).toInt(), (block.y + 2).toInt()),
            Pair((block.x + block.width / 2).toInt(), (block.y + block.height - 3).toInt()),
        )

        val colors = points.mapNotNull { (px, py) ->
            if (px in 0 until w && py in 0 until h) {
                bitmap.getPixel(px, py)
            } else {
                null
            }
        }

        if (colors.isEmpty()) return Color.White

        val bestColor = colors.minByOrNull { c1 ->
            colors.sumOf { c2 ->
                val rDiff = android.graphics.Color.red(c1) - android.graphics.Color.red(c2)
                val gDiff = android.graphics.Color.green(c1) - android.graphics.Color.green(c2)
                val bDiff = android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2)
                rDiff * rDiff + gDiff * gDiff + bDiff * bDiff
            }
        } ?: colors[0]

        return Color(bestColor)
    } catch (e: Exception) {
        return Color.White
    }
}

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    fontFamily: FontFamily,
    pageBitmap: android.graphics.Bitmap?,
) {
    val rawWidth = max(block.width, 10f)
    val rawHeight = max(block.height, 10f)

    val centroidX = block.x + block.width / 2f
    val centroidY = block.y + block.height / 2f

    val xPx = max(centroidX * scaleFactor - rawWidth * scaleFactor / 2f, 0f)
    val yPx = max(centroidY * scaleFactor - rawHeight * scaleFactor / 2f, 0f)
    val width = (rawWidth * scaleFactor).pxToDp()
    val height = (rawHeight * scaleFactor).pxToDp()

    val density = LocalDensity.current
    val bgColor = remember(pageBitmap, block) { getBubbleBackgroundColor(pageBitmap, block) }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.CenterStart, true)
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(width, height),
    ) {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = TextStyle(
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        )

        val maxWidthPx = with(density) { width.toPx() }
        val maxHeightPx = with(density) { height.toPx() }

        var low = 6
        var high = 36
        var bestSize = low
        var bestLayout: androidx.compose.ui.text.TextLayoutResult? = null

        val wordsToCheck = remember(block.translation) {
            block.translation.split(Regex("[\\s\\p{Punct}]+")).filter { it.length in 1..5 }
        }

        while (low <= high) {
            val mid = (low + high) / 2
            val midStyle = textStyle.copy(fontSize = mid.sp)
            val fiveCharWidth = textMeasurer.measure(
                text = "MMMMM",
                style = midStyle,
            ).size.width
            val measured = textMeasurer.measure(
                text = block.translation,
                style = midStyle,
                constraints = Constraints(maxWidth = max(maxWidthPx.toInt(), 1)),
            )
            val wordTooWide = wordsToCheck.any { word ->
                textMeasurer.measure(text = word, style = midStyle).size.width > maxWidthPx
            }
            if (measured.size.height <= maxHeightPx && fiveCharWidth <= maxWidthPx && !wordTooWide) {
                bestSize = mid
                bestLayout = measured
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val finalLayout = bestLayout ?: textMeasurer.measure(
            text = block.translation,
            style = textStyle.copy(fontSize = bestSize.sp),
            constraints = Constraints(maxWidth = max(maxWidthPx.toInt(), 1)),
        )

        Canvas(
            modifier = Modifier.requiredSize(width, height),
        ) {
            // Erase the original text block by covering the entire bounding box
            drawRect(
                color = bgColor,
                topLeft = Offset.Zero,
                size = size,
            )

            val padX = 6.dp.toPx()
            val padY = 3.dp.toPx()

            for (i in 0 until finalLayout.lineCount) {
                val lineLeft = finalLayout.getLineLeft(i)
                val lineRight = finalLayout.getLineRight(i)
                val lineTop = finalLayout.getLineTop(i)
                val lineBottom = finalLayout.getLineBottom(i)

                val lineWidth = lineRight - lineLeft
                if (lineWidth > 0) {
                    val rectLeft = (size.width - lineWidth) / 2f - padX
                    val rectTop = lineTop - padY
                    val rectRight = rectLeft + lineWidth + padX * 2
                    val rectBottom = lineBottom + padY

                    if (block.isBubble) {
                        drawOval(
                            color = bgColor,
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                        )
                    } else {
                        drawRoundRect(
                            color = bgColor,
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }

            drawContext.canvas.nativeCanvas.save()
            val layoutYOffset = (size.height - finalLayout.size.height) / 2f
            drawContext.canvas.nativeCanvas.translate(0f, layoutYOffset)

            drawText(
                textLayoutResult = finalLayout,
                color = Color.White,
                drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
            )
            drawText(
                textLayoutResult = finalLayout,
                color = Color.Black,
            )

            drawContext.canvas.nativeCanvas.restore()
        }
    }
}
