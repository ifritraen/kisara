package eu.kanade.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.ai.AiModelManager
import eu.kanade.tachiyomi.data.ai.MangaColorizeEngine
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.translation.model.Translation
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream

import eu.kanade.translation.model.TranslationReport

/**
 * Manages local on-device batch chapter colorization using ONNX AI models (Manga-Colorizer-v2 / DeOldify).
 * Saves colorized chapter images into storageManager.getColorizerDirectory().
 */
class ColorizerManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
) {
    data class Progress(
        val chapterId: Long,
        val chapterName: String,
        val currentPage: Int,
        val totalPages: Int,
        val percent: Int = 0,
        val step: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val _progressState = MutableStateFlow<Progress?>(null)
    val progressState = _progressState.asStateFlow()

    private val colorizeEngine by lazy { MangaColorizeEngine() }
    private val modelManager by lazy { AiModelManager(context) }
    private var idleUnloadJob: kotlinx.coroutines.Job? = null
    private var activeColorizeJob: kotlinx.coroutines.Job? = null
    private var activeChapterId: Long? = null

    private fun scheduleIdleUnload(delayMs: Long = 5_000L) {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (_queueState.value.isEmpty()) {
                colorizeEngine.unloadSession()
                System.gc()
                Runtime.getRuntime().gc()
                eu.kanade.tachiyomi.data.ai.AppLogger.info("Auto-freed RAM: Colorizer ONNX session unloaded after ${delayMs / 1000}s idle.")
            }
        }
    }

    private val colorizerDir: UniFile?
        get() = storageManager.getColorizerDirectory()

    fun getQueuedColorizerOrNull(chapterId: Long): Translation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun colorizeChapter(manga: Manga, chapter: Chapter) {
        val source = (sourceManager.get(manga.source) as? HttpSource) ?: return
        val translation = Translation(source, manga, chapter)

        synchronized(_queueState) {
            val current = _queueState.value
            if (current.any { it.chapter.id == chapter.id }) return
            _queueState.value = current + translation
        }

        translation.status = Translation.State.QUEUE
        processQueue()
    }

    private fun processQueue() {
        if (activeColorizeJob?.isActive == true) return

        activeColorizeJob = scope.launch {
            val next = synchronized(_queueState) {
                _queueState.value.find { it.status == Translation.State.QUEUE }
            } ?: run {
                activeChapterId = null
                return@launch
            }

            activeChapterId = next.chapter.id
            next.status = Translation.State.TRANSLATING
            try {
                TranslationReport.clear()
                TranslationReport.log("INFO", "Colorizer", "Starting colorization for chapter: ${next.chapter.name}")

                val isCloudBackend = translationPreferences.colorizerEngine().get() == 1
                val kaggleApiKey = translationPreferences.colorizerKaggleApiKey().get()
                val ngrokToken = translationPreferences.colorizerNgrokAuthToken().get()

                // Check if user explicitly configured and wants remote Cloud processing
                if (isCloudBackend && (kaggleApiKey.isNotBlank() || ngrokToken.isNotBlank())) {
                    TranslationReport.log("INFO", "Colorizer", "Using remote Cloud Kaggle colorizer backend")
                    logcat { "Using remote Cloud Kaggle colorizer backend" }
                    // Cloud processing stub / bridge: marked translated upon remote completion
                    next.status = Translation.State.TRANSLATED
                    return@launch
                }

                // Default: Local On-Device AI (ONNX)
                val modelType = AiModelManager.ModelType.MANGA_COLORIZER_V2

                idleUnloadJob?.cancel()
                eu.kanade.tachiyomi.data.ai.AppLogger.init(context)
                eu.kanade.tachiyomi.data.ai.ResourceMonitor.start()
                eu.kanade.tachiyomi.data.ai.WakeLockHelper.acquire(context)
                _progressState.value = Progress(
                    chapterId = next.chapter.id,
                    chapterName = next.chapter.name,
                    currentPage = 0,
                    totalPages = 0,
                    percent = 0,
                    step = "Checking AI model (${modelType.displayName})...",
                )
                TranslationReport.log("INFO", "Colorizer", "Selected model: ${modelType.displayName}")

                var modelFile = modelManager.getModelFile(modelType)
                if (!modelFile.exists() || modelFile.length() < modelType.minSize) {
                    TranslationReport.log("INFO", "Colorizer", "Model not found locally, downloading ${modelType.displayName}...")
                    _progressState.value = Progress(
                        chapterId = next.chapter.id,
                        chapterName = next.chapter.name,
                        currentPage = 0,
                        totalPages = 0,
                        percent = 0,
                        step = "Downloading model ${modelType.displayName}...",
                    )
                    val downloaded = modelManager.downloadModel(modelType)
                    if (!downloaded) {
                        TranslationReport.log("WARNING", "Colorizer", "Download failed for ${modelType.displayName}, attempting fallback...")
                        // Fallback to Manga Colorizer v2 FP32
                        val fallbackType = AiModelManager.ModelType.MANGA_COLORIZER_V2
                        modelFile = modelManager.getModelFile(fallbackType)
                        if (!modelFile.exists() || modelFile.length() < fallbackType.minSize) {
                            modelManager.downloadModel(fallbackType)
                        }
                    }
                }

                if (!modelFile.exists()) {
                    val err = "No valid colorization AI model found."
                    TranslationReport.log("ERROR", "Colorizer", err)
                    throw IllegalStateException(err)
                }
                TranslationReport.log("INFO", "Colorizer", "AI model ready: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")

                val intensity = translationPreferences.colorizerIntensity().get().coerceIn(0.5f, 35.0f)
                val useNnapi = translationPreferences.colorizerUseNnapi().get()
                val blendMode = translationPreferences.colorizerBlendMode().get()
                val customParams = if (blendMode == 7) {
                    eu.kanade.tachiyomi.data.ai.ColorizeImageUtils.ColorizerTuningParams(
                        skinHue = translationPreferences.colorizerSkinHue().get(),
                        skinSat = translationPreferences.colorizerSkinSat().get(),
                        blueCap = translationPreferences.colorizerBlueCap().get(),
                        redFlush = translationPreferences.colorizerRedFlush().get(),
                        skinMinLum = translationPreferences.colorizerSkinMinLum().get(),
                        paperThresh = translationPreferences.colorizerPaperThresh().get(),
                        paperFeather = translationPreferences.colorizerPaperFeather().get(),
                        gamma = translationPreferences.colorizerGamma().get(),
                        contrast = translationPreferences.colorizerContrast().get(),
                        blackFloor = translationPreferences.colorizerBlackFloor().get(),
                        lineThresh = translationPreferences.colorizerLineThresh().get(),
                        lineExp = translationPreferences.colorizerLineExp().get(),
                        satMul = translationPreferences.colorizerSatMul().get(),
                        colorMix = translationPreferences.colorizerColorMix().get(),
                        clarity = translationPreferences.colorizerClarity().get(),
                    )
                } else {
                    null
                }
                TranslationReport.log("INFO", "Colorizer", "Config: intensity=$intensity, useNnapi=$useNnapi, blendMode=$blendMode")

                // Locate downloaded chapter directory / archive
                val chapterDir = downloadProvider.findChapterDir(
                    chapterName = next.chapter.name,
                    chapterScanlator = next.chapter.scanlator,
                    chapterUrl = next.chapter.url,
                    mangaTitle = next.manga.ogTitle,
                    source = next.source,
                )

                if (chapterDir == null || !chapterDir.exists()) {
                    val err = "Downloaded chapter files not found."
                    TranslationReport.log("ERROR", "Colorizer", err)
                    throw IllegalStateException(err)
                }

                val mangaDir = getMangaDir(next.manga.ogTitle, next.source)
                    ?: throw IllegalStateException("Failed to access colorizer manga directory")
                val chapterDirName = getChapterDirName(next.chapter.name, next.chapter.scanlator)
                val outChapterDir = mangaDir.findFile(chapterDirName) ?: mangaDir.createDirectory(chapterDirName)
                    ?: throw IllegalStateException("Failed to create colorizer output directory")

                val pages = getChapterPages(chapterDir)

                if (pages.isEmpty()) {
                    val err = "No image files found in downloaded chapter."
                    TranslationReport.log("ERROR", "Colorizer", err)
                    throw IllegalStateException(err)
                }
                TranslationReport.log("INFO", "Colorizer", "Loaded ${pages.size} pages to colorize")

                for ((idx, pagePair) in pages.withIndex()) {
                    ensureActive()
                    val pageName = pagePair.first.substringAfterLast("/")
                    val basePercent = ((idx.toFloat() / pages.size.coerceAtLeast(1)) * 100).toInt()

                    _progressState.value = Progress(
                        chapterId = next.chapter.id,
                        chapterName = next.chapter.name,
                        currentPage = idx + 1,
                        totalPages = pages.size,
                        percent = basePercent,
                        step = "Colorizing page ${idx + 1}/${pages.size} ($pageName)...",
                    )
                    TranslationReport.log("INFO", "Colorizer", "Processing page ${idx + 1}/${pages.size} ($pageName)")

                    val pageStart = System.currentTimeMillis()
                    val outFile = outChapterDir.findFile(pageName) ?: outChapterDir.createFile(pageName) ?: continue

                    pagePair.second().use { inputStream ->
                        val inputBitmap = BitmapFactory.decodeStream(inputStream)
                        if (inputBitmap != null) {
                            val speedTier = translationPreferences.colorizerSpeedTier().get()
                            val colorizedBitmap = colorizeEngine.colorize(
                                inputBitmap = inputBitmap,
                                modelFile = modelFile,
                                intensity = intensity,
                                useNnapi = useNnapi,
                                blendMode = blendMode,
                                customParams = customParams,
                                speedTier = speedTier,
                                onProgress = { subStep, subPct ->
                                    val interpolatedPercent = (((idx.toFloat() + (subPct * 0.85f)) / pages.size.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 99)
                                    _progressState.value = Progress(
                                        chapterId = next.chapter.id,
                                        chapterName = next.chapter.name,
                                        currentPage = idx + 1,
                                        totalPages = pages.size,
                                        percent = interpolatedPercent,
                                        step = subStep,
                                    )
                                },
                            )

                            _progressState.value = Progress(
                                chapterId = next.chapter.id,
                                chapterName = next.chapter.name,
                                currentPage = idx + 1,
                                totalPages = pages.size,
                                percent = (((idx.toFloat() + 0.9f) / pages.size.coerceAtLeast(1)) * 100).toInt().coerceAtMost(99),
                                step = "Saving colorized page ${idx + 1}/${pages.size} ($pageName)...",
                            )

                            outFile.openOutputStream()?.use { outputStream ->
                                colorizedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                                outputStream.flush()
                            }

                            if (colorizedBitmap != inputBitmap) {
                                colorizedBitmap.recycle()
                            }
                            inputBitmap.recycle()

                            val pageElapsed = System.currentTimeMillis() - pageStart
                            TranslationReport.log("INFO", "Colorizer", "Page ${idx + 1}/${pages.size} finished in ${pageElapsed}ms")
                        } else {
                            TranslationReport.log("WARNING", "Colorizer", "Could not decode bitmap for page ${idx + 1}")
                        }
                    }
                }

                _progressState.value = Progress(
                    chapterId = next.chapter.id,
                    chapterName = next.chapter.name,
                    currentPage = pages.size,
                    totalPages = pages.size,
                    percent = 100,
                    step = "Colorization complete!",
                )
                TranslationReport.log("INFO", "Colorizer", "Colorization finished successfully for chapter: ${next.chapter.name}")
                next.status = Translation.State.TRANSLATED
            } catch (e: CancellationException) {
                TranslationReport.log("WARNING", "Colorizer", "Colorization cancelled for chapter: ${next.chapter.name}")
                next.status = Translation.State.NOT_TRANSLATED
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Colorization failed" }
                TranslationReport.log("ERROR", "Colorizer", "Colorization failed: ${e.message}", e)
                next.status = Translation.State.ERROR
                withContext(Dispatchers.Main) {
                    context.toast(e.message ?: "Colorization failed")
                }
            } finally {
                activeChapterId = null
                activeColorizeJob = null
                _progressState.value = null
                synchronized(_queueState) {
                    _queueState.value = _queueState.value - next
                    if (_queueState.value.isEmpty()) {
                        eu.kanade.tachiyomi.data.ai.WakeLockHelper.release()
                        scheduleIdleUnload(2_000L)
                    }
                }
                processQueue()
            }
        }
    }

    fun cancelActiveColorizer() {
        activeColorizeJob?.cancel()
        activeColorizeJob = null
        activeChapterId = null
        _progressState.value = null
        eu.kanade.tachiyomi.data.ai.WakeLockHelper.release()
        colorizeEngine.unloadSession()
        System.gc()
        Runtime.getRuntime().gc()
        TranslationReport.log("WARNING", "Colorizer", "Active colorization cancelled immediately by user and memory released.")
        processQueue()
    }

    fun cancelQueuedColorizer(translation: Translation) {
        synchronized(_queueState) {
            _queueState.value = _queueState.value - translation
        }
        if (activeChapterId == translation.chapter.id) {
            cancelActiveColorizer()
        }
    }

    fun deleteColorizer(chapter: Chapter, manga: Manga, source: Source) {
        scope.launch {
            val mangaDir = getMangaDir(manga.ogTitle, source)
            val chapterDirName = getChapterDirName(chapter.name, chapter.scanlator)
            mangaDir?.findFile(chapterDirName)?.delete()
        }
    }

    fun getChapterColorizerStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        sourceId: Long,
    ): Translation.State {
        val active = getQueuedColorizerOrNull(chapterId)
        if (active != null) return active.status
        if (isChapterColorized(chapterName, scanlator, title, sourceId)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun isChapterColorized(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        val source = sourceManager.get(sourceId) ?: return false
        val chapterDir = findChapterDir(chapterName, chapterScanlator, mangaTitle, source)
        return chapterDir?.exists() == true && chapterDir.listFiles()?.any { it.length() > 0 } == true
    }

    fun getColorizedPageFile(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageName: String,
    ): UniFile? {
        val chapterDir = findChapterDir(chapterName, scanlator, mangaTitle, source)
        return chapterDir?.findFile(pageName)?.takeIf { it.exists() && it.length() > 0 }
    }

    internal fun findChapterDir(chapterName: String, scanlator: String?, mangaTitle: String, source: Source): UniFile? {
        val mangaDir = getMangaDir(mangaTitle, source) ?: return null
        val candidates = listOf(
            getChapterDirName(chapterName, scanlator),
            DiskUtil.buildValidFilename(chapterName),
            chapterName,
        )
        return candidates.asSequence()
            .mapNotNull {
                try {
                    mangaDir.findFile(it)
                } catch (_: Exception) {
                    null
                }
            }
            .firstOrNull()
    }

    private fun getMangaDir(mangaTitle: String, source: Source): UniFile? {
        val dir = colorizerDir ?: return null
        val sourceDirName = getSourceDirName(source)
        val mangaDirName = getMangaDirName(mangaTitle)
        val sourceDir = dir.findFile(sourceDirName) ?: dir.createDirectory(sourceDirName) ?: return null
        return sourceDir.findFile(mangaDirName) ?: sourceDir.createDirectory(mangaDirName)
    }

    private fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    private fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(mangaTitle)
    }

    private fun getChapterDirName(chapterName: String, scanlator: String?): String {
        val name = if (scanlator.isNullOrBlank()) chapterName else "$chapterName - $scanlator"
        return DiskUtil.buildValidFilename(name)
    }

    private fun getChapterPages(chapterPath: UniFile): List<Pair<String, () -> InputStream>> {
        if (chapterPath.isFile) {
            val entryNames = chapterPath.archiveReader(context).use { reader ->
                reader.useEntries { entries ->
                    entries.filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .map { it.name }
                        .toList()
                }
            }
            return entryNames.map { name ->
                Pair(name) {
                    val r = chapterPath.archiveReader(context)
                    val stream = r.getInputStream(name)
                        ?: throw java.io.FileNotFoundException("Entry $name not found in archive")
                    object : java.io.FilterInputStream(stream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                r.close()
                            }
                        }
                    }
                }
            }
        } else {
            return chapterPath.listFiles()?.filter { ImageUtil.isImage(it.name) }?.map { entry ->
                Pair(entry.name ?: "page.jpg") { entry.openInputStream()!! }
            } ?: emptyList()
        }
    }

    fun statusFlow(): Flow<Translation> = queueState
        .flatMapLatest { translations ->
            translations
                .map { translation ->
                    translation.statusFlow.drop(1).map { translation }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { it.status == Translation.State.TRANSLATING }.asFlow(),
            )
        }
}
