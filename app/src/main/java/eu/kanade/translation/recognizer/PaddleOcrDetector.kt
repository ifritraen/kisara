package eu.kanade.translation.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PaddleOcrDetector(
    private val context: Context,
    private val env: OrtEnvironment,
) : AutoCloseable {

    private val modelDir = File(context.filesDir, "paddleocr")
    val modelFile get() = File(modelDir, "ppocrv5_det.onnx")

    private var session: OrtSession? = null

    val isReady get() = modelFile.exists()

    suspend fun downloadModel(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        if (modelFile.exists()) {
            onProgress("Already downloaded")
            return@withContext
        }
        onProgress("Downloading PP-OCRv5 Detector…")
        val url = "https://huggingface.co/ilaylow/PP_OCRv5_mobile_onnx/resolve/main/ppocrv5_det.onnx"
        try {
            URL(url).openStream().use { input ->
                modelFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        ensureActive()
                        output.write(buffer, 0, bytes)
                        bytes = input.read(buffer)
                    }
                }
            }
            onProgress("Done")
        } catch (e: CancellationException) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            throw e
        } catch (e: Exception) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            onProgress("Download failed: ${e.localizedMessage}")
            throw e
        }
    }

    private fun ensureSession() {
        if (session == null && modelFile.exists()) {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = env.createSession(modelFile.absolutePath, opts)
        }
    }

    /**
     * Detects text boxes in [bitmap].
     * Returns list of Rects mapped back to the original bitmap coordinate space.
     */
    suspend fun detect(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.Default) {
        if (!isReady) return@withContext emptyList()
        ensureSession()
        val sess = session ?: return@withContext emptyList()

        // 1. Rescale input to multiple of 32
        val longSide = 960
        val ratio = longSide.toFloat() / max(bitmap.width, bitmap.height)
        val targetW = ((bitmap.width * ratio).roundToInt() / 32 * 32).coerceAtLeast(32)
        val targetH = ((bitmap.height * ratio).roundToInt() / 32 * 32).coerceAtLeast(32)

        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val tensor = bitmapToTensor(scaled)

        val outputs = sess.run(mapOf("x" to tensor))
        val outputTensor = outputs.firstOrNull()?.value as? OnnxTensor ?: return@withContext emptyList()

        val shape = outputTensor.info.shape // [1, 1, H, W]
        val outH = shape[2].toInt()
        val outW = shape[3].toInt()

        val buffer = outputTensor.floatBuffer
        val rawOutput = FloatArray(buffer.remaining())
        buffer.get(rawOutput)

        tensor.close()
        outputs.close()

        // 2. Post-process to extract boxes
        val boxes = postProcessHeatmap(rawOutput, outH, outW, bitmap.width, bitmap.height)
        return@withContext boxes
    }

    private fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Normalize using standard ImageNet mean/std
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val buf = FloatBuffer.allocate(3 * h * w)

        for (c in 0..2) {
            for (px in pixels) {
                val v = when (c) {
                    0 -> Color.red(px) / 255f
                    1 -> Color.green(px) / 255f
                    else -> Color.blue(px) / 255f
                }
                buf.put((v - mean[c]) / std[c])
            }
        }
        buf.rewind()
        return OnnxTensor.createTensor(env, buf, longArrayOf(1L, 3L, h.toLong(), w.toLong()))
    }

    private fun postProcessHeatmap(
        heatmap: FloatArray,
        h: Int,
        w: Int,
        origW: Int,
        origH: Int,
    ): List<Rect> {
        val binaryMap = BooleanArray(h * w)
        val thresh = 0.3f
        for (i in heatmap.indices) {
            binaryMap[i] = heatmap[i] >= thresh
        }

        // Two-pass Connected Component Labeling using Union-Find
        val labels = IntArray(h * w)
        var nextLabel = 1
        val parent = mutableListOf<Int>()
        parent.add(0) // label 0 is background

        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) {
                r = parent[r]
            }
            // path compression
            var curr = i
            while (curr != r) {
                val nxt = parent[curr]
                parent[curr] = r
                curr = nxt
            }
            return r
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootI] = rootJ
            }
        }

        // First pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (binaryMap[idx]) {
                    val leftLabel = if (x > 0 && binaryMap[idx - 1]) labels[idx - 1] else 0
                    val topLabel = if (y > 0 && binaryMap[idx - w]) labels[idx - w] else 0

                    if (leftLabel == 0 && topLabel == 0) {
                        labels[idx] = nextLabel
                        parent.add(nextLabel)
                        nextLabel++
                    } else if (leftLabel != 0 && topLabel == 0) {
                        labels[idx] = leftLabel
                    } else if (leftLabel == 0 && topLabel != 0) {
                        labels[idx] = topLabel
                    } else {
                        labels[idx] = leftLabel
                        union(leftLabel, topLabel)
                    }
                }
            }
        }

        // Second pass: resolve labels and gather metrics
        val boxMinX = HashMap<Int, Int>()
        val boxMaxX = HashMap<Int, Int>()
        val boxMinY = HashMap<Int, Int>()
        val boxMaxY = HashMap<Int, Int>()
        val boxSumScore = HashMap<Int, Float>()
        val boxCount = HashMap<Int, Int>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val label = labels[idx]
                if (label > 0) {
                    val root = find(label)
                    boxMinX[root] = min(boxMinX[root] ?: x, x)
                    boxMaxX[root] = max(boxMaxX[root] ?: x, x)
                    boxMinY[root] = min(boxMinY[root] ?: y, y)
                    boxMaxY[root] = max(boxMaxY[root] ?: y, y)
                    boxSumScore[root] = (boxSumScore[root] ?: 0f) + heatmap[idx]
                    boxCount[root] = (boxCount[root] ?: 0) + 1
                }
            }
        }

        val scaleX = origW.toFloat() / w
        val scaleY = origH.toFloat() / h
        val unclipRatio = 1.6f
        val boxThresh = 0.6f

        val resultRects = mutableListOf<Rect>()
        for (root in boxMinX.keys) {
            val count = boxCount[root] ?: 0
            if (count < 10) continue // filter noise

            val avgScore = (boxSumScore[root] ?: 0f) / count
            if (avgScore < boxThresh) continue

            val xMin = boxMinX[root]!!
            val xMax = boxMaxX[root]!!
            val yMin = boxMinY[root]!!
            val yMax = boxMaxY[root]!!

            val width = xMax - xMin + 1
            val height = yMax - yMin + 1

            if (width < 3 || height < 3) continue

            // Unclip/expand bounding box
            val perimeter = 2 * (width + height)
            val area = width * height
            val distance = (area * unclipRatio) / perimeter
            val pad = distance.roundToInt().coerceIn(1, 16)

            val finalXMin = max(0, xMin - pad)
            val finalXMax = min(w - 1, xMax + pad)
            val finalYMin = max(0, yMin - pad)
            val finalYMax = min(h - 1, yMax + pad)

            // Scale back to original coordinates
            val rect = Rect(
                (finalXMin * scaleX).roundToInt(),
                (finalYMin * scaleY).roundToInt(),
                (finalXMax * scaleX).roundToInt(),
                (finalYMax * scaleY).roundToInt(),
            )
            resultRects.add(rect)
        }

        return resultRects
    }

    override fun close() {
        session?.close()
        session = null
    }
}
