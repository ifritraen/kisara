package eu.kanade.tachiyomi.data.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the lifecycle, downloading, verification, and local storage
 * of ONNX neural network models for on-device AI Colorization and Super-Resolution.
 */
class AiModelManager(private val context: Context) {

    private val modelsDir = File(context.filesDir, "models/ai").apply { mkdirs() }

    enum class ModelType(
        val id: String,
        val displayName: String,
        val fileName: String,
        val minSize: Long,
        val mirrors: List<String>,
    ) {
        MANGA_COLORIZER_V2(
            id = "manga_colorizer_v2",
            displayName = "Manga Colorizer v2 (INT8)",
            fileName = "manga_colorization_v2_int8.onnx",
            minSize = 25 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/AiColorize/releases/download/models/manga_colorization_v2_int8.onnx",
                "https://mirror.ghproxy.com/https://github.com/Kiastr/AiColorize/releases/download/models/manga_colorization_v2_int8.onnx",
                "https://huggingface.co/ilaylow/manga-colorizer-v2-onnx/resolve/main/manga_colorization_v2_int8.onnx",
            ),
        ),
        DEOLDIFY_ARTISTIC(
            id = "deoldify_artistic",
            displayName = "DeOldify Artistic (INT8)",
            fileName = "deoldify_artistic_int8.onnx",
            minSize = 35 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/AiColorize/releases/download/models/deoldify_int8.onnx",
                "https://mirror.ghproxy.com/https://github.com/instant-high/deoldify-onnx/releases/download/deoldify-onnx/deoldify.onnx",
                "https://github.com/instant-high/deoldify-onnx/releases/download/deoldify-onnx/deoldify.onnx",
            ),
        ),
        DDCOLOR_TINY(
            id = "ddcolor_tiny",
            displayName = "DDColor Tiny (INT8)",
            fileName = "ddcolor_tiny_int8.onnx",
            minSize = 20 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/AiColorize/releases/download/models/ddcolor_tiny_int8.onnx",
                "https://huggingface.co/pku-ddcolor/ddcolor-onnx/resolve/main/ddcolor_tiny_int8.onnx",
            ),
        ),
        ANIME4K_ACNET(
            id = "anime4k_acnet",
            displayName = "Anime4K ACNet (ONNX)",
            fileName = "anime4k_acnet.onnx",
            minSize = 10 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/Venera-SSR/raw/master/assets/models/anime4k_acnet.onnx",
                "https://raw.githubusercontent.com/Kiastr/Venera-SSR/master/assets/models/anime4k_acnet.onnx",
            ),
        ),
        REAL_ESRGAN_COMPACT(
            id = "realesrgan_compact",
            displayName = "Real-ESRGAN Compact (INT8)",
            fileName = "realesrgan_compact_int8.onnx",
            minSize = 15 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/AiColorize/releases/download/models/realesrgan_compact_int8.onnx",
                "https://huggingface.co/xinntao/realesrgan-onnx/resolve/main/realesrgan_compact_int8.onnx",
            ),
        ),
        COMIC_TEXT_DETECTOR(
            id = "comic_text_detector",
            displayName = "Comic Text Detector (ONNX)",
            fileName = "comic_text_detector.onnx",
            minSize = 15 * 1024 * 1024L,
            mirrors = listOf(
                "https://ghproxy.net/https://github.com/Kiastr/AiColorize/releases/download/models/comic_text_detector.onnx",
                "https://huggingface.co/zyddnys/manga-image-translator/resolve/main/comictextdetector.pt.onnx",
            ),
        ),
    }

    fun getModelFile(type: ModelType): File {
        return File(modelsDir, type.fileName)
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.exists() && file.length() >= type.minSize
    }

    fun deleteModel(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.delete()
    }

    /**
     * Downloads an AI model with progress reporting, HTTP Range resuming, and mirror failover.
     */
    suspend fun downloadModel(
        type: ModelType,
        onProgress: (Float) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(type)
        val tempFile = File(modelsDir, "${type.fileName}.tmp")

        if (isModelDownloaded(type)) {
            onStatus("Model already downloaded")
            onProgress(1.0f)
            return@withContext true
        }

        var success = false
        for ((index, urlString) in type.mirrors.withIndex()) {
            onStatus("Trying mirror ${index + 1}/${type.mirrors.size}...")
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Kisara/1.0")

                var startByte = 0L
                if (tempFile.exists()) {
                    startByte = tempFile.length()
                    connection.setRequestProperty("Range", "bytes=$startByte-")
                }

                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode from mirror")
                }

                val totalLength = connection.contentLengthLong + startByte
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, startByte > 0).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var currentBytes = startByte

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive()
                            output.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead
                            if (totalLength > 0) {
                                onProgress(currentBytes.toFloat() / totalLength)
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.length() >= type.minSize) {
                    tempFile.renameTo(targetFile)
                    onStatus("Download complete")
                    onProgress(1.0f)
                    success = true
                    break
                }
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Mirror $urlString failed" }
                onStatus("Mirror ${index + 1} failed, trying next...")
            }
        }

        if (!success && tempFile.exists()) {
            tempFile.delete()
        }
        success
    }

    /**
     * Safely copies an external user-selected .onnx model into internal storage
     * using bounded 64KB chunks to prevent OutOfMemoryError.
     */
    suspend fun importCustomModel(
        uri: Uri,
        targetType: ModelType,
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(targetType)
        val tempFile = File(modelsDir, "${targetType.fileName}.custom.tmp")

        try {
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            } ?: return@withContext false

            if (tempFile.length() >= 5 * 1024 * 1024L) { // Min 5MB for valid ONNX model
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to import custom model" }
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }
}
