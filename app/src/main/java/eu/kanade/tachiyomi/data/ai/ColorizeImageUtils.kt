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
 * Implements both options from colorizer_bug_findings.md:
 * - Option A: CIE-Lab chrominance splicing with AB saturation amplification (1.0x - 30.0x)
 * - Option B: Direct RGB line-art blend with dark ink multiplication
 */
object ColorizeImageUtils {

    enum class PipelineMode {
        DIRECT_RGB_BLEND,  // Option B: Direct full RGB with line-art ink multiplication
        LAB_SPLICING,      // Option A: CIE-LAB splicing with AB amplification
        TUNED_PRESET,      // Full Komikku/Catmikku tuning filter pipeline with presets (Default)
    }

    enum class PerformanceTier(val level: Int, val title: String, val description: String) {
        LOW(1, "Low", "2 CPU threads • Coolest & Lowest Battery"),
        MID(2, "Mid", "3 CPU threads • Balanced Efficiency"),
        HIGH(3, "High", "4 Big CPU Cores • Optimal Fast (Sweet Spot)"),
        EXTREME(4, "Extreme", "Max Cores • Maximum Multi-core Throughput");

        companion object {
            fun fromLevel(level: Int): PerformanceTier {
                return entries.find { it.level == level } ?: HIGH
            }
        }
    }

    /**
     * Hardware Performance & Speed Scaling Profile (4 Levels: Low, Mid, High, Extreme).
     * Optimal multi-threading on ARM big.LITTLE architectures without driver/IPC overhead.
     */
    data class PerformanceConfig(
        val tier: PerformanceTier,
        val cpuThreads: Int,
        val chunkWorkers: Int,
        val label: String,
        val description: String,
    ) {
        companion object {
            fun fromLevel(level: Int): PerformanceConfig {
                val tier = PerformanceTier.fromLevel(level)
                val totalCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
                return when (tier) {
                    PerformanceTier.LOW -> PerformanceConfig(
                        tier = tier,
                        cpuThreads = 2,
                        chunkWorkers = 2,
                        label = "Low (Battery Saver)",
                        description = "2 CPU cores, lowest battery and thermal footprint",
                    )
                    PerformanceTier.MID -> PerformanceConfig(
                        tier = tier,
                        cpuThreads = 3.coerceAtMost(totalCores),
                        chunkWorkers = 3.coerceAtMost(totalCores),
                        label = "Mid (Balanced)",
                        description = "3 CPU cores, steady and balanced performance",
                    )
                    PerformanceTier.HIGH -> PerformanceConfig(
                        tier = tier,
                        cpuThreads = 4.coerceAtMost(totalCores),
                        chunkWorkers = 4.coerceAtMost(totalCores),
                        label = "High (Optimal Fast)",
                        description = "4 Big CPU cores (Optimal sweet spot without core contention)",
                    )
                    PerformanceTier.EXTREME -> PerformanceConfig(
                        tier = tier,
                        cpuThreads = totalCores.coerceIn(4, 8),
                        chunkWorkers = totalCores.coerceIn(4, 8),
                        label = "Extreme (Max Turbo)",
                        description = "All $totalCores CPU cores running in parallel",
                    )
                }
            }
        }
    }

