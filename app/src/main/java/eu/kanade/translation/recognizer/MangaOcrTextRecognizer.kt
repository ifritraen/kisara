package eu.kanade.translation.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.roundToInt

/**
 * MangaOCR ONNX-based text recognizer.
 * On first use, downloads the encoder + decoder ONNX models and tokenizer from HuggingFace.
 * Falls back to ML Kit if models are not available yet.
 *
 * ponytail: no model version tracking, upgrade when we add model update checks
 */
class MangaOcrEngine(
    private val context: Context,
    private val language: TextRecognizerLanguage,
) : AutoCloseable {

    private val modelDir = File(context.filesDir, "mangaocr")

    private val encoderFile get() = File(modelDir, "encoder_model.onnx")
    private val decoderFile get() = File(modelDir, "decoder_model.onnx")
    private val tokenizerFile get() = File(modelDir, "tokenizer.json")

    private val env = OrtEnvironment.getEnvironment()
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var tokenizer: SimpleTokenizer? = null

    val isReady get() = encoderFile.exists() && decoderFile.exists() && tokenizerFile.exists()

    // ──────────── Model Download ────────────

    suspend fun downloadModels(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val base = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/main"
        val files = listOf(
            "encoder_model.onnx" to encoderFile,
            "decoder_model.onnx" to decoderFile,
            "tokenizer.json" to tokenizerFile,
        )
        try {
            for ((name, dest) in files) {
                if (dest.exists()) continue
                onProgress("Downloading $name…")
                URL("$base/$name").openStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            onProgress("Done")
        } catch (e: Exception) {
            onProgress("Download failed: ${e.localizedMessage}")
            throw e
        }
    }

    // ──────────── Session Management ────────────

    private fun ensureSessions() {
        if (encoderSession == null && encoderFile.exists()) {
            encoderSession = env.createSession(encoderFile.absolutePath)
        }
        if (decoderSession == null && decoderFile.exists()) {
            decoderSession = env.createSession(decoderFile.absolutePath)
        }
        if (tokenizer == null && tokenizerFile.exists()) {
            tokenizer = SimpleTokenizer.load(tokenizerFile)
        }
    }

    // ──────────── Inference ────────────

    /**
     * Recognize text from a cropped region bitmap.
     * Returns recognized string or empty if model unavailable.
     */
    suspend fun recognize(crop: Bitmap): String = withContext(Dispatchers.Default) {
        ensureSessions()
        val enc = encoderSession ?: return@withContext ""
        val dec = decoderSession ?: return@withContext ""
        val tok = tokenizer ?: return@withContext ""

        val processed = preprocessBitmap(crop)
        val inputTensor = bitmapToTensor(processed)

        // Encode
        val encoderOutput = enc.run(mapOf("pixel_values" to inputTensor))
        val hiddenStates = encoderOutput["last_hidden_state"].get() as OnnxTensor

        // Autoregressive decode
        val maxLen = 300
        val eosId = tok.eosTokenId
        val decodedIds = mutableListOf(tok.bosTokenId)

        for (step in 0 until maxLen) {
            val inputIds = LongBuffer.wrap(decodedIds.map { it.toLong() }.toLongArray())
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                inputIds,
                longArrayOf(1L, decodedIds.size.toLong()),
            )
            val decOut = dec.run(
                mapOf(
                    "input_ids" to inputIdsTensor,
                    "encoder_hidden_states" to hiddenStates,
                ),
            )
            val logits = decOut["logits"].get() as OnnxTensor
            val shape = logits.info.shape
            val seqLen = shape[1].toInt()
            val vocabSize = shape[2].toInt()
            val logitsBuffer = logits.floatBuffer
            val logitsArr = FloatArray(logitsBuffer.remaining())
            logitsBuffer.get(logitsArr)
            // Pick last token position argmax
            val lastPos = (seqLen - 1) * vocabSize
            var maxVal = Float.NEGATIVE_INFINITY
            var maxIdx = 0
            for (i in 0 until vocabSize) {
                if (logitsArr[lastPos + i] > maxVal) {
                    maxVal = logitsArr[lastPos + i]
                    maxIdx = i
                }
            }
            if (maxIdx == eosId) break
            decodedIds.add(maxIdx)
            inputIdsTensor.close()
            logits.close()
        }

        hiddenStates.close()
        inputTensor.close()

        tok.decode(decodedIds.drop(1)) // drop BOS
    }

    private fun preprocessBitmap(src: Bitmap): Bitmap {
        // MangaOCR input: 224×224 RGB
        val size = 224
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, Paint())
        }
        return result
    }

    private fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        // Normalize to ImageNet mean/std (MangaOCR uses standard ViT preprocessing)
        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(0.5f, 0.5f, 0.5f)
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

    override fun close() {
        encoderSession?.close()
        encoderSession = null
        decoderSession?.close()
        decoderSession = null
        env.close()
    }

    // ──────────── Tokenizer ────────────

    class SimpleTokenizer(
        private val idToToken: Array<String>,
        val bosTokenId: Int,
        val eosTokenId: Int,
    ) {
        fun decode(ids: List<Int>): String {
            val sb = StringBuilder()
            for (id in ids) {
                if (id !in idToToken.indices) continue
                val tok = idToToken[id]
                if (tok.startsWith("##")) sb.append(tok.substring(2)) else sb.append(tok)
            }
            return sb.toString().trim()
        }

        companion object {
            fun load(file: File): SimpleTokenizer {
                val json = JSONObject(file.readText())
                val vocab = json.getJSONObject("model").getJSONObject("vocab")
                val arr = Array(vocab.length()) { "" }
                vocab.keys().forEach { k -> arr[vocab.getInt(k)] = k }
                val added = json.optJSONArray("added_tokens")
                var bos = 0
                var eos = 2
                if (added != null) {
                    for (i in 0 until added.length()) {
                        val tok = added.getJSONObject(i)
                        if (tok.getString("content") == "<s>") bos = tok.getInt("id")
                        if (tok.getString("content") == "</s>") eos = tok.getInt("id")
                    }
                }
                return SimpleTokenizer(arr, bos, eos)
            }
        }
    }
}

/**
 * Wraps MangaOcrEngine into the same recognize(InputImage) interface as ML Kit TextRecognizer
 * so ChapterTranslator can swap them with minimal changes.
 */
class MangaOcrTextRecognizer(
    private val context: Context,
    val language: TextRecognizerLanguage,
) : AutoCloseable {

    val engine = MangaOcrEngine(context, language)

    /** True when ONNX models are downloaded and ready. */
    val isReady get() = engine.isReady

    /** Download models if not present. Call from a coroutine before first use. */
    suspend fun ensureModels(onProgress: (String) -> Unit = {}) {
        if (!engine.isReady) engine.downloadModels(onProgress)
    }

    /**
     * Recognize text in [image].
     * Returns list of (text, boundingBox) pairs compatible with ML Kit usage.
     */
    suspend fun recognize(image: InputImage): List<Pair<String, android.graphics.Rect>> {
        val bitmap = image.bitmapInternal ?: return emptyList()
        val text = engine.recognize(bitmap)
        if (text.isBlank()) return emptyList()
        // Full-image bounding box — bubble detector provides the crop, so this covers the whole region
        val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        return listOf(text to rect)
    }

    override fun close() = engine.close()
}
