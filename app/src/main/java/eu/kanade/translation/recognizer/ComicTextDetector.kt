package eu.kanade.translation.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import eu.kanade.tachiyomi.data.ai.AiModelManager
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.system.logcat
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Comic-Text-Detector (CTD) ONNX Inference Engine.
 *
 * Runs a specialized deep learning segmentation model tailored for manga/comic
 * speech balloons, narration boxes, and vertical text lines.
 */
class ComicTextDetector(
    private val context: Context,
    private val modelManager: AiModelManager = AiModelManager(context),
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : Closeable {

    private var session: OrtSession? = null
    private var activePath: String? = null

    val isReady: Boolean
        get() = modelManager.isModelDownloaded(AiModelManager.ModelType.COMIC_TEXT_DETECTOR)

    @Synchronized
    private fun getSession(): OrtSession? {
        val modelFile = modelManager.getModelFile(AiModelManager.ModelType.COMIC_TEXT_DETECTOR)
        if (!modelFile.exists() || modelFile.length() < AiModelManager.ModelType.COMIC_TEXT_DETECTOR.minSize) {
            return null
        }

        if (session != null && activePath == modelFile.absolutePath) {
            return session
        }

        session?.close()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            addCPU(true)
        }
        session = env.createSession(modelFile.absolutePath, opts)
        activePath = modelFile.absolutePath
        return session
    }

    /**
     * Detects text and speech bubble regions in a manga page.
     *
     * @param bitmap The raw manga page.
     * @return List of bounding boxes around text/bubble regions.
     */
    fun detect(bitmap: Bitmap): List<Rect> {
        val sess = getSession() ?: return emptyList()
        val targetDim = 512

        val scaled = Bitmap.createScaledBitmap(bitmap, targetDim, targetDim, true)
        val numPixels = targetDim * targetDim
        val floatBuf = FloatBuffer.allocate(3 * numPixels)

        val pixels = IntArray(numPixels)
        scaled.getPixels(pixels, 0, targetDim, 0, 0, targetDim, targetDim)

        // Fill NCHW planar RGB normalized to [0..1]
        for (c in 0..2) {
            for (i in 0 until numPixels) {
                val p = pixels[i]
                val v = when (c) {
                    0 -> Color.red(p) / 255f
                    1 -> Color.green(p) / 255f
                    else -> Color.blue(p) / 255f
                }
                floatBuf.put(v)
            }
        }
        floatBuf.rewind()

        val inputName = sess.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env,
            floatBuf,
            longArrayOf(1L, 3L, targetDim.toLong(), targetDim.toLong()),
        )

        val boxes = mutableListOf<Rect>()
        try {
            val result = sess.run(java.util.Collections.singletonMap(inputName, inputTensor))
            val outputTensor = result.get(0) as OnnxTensor
            val outBuf = outputTensor.floatBuffer

            // Parse segmentation mask or proposal bounding boxes
            val scaleX = bitmap.width.toFloat() / targetDim.toFloat()
            val scaleY = bitmap.height.toFloat() / targetDim.toFloat()

            val maskThreshold = 0.5f
            val gridStep = 8
            val gridW = targetDim / gridStep
            val gridH = targetDim / gridStep

            // Spatial grouping of high-confidence activation clusters
            val visited = BooleanArray(gridW * gridH)
            val minClusterPoints = 2

            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    val idx = gy * gridW + gx
                    if (visited[idx]) continue

                    val px = gx * gridStep + gridStep / 2
                    val py = gy * gridStep + gridStep / 2
                    val bufIdx = (py * targetDim + px).coerceIn(0, outBuf.capacity() - 1)
                    val score = outBuf.get(bufIdx)

                    if (score >= maskThreshold) {
                        // Flood fill / cluster detection
                        var minPx = px
                        var maxPx = px
                        var minPy = py
                        var maxPy = py
                        var count = 0

                        val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                        queue.add(Pair(gx, gy))
                        visited[idx] = true

                        while (queue.isNotEmpty()) {
                            val (cx, cy) = queue.removeFirst()
                            count++
                            val curX = cx * gridStep + gridStep / 2
                            val curY = cy * gridStep + gridStep / 2
                            minPx = min(minPx, curX)
                            maxPx = max(maxPx, curX + gridStep)
                            minPy = min(minPy, curY)
                            maxPy = max(maxPy, curY + gridStep)

                            val neighbors = listOf(
                                Pair(cx - 1, cy),
                                Pair(cx + 1, cy),
                                Pair(cx, cy - 1),
                                Pair(cx, cy + 1),
                            )

                            for ((nx, ny) in neighbors) {
                                if (nx in 0 until gridW && ny in 0 until gridH) {
                                    val nIdx = ny * gridW + nx
                                    if (!visited[nIdx]) {
                                        val nPx = nx * gridStep + gridStep / 2
                                        val nPy = ny * gridStep + gridStep / 2
                                        val nBufIdx = (nPy * targetDim + nPx).coerceIn(0, outBuf.capacity() - 1)
                                        if (outBuf.get(nBufIdx) >= maskThreshold) {
                                            visited[nIdx] = true
                                            queue.add(Pair(nx, ny))
                                        }
                                    }
                                }
                            }
                        }

                        if (count >= minClusterPoints) {
                            val rect = Rect(
                                (minPx * scaleX).toInt().coerceAtLeast(0),
                                (minPy * scaleY).toInt().coerceAtLeast(0),
                                (maxPx * scaleX).toInt().coerceAtMost(bitmap.width),
                                (maxPy * scaleY).toInt().coerceAtMost(bitmap.height),
                            )
                            if (rect.width() > 16 && rect.height() > 16) {
                                boxes.add(rect)
                            }
                        }
                    }
                }
            }

            result.close()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "ComicTextDetector inference failed" }
        } finally {
            inputTensor.close()
            scaled.recycle()
        }

        return boxes
    }

    override fun close() {
        session?.close()
        session = null
    }
}
