package eu.kanade.translation.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.PageTranslation

class WebtoonTranslationsView :
    AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = PageTranslation.EMPTY
        this.font = TranslationFont.ANIME_ACE
        this.fontFamily = Font(
            resId = font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translation: PageTranslation,
        font: TranslationFont? = null,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = translation
        this.font = font ?: TranslationFont.ANIME_ACE
        this.fontFamily = Font(
            resId = this.font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
    }

    @Composable
    override fun Content() {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) {
                        hide()
                    } else {
                        show()
                    }
                },
        ) {
            if (size == IntSize.Zero) return
            val scaleFactor = size.width / translation.imgWidth
            TextBlockBackground(scaleFactor)
            TextBlockContent(scaleFactor)
        }
    }

    @Composable
    fun TextBlockBackground(scaleFactor: Float) {
        translation.blocks.forEach { block ->
            val isVertical = block.angle > 85f || (block.height > block.width * 1.3f)
            val padX = block.symWidth * 2
            val padY = block.symHeight
            val centroidX = block.x + block.width / 2f
            val centroidY = block.y + block.height / 2f
            val rawWidth = if (isVertical) {
                maxOf(block.width + padX, (block.height + padY) * 0.65f)
            } else {
                block.width + padX
            }
            val rawHeight = if (isVertical) {
                val area = (block.width + padX) * (block.height + padY)
                maxOf(area / rawWidth, (block.height + padY) * 0.3f)
            } else {
                block.height + padY
            }
            val bgX = maxOf(centroidX * scaleFactor - rawWidth * scaleFactor / 2f, 0f)
            val bgY = maxOf(centroidY * scaleFactor - rawHeight * scaleFactor / 2f, 0f)
            Box(
                modifier = Modifier
                    .offset(bgX.pxToDp(), bgY.pxToDp())
                    .size((rawWidth * scaleFactor).pxToDp(), (rawHeight * scaleFactor).pxToDp())
                    .background(Color.White, shape = RoundedCornerShape(4.dp)),
            )
        }
    }

    @Composable
    fun TextBlockContent(scaleFactor: Float) {
        translation.blocks.forEach { block ->
            SmartTranslationBlock(
                block = block,
                scaleFactor = scaleFactor,
                fontFamily = fontFamily,
            )
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}
