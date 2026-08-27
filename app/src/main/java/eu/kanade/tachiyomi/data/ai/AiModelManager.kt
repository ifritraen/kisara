package eu.kanade.tachiyomi.data.ai

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the lifecycle, downloading, verification, and local storage
 * of ONNX neural network models for on-device AI Colorization and Super-Resolution.
 */
class AiModelManager(
    private val context: Context,
    private val networkHelper: NetworkHelper = Injekt.get(),
) {

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
            minSize = 30 * 1024 * 1024L,
            mirrors = listOf(
                "https://huggingface.co/Faridzar/manga-colorization-v2-onnx/resolve/main/manga-colorize-fp16.onnx",
                "https://huggingface.co/Faridzar/manga-colorization-v2-onnx/raw/main/manga-colorize-fp16.onnx",
            ),
        ),
        DEOLDIFY_ARTISTIC(
            id = "deoldify_artistic",
            displayName = "DeOldify Artistic (INT8)",
            fileName = "deoldify_artistic_int8.onnx",
            minSize = 50 * 1024 * 1024L,
            mirrors = listOf(
                "https://github.com/instant-high/deoldify-onnx/releases/download/deoldify-onnx/deoldify.onnx",
                "https://huggingface.co/facefusion/models-3.0.0/resolve/main/deoldify.onnx",
            ),
        ),
        DDCOLOR_TINY(
            id = "ddcolor_tiny",
            displayName = "DDColor Tiny (INT8)",
            fileName = "ddcolor_tiny_int8.onnx",
            minSize = 50 * 1024 * 1024L,
            mirrors = listOf(
                "https://huggingface.co/facefusion/models-3.0.0/resolve/main/ddcolor.onnx",
                "https://github.com/instant-high/DDColor-onnx/releases/download/v1.0.0/ddcolor.onnx",
            ),
        ),
        ANIME4K_ACNET(
            id = "anime4k_acnet",
            displayName = "Anime4K ACNet (ONNX)",
            fileName = "anime4k_acnet.onnx",
            minSize = 10 * 1024L,
            mirrors = listOf(
                "https://raw.githubusercontent.com/Kiastr/Venera-SSR/master/assets/models/anime4k_acnet.onnx",
            ),
        ),
        REAL_ESRGAN_COMPACT(
            id = "realesrgan_compact",
            displayName = "Real-ESRGAN Compact (INT8)",
            fileName = "realesrgan_compact_int8.onnx",
            minSize = 2 * 1024 * 1024L,
            mirrors = listOf(
                "https://huggingface.co/Heliosoph/realesrgan-onnx/resolve/main/realesr-general-x4v3.onnx",
                "https://huggingface.co/Heliosoph/realesrgan-onnx/raw/main/realesr-general-x4v3.onnx",
            ),
        ),
        COMIC_TEXT_DETECTOR(
            id = "comic_text_detector",
            displayName = "Comic Text Detector (ONNX)",
            fileName = "comic_text_detector.onnx",
            minSize = 50 * 1024 * 1024L,
            mirrors = listOf(
                "https://huggingface.co/mayocream/comic-text-detector-onnx/resolve/main/comic-text-detector.onnx",
                "https://huggingface.co/mayocream/comic-text-detector-onnx/raw/main/comic-text-detector.onnx",
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
        val client = networkHelper.client

        for ((index, urlString) in type.mirrors.withIndex()) {
            onStatus("Downloading model (${index + 1}/${type.mirrors.size})...")
            try {
                var startByte = 0L
                if (tempFile.exists()) {
                    startByte = tempFile.length()
                }

                val requestBuilder = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")

                if (startByte > 0) {
                    requestBuilder.header("Range", "bytes=$startByte-")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (response.code == 416 && tempFile.exists()) {
                        tempFile.delete()
                        startByte = 0L
                        response.close()
                        val freshReq = Request.Builder()
                            .url(urlString)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                            .build()
                        val freshRes = client.newCall(freshReq).execute()
                        if (!freshRes.isSuccessful) {
                            freshRes.close()
                            throw Exception("HTTP ${freshRes.code} from mirror")
                        }
                        readResponseBody(freshRes, tempFile, 0L, onProgress)
                    } else {
                        response.close()
                        throw Exception("HTTP ${response.code} from mirror")
                    }
                } else {
                    readResponseBody(response, tempFile, startByte, onProgress)
                }

                if (tempFile.length() >= type.minSize) {
                    if (targetFile.exists()) targetFile.delete()
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

    private fun readResponseBody(
        response: okhttp3.Response,
        tempFile: File,
        startByte: Long,
        onProgress: (Float) -> Unit,
    ) {
        val body = response.body ?: throw Exception("Empty response body")
        val totalLength = (body.contentLength().takeIf { it > 0 } ?: 0L) + startByte

        body.byteStream().use { input ->
            FileOutputStream(tempFile, startByte > 0).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var currentBytes = startByte

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead
                    if (totalLength > 0) {
                        onProgress(currentBytes.toFloat() / totalLength)
                    }
                }
                output.flush()
            }
        }
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

            if (tempFile.length() >= targetType.minSize) {
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