    /**
     * Converts a [Bitmap] into a 5-channel NCHW [FloatBuffer] formatted for Manga-Colorization-v2:
     * - Channel 0: Grayscale luminance in range [0..1]
     * - Channels 1..3: Color hint RGB channels (0.0f when unprompted)
     * - Channel 4: Hint mask (0.0f when unprompted)
     */
    fun bitmapToMangaColorizerV2NchwFloatBuffer(
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

        val plane = h * w
        val buffer = AiBufferUtils.allocateDirectFloatBuffer(5 * plane)

        // Channel 0 (Grayscale in 0.0 .. 1.0)
        for (i in 0 until plane) {
            val px = pixels[i]
            val gray = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255.0f
            buffer.put(i, gray.coerceIn(0.0f, 1.0f))
        }

        // Channels 1..4 (all 0.0f for automatic manga colorization)
        for (i in plane until (5 * plane)) {
            buffer.put(i, 0.0f)
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Converts a [Bitmap] into a 3-channel NCHW [FloatBuffer] for DeOldify:
     * Expects 3-channel BGR float inputs with value range [0..255].
     */
    fun bitmapToDeOldifyNchwFloatBuffer(
        bitmap: Bitmap,
        targetWidth: Int = 256,
        targetHeight: Int = 256,
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

        val plane = h * w
        val buffer = AiBufferUtils.allocateDirectFloatBuffer(3 * plane)

        // Channel 0 (B), Channel 1 (G), Channel 2 (R) in [0..255]
        for (i in 0 until plane) {
            val px = pixels[i]
            buffer.put(i, Color.blue(px).toFloat())
            buffer.put(plane + i, Color.green(px).toFloat())
            buffer.put(2 * plane + i, Color.red(px).toFloat())
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        buffer.rewind()
        return buffer
    }

    // ------------------------------------------------------------------------
    // OPTION A: CIE-LAB Chrominance Splicing with Saturation Amplification
    // ------------------------------------------------------------------------

    /**
     * Slices the model's low-res RGB prediction into CIE-LAB (a, b) chrominance,
     * amplifies them by [intensity] around neutral (0.0), and merges them with
     * the original high-resolution black & white page's L (luminance) channel.
     *
     * This compensates for ARM CPU FP16 color compression and preserves 100%
     * of original manga line art sharpness.
     */
    fun spliceLabColorization(
        originalBitmap: Bitmap,
        rawModelRgbBitmap: Bitmap,
        intensity: Float = 1.0f,
    ): Bitmap {
        val modelW = rawModelRgbBitmap.width
        val modelH = rawModelRgbBitmap.height
        val modelPlane = modelW * modelH

        val modelPixels = IntArray(modelPlane)
        rawModelRgbBitmap.getPixels(modelPixels, 0, modelW, 0, 0, modelW, modelH)

        val predA = FloatArray(modelPlane)
        val predB = FloatArray(modelPlane)

        for (i in 0 until modelPlane) {
            val px = modelPixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val lab = rgbToLab(r, g, b)
            predA[i] = lab[1]
            predB[i] = lab[2]
        }

        val origW = originalBitmap.width
        val origH = originalBitmap.height
        val origPixels = IntArray(origW * origH)
        originalBitmap.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

        // 3x3 filter smoothing to eliminate blocky tile boundaries
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
                outPixels[y * origW + x] = labToRgb(origLabL, aVal, bVal)
            }
        }

        val outBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, origW, 0, 0, origW, origH)
        return outBitmap
    }

    // ------------------------------------------------------------------------
    // OPTION B: Direct Full RGB Line-Art Blend
    // ------------------------------------------------------------------------

