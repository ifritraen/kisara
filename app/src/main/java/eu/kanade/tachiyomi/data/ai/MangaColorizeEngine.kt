package eu.kanade.tachiyomi.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.Collections
import kotlin.math.roundToInt

/**
 * On-device AI Manga Colorization engine using ONNX Runtime.
 *
 * Runs full-precision FP32 Manga-Colorization-v2 to force 32-bit registers on Android ARM CPUs/NPUs,
 * completely eliminating color compression and reproducing desktop-grade anime colors.
 */
class MangaColorizeEngine(
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : AutoCloseable {

    private val inferenceLock = Any()
    @Volatile private var activeRunOptions: OrtSession.RunOptions? = null

    private var activeSession: OrtSession? = null
    private var activeModelPath: String? = null
    private var isNnapiActive: Boolean = false
    private var currentSpeedTier: Int = 3

    fun cancelCurrentInference() {
        try {
            activeRunOptions?.setTerminate(true)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun getSession(modelFile: File, useNnapi: Boolean, speedTier: Int = 3): OrtSession {
        if (activeSession != null && activeModelPath == modelFile.absolutePath && isNnapiActive == useNnapi && currentSpeedTier == speedTier) {
            return activeSession!!
        }

        synchronized(inferenceLock) {
            activeSession?.close()
            activeSession = null
        }
        currentSpeedTier = speedTier
        val perf = ColorizeImageUtils.PerformanceConfig.fromLevel(speedTier)
        var session: OrtSession? = null
        var isNnapi = false

        if (useNnapi) {
            try {
                AppLogger.step("Initializing NNAPI hardware acceleration for ${modelFile.name}...")
                val nnapiOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(perf.cpuThreads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    addNnapi()
                }
                val candidate = env.createSession(modelFile.absolutePath, nnapiOpts)
                if (verifyNnapiSanity(candidate, 576, 5)) {
                    session = candidate
                    isNnapi = true
                    AppLogger.success("NNAPI hardware acceleration successfully initialized! (${perf.cpuThreads} threads)")
                } else {
                    AppLogger.warn("NNAPI probe failed sanity check, falling back to CPU")
                    candidate.close()
                }
            } catch (e: Exception) {
                AppLogger.warn("NNAPI init failed: ${e.message}, falling back to CPU")
            }
        }

        if (session == null) {
            AppLogger.step("Initializing session on ARM CPU (${perf.label})...")
            val cpuOpts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(perf.cpuThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                addCPU(true)
            }
            session = env.createSession(modelFile.absolutePath, cpuOpts)
            isNnapi = false
            AppLogger.info("CPU Session ready with ${perf.cpuThreads} threads.")
        }

        activeSession = session
        activeModelPath = modelFile.absolutePath
        isNnapiActive = isNnapi
        return session
    }

    private fun verifyNnapiSanity(session: OrtSession, modelDim: Int, channels: Int): Boolean {
        return try {
            val probeBuffer = AiBufferUtils.allocateDirectFloatBuffer(channels * modelDim * modelDim)
            for (i in 0 until (channels * modelDim * modelDim)) {
                probeBuffer.put(i, 0.5f)
            }
            probeBuffer.rewind()
            val inputName = session.inputNames.firstOrNull { it.contains("input", ignoreCase = true) || it.contains("data", ignoreCase = true) } ?: session.inputNames.first()
            val tensor = OnnxTensor.createTensor(
                env,
                probeBuffer,
                longArrayOf(1L, channels.toLong(), modelDim.toLong(), modelDim.toLong()),
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
     * Colorizes a black & white manga page using pure FP32 inference + 15 Tuning Filter Pipeline.
     */
    suspend fun colorize(
        inputBitmap: Bitmap,
        modelFile: File,
        intensity: Float = 1.0f,
        useNnapi: Boolean = true,
        blendMode: Int = 0, // Default 0 = OPTIMIZED
        customParams: ColorizeImageUtils.ColorizerTuningParams? = null,
        speedTier: Int = 3,
        onProgress: ((String, Float) -> Unit)? = null,
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file does not exist: ${modelFile.absolutePath}")
        }

        val perf = ColorizeImageUtils.PerformanceConfig.fromLevel(speedTier)
        onProgress?.invoke("Initializing session (${modelFile.name}, ${perf.label})...", 0.10f)

        val startTime = System.currentTimeMillis()
        val session = try {
            getSession(modelFile, useNnapi, speedTier)
        } catch (e: Exception) {
            AppLogger.warn("Session init error: ${e.message}, retrying CPU")
            getSession(modelFile, false, speedTier)
        }

        val baseDim = 576
        val aspect = inputBitmap.height.toFloat() / inputBitmap.width.coerceAtLeast(1).toFloat()
        val targetW = baseDim
        val targetH = (kotlin.math.round(baseDim * aspect / 32.0f).toInt() * 32).coerceIn(384, 896)

        onProgress?.invoke("Preparing NCHW tensor (5x${targetH}x${targetW})...", 0.25f)
        AppLogger.step("Allocating Direct FloatBuffer for 1x5x${targetH}x${targetW} (image: ${inputBitmap.width}x${inputBitmap.height})")

        val inputBuffer = ColorizeImageUtils.bitmapToMangaColorizerV2NchwFloatBuffer(
            bitmap = inputBitmap,
            targetWidth = targetW,
            targetHeight = targetH,
        )

        val inputName = session.inputNames.firstOrNull { it.contains("input", ignoreCase = true) || it.contains("data", ignoreCase = true) } ?: session.inputNames.first()
        val runOpts = OrtSession.RunOptions()
        activeRunOptions = runOpts
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        var outputTensor: OnnxTensor? = null
        val rawFloats: FloatArray
        val inferDurationMs: Long

        try {
            inputTensor = OnnxTensor.createTensor(
                env,
                inputBuffer,
                longArrayOf(1L, 5L, targetH.toLong(), targetW.toLong()),
            )

            val providerDesc = if (isNnapiActive) "NNAPI Hardware Acceleration" else "ARM CPU (${perf.cpuThreads} threads)"
            onProgress?.invoke("Running neural network forward pass ($providerDesc)...", 0.45f)
            AppLogger.step("Running forward inference pass on $providerDesc...")

            val inferStart = System.currentTimeMillis()
            results = synchronized(inferenceLock) {
                coroutineContext.ensureActive()
                session.run(Collections.singletonMap(inputName, inputTensor), runOpts)
            }
            inferDurationMs = System.currentTimeMillis() - inferStart

            outputTensor = results.get(0) as OnnxTensor
            val outBuffer = outputTensor.floatBuffer
            outBuffer.rewind()

            val plane = targetW * targetH
            rawFloats = FloatArray(outBuffer.remaining())
            outBuffer.get(rawFloats)
        } catch (e: OrtException) {
            if (!coroutineContext.isActive || activeRunOptions == null || e.message?.contains("terminate", ignoreCase = true) == true) {
                throw CancellationException("Colorization inference cancelled", e)
            }
            throw e
        } finally {
            try {
                inputTensor?.close()
            } catch (_: Exception) {}
            try {
                outputTensor?.close()
            } catch (_: Exception) {}
            try {
                results?.close()
            } catch (_: Exception) {}
            try {
                runOpts.close()
            } catch (_: Exception) {}
            if (activeRunOptions === runOpts) {
                activeRunOptions = null
            }
        }

        coroutineContext.ensureActive()

        val plane = targetW * targetH

        val ch0 = rawFloats.take(plane).average().toFloat()
        val ch1 = rawFloats.slice(plane until 2 * plane).average().toFloat()
        val ch2 = rawFloats.slice(2 * plane until 3 * plane).average().toFloat()
        val channelDiff = ch0 - ch1
        AppLogger.info("Inference completed in ${inferDurationMs}ms. Ch0: %.3f, Ch1: %.3f, Ch2: %.3f (diff: %.3f)".format(ch0, ch1, ch2, channelDiff))

        onProgress?.invoke("Inference done (${inferDurationMs}ms). Processing tensor output...", 0.80f)

        // Raw model output: [0..1] float range -> convert to RGB Bitmap
        val rawColorBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val colorPixels = IntArray(plane)
        for (i in 0 until plane) {
            val r = (rawFloats[i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val g = (rawFloats[plane + i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val b = (rawFloats[2 * plane + i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            colorPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        rawColorBitmap.setPixels(colorPixels, 0, targetW, 0, 0, targetW, targetH)

        val tuningParams = customParams ?: ColorizeImageUtils.ColorizerTuningParams.getPreset(blendMode)
        onProgress?.invoke("Applying Tuning Pipeline (Skin: ${tuningParams.skinHue}°)...", 0.90f)

        val finalBitmap = ColorizeImageUtils.applyTuningPipeline(
            originalBitmap = inputBitmap,
            rawRgbBitmap = rawColorBitmap,
            params = tuningParams,
            intensity = intensity,
            numThreads = perf.chunkWorkers,
        )
        rawColorBitmap.recycle()

        val totalDurationMs = System.currentTimeMillis() - startTime
        onProgress?.invoke("Page complete (${totalDurationMs}ms)", 1.0f)
        AppLogger.success("Output ready: ${finalBitmap.width}x${finalBitmap.height} in ${totalDurationMs}ms")

        finalBitmap
    }

    fun unloadSession() {
        cancelCurrentInference()
        synchronized(inferenceLock) {
            try {
                activeSession?.close()
                activeSession = null
                activeModelPath = null
                isNnapiActive = false
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        unloadSession()
    }
}