package eu.kanade.tachiyomi.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * High-performance image conversion and color-space manipulation utilities
 * for on-device ONNX neural networks.
 *
 * Implements CIE-Lab <-> sRGB color conversions, NCHW/HWC buffer transpositions,
 * Gaussian blur, and high-precision luminance preservation for Manga Colorization.
 */
object ColorizeImageUtils {

    /**
     * Converts an ARGB_8888 [Bitmap] into an NCHW [FloatBuffer] formatted for DeOldify / Manga-Colorization-v2.
     * Expects 3-channel BGR float inputs with value range [0..255] (or normalized [0..1] if normalized = true).
     */
    fun bitmapToNchwFloatBuffer(
        bitmap: Bitmap,
        targetWidth: Int = bitmap.width,
        targetHeight: Int = bitmap.height,
        normalized: Boolean = false,
        isBgr: Boolean = true,
    ): FloatBuffer {
        val scaled = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val buffer = FloatBuffer.allocate(3 * h * w)
        val plane = h * w
        val divisor = if (normalized) 255.0f else 1.0f

        // Channel 0 (B if isBgr, R if RGB)
        for (i in 0 until plane) {
            val px = pixels[i]
            val c = if (isBgr) Color.blue(px) else Color.red(px)
            buffer.put(i, c.toFloat() / divisor)
        }

        // Channel 1 (G)
        for (i in 0 until plane) {
            val px = pixels[i]
            val c = Color.green(px)
            buffer.put(plane + i, c.toFloat() / divisor)
        }

        // Channel 2 (R if isBgr, B if RGB)
        for (i in 0 until plane) {
            val px = pixels[i]
            val c = if (isBgr) Color.red(px) else Color.blue(px)
            buffer.put(2 * plane + i, c.toFloat() / divisor)
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Converts a single-channel Grayscale/Luminance [Bitmap] into an NCHW [FloatBuffer] with shape [1, 1, H, W].
     * Used for Anime4K ACNet luma upscaling.
     */
    fun bitmapToLumaNchwFloatBuffer(
        bitmap: Bitmap,
        targetWidth: Int = bitmap.width,
        targetHeight: Int = bitmap.height,
    ): FloatBuffer {
        val scaled = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val buffer = FloatBuffer.allocate(h * w)
        for (i in 0 until (h * w)) {
            val px = pixels[i]
            // Standard Rec.601 / BT.709 Luma: Y = 0.299R + 0.587G + 0.114B
            val y = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255.0f
            buffer.put(i, y.coerceIn(0.0f, 1.0f))
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Reconstructs an ARGB_8888 [Bitmap] by splicing the original high-resolution luminance (L)
     * with the predicted chrominance (a, b) channels.
     *
     * @param originalBitmap The high-resolution original black-and-white/grayscale manga page.
     * @param predA Float array of predicted 'a' channel values at model resolution (e.g. 256x256), range [-128..127].
     * @param predB Float array of predicted 'b' channel values at model resolution (e.g. 256x256), range [-128..127].
     * @param modelW Model prediction width (e.g. 256).
     * @param modelH Model prediction height (e.g. 256).
     * @param intensity Color saturation scaling multiplier (0.3x .. 1.5x, default 1.0x).
     */
    fun spliceLabColorization(
        originalBitmap: Bitmap,
        predA: FloatArray,
        predB: FloatArray,
        modelW: Int,
        modelH: Int,
        intensity: Float = 1.0f,
    ): Bitmap {
        val origW = originalBitmap.width
        val origH = originalBitmap.height
        val origPixels = IntArray(origW * origH)
        originalBitmap.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

        // Smooth predicted a/b slightly with 3x3 box/gaussian filter to eliminate tile boundaries
        val smoothedA = boxBlur2D(predA, modelW, modelH)
        val smoothedB = boxBlur2D(predB, modelW, modelH)

        val outPixels = IntArray(origW * origH)
        val scaleX = (modelW - 1).toFloat() / max(1, origW - 1)
        val scaleY = (modelH - 1).toFloat() / max(1, origH - 1)

        for (y in 0 until origH) {
            val srcY = y * scaleY
            val y0 = srcY.toInt().coerceIn(0, modelH - 2)
            val y1 = y0 + 1
            val dy = srcY - y0

            for (x in 0 until origW) {
                val srcX = x * scaleX
                val x0 = srcX.toInt().coerceIn(0, modelW - 2)
                val x1 = x0 + 1
                val dx = srcX - x0

                // Bilinear interpolation for predicted (a, b)
                val a00 = smoothedA[y0 * modelW + x0]
                val a10 = smoothedA[y0 * modelW + x1]
                val a01 = smoothedA[y1 * modelW + x0]
                val a11 = smoothedA[y1 * modelW + x1]
                val aVal = (a00 * (1f - dx) * (1f - dy) + a10 * dx * (1f - dy) + a01 * (1f - dx) * dy + a11 * dx * dy) * intensity

                val b00 = smoothedB[y0 * modelW + x0]
                val b10 = smoothedB[y0 * modelW + x1]
                val b01 = smoothedB[y1 * modelW + x0]
                val b11 = smoothedB[y1 * modelW + x1]
                val bVal = (b00 * (1f - dx) * (1f - dy) + b10 * dx * (1f - dy) + b01 * (1f - dx) * dy + b11 * dx * dy) * intensity

                // Extract exact original high-res luminance from raw manga pixel
                val origPx = origPixels[y * origW + x]
                val r = Color.red(origPx)
                val g = Color.green(origPx)
                val b = Color.blue(origPx)

                val origLabL = rgbToLabL(r, g, b)

                // Convert merged LAB (origLabL, aVal, bVal) back to sRGB
                val rgb = labToRgb(origLabL, aVal, bVal)
                outPixels[y * origW + x] = rgb
            }
        }

        val outBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, origW, 0, 0, origW, origH)
        return outBitmap
    }

    /**
     * Extracts only the 'L' component in CIE-Lab [0..100] from sRGB [0..255].
     */
    private fun rgbToLabL(r: Int, g: Int, b: Int): Float {
        val rNorm = pivotRgb(r / 255.0f)
        val gNorm = pivotRgb(g / 255.0f)
        val bNorm = pivotRgb(b / 255.0f)

        // CIE D65 standard illuminant Y
        val y = 0.2126729f * rNorm + 0.7151522f * gNorm + 0.0721750f * bNorm
        val fy = pivotXyz(y / 1.0f)
        return max(0.0f, (116.0f * fy) - 16.0f)
    }

    /**
     * Converts CIE-Lab (L: [0..100], a: [-128..127], b: [-128..127]) to packed ARGB_8888 Int.
     */
    private fun labToRgb(l: Float, a: Float, b: Float): Int {
        val fy = (l + 16.0f) / 116.0f
        val fx = a / 500.0f + fy
        val fz = fy - b / 200.0f

        // D65 reference white point (Xn = 0.95047, Yn = 1.00000, Zn = 1.08883)
        val x = 0.95047f * unpivotXyz(fx)
        val y = 1.00000f * unpivotXyz(fy)
        val z = 1.08883f * unpivotXyz(fz)

        // XYZ to sRGB matrix
        var r = x * 3.2404542f + y * -1.5371385f + z * -0.4985314f
        var g = x * -0.9692660f + y * 1.8760108f + z * 0.0415560f
        var bl = x * 0.0556434f + y * -0.2040259f + z * 1.0572252f

        r = unpivotRgb(r) * 255.0f
        g = unpivotRgb(g) * 255.0f
        bl = unpivotRgb(bl) * 255.0f

        val rClamped = r.roundToInt().coerceIn(0, 255)
        val gClamped = g.roundToInt().coerceIn(0, 255)
        val bClamped = bl.roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (rClamped shl 16) or (gClamped shl 8) or bClamped
    }

    private fun pivotRgb(n: Float): Float {
        return if (n > 0.04045f) ((n + 0.055f) / 1.055f).pow(2.4f) else n / 12.92f
    }

    private fun unpivotRgb(n: Float): Float {
        return if (n > 0.0031308f) 1.055f * n.pow(1.0f / 2.4f) - 0.055f else 12.92f * n
    }

    private fun pivotXyz(n: Float): Float {
        val t = 0.008856f
        return if (n > t) n.pow(1.0f / 3.0f) else (7.787f * n) + (16.0f / 116.0f)
    }

    private fun unpivotXyz(n: Float): Float {
        val t = 0.20689655f // 6/29
        return if (n > t) n.pow(3.0f) else (n - (16.0f / 116.0f)) / 7.787f
    }

    private fun boxBlur2D(src: FloatArray, w: Int, h: Int): FloatArray {
        val dst = FloatArray(w * h)
        for (y in 0 until h) {
            val ym1 = max(0, y - 1)
            val yp1 = min(h - 1, y + 1)
            for (x in 0 until w) {
                val xm1 = max(0, x - 1)
                val xp1 = min(w - 1, x + 1)

                var sum = 0f
                sum += src[ym1 * w + xm1] + src[ym1 * w + x] + src[ym1 * w + xp1]
                sum += src[y * w + xm1] + src[y * w + x] + src[y * w + xp1]
                sum += src[yp1 * w + xm1] + src[yp1 * w + x] + src[yp1 * w + xp1]
                dst[y * w + x] = sum / 9.0f
            }
        }
        return dst
    }
}