    /**
     * Preserves 100% of the model's raw RGB prediction (skin, hair, sky, clothing)
     * on light paper, while multiplying original dark ink lines (`origLum < inkThreshold`)
     * to keep lines razor-sharp black.
     */
    fun blendDirectRgbWithLineArt(
        originalBitmap: Bitmap,
        rawModelRgbBitmap: Bitmap,
        inkThreshold: Float = 0.15f,
        saturationBoost: Float = 1.0f,
    ): Bitmap {
        val origW = originalBitmap.width
        val origH = originalBitmap.height

        val scaledModel = if (rawModelRgbBitmap.width != origW || rawModelRgbBitmap.height != origH) {
            Bitmap.createScaledBitmap(rawModelRgbBitmap, origW, origH, true)
        } else {
            rawModelRgbBitmap
        }

        val origPixels = IntArray(origW * origH)
        originalBitmap.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

        val modelPixels = IntArray(origW * origH)
        scaledModel.getPixels(modelPixels, 0, origW, 0, 0, origW, origH)

        val outPixels = IntArray(origW * origH)
        val thresh = inkThreshold.coerceIn(0.01f, 0.99f)

        for (i in 0 until (origW * origH)) {
            val origPx = origPixels[i]
            val origLum = (0.299f * Color.red(origPx) + 0.587f * Color.green(origPx) + 0.114f * Color.blue(origPx)) / 255.0f

            val modelPx = modelPixels[i]
            var mr = Color.red(modelPx).toFloat()
            var mg = Color.green(modelPx).toFloat()
            var mb = Color.blue(modelPx).toFloat()

            // Optional Saturation multiplier
            if (saturationBoost != 1.0f) {
                val modelLum = 0.299f * mr + 0.587f * mg + 0.114f * mb
                mr = (modelLum + (mr - modelLum) * saturationBoost).coerceIn(0f, 255f)
                mg = (modelLum + (mg - modelLum) * saturationBoost).coerceIn(0f, 255f)
                mb = (modelLum + (mb - modelLum) * saturationBoost).coerceIn(0f, 255f)
            }

            // Multiply sharp original dark ink lines onto model colors
            val finalR: Int
            val finalG: Int
            val finalB: Int
            if (origLum < thresh) {
                val inkFactor = origLum / thresh
                finalR = (mr * inkFactor).roundToInt().coerceIn(0, 255)
                finalG = (mg * inkFactor).roundToInt().coerceIn(0, 255)
                finalB = (mb * inkFactor).roundToInt().coerceIn(0, 255)
            } else {
                finalR = mr.roundToInt().coerceIn(0, 255)
                finalG = mg.roundToInt().coerceIn(0, 255)
                finalB = mb.roundToInt().coerceIn(0, 255)
            }

            outPixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }

        if (scaledModel != rawModelRgbBitmap) {
            scaledModel.recycle()
        }

        val outBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, origW, 0, 0, origW, origH)
        return outBitmap
    }

    // ------------------------------------------------------------------------
    // Color Space Conversions: sRGB <-> CIE-XYZ <-> CIE-LAB
    // ------------------------------------------------------------------------

    private fun rgbToLab(r: Int, g: Int, b: Int): FloatArray {
        val rNorm = pivotRgb(r / 255.0f)
        val gNorm = pivotRgb(g / 255.0f)
        val bNorm = pivotRgb(b / 255.0f)

        // sRGB D65 -> XYZ
        val x = rNorm * 0.4124564f + gNorm * 0.3575761f + bNorm * 0.1804375f
        val y = rNorm * 0.2126729f + gNorm * 0.7151522f + bNorm * 0.0721750f
        val z = rNorm * 0.0193339f + gNorm * 0.1191920f + bNorm * 0.9503041f

        // XYZ D65 -> Lab (D65 white: Xn=0.95047, Yn=1.0, Zn=1.08883)
        val fx = pivotXyz(x / 0.95047f)
        val fy = pivotXyz(y / 1.00000f)
        val fz = pivotXyz(z / 1.08883f)

        val l = max(0.0f, 116.0f * fy - 16.0f)
        val a = 500.0f * (fx - fy)
        val bLab = 200.0f * (fy - fz)
        return floatArrayOf(l, a, bLab)
    }

    private fun rgbToLabL(r: Int, g: Int, b: Int): Float {
        val rNorm = pivotRgb(r / 255.0f)
        val gNorm = pivotRgb(g / 255.0f)
        val bNorm = pivotRgb(b / 255.0f)
        val y = 0.2126729f * rNorm + 0.7151522f * gNorm + 0.0721750f * bNorm
        val fy = pivotXyz(y / 1.0f)
        return max(0.0f, (116.0f * fy) - 16.0f)
    }

    private fun labToRgb(l: Float, a: Float, b: Float): Int {
        val fy = (l + 16.0f) / 116.0f
        val fx = a / 500.0f + fy
        val fz = fy - b / 200.0f

        val x = 0.95047f * unpivotXyz(fx)
        val y = 1.00000f * unpivotXyz(fy)
        val z = 1.08883f * unpivotXyz(fz)

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

    /**
     * Tuning parameters and presets from Komikku/Catmikku.
     */
    data class ColorizerTuningParams(
        val skinHue: Int = 10,
        val skinSat: Int = 46,
        val blueCap: Int = 99,
        val redFlush: Int = 121,
        val skinMinLum: Int = 27,
        val paperThresh: Int = 100,
        val paperFeather: Int = 2,
        val gamma: Int = 60,
        val contrast: Int = 18,
        val blackFloor: Int = 9,
        val lineThresh: Int = 33,
        val lineExp: Int = 240,
        val satMul: Int = 155,
        val colorMix: Int = 105,
        val clarity: Int = 170,
    ) {
        companion object {
            val OPTIMIZED = ColorizerTuningParams(
                skinHue = 10, skinSat = 46, blueCap = 99, redFlush = 121, skinMinLum = 27,
                paperThresh = 100, paperFeather = 2, gamma = 60, contrast = 18, blackFloor = 9,
                lineThresh = 33, lineExp = 240, satMul = 155, colorMix = 105, clarity = 170,
            )
            val COLORFUL = ColorizerTuningParams(
                skinHue = 10, skinSat = 46, blueCap = 99, redFlush = 121, skinMinLum = 27,
                paperThresh = 100, paperFeather = 2, gamma = 68, contrast = 0, blackFloor = 9,
                lineThresh = 30, lineExp = 250, satMul = 125, colorMix = 115, clarity = 170,
            )
            val HEAVY_DARKISH = ColorizerTuningParams(
                skinHue = 10, skinSat = 46, blueCap = 99, redFlush = 130, skinMinLum = 27,
                paperThresh = 100, paperFeather = 2, gamma = 50, contrast = 50, blackFloor = 2,
                lineThresh = 30, lineExp = 250, satMul = 215, colorMix = 95, clarity = 150,
            )
            val BRIGHTER = ColorizerTuningParams(
                skinHue = 10, skinSat = 46, blueCap = 99, redFlush = 121, skinMinLum = 27,
                paperThresh = 100, paperFeather = 2, gamma = 80, contrast = -20, blackFloor = 9,
                lineThresh = 30, lineExp = 250, satMul = 130, colorMix = 110, clarity = 170,
            )
            val LESS_COLOR = ColorizerTuningParams(
                skinHue = 10, skinSat = 46, blueCap = 99, redFlush = 121, skinMinLum = 27,
                paperThresh = 100, paperFeather = 2, gamma = 62, contrast = 18, blackFloor = 9,
                lineThresh = 33, lineExp = 240, satMul = 120, colorMix = 95, clarity = 170,
            )
            val TESTING = ColorizerTuningParams(
                skinHue = 0, skinSat = 0, blueCap = 100, redFlush = 100, skinMinLum = 0,
                paperThresh = 100, paperFeather = 0, gamma = 100, contrast = 0, blackFloor = 0,
                lineThresh = 20, lineExp = 100, satMul = 100, colorMix = 100, clarity = 100,
            )
            val V20 = ColorizerTuningParams(
                skinHue = 15, skinSat = 50, blueCap = 90, redFlush = 130, skinMinLum = 28,
                paperThresh = 95, paperFeather = 5, gamma = 70, contrast = 15, blackFloor = 5,
                lineThresh = 28, lineExp = 200, satMul = 145, colorMix = 100, clarity = 140,
            )

            fun getPreset(index: Int): ColorizerTuningParams {
                return when (index) {
                    0 -> OPTIMIZED
                    1 -> COLORFUL
                    2 -> HEAVY_DARKISH
                    3 -> BRIGHTER
                    4 -> LESS_COLOR
                    5 -> V20
                    6 -> TESTING
                    else -> OPTIMIZED
                }
            }

            fun getPreset(name: String): ColorizerTuningParams {
                val clean = name.replace(" ", "_").replace("-", "_").uppercase()
                return when {
                    clean.contains("COLORFUL") -> COLORFUL
                    clean.contains("DARKISH") || clean.contains("HEAVY") -> HEAVY_DARKISH
                    clean.contains("BRIGHT") -> BRIGHTER
                    clean.contains("LESS") -> LESS_COLOR
                    clean.contains("V20") -> V20
                    clean.contains("TEST") -> TESTING
                    else -> OPTIMIZED
                }
            }
        }
    }

    /**
     * Complete Catmikku/Komikku Tuning Filter Pipeline:
     * - Blue / Slate suppression on character midtones
     * - Skin warmth diffusion & Saturation scaling
     * - Midtone Gamma & Contrast S-Curve
     * - Line Art Deepness & Inking
     * - Speech Bubble & Paper Whitening
     */
    fun applyTuningPipeline(
        originalBitmap: Bitmap,
        rawRgbBitmap: Bitmap,
        params: ColorizerTuningParams,
        intensity: Float = 1.0f,
        numThreads: Int = 4,
    ): Bitmap {
        val origW = originalBitmap.width
        val origH = originalBitmap.height

        val scaledColor = if (rawRgbBitmap.width != origW || rawRgbBitmap.height != origH) {
            Bitmap.createScaledBitmap(rawRgbBitmap, origW, origH, true)
        } else {
            rawRgbBitmap
        }

        val origPixels = IntArray(origW * origH)
        originalBitmap.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

        val colorPixels = IntArray(origW * origH)
        scaledColor.getPixels(colorPixels, 0, origW, 0, 0, origW, origH)

        val outPixels = IntArray(origW * origH)

        val skinHueNorm = (params.skinHue + 360) % 360 / 360.0f
        val skinSatNorm = params.skinSat / 100.0f
        val blueCapNorm = params.blueCap / 100.0f
        val redFlushMul = params.redFlush / 100.0f
        val skinMinLumNorm = params.skinMinLum / 100.0f
        val paperThreshVal = params.paperThresh / 100.0f
        val paperFeatherVal = params.paperFeather / 100.0f
        val gammaExp = if (params.gamma > 0) 1.0f / (params.gamma / 100.0f) else 1.0f
        val contrastNorm = params.contrast / 100.0f
        val lineThreshVal = params.lineThresh / 100.0f
        val lineExpVal = params.lineExp / 100.0f
        val satMulVal = (params.satMul / 100.0f) * intensity
        val colorMixVal = params.colorMix / 100.0f
        val blackFloorVal = params.blackFloor

        val actualThreads = numThreads.coerceIn(1, 16)
        if (actualThreads > 1) {
            val rowsPerThread = (origH + actualThreads - 1) / actualThreads
            val threads = (0 until actualThreads).map { threadIdx ->
                Thread {
                    val startY = threadIdx * rowsPerThread
                    val endY = min(origH, (threadIdx + 1) * rowsPerThread)
                    for (y in startY until endY) {
                        val rowOffset = y * origW
                        for (x in 0 until origW) {
                            val i = rowOffset + x
                            val origPx = origPixels[i]
                            val origLum = (0.299f * Color.red(origPx) + 0.587f * Color.green(origPx) + 0.114f * Color.blue(origPx)) / 255.0f

                            val colorPx = colorPixels[i]
                            var r = Color.red(colorPx).toFloat()
                            var g = Color.green(colorPx).toFloat()
                            var b = Color.blue(colorPx).toFloat()

                            // 1. Blue / Slate Suppression on Character Midtones
                            if (blueCapNorm < 1.0f && skinSatNorm > 0f && origLum >= skinMinLumNorm && origLum <= 0.88f) {
                                val maxRG = max(r, g)
                                val maxAllowedB = maxRG * blueCapNorm
                                if (b > maxAllowedB) {
                                    b = maxAllowedB
                                    r = min(255f, r * redFlushMul)
                                }
                            }

                            // 2. RGB to HSL
                            val rF = r / 255.0f
                            val gF = g / 255.0f
                            val bF = b / 255.0f
                            val maxC = max(rF, max(gF, bF))
                            val minC = min(rF, min(gF, bF))
                            val delta = maxC - minC
                            var l = (maxC + minC) / 2.0f
                            var s = 0.0f
                            var h = 0.0f

                            if (delta > 0.0001f) {
                                s = if (l <= 0.5f) delta / (maxC + minC) else delta / (2.0f - maxC - minC)
                                h = when (maxC) {
                                    rF -> ((gF - bF) / delta) % 6.0f
                                    gF -> ((bF - rF) / delta) + 2.0f
                                    else -> ((rF - gF) / delta) + 4.0f
                                } / 6.0f
                                if (h < 0f) h += 1.0f
                            }

                            // 3. Skin Warmth & Saturation Multiplier
                            val isCoolScreentone = (origLum >= skinMinLumNorm && origLum <= 0.78f) && (h in 0.52f..0.80f) && (s in 0.05f..0.22f)
                            if (isCoolScreentone && skinSatNorm > 0f) {
                                h = skinHueNorm
                                s = skinSatNorm
                            } else if (satMulVal != 1.0f) {
                                s = min(1.0f, s * satMulVal)
                            }

                            // 4. Midtone Gamma & Contrast S-Curve
                            if (gammaExp != 1.0f) {
                                l = l.pow(gammaExp)
                            }
                            if (contrastNorm != 0.0f) {
                                val sCurve = if (l < 0.5f) 2.0f * l * l else 1.0f - 2.0f * (1.0f - l).pow(2)
                                l = (1.0f - kotlin.math.abs(contrastNorm)) * l + kotlin.math.abs(contrastNorm) * (if (contrastNorm > 0f) sCurve else (l * 0.7f + 0.15f))
                            }

                            // HSL back to RGB
                            var outR = l
                            var outG = l
                            var outB = l
                            if (s > 0.0001f) {
                                val q = if (l < 0.5f) l * (1.0f + s) else l + s - l * s
                                val p = 2.0f * l - q
                                outR = hueToRgb(p, q, h + 1.0f / 3.0f)
                                outG = hueToRgb(p, q, h)
                                outB = hueToRgb(p, q, h - 1.0f / 3.0f)
                            }

                            // Color mix weighting
                            if (colorMixVal != 1.0f) {
                                outR = origLum + (outR - origLum) * colorMixVal
                                outG = origLum + (outG - origLum) * colorMixVal
                                outB = origLum + (outB - origLum) * colorMixVal
                            }

                            // 5. Line Art Deepness Sharpening
                            if (lineThreshVal > 0.001f && origLum < lineThreshVal) {
                                var factor = origLum / lineThreshVal
                                if (lineExpVal != 1.0f) {
                                    factor = factor.pow(lineExpVal)
                                }
                                outR *= factor
                                outG *= factor
                                outB *= factor
                            }

                            // 6. Speech Bubble & Paper Whitening
                            if (paperThreshVal < 0.999f && origLum >= (paperThreshVal - paperFeatherVal)) {
                                if (paperFeatherVal > 0.001f) {
                                    val blendW = min(1.0f, (origLum - (paperThreshVal - paperFeatherVal)) / paperFeatherVal)
                                    outR = outR * (1.0f - blendW) + 1.0f * blendW
                                    outG = outG * (1.0f - blendW) + 1.0f * blendW
                                    outB = outB * (1.0f - blendW) + 1.0f * blendW
                                } else {
                                    outR = 1.0f
                                    outG = 1.0f
                                    outB = 1.0f
                                }
                            }

                            // Black floor offset
                            if (blackFloorVal != 0) {
                                val bf = blackFloorVal / 255.0f
                                outR += bf
                                outG += bf
                                outB += bf
                            }

                            val finalR = (outR * 255.0f).roundToInt().coerceIn(0, 255)
                            val finalG = (outG * 255.0f).roundToInt().coerceIn(0, 255)
                            val finalB = (outB * 255.0f).roundToInt().coerceIn(0, 255)
                            outPixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                        }
                    }
                }.apply { start() }
            }
            threads.forEach { it.join() }
        } else {
            for (i in 0 until (origW * origH)) {
                val origPx = origPixels[i]
                val origLum = (0.299f * Color.red(origPx) + 0.587f * Color.green(origPx) + 0.114f * Color.blue(origPx)) / 255.0f

                val colorPx = colorPixels[i]
                var r = Color.red(colorPx).toFloat()
                var g = Color.green(colorPx).toFloat()
                var b = Color.blue(colorPx).toFloat()

                // 1. Blue / Slate Suppression on Character Midtones
                if (blueCapNorm < 1.0f && skinSatNorm > 0f && origLum >= skinMinLumNorm && origLum <= 0.88f) {
                    val maxRG = max(r, g)
                    val maxAllowedB = maxRG * blueCapNorm
                    if (b > maxAllowedB) {
                        b = maxAllowedB
                        r = min(255f, r * redFlushMul)
                    }
                }

                // 2. RGB to HSL
                val rF = r / 255.0f
                val gF = g / 255.0f
                val bF = b / 255.0f
                val maxC = max(rF, max(gF, bF))
                val minC = min(rF, min(gF, bF))
                val delta = maxC - minC
                var l = (maxC + minC) / 2.0f
                var s = 0.0f
                var h = 0.0f

                if (delta > 0.0001f) {
                    s = if (l <= 0.5f) delta / (maxC + minC) else delta / (2.0f - maxC - minC)
                    h = when (maxC) {
                        rF -> ((gF - bF) / delta) % 6.0f
                        gF -> ((bF - rF) / delta) + 2.0f
                        else -> ((rF - gF) / delta) + 4.0f
                    } / 6.0f
                    if (h < 0f) h += 1.0f
                }

                // 3. Skin Warmth & Saturation Multiplier
                val isCoolScreentone = (origLum >= skinMinLumNorm && origLum <= 0.78f) && (h in 0.52f..0.80f) && (s in 0.05f..0.22f)
                if (isCoolScreentone && skinSatNorm > 0f) {
                    h = skinHueNorm
                    s = skinSatNorm
                } else if (satMulVal != 1.0f) {
                    s = min(1.0f, s * satMulVal)
                }

                // 4. Midtone Gamma & Contrast S-Curve
                if (gammaExp != 1.0f) {
                    l = l.pow(gammaExp)
                }
                if (contrastNorm != 0.0f) {
                    val sCurve = if (l < 0.5f) 2.0f * l * l else 1.0f - 2.0f * (1.0f - l).pow(2)
                    l = (1.0f - kotlin.math.abs(contrastNorm)) * l + kotlin.math.abs(contrastNorm) * (if (contrastNorm > 0f) sCurve else (l * 0.7f + 0.15f))
                }

                // HSL back to RGB
                var outR = l
                var outG = l
                var outB = l
                if (s > 0.0001f) {
                    val q = if (l < 0.5f) l * (1.0f + s) else l + s - l * s
                    val p = 2.0f * l - q
                    outR = hueToRgb(p, q, h + 1.0f / 3.0f)
                    outG = hueToRgb(p, q, h)
                    outB = hueToRgb(p, q, h - 1.0f / 3.0f)
                }

                // Color mix weighting
                if (colorMixVal != 1.0f) {
                    outR = origLum + (outR - origLum) * colorMixVal
                    outG = origLum + (outG - origLum) * colorMixVal
                    outB = origLum + (outB - origLum) * colorMixVal
                }

                // 5. Line Art Deepness Sharpening
                if (lineThreshVal > 0.001f && origLum < lineThreshVal) {
                    var factor = origLum / lineThreshVal
                    if (lineExpVal != 1.0f) {
                        factor = factor.pow(lineExpVal)
                    }
                    outR *= factor
                    outG *= factor
                    outB *= factor
                }

                // 6. Speech Bubble & Paper Whitening
                if (paperThreshVal < 0.999f && origLum >= (paperThreshVal - paperFeatherVal)) {
                    if (paperFeatherVal > 0.001f) {
                        val blendW = min(1.0f, (origLum - (paperThreshVal - paperFeatherVal)) / paperFeatherVal)
                        outR = outR * (1.0f - blendW) + 1.0f * blendW
                        outG = outG * (1.0f - blendW) + 1.0f * blendW
                        outB = outB * (1.0f - blendW) + 1.0f * blendW
                    } else {
                        outR = 1.0f
                        outG = 1.0f
                        outB = 1.0f
                    }
                }

                // Black floor offset
                if (blackFloorVal != 0) {
                    val bf = blackFloorVal / 255.0f
                    outR += bf
                    outG += bf
                    outB += bf
                }

                val finalR = (outR * 255.0f).roundToInt().coerceIn(0, 255)
                val finalG = (outG * 255.0f).roundToInt().coerceIn(0, 255)
                val finalB = (outB * 255.0f).roundToInt().coerceIn(0, 255)
                outPixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        if (scaledColor != rawRgbBitmap) {
            scaledColor.recycle()
        }

        val resultBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(outPixels, 0, origW, 0, 0, origW, origH)
        return resultBitmap
    }

    private fun hueToRgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        if (t < 1f / 6f) return p + (q - p) * 6f * t
        if (t < 1f / 2f) return q
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f
        return p
    }

    data class ComparisonMetrics(
        val mae: Float,
        val similarityPercent: Float,
        val maxChannelDiff: Int,
        val meanCh0Diff: Float,
        val meanCh1Diff: Float,
        val meanCh2Diff: Float,
    )

    fun compareBitmaps(bmpA: Bitmap, bmpB: Bitmap): ComparisonMetrics {
        val w = min(bmpA.width, bmpB.width)
        val h = min(bmpA.height, bmpB.height)
        val scaledA = if (bmpA.width != w || bmpA.height != h) Bitmap.createScaledBitmap(bmpA, w, h, true) else bmpA
        val scaledB = if (bmpB.width != w || bmpB.height != h) Bitmap.createScaledBitmap(bmpB, w, h, true) else bmpB

        val pixA = IntArray(w * h)
        val pixB = IntArray(w * h)
        scaledA.getPixels(pixA, 0, w, 0, 0, w, h)
        scaledB.getPixels(pixB, 0, w, 0, 0, w, h)

        var totalDiff = 0.0
        var diffR = 0.0
        var diffG = 0.0
        var diffB = 0.0
        var maxDiff = 0

        val totalPixels = w * h
        for (i in 0 until totalPixels) {
            val cA = pixA[i]
            val cB = pixB[i]

            val dr = kotlin.math.abs(((cA shr 16) and 0xFF) - ((cB shr 16) and 0xFF))
            val dg = kotlin.math.abs(((cA shr 8) and 0xFF) - ((cB shr 8) and 0xFF))
            val db = kotlin.math.abs((cA and 0xFF) - (cB and 0xFF))

            diffR += dr
            diffG += dg
            diffB += db
            val pDiff = (dr + dg + db) / 3.0
            totalDiff += pDiff
            if (dr > maxDiff) maxDiff = dr
            if (dg > maxDiff) maxDiff = dg
            if (db > maxDiff) maxDiff = db
        }

        if (scaledA != bmpA) scaledA.recycle()
        if (scaledB != bmpB) scaledB.recycle()

        val mae = (totalDiff / totalPixels).toFloat()
        val sim = ((1.0 - (mae / 255.0)) * 100.0).toFloat().coerceIn(0f, 100f)

        return ComparisonMetrics(
            mae = mae,
            similarityPercent = sim,
            maxChannelDiff = maxDiff,
            meanCh0Diff = (diffR / totalPixels).toFloat(),
            meanCh1Diff = (diffG / totalPixels).toFloat(),
            meanCh2Diff = (diffB / totalPixels).toFloat(),
        )
    }
}
