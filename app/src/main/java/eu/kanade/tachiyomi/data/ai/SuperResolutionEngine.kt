package eu.kanade.tachiyomi.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device AI Super-Resolution engine using ONNX Runtime.
 *
 * Implements Anime4K ACNet (2x Luma upscaling) and Real-ESRGAN Compact (2x-4x RGB upscaling).
 * Employs a fixed-size 384x384 tiling architecture with 16px overlap padding to eliminate
 * seam artifacts, bound peak memory under ~28MB, and maintain NNAPI graph stability.
 */
class SuperResolutionEngine(
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : AutoCloseable {

    private var activeSession: OrtSession? = null
    private var activeModelPath: String? = null
    private var isNnapiActive: Boolean = false

    @Synchronized
    private fun getSession(modelFile: File, useNnapi: Boolean): OrtSession {
        if (activeSession != null && activeModelPath == modelFile.absolutePath && isNnapiActive == useNnapi) {
            return activeSession!!
        }

        activeSession?.close()
        var session: OrtSession? = null
        var isNnapi = false

        val isAcnetLuma = modelFile.name.contains("acnet", ignoreCase = true)
        val channels = if (isAcnetLuma) 1 else 3

        if (useNnapi) {
            try {
                val nnapiOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    addNnapi()
                }
                val candidate = env.createSession(modelFile.absolutePath, nnapiOpts)
                if (verifyNnapiSanity(candidate, channels, 384)) {
                    session = candidate
                    isNnapi = true
                } else {
                    logcat(LogPriority.WARN) { "NNAPI SR probe failed sanity check, falling back to CPU" }
                    candidate.close()
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "NNAPI SR init failed, falling back to CPU" }
            }
        }

        if (session == null) {
            val cpuOpts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                addCPU(true)
            }
            session = env.createSession(modelFile.absolutePath, cpuOpts)
            isNnapi = false
        }

        activeSession = session
        activeModelPath = modelFile.absolutePath
        isNnapiActive = isNnapi
        return session
    }

    private fun verifyNnapiSanity(session: OrtSession, channels: Int, tileDim: Int): Boolean {
        return try {
            val probeBuffer = AiBufferUtils.allocateDirectFloatBuffer(channels * tileDim * tileDim)
            for (i in 0 until (channels * tileDim * tileDim)) {
                probeBuffer.put(i, 0.5f)
            }
            probeBuffer.rewind()
            val inputName = session.inputNames.iterator().next()
            val tensor = OnnxTensor.createTensor(
                env,
                probeBuffer,
                longArrayOf(1L, channels.toLong(), tileDim.toLong(), tileDim.toLong()),
            )
            val result = session.run(Collections.singletonMap(inputName, tensor))
            val out = result.get(0) as OnnxTensor
            val buf = out.floatBuffer
            var nanOrInf = false
            for (i in 0 until kotlin.math.min(buf.remaining(), 100)) {
                val v = buf.get(i)
                if (v.isNaN() || v.isInfinite()) {
                    nanOrInf = true
                    break
                }
            }
            tensor.close()
            result.close()
            !nanOrInf
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Upscales a manga page image using fixed-size tiling.
     *
     * @param inputBitmap The input manga page.
     * @param modelFile The local .onnx super-resolution model file.
     * @param scale Scaling factor (2 or 4).
     * @param useNnapi Hardware acceleration flag.
     * @param onTileProgress Optional callback for tile progress (currentTile, totalTiles).
     */
    suspend fun upscale(
        inputBitmap: Bitmap,
        modelFile: File,
        scale: Int = 2,
        useNnapi: Boolean = false,
        onTileProgress: ((currentTile: Int, totalTiles: Int) -> Unit)? = null,
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file does not exist: ${modelFile.absolutePath}")
        }

        val session = try {
            getSession(modelFile, useNnapi)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to initialize NNAPI session for SR, falling back to CPU" }
            getSession(modelFile, false)
        }

        val isAcnetLuma = modelFile.name.contains("acnet", ignoreCase = true)
        val channels = if (isAcnetLuma) 1 else 3

        val w = inputBitmap.width
        val h = inputBitmap.height

        val tileIn = 384
        val pad = 16
        val core = tileIn - 2 * pad // Effective step = 352
        val inputName = session.inputNames.iterator().next()

        // Calculate total tiles for progress tracking
        val numTilesX = (w + core - 1) / core
        val numTilesY = (h + core - 1) / core
        val totalTiles = numTilesX * numTilesY
        var currentTile = 0

        // Probe model output shape with a single dummy tile if needed, or dynamically on first tile
        var modelScale = if (isAcnetLuma) 2 else 4
        var rawOutW = w * modelScale
        var rawOutH = h * modelScale
        var outPixels = IntArray(rawOutW * rawOutH)
        var initializedScale = false

        // Reusable direct FloatBuffer for all tiles in this page to avoid memory churning
        val tensorBuffer = AiBufferUtils.allocateDirectFloatBuffer(channels * tileIn * tileIn)
        val plane = tileIn * tileIn

        var y = 0
        while (y < h) {
            val coreH = min(core, h - y)
            var x = 0
            while (x < w) {
                val coreW = min(core, w - x)

                currentTile++
                onTileProgress?.invoke(currentTile, totalTiles)

                // 1. Source bounds with padding
                val sx0 = max(0, x - pad)
                val sy0 = max(0, y - pad)
                val sx1 = min(w, x + coreW + pad)
                val sy1 = min(h, y + coreH + pad)

                val tileW = sx1 - sx0
                val tileH = sy1 - sy0

                // 2. Extract tile pixels and pad to exact tileIn x tileIn
                val tilePixels = IntArray(tileW * tileH)
                inputBitmap.getPixels(tilePixels, 0, tileW, sx0, sy0, tileW, tileH)

                val paddedTile = IntArray(tileIn * tileIn)
                val padLeft = max(0, pad - x)
                val padTop = max(0, pad - y)

                for (ty in 0 until tileH) {
                    for (tx in 0 until tileW) {
                        paddedTile[(padTop + ty) * tileIn + (padLeft + tx)] = tilePixels[ty * tileW + tx]
                    }
                }

                // 3. Prepare NCHW FloatBuffer for ONNX
                tensorBuffer.clear()
                if (channels == 1) {
                    for (i in 0 until plane) {
                        val px = paddedTile[i]
                        val luma = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px)) / 255.0f
                        tensorBuffer.put(i, luma)
                    }
                } else {
                    for (i in 0 until plane) {
                        val px = paddedTile[i]
                        tensorBuffer.put(i, Color.red(px) / 255.0f)
                        tensorBuffer.put(plane + i, Color.green(px) / 255.0f)
                        tensorBuffer.put(2 * plane + i, Color.blue(px) / 255.0f)
                    }
                }
                tensorBuffer.rewind()

                // 4. Run ONNX Inference on Tile
                val tensor = OnnxTensor.createTensor(
                    env,
                    tensorBuffer,
                    longArrayOf(1L, channels.toLong(), tileIn.toLong(), tileIn.toLong()),
                )
                val results = session.run(Collections.singletonMap(inputName, tensor))
                val outTensor = results.get(0) as OnnxTensor
                val outBuffer = outTensor.floatBuffer

                val outShape = outTensor.info.shape
                val outTileDim = outShape[2].toInt()
                val detectedScale = max(1, outTileDim / tileIn)

                if (!initializedScale) {
                    modelScale = detectedScale
                    rawOutW = w * modelScale
                    rawOutH = h * modelScale
                    outPixels = IntArray(rawOutW * rawOutH)
                    initializedScale = true
                }

                val outPlane = outTileDim * outTileDim
                val outFloats = FloatArray(outBuffer.remaining())
                outBuffer.get(outFloats)

                tensor.close()
                results.close()

                // 5. Crop the effective core region and copy to destination
                val cropX = pad * modelScale
                val cropY = pad * modelScale
                val targetCoreW = coreW * modelScale
                val targetCoreH = coreH * modelScale

                for (cy in 0 until targetCoreH) {
                    val dstY = y * modelScale + cy
                    if (dstY >= rawOutH) break

                    for (cx in 0 until targetCoreW) {
                        val dstX = x * modelScale + cx
                        if (dstX >= rawOutW) break

                        val tileIdx = (cropY + cy) * outTileDim + (cropX + cx)
                        val color = if (channels == 1) {
                            val srcX = (x + cx / modelScale).coerceIn(0, w - 1)
                            val srcY = (y + cy / modelScale).coerceIn(0, h - 1)
                            val origPx = inputBitmap.getPixel(srcX, srcY)
                            val origR = Color.red(origPx)
                            val origG = Color.green(origPx)
                            val origB = Color.blue(origPx)
                            val origLuma = (0.299f * origR + 0.587f * origG + 0.114f * origB).coerceAtLeast(1.0f)
                            val newLuma = (outFloats[tileIdx] * 255.0f).coerceIn(0.0f, 255.0f)
                            val ratio = newLuma / origLuma
                            val r = (origR * ratio).roundToInt().coerceIn(0, 255)
                            val g = (origG * ratio).roundToInt().coerceIn(0, 255)
                            val b = (origB * ratio).roundToInt().coerceIn(0, 255)
                            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        } else {
                            val r = (outFloats[tileIdx] * 255.0f).roundToInt().coerceIn(0, 255)
                            val g = (outFloats[outPlane + tileIdx] * 255.0f).roundToInt().coerceIn(0, 255)
                            val b = (outFloats[2 * outPlane + tileIdx] * 255.0f).roundToInt().coerceIn(0, 255)
                            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        outPixels[dstY * rawOutW + dstX] = color
                    }
                }

                x += coreW
            }
            y += coreH
        }

        val rawBitmap = Bitmap.createBitmap(rawOutW, rawOutH, Bitmap.Config.ARGB_8888)
        rawBitmap.setPixels(outPixels, 0, rawOutW, 0, 0, rawOutW, rawOutH)

        val targetW = w * scale
        val targetH = h * scale
        if (rawOutW != targetW || rawOutH != targetH) {
            val scaled = Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            rawBitmap.recycle()
            scaled
        } else {
            rawBitmap
        }
    }

    override fun close() {
        activeSession?.close()
        activeSession = null
    }
}
