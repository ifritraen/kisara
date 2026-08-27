package eu.kanade.tachiyomi.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.Collections

/**
 * On-device AI Manga Colorization engine using ONNX Runtime.
 *
 * Implements Manga-Colorization-v2, DeOldify, and DDColor architectures.
 * Preserves 100% of original manga sharpness by performing CIE-Lab chrominance
 * splicing: only (a, b) channels are generated, leaving original line art (L channel) untouched.
 */
class MangaColorizeEngine(
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

        if (useNnapi) {
            try {
                val nnapiOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    addNnapi()
                }
                val candidate = env.createSession(modelFile.absolutePath, nnapiOpts)
                if (verifyNnapiSanity(candidate, 256)) {
                    session = candidate
                    isNnapi = true
                } else {
                    logcat(LogPriority.WARN) { "NNAPI probe failed sanity check, falling back to CPU" }
                    candidate.close()
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "NNAPI init failed, falling back to CPU" }
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

    private fun verifyNnapiSanity(session: OrtSession, modelDim: Int): Boolean {
        return try {
            val probeBuffer = java.nio.FloatBuffer.allocate(3 * modelDim * modelDim)
            for (i in 0 until (3 * modelDim * modelDim)) {
                probeBuffer.put(i, 0.5f)
            }
            probeBuffer.rewind()
            val inputName = session.inputNames.iterator().next()
            val tensor = OnnxTensor.createTensor(
                env,
                probeBuffer,
                longArrayOf(1L, 3L, modelDim.toLong(), modelDim.toLong()),
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
     * Colorizes a black & white manga page.
     *
     * @param inputBitmap The input grayscale/BW page.
     * @param modelFile The local .onnx model file.
     * @param intensity Color saturation multiplier (0.3f .. 1.5f).
     * @param useNnapi Whether to attempt hardware NPU/GPU acceleration via NNAPI.
     * @return A new colorized ARGB_8888 [Bitmap].
     */
    suspend fun colorize(
        inputBitmap: Bitmap,
        modelFile: File,
        intensity: Float = 1.0f,
        useNnapi: Boolean = false,
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file does not exist: ${modelFile.absolutePath}")
        }

        val session = try {
            getSession(modelFile, useNnapi)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to initialize NNAPI session, falling back to CPU" }
            getSession(modelFile, false)
        }

        val modelDim = 256
        val isNormalized = modelFile.name.contains("ddcolor", ignoreCase = true)
        val isBgr = !modelFile.name.contains("rgb", ignoreCase = true)

        // 1. Prepare 256x256 NCHW Input FloatBuffer
        val inputBuffer = ColorizeImageUtils.bitmapToNchwFloatBuffer(
            bitmap = inputBitmap,
            targetWidth = modelDim,
            targetHeight = modelDim,
            normalized = isNormalized,
            isBgr = isBgr,
        )

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env,
            inputBuffer,
            longArrayOf(1L, 3L, modelDim.toLong(), modelDim.toLong()),
        )

        val results = session.run(Collections.singletonMap(inputName, inputTensor))
        val outputTensor = results.get(0) as OnnxTensor
        val outBuffer = outputTensor.floatBuffer

        // 2. Extract predicted chrominance channels (a, b)
        val plane = modelDim * modelDim
        val rawFloats = FloatArray(outBuffer.remaining())
        outBuffer.get(rawFloats)

        inputTensor.close()
        results.close()

        val predA = FloatArray(plane)
        val predB = FloatArray(plane)

        if (rawFloats.size >= 3 * plane) {
            // 3-channel output (DeOldify / Manga-Colorization-v2 RGB/BGR):
            // Convert RGB/BGR output into CIE-Lab (a, b) space
            for (i in 0 until plane) {
                val c0 = rawFloats[i] // B or R
                val c1 = rawFloats[plane + i] // G
                val c2 = rawFloats[2 * plane + i] // R or B
                val r = if (isBgr) c2 else c0
                val g = c1
                val b = if (isBgr) c0 else c2

                // Calculate CIE-Lab a and b
                val rNorm = (r / 255.0f).coerceIn(0.0f, 1.0f)
                val gNorm = (g / 255.0f).coerceIn(0.0f, 1.0f)
                val bNorm = (b / 255.0f).coerceIn(0.0f, 1.0f)

                val x = 0.4124564f * rNorm + 0.3575761f * gNorm + 0.1804375f * bNorm
                val y = 0.2126729f * rNorm + 0.7151522f * gNorm + 0.0721750f * bNorm
                val z = 0.0193339f * rNorm + 0.1191920f * gNorm + 0.9503041f * bNorm

                val fx = if (x > 0.008856f) Math.cbrt(x.toDouble()).toFloat() else (7.787f * x) + (16.0f / 116.0f)
                val fy = if (y > 0.008856f) Math.cbrt(y.toDouble()).toFloat() else (7.787f * y) + (16.0f / 116.0f)
                val fz = if (z > 0.008856f) Math.cbrt(z.toDouble()).toFloat() else (7.787f * z) + (16.0f / 116.0f)

                predA[i] = 500.0f * (fx - fy)
                predB[i] = 200.0f * (fy - fz)
            }
        } else if (rawFloats.size >= 2 * plane) {
            // 2-channel output (DDColor ab channels directly)
            for (i in 0 until plane) {
                predA[i] = rawFloats[i]
                predB[i] = rawFloats[plane + i]
            }
        }

        // 3. Splicing: Merge original high-res L with predicted (a, b)
        val colorizedBitmap = ColorizeImageUtils.spliceLabColorization(
            originalBitmap = inputBitmap,
            predA = predA,
            predB = predB,
            modelW = modelDim,
            modelH = modelDim,
            intensity = intensity,
        )

        colorizedBitmap
    }

    override fun close() {
        activeSession?.close()
        activeSession = null
    }
}
