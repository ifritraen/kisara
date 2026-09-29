package eu.kanade.translation.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
    val dictFile get() = File(modelDir, "ppocrv5_dict.txt")

    private var session: OrtSession? = null
    private var dictionary: List<String>? = null

    val isReady get() = modelFile.exists() && dictFile.exists()

    suspend fun downloadModels(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val files = listOf(
            "https://huggingface.co/ilaylow/PP_OCRv5_mobile_onnx/resolve/main/ppocrv5_rec.onnx" to modelFile,
            "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocrv5_dict.txt" to dictFile,
        )
        val networkHelper = try {
            uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        } catch (_: Exception) {
            null
        }
        val client = networkHelper?.client ?: okhttp3.OkHttpClient()

        try {
            for ((url, dest) in files) {
                if (dest.exists() && dest.length() > 1000) continue
                val name = dest.name
                onProgress("Downloading $name…")
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw java.io.IOException("HTTP ${response.code} downloading $name")
                }

                val body = response.body ?: throw java.io.IOException("Empty response body for $name")
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            ensureActive()
                            output.write(buffer, 0, bytes)
                            bytes = input.read(buffer)
                        }
                        output.flush()
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
            // Load key dictionary line by line + space token
            dictionary = dictFile.readLines(Charsets.UTF_8).filter { it.isNotEmpty() } + " "
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

        // 1. Orientation check: Rotate vertical text columns (H > 1.3 * W) 90 deg clockwise
        val normalizedCrop = if (crop.height.toFloat() / crop.width.coerceAtLeast(1) >= 1.3f) {
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, matrix, true)
        } else {
            crop
        }

        // 2. Resize image to fixed height 48, dynamic width (min 32px)
        val targetH = 48
        val ratio = targetH.toFloat() / normalizedCrop.height.coerceAtLeast(1)
        val targetW = (normalizedCrop.width * ratio).roundToInt().coerceIn(32, 1024)

        val scaled = Bitmap.createScaledBitmap(normalizedCrop, targetW, targetH, true)
        val tensor = bitmapToTensor(scaled)

        val inputName = sess.inputNames.firstOrNull() ?: "x"
        val outputs = sess.run(mapOf(inputName to tensor))
        val logits = outputs.firstOrNull()?.value as? OnnxTensor ?: run {
            tensor.close()
            outputs.close()
            if (normalizedCrop != crop) normalizedCrop.recycle()
            scaled.recycle()
            return@withContext ""
        }

        val shape = logits.info.shape // [1, seqLen, vocabSize]
        val seqLen = shape[1].toInt()
        val vocabSize = shape[2].toInt()

        val buffer = logits.floatBuffer
        val logitsArr = FloatArray(buffer.remaining())
        buffer.get(logitsArr)

        tensor.close()
        outputs.close()
        if (normalizedCrop != crop) normalizedCrop.recycle()
        scaled.recycle()

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
        val buf = eu.kanade.tachiyomi.data.ai.AiBufferUtils.allocateDirectFloatBuffer(3 * h * w)
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
