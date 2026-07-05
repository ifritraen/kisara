package eu.kanade.translation.recognizer

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaddleOcrTextRecognizer(
    private val context: Context,
    val language: TextRecognizerLanguage,
) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    val detector = PaddleOcrDetector(context, env)
    val recognizer = PaddleOcrRecognizer(context, env)

    val isReady get() = detector.isReady && recognizer.isReady

    suspend fun ensureModels(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (!detector.isReady) {
            detector.downloadModel(onProgress)
        }
        if (!recognizer.isReady) {
            recognizer.downloadModels(onProgress)
        }
    }

    /**
     * Recognizes text in the given [image] by first detecting text line regions,
     * cropping them, and running the text recognizer on each line.
     * Returns a list of (text, boundingBox) pairs relative to the input image coordinates.
     */
    suspend fun recognize(image: InputImage): List<Pair<String, Rect>> = withContext(Dispatchers.Default) {
        val bitmap = image.bitmapInternal ?: return@withContext emptyList()

        // 1. Detect text line boxes
        val boxes = detector.detect(bitmap)
        if (boxes.isEmpty()) return@withContext emptyList()

        // 2. Recognize text in each box
        val results = mutableListOf<Pair<String, Rect>>()
        for (box in boxes) {
            val left = box.left.coerceAtLeast(0)
            val top = box.top.coerceAtLeast(0)
            val width = box.width().coerceAtMost(bitmap.width - left)
            val height = box.height().coerceAtMost(bitmap.height - top)

            if (width < 3 || height < 3) continue

            val crop = try {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } catch (e: Throwable) {
                null
            } ?: continue

            val text = recognizer.recognize(crop)
            if (text.isNotBlank()) {
                results.add(text to box)
            }
        }

        return@withContext results
    }

    override fun close() {
        detector.close()
        recognizer.close()
        env.close()
    }
}
