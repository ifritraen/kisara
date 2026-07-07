package eu.kanade.translation.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.FloatBuffer
import kotlin.math.roundToInt

class PaddleOcrRecognizer(
    private val context: Context,
    private val env: OrtEnvironment,
) : AutoCloseable {

    private val modelDir = File(context.filesDir, "paddleocr")
    val modelFile get() = File(modelDir, "ppocrv5_rec.onnx")
    val dictFile get() = File(modelDir, "ppocr_keys.txt")

    private var session: OrtSession? = null
    private var dictionary: List<String>? = null

    val isReady get() = modelFile.exists() && dictFile.exists()

    suspend fun downloadModels(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val files = listOf(
            "https://huggingface.co/ilaylow/PP_OCRv5_mobile_onnx/resolve/main/ppocrv5_rec.onnx" to modelFile,
            "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_v1.txt" to dictFile,
        )

        try {
            for ((url, dest) in files) {
                if (dest.exists()) continue
                val name = dest.name
                onProgress("Downloading $name…")
                URL(url).openStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            ensureActive()
                            output.write(buffer, 0, bytes)
                            bytes = input.read(buffer)
                        }
                    }
                }
            }
            onProgress("Done")
        } catch (e: CancellationException) {
            for ((_, dest) in files) {
                if (dest.exists()) {
                    dest.delete()
                }
            }
            throw e
        } catch (e: Exception) {
            for ((_, dest) in files) {
                if (dest.exists()) {
                    dest.delete()
                }
            }
            onProgress("Download failed: ${e.localizedMessage}")
            throw e
        }
    }

    private fun ensureSessionAndDict() {
        if (session == null && modelFile.exists()) {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = env.createSession(modelFile.absolutePath, opts)
        }
        if (dictionary == null && dictFile.exists()) {
            // Load key dictionary line by line
            dictionary = dictFile.readLines(Charsets.UTF_8)
        }
    }

    /**
     * Recognize text from a cropped region bitmap.
     * Returns recognized string.
     */
    suspend fun recognize(crop: Bitmap): String = withContext(Dispatchers.Default) {
        ensureSessionAndDict()
        val sess = session ?: return@withContext ""
        val dict = dictionary ?: return@withContext ""

        // Resize image to fixed height 48, dynamic width
        val targetH = 48
        val ratio = targetH.toFloat() / crop.height
        val targetW = (crop.width * ratio).roundToInt().coerceAtLeast(32)

        val scaled = Bitmap.createScaledBitmap(crop, targetW, targetH, true)
        val tensor = bitmapToTensor(scaled)

        val outputs = sess.run(mapOf("x" to tensor))
        val logits = outputs.firstOrNull()?.value as? OnnxTensor ?: return@withContext ""

        val shape = logits.info.shape // [1, seqLen, vocabSize]
        val seqLen = shape[1].toInt()
        val vocabSize = shape[2].toInt()

        val buffer = logits.floatBuffer
        val logitsArr = FloatArray(buffer.remaining())
        buffer.get(logitsArr)

        tensor.close()
        outputs.close()

        // CTC greedy decoder
        val sb = StringBuilder()
        var prevIdx = -1

        for (step in 0 until seqLen) {
            val offset = step * vocabSize
            var maxVal = Float.NEGATIVE_INFINITY
            var maxIdx = 0
            for (v in 0 until vocabSize) {
                val score = logitsArr[offset + v]
                if (score > maxVal) {
                    maxVal = score
                    maxIdx = v
                }
            }

            // 0 is blank token in standard PaddleOCR CTC head
            if (maxIdx != 0 && maxIdx != prevIdx) {
                // Dictionary index starts at 1, map back to list (index - 1)
                val dictIdx = maxIdx - 1
                if (dictIdx >= 0 && dictIdx < dict.size) {
                    sb.append(dict[dictIdx])
                }
            }
            prevIdx = maxIdx
        }

        return@withContext sb.toString().trim()
    }

    private fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Normalize using standard mean/std 0.5 for recognition models
        val buf = FloatBuffer.allocate(3 * h * w)
        for (c in 0..2) {
            for (px in pixels) {
                val v = when (c) {
                    0 -> Color.red(px) / 255f
                    1 -> Color.green(px) / 255f
                    else -> Color.blue(px) / 255f
                }
                buf.put((v - 0.5f) / 0.5f)
            }
        }
        buf.rewind()
        return OnnxTensor.createTensor(env, buf, longArrayOf(1L, 3L, h.toLong(), w.toLong()))
    }

    override fun close() {
        session?.close()
        session = null
    }
}
