package eu.kanade.tachiyomi.data.ai

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle, downloading, verification, and local storage
 * of ONNX neural network models for on-device AI Colorization and Super-Resolution.
 */
class AiModelManager(
    private val context: Context,
    private val networkHelper: NetworkHelper = Injekt.get(),
) {

    data class DownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val status: String = "",
    )

    companion object {
        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val downloadJobs = ConcurrentHashMap<ModelType, Job>()
        private val downloadStates = ConcurrentHashMap<ModelType, MutableStateFlow<DownloadState>>()

        fun getDownloadState(type: ModelType): StateFlow<DownloadState> {
            return downloadStates.getOrPut(type) { MutableStateFlow(DownloadState()) }.asStateFlow()
        }

        fun isDownloading(type: ModelType): Boolean {
            return downloadJobs[type]?.isActive == true
        }

        fun getModelsBaseDir(context: Context): File {
            val storageManager = runCatching { Injekt.get<StorageManager>() }.getOrNull()
            val externalUniDir = storageManager?.getModelsDirectory()
            val externalPath = externalUniDir?.filePath
            if (!externalPath.isNullOrBlank()) {
                val externalFile = File(externalPath)
                if (externalFile.exists() || externalFile.mkdirs()) {
                    return externalFile
                }
            }

            val storagePrefs = runCatching { Injekt.get<StoragePreferences>() }.getOrNull()
            val baseUriString = storagePrefs?.baseStorageDirectory()?.get()
            if (!baseUriString.isNullOrBlank()) {
                val baseUni = UniFile.fromUri(context, baseUriString.toUri())
                val basePath = baseUni?.filePath
                if (!basePath.isNullOrBlank()) {
                    val externalFile = File(basePath, StorageManager.MODELS_PATH)
                    if (externalFile.exists() || externalFile.mkdirs()) {
                        return externalFile
                    }
                }
            }

            return File(context.filesDir, "models").apply { mkdirs() }
        }

        fun getModelSubdir(context: Context, subdir: String): File {
            val base = getModelsBaseDir(context)
            val dir = File(base, subdir).apply { mkdirs() }
            val internalDir = File(context.filesDir, subdir)
            if (internalDir.exists() && internalDir.isDirectory && internalDir != dir) {
                internalDir.listFiles()?.forEach { file ->
                    val target = File(dir, file.name)
                    if (!target.exists()) {
                        try {
                            file.copyTo(target, overwrite = false)
                        } catch (_: Exception) {}
                    }
                }
            }
            return dir
        }
    }

    fun startDownload(type: ModelType, onComplete: ((Boolean) -> Unit)? = null) {
        if (isDownloading(type)) return
        val stateFlow = downloadStates.getOrPut(type) { MutableStateFlow(DownloadState()) }
        val job = downloadScope.launch {
            stateFlow.value = DownloadState(isDownloading = true, progress = 0f, status = "Connecting...")
            var success = false
            try {
                success = downloadModel(
                    type = type,
                    onProgress = { progress ->
                        stateFlow.value = stateFlow.value.copy(progress = progress)
                    },
                    onStatus = { status ->
                        stateFlow.value = stateFlow.value.copy(status = status)
                    },
                )
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to download model ${type.displayName}" }
            } finally {
                stateFlow.value = DownloadState(isDownloading = false, progress = if (success) 1f else 0f, status = "")
                downloadJobs.remove(type)
                withUIContext {
                    onComplete?.invoke(success)
                }
            }
        }
        downloadJobs[type] = job
    }

    fun cancelDownload(type: ModelType) {
        downloadJobs.remove(type)?.cancel()
        downloadStates[type]?.value = DownloadState(isDownloading = false, progress = 0f, status = "")
    }

    private val modelsDir get() = getModelSubdir(context, "ai")

    enum class ModelType(
        val id: String,
        val displayName: String,
        val fileName: String,
        val minSize: Long,
        val mirrors: List<String>,
    ) {
        MANGA_COLORIZER_V2(
            id = "manga_colorizer_v2_fp32",
            displayName = "Manga Colorizer v2 (FP32 Studio)",
            fileName = "manga_colorization_v2_fp32.onnx",
            minSize = 80 * 1024 * 1024L,
            mirrors = listOf(
                "https://github.com/ifritraen/color_model/releases/download/v0.1/manga_colorization_v2_fp32.onnx",
                "https://huggingface.co/ifritraen/manga-colorization-v2-fp32/resolve/main/manga_colorization_v2_fp32.onnx",
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
        // KMK -->
        MINILM_L6_TAGGER(
            id = "minilm_l6_tagger",
            displayName = "MiniLM-L6 Tag Extractor (INT8)",
            fileName = "minilm_l6_int8.onnx",
            minSize = 15 * 1024 * 1024L,
            mirrors = listOf(
                "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
            ),
        ),
        // KMK <--
    }

    fun getModelFile(type: ModelType): File {
        // 1. Shared external directory (preferred for multi-variant sharing)
        val externalDir = modelsDir
        val externalFile = File(externalDir, type.fileName)
        if (externalFile.exists() && externalFile.length() >= type.minSize) {
            return externalFile
        }

        // 2. Private internal directory fallback & auto-migration
        val internalDir = File(context.filesDir, "models/ai")
        val internalFile = File(internalDir, type.fileName)
        if (internalFile.exists() && internalFile.length() >= type.minSize) {
            // Auto-migrate to external directory if writable
            if (externalDir.exists() && !externalFile.exists()) {
                try {
                    internalFile.copyTo(externalFile, overwrite = false)
                } catch (_: Exception) {}
            }
            return if (externalFile.exists() && externalFile.length() >= type.minSize) externalFile else internalFile
        }

        // 3. Fallback: package external files dir
        try {
            val extAppDir = context.getExternalFilesDir("models/ai")
            if (extAppDir != null) {
                val candidateApp = File(extAppDir, type.fileName)
                if (candidateApp.exists() && candidateApp.length() >= type.minSize) {
                    if (externalDir.exists() && !externalFile.exists()) {
                        try {
                            candidateApp.copyTo(externalFile, overwrite = false)
                        } catch (_: Exception) {}
                    }
                    return if (externalFile.exists() && externalFile.length() >= type.minSize) externalFile else candidateApp
                }
            }
        } catch (_: Exception) {}

        // 4. Fallback: primary external storage root (/storage/emulated/0/<filename>)
        try {
            val rootFile = File(android.os.Environment.getExternalStorageDirectory(), type.fileName)
            if (rootFile.exists() && rootFile.length() >= type.minSize) {
                if (externalDir.exists() && !externalFile.exists()) {
                    try {
                        rootFile.copyTo(externalFile, overwrite = false)
                    } catch (_: Exception) {}
                }
                return if (externalFile.exists() && externalFile.length() >= type.minSize) externalFile else rootFile
            }
        } catch (_: Exception) {}

        // Default target is the shared external file
        return externalFile
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.exists() && file.length() >= type.minSize
    }

    fun deleteModel(type: ModelType): Boolean {
        var deletedAny = false
        val externalFile = File(modelsDir, type.fileName)
        if (externalFile.exists()) {
            externalFile.setWritable(true)
            deletedAny = externalFile.delete() || deletedAny
        }
        val storageManager = runCatching { Injekt.get<tachiyomi.domain.storage.service.StorageManager>() }.getOrNull()
        val uniModel = storageManager?.getModelsDirectory()?.findFile(type.fileName)
        if (uniModel != null && uniModel.exists()) {
            deletedAny = uniModel.delete() || deletedAny
        }
        val internalFile = File(context.filesDir, "models/ai/${type.fileName}")
        if (internalFile.exists()) {
            internalFile.setWritable(true)
            deletedAny = internalFile.delete() || deletedAny
        }
        val extAppDir = context.getExternalFilesDir("models/ai")
        if (extAppDir != null) {
            val candidateApp = File(extAppDir, type.fileName)
            if (candidateApp.exists()) {
                candidateApp.setWritable(true)
                deletedAny = candidateApp.delete() || deletedAny
            }
        }
        return deletedAny || !isModelDownloaded(type)
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
        val isPartial = response.code == 206
        val effectiveStartByte = if (isPartial) startByte else 0L
        val body = response.body ?: throw Exception("Empty response body")
        val totalLength = (body.contentLength().takeIf { it > 0 } ?: 0L) + effectiveStartByte

        body.byteStream().use { input ->
            FileOutputStream(tempFile, isPartial).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var currentBytes = effectiveStartByte

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
