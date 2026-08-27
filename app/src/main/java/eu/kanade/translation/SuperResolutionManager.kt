package eu.kanade.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.ai.AiModelManager
import eu.kanade.tachiyomi.data.ai.SuperResolutionEngine
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
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Manages local on-device batch chapter Super-Resolution using ONNX AI models (Anime4K ACNet / Real-ESRGAN).
 * Saves super-resolved chapter images into storageManager.getSuperResolutionDirectory().
 */
class SuperResolutionManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val colorizerManager: ColorizerManager = Injekt.get(),
    private val translationPreferences: tachiyomi.domain.translation.TranslationPreferences = Injekt.get(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val srEngine by lazy { SuperResolutionEngine() }
    private val modelManager by lazy { AiModelManager(context) }

    private val superResDir: UniFile?
        get() = storageManager.getSuperResolutionDirectory()

    fun getQueuedSuperResolutionOrNull(chapterId: Long): Translation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun superResolveChapter(manga: Manga, chapter: Chapter) {
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
        scope.launch {
            val next = synchronized(_queueState) {
                _queueState.value.find { it.status == Translation.State.QUEUE }
            } ?: return@launch

            next.status = Translation.State.TRANSLATING
            try {
                // Ensure model is available
                val prefModelId = translationPreferences.superResolutionModel().get()
                val modelType = when (prefModelId) {
                    "realesrgan_compact" -> AiModelManager.ModelType.REAL_ESRGAN_COMPACT
                    else -> AiModelManager.ModelType.ANIME4K_ACNET
                }

                var modelFile = modelManager.getModelFile(modelType)
                if (!modelFile.exists() || modelFile.length() < modelType.minSize) {
                    val downloaded = modelManager.downloadModel(modelType)
                    if (!downloaded) {
                        val fallbackType = if (modelType != AiModelManager.ModelType.ANIME4K_ACNET) {
                            AiModelManager.ModelType.ANIME4K_ACNET
                        } else {
                            AiModelManager.ModelType.REAL_ESRGAN_COMPACT
                        }
                        modelFile = modelManager.getModelFile(fallbackType)
                        if (!modelFile.exists() || modelFile.length() < fallbackType.minSize) {
                            modelManager.downloadModel(fallbackType)
                        }
                    }
                }

                if (!modelFile.exists()) {
                    throw IllegalStateException("No valid Super-Resolution AI model found.")
                }

                val scale = translationPreferences.superResolutionScale().get().coerceIn(2, 4)
                val useNnapi = translationPreferences.superResolutionUseNnapi().get()

                // Locate source chapter directory (prioritizing Colorized directory if already colorized)
                val colorizedChapterDir = colorizerManager.findChapterDir(
                    chapterName = next.chapter.name,
                    scanlator = next.chapter.scanlator,
                    mangaTitle = next.manga.ogTitle,
                    source = next.source,
                )

                val rawChapterDir = downloadProvider.findChapterDir(
                    chapterName = next.chapter.name,
                    chapterScanlator = next.chapter.scanlator,
                    chapterUrl = next.chapter.url,
                    mangaTitle = next.manga.ogTitle,
                    source = next.source,
                )

                val inputDir = if (colorizedChapterDir != null && colorizedChapterDir.exists() && colorizedChapterDir.listFiles()?.isNotEmpty() == true) {
                    colorizedChapterDir
                } else {
                    rawChapterDir
                }

                if (inputDir == null || !inputDir.exists()) {
                    throw IllegalStateException("Downloaded chapter files not found.")
                }

                val mangaDir = getMangaDir(next.manga.ogTitle, next.source)
                    ?: throw IllegalStateException("Failed to access super-resolution manga directory")
                val chapterDirName = getChapterDirName(next.chapter.name, next.chapter.scanlator)
                val outChapterDir = mangaDir.findFile(chapterDirName) ?: mangaDir.createDirectory(chapterDirName)
                    ?: throw IllegalStateException("Failed to create super-resolution output directory")

                val pages = getChapterPages(inputDir)

                if (pages.isEmpty()) {
                    throw IllegalStateException("No image files in source chapter.")
                }

                for ((idx, pagePair) in pages.withIndex()) {
                    ensureActive()
                    val pageName = pagePair.first.substringAfterLast("/")
                    val outFile = outChapterDir.findFile(pageName) ?: outChapterDir.createFile(pageName) ?: continue

                    pagePair.second().use { inputStream ->
                        val inputBitmap = BitmapFactory.decodeStream(inputStream)
                        if (inputBitmap != null) {
                            val upscaledBitmap = srEngine.upscale(
                                inputBitmap = inputBitmap,
                                modelFile = modelFile,
                                scale = scale,
                                useNnapi = useNnapi,
                            )

                            outFile.openOutputStream()?.use { outputStream ->
                                upscaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                                outputStream.flush()
                            }
                            if (upscaledBitmap != inputBitmap) {
                                upscaledBitmap.recycle()
                            }
                            inputBitmap.recycle()
                        }
                    }
                }

                next.status = Translation.State.TRANSLATED
            } catch (e: CancellationException) {
                next.status = Translation.State.NOT_TRANSLATED
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Super-Resolution failed" }
                next.status = Translation.State.ERROR
            } finally {
                synchronized(_queueState) {
                    _queueState.value = _queueState.value - next
                }
                processQueue()
            }
        }
    }

    fun cancelQueuedSuperResolution(translation: Translation) {
        synchronized(_queueState) {
            _queueState.value = _queueState.value - translation
        }
    }

    fun deleteSuperResolution(chapter: Chapter, manga: Manga, source: Source) {
        scope.launch {
            val mangaDir = getMangaDir(manga.ogTitle, source)
            val chapterDirName = getChapterDirName(chapter.name, chapter.scanlator)
            mangaDir?.findFile(chapterDirName)?.delete()
        }
    }

    fun getChapterSuperResolutionStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        sourceId: Long,
    ): Translation.State {
        val active = getQueuedSuperResolutionOrNull(chapterId)
        if (active != null) return active.status
        if (isChapterSuperResolved(chapterName, scanlator, title, sourceId)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun isChapterSuperResolved(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        val source = sourceManager.get(sourceId) ?: return false
        val chapterDir = findChapterDir(chapterName, chapterScanlator, mangaTitle, source)
        return chapterDir?.exists() == true && chapterDir.listFiles()?.isNotEmpty() == true
    }

    fun getSuperResolutionPageFile(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageName: String,
    ): UniFile? {
        val chapterDir = findChapterDir(chapterName, scanlator, mangaTitle, source)
        return chapterDir?.findFile(pageName)
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
        val dir = superResDir ?: return null
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
