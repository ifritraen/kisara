package eu.kanade.tachiyomi.ui.reader.domain

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

data class ReaderColorFilter(
    val brightness: Float = 0.0f,       // -1.0f .. 1.0f (0.0 = default)
    val contrast: Float = 0.0f,         // -1.0f .. 1.0f (0.0 = default)
    val saturation: Float = 1.0f,       // 0.0f .. 2.0f (1.0 = default)
    val gamma: Float = 1.0f,            // 0.5f .. 2.0f (1.0 = default)
    val blackLevel: Float = 0.0f,       // 0.0f .. 0.5f (0.0 = default)
    val whiteLevel: Float = 1.0f,       // 0.5f .. 1.0f (1.0 = default)
    val warmth: Float = 0.0f,           // 0.0f .. 1.0f (0.0 = default)
    val customTint: Int? = null,        // ARGB color or null
    val customTintOpacity: Float = 0.3f, // 0.0f .. 1.0f
    val isInverted: Boolean = false,
    val isGrayscale: Boolean = false,
    val isBookBackground: Boolean = false,
) {

    val isEmpty: Boolean
        get() = !isGrayscale &&
            !isInverted &&
            !isBookBackground &&
            brightness == 0f &&
            contrast == 0f &&
            saturation == 1.0f &&
            gamma == 1.0f &&
            blackLevel == 0.0f &&
            whiteLevel == 1.0f &&
            warmth == 0.0f &&
            (customTint == null || customTintOpacity == 0f)

    fun toColorFilter(): ColorMatrixColorFilter? {
        if (isEmpty) return null

        val cm = ColorMatrix()

        // 1. Grayscale
        if (isGrayscale) {
            cm.setSaturation(0f)
        } else if (saturation != 1.0f) {
            // 2. Custom Saturation
            cm.setSaturation(saturation.coerceIn(0f, 2f))
        }

        // 3. Inversion
        if (isInverted) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                    0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                    0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                ),
            )
            cm.postConcat(invertMatrix)
        }

        // 4. Brightness
        if (brightness != 0f) {
            val scale = brightness + 1f
            val brightnessMatrix = ColorMatrix()
            brightnessMatrix.setScale(scale, scale, scale, 1f)
            cm.postConcat(brightnessMatrix)
        }

        // 5. Contrast
        if (contrast != 0f) {
            val scale = contrast + 1f
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            cm.postConcat(contrastMatrix)
        }

        // 6. Gamma Adjustment
        if (gamma != 1.0f && gamma > 0f) {
            // Midtone curve scaling approximation
            val invGamma = 1f / gamma
            val gammaMatrix = ColorMatrix(
                floatArrayOf(
                    invGamma, 0f, 0f, 0f, (1f - invGamma) * 128f,
                    0f, invGamma, 0f, 0f, (1f - invGamma) * 128f,
                    0f, 0f, invGamma, 0f, (1f - invGamma) * 128f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            cm.postConcat(gammaMatrix)
        }

        // 7. Black & White Line-Art Cleanup Levels
        if (blackLevel > 0f || whiteLevel < 1.0f) {
            val range = (whiteLevel - blackLevel).coerceAtLeast(0.05f)
            val scale = 1f / range
            val translate = -blackLevel * 255f * scale
            val levelMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            cm.postConcat(levelMatrix)
        }

        // 8. Classic Book Warmth
        if (isBookBackground) {
            val bookMatrix = ColorMatrix(
                floatArrayOf(
                    1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.92f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                ),
            )
            cm.postConcat(bookMatrix)
        } else if (warmth > 0f) {
            // 9. Smooth Custom Warmth Slider (Red/Yellow boost, Blue suppression)
            val blueFactor = 1.0f - (warmth * 0.25f)
            val redFactor = 1.0f + (warmth * 0.08f)
            val warmthMatrix = ColorMatrix(
                floatArrayOf(
                    redFactor, 0.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, blueFactor, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
                ),
            )
            cm.postConcat(warmthMatrix)
        }

        // 10. Custom Color Tint
        if (customTint != null && customTintOpacity > 0f) {
            val r = Color.red(customTint) / 255f
            val g = Color.green(customTint) / 255f
            val b = Color.blue(customTint) / 255f
            val a = customTintOpacity.coerceIn(0f, 1f)
            val invA = 1f - a

            val tintMatrix = ColorMatrix(
                floatArrayOf(
                    invA + a * r, 0f, 0f, 0f, a * r * 255f,
                    0f, invA + a * g, 0f, 0f, a * g * 255f,
                    0f, 0f, invA + a * b, 0f, a * b * 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            cm.postConcat(tintMatrix)
        }

        return ColorMatrixColorFilter(cm)
    }

    companion object {
        val DEFAULT = ReaderColorFilter()
    }
}
