package eu.kanade.translation

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.recognizer.BubbleDetector
import eu.kanade.translation.recognizer.MangaOcrTextRecognizer
import eu.kanade.translation.recognizer.TextRecognizer
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslator
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import kotlin.math.abs

class ChapterTranslator(
    private val context: Context,
    private val provider: TranslationProvider,
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) {

    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    data class Progress(
        val chapterId: Long,
        val chapterName: String,
        val currentPage: Int,
        val totalPages: Int,
        val step: String,
    )

    private val _progressState = MutableStateFlow<Progress?>(null)
    val progressState = _progressState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var translationJob: Job? = null

    val isRunning: Boolean
        get() = translationJob?.isActive == true

    @Volatile
    var isPaused: Boolean = false

    private var textRecognizer: TextRecognizer
    private var textTranslator: TextTranslator

    // KMK --> optional advanced OCR pipeline
    private var mangaOcrRecognizer: MangaOcrTextRecognizer? = null
    private var bubbleDetector: BubbleDetector? = null
    // KMK <--

    init {
        val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
        val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
        textRecognizer = TextRecognizer(fromLang)
        textTranslator = TextTranslators.fromPref(translationPreferences.translationEngine())
            .build(translationPreferences, fromLang, toLang)
    }

    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != Translation.State.TRANSLATED }
        pending.forEach { if (it.status != Translation.State.QUEUE) it.status = Translation.State.QUEUE }
        isPaused = false
        launchTranslatorJob()
        return pending.isNotEmpty()
    }

    fun stop(reason: String? = null) {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }
            .forEach { it.status = Translation.State.ERROR }
        if (reason != null) return
        isPaused = false
    }

    fun pause() {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }
            .forEach { it.status = Translation.State.QUEUE }
        isPaused = true
    }

    fun clearQueue() {
        cancelTranslatorJob()
        internalClearQueue()
    }

    private fun launchTranslatorJob() {
        if (isRunning) return

        translationJob = scope.launch {
            val activeTranslationFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeTranslations =
                        queue.asSequence().filter { it.status.value <= Translation.State.TRANSLATING.value }
                            .groupBy { it.source }.toList().take(5).map { (_, translations) -> translations.first() }
                    emit(activeTranslations)

                    if (activeTranslations.isEmpty()) break
                    val activeTranslationsErroredFlow =
                        combine(activeTranslations.map(Translation::statusFlow)) { states ->
                            states.contains(Translation.State.ERROR)
                        }.filter { it }
                    activeTranslationsErroredFlow.first()
                }
            }.distinctUntilChanged()
            supervisorScope {
                val translationJobs = mutableMapOf<Translation, Job>()

                activeTranslationFlow.collectLatest { activeTranslations ->
                    val translationJobsToStop = translationJobs.filter { it.key !in activeTranslations }
                    translationJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        translationJobs.remove(download)
                    }

                    val translationsToStart = activeTranslations.filter { it !in translationJobs }
                    translationsToStart.forEach { translation ->
                        translationJobs[translation] = launchTranslationJob(translation)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchTranslationJob(translation: Translation) = launchIO {
        try {
            translateChapter(translation)
            if (translation.status == Translation.State.TRANSLATED) {
                removeFromQueue(translation)
            }
            if (areAllTranslationsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            stop()
        }
    }

    private fun cancelTranslatorJob() {
        translationJob?.cancel()
        translationJob = null
    }

    fun queueChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        if (provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source) != null) return
        if (queueState.value.any { it.chapter.id == chapter.id }) return
        val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
        val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
        val engine = TextTranslators.fromPref(translationPreferences.translationEngine())
        if (engine == TextTranslators.MLKIT && !TextTranslatorLanguage.mlkitSupportedLanguages().contains(toLang)) {
            context.toast(KMR.strings.error_mlkit_language_unsupported)
            return
        }
        val translation = Translation(source, manga, chapter, fromLang, toLang)
        addToQueue(translation)
    }

    private suspend fun translateChapter(translation: Translation) {
        try {
            translateChapterInternal(translation)
        } catch (error: Throwable) {
            translation.status = Translation.State.ERROR
            logcat(LogPriority.ERROR, error)
        } finally {
            _progressState.value = null
        }
    }

    private suspend fun translateChapterInternal(translation: Translation) {
        try {
            // Check if recognizer reinitialization is needed
            if (translation.fromLang != textRecognizer.language) {
                textRecognizer.close()
                textRecognizer = TextRecognizer(translation.fromLang)
            }
            // Check if translator reinitialization is needed
            if (translation.fromLang != textTranslator.fromLang || translation.toLang != textTranslator.toLang) {
                withContext(Dispatchers.IO) {
                    textTranslator.close()
                }
                textTranslator = TextTranslators.fromPref(translationPreferences.translationEngine())
                    .build(translationPreferences, translation.fromLang, translation.toLang)
            }
            // Directory where translations for a manga is stored
            val translationMangaDir = provider.getMangaDir(translation.manga.title, translation.source)

            // translations save file
            val saveFile = provider.getTranslationFileName(translation.chapter.name, translation.chapter.scanlator)

            // Directory where chapter images is stored
            val chapterPath = downloadProvider.findChapterDir(
                chapterName = translation.chapter.name,
                chapterScanlator = translation.chapter.scanlator,
                chapterUrl = translation.chapter.url,
                mangaTitle = translation.manga.title,
                source = translation.source,
            )!!

            val pages = mutableMapOf<String, PageTranslation>()
            val tmpFile = translationMangaDir.createFile("tmp")!!
            val streams = getChapterPages(chapterPath)
            // saving the stream to tmp file cuz i can't get the
            // BitmapFactory.decodeStream() to work with the stream from .cbz archive
            // KMK --> optional advanced pipeline: MangaOCR + BubbleDetector
            val useMangaOcr = translationPreferences.ocrEngine().get() == 1
            val useBubbleDetection = translationPreferences.bubbleDetectionEnabled().get()

            if (useMangaOcr) {
                val recognizer = mangaOcrRecognizer
                    ?: MangaOcrTextRecognizer(context, translation.fromLang).also { mangaOcrRecognizer = it }
                if (!recognizer.isReady) {
                    // Models not downloaded yet — fall back to ML Kit silently
                    withContext(Dispatchers.IO) {
                        streams.forEachIndexed { index, (fileName, streamFn) ->
                            coroutineContext.ensureActive()
                            _progressState.value = Progress(
                                chapterId = translation.chapter.id,
                                chapterName = translation.chapter.name,
                                currentPage = index + 1,
                                totalPages = streams.size,
                                step = "Loading page (Fallback to ML Kit)...",
                            )
                            streamFn().use { tmpFile.openOutputStream().use { out -> it.copyTo(out) } }
                            _progressState.value = Progress(
                                chapterId = translation.chapter.id,
                                chapterName = translation.chapter.name,
                                currentPage = index + 1,
                                totalPages = streams.size,
                                step = "Running MLKit OCR...",
                            )
                            val image = InputImage.fromFilePath(context, tmpFile.uri)
                            val result = textRecognizer.recognize(image)
                            val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
                            val pageTranslation = convertToPageTranslation(blocks, image.width, image.height)
                            if (pageTranslation.blocks.isNotEmpty()) pages[fileName] = pageTranslation
                        }
                    }
                } else {
                    val detector = if (useBubbleDetection) {
                        bubbleDetector ?: BubbleDetector(context).also { bubbleDetector = it }
                    } else {
                        null
                    }

                    withContext(Dispatchers.IO) {
                        streams.forEachIndexed { index, (fileName, streamFn) ->
                            coroutineContext.ensureActive()
                            _progressState.value = Progress(
                                chapterId = translation.chapter.id,
                                chapterName = translation.chapter.name,
                                currentPage = index + 1,
                                totalPages = streams.size,
                                step = "Loading page...",
                            )
                            streamFn().use { tmpFile.openOutputStream().use { out -> it.copyTo(out) } }
                            val fullBitmap = tmpFile.openInputStream().use { BitmapFactory.decodeStream(it) } ?: return@forEachIndexed
                            val regions = if (detector != null) {
                                _progressState.value = Progress(
                                    chapterId = translation.chapter.id,
                                    chapterName = translation.chapter.name,
                                    currentPage = index + 1,
                                    totalPages = streams.size,
                                    step = "Detecting speech bubbles...",
                                )
                                val detected = detector.detect(fullBitmap).map { it.rect }
                                _progressState.value = Progress(
                                    chapterId = translation.chapter.id,
                                    chapterName = translation.chapter.name,
                                    currentPage = index + 1,
                                    totalPages = streams.size,
                                    step = "Bubble detection done (${detected.size} found)",
                                )
                                detected
                            } else {
                                listOf(android.graphics.Rect(0, 0, fullBitmap.width, fullBitmap.height))
                            }

                            val pageTranslation = PageTranslation(imgWidth = fullBitmap.width.toFloat(), imgHeight = fullBitmap.height.toFloat())
                            for ((regionIndex, region) in regions.withIndex()) {
                                _progressState.value = Progress(
                                    chapterId = translation.chapter.id,
                                    chapterName = translation.chapter.name,
                                    currentPage = index + 1,
                                    totalPages = streams.size,
                                    step = "Recognizing text block ${regionIndex + 1}/${regions.size}...",
                                )
                                val crop = android.graphics.Bitmap.createBitmap(
                                    fullBitmap,
                                    region.left.coerceAtLeast(0),
                                    region.top.coerceAtLeast(0),
                                    region.width().coerceAtMost(fullBitmap.width - region.left),
                                    region.height().coerceAtMost(fullBitmap.height - region.top),
                                )
                                val text = recognizer.engine.recognize(crop)
                                if (text.isBlank()) continue
                                pageTranslation.blocks.add(
                                    TranslationBlock(
                                        text = text,
                                        width = region.width().toFloat(),
                                        height = region.height().toFloat(),
                                        x = region.left.toFloat(),
                                        y = region.top.toFloat(),
                                        symWidth = (region.width() / (text.length.coerceAtLeast(1))).toFloat(),
                                        symHeight = (region.height() / (text.length.coerceAtLeast(1))).toFloat(),
                                        angle = if (region.height() > region.width() * 1.3f) 90f else 0f,
                                    ),
                                )
                            }
                            if (pageTranslation.blocks.isNotEmpty()) pages[fileName] = pageTranslation
                        }
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    streams.forEachIndexed { index, (fileName, streamFn) ->
                        coroutineContext.ensureActive()
                        _progressState.value = Progress(
                            chapterId = translation.chapter.id,
                            chapterName = translation.chapter.name,
                            currentPage = index + 1,
                            totalPages = streams.size,
                            step = "Loading page...",
                        )
                        streamFn().use { tmpFile.openOutputStream().use { out -> it.copyTo(out) } }
                        _progressState.value = Progress(
                            chapterId = translation.chapter.id,
                            chapterName = translation.chapter.name,
                            currentPage = index + 1,
                            totalPages = streams.size,
                            step = "Running MLKit OCR...",
                        )
                        val image = InputImage.fromFilePath(context, tmpFile.uri)
                        val result = textRecognizer.recognize(image)
                        val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
                        val pageTranslation = convertToPageTranslation(blocks, image.width, image.height)
                        if (pageTranslation.blocks.isNotEmpty()) pages[fileName] = pageTranslation
                    }
                }
            }
            tmpFile.delete()
            _progressState.value = Progress(
                chapterId = translation.chapter.id,
                chapterName = translation.chapter.name,
                currentPage = streams.size,
                totalPages = streams.size,
                step = "Translating text blocks...",
            )
            withContext(Dispatchers.IO) {
                // Translate the text in blocks , this mutates the original blocks
                textTranslator.translate(pages)
            }
            // Serialize the Map and save to translations json file
            Json.encodeToStream(pages, translationMangaDir.createFile(saveFile)!!.openOutputStream())
            translation.status = Translation.State.TRANSLATED

            // KMK -->
            val fromLabel = translation.fromLang.label
            val toLabel = translation.toLang.label
            val engine = TextTranslators.fromPref(translationPreferences.translationEngine())
            val methodName = when (engine) {
                TextTranslators.MLKIT -> "MlKits"
                TextTranslators.GOOGLE -> "Google Translate"
                TextTranslators.GEMINI -> "Gemini"
                TextTranslators.OPENROUTER -> "OpenRouter"
            }
            withContext(Dispatchers.Main) {
                context.toast(
                    context.stringResource(
                        KMR.strings.translation_completed_toast,
                        fromLabel,
                        toLabel,
                        methodName,
                    ),
                )
            }
            // KMK <--
        } catch (error: Throwable) {
            translation.status = Translation.State.ERROR
            logcat(LogPriority.ERROR, error)
        }
    }

    private fun convertToPageTranslation(blocks: List<Text.TextBlock>, width: Int, height: Int): PageTranslation {
        val translation = PageTranslation(imgWidth = width.toFloat(), imgHeight = height.toFloat())
        for (block in blocks) {
            val bounds = block.boundingBox!!
            val symBounds = block.lines.first().elements.first().symbols.first().boundingBox!!
            translation.blocks.add(
                TranslationBlock(
                    text = block.text,
                    width = bounds.width().toFloat(),
                    height = bounds.height().toFloat(),
                    symWidth = symBounds.width().toFloat(),
                    symHeight = symBounds.height().toFloat(),
                    angle = block.lines.first().angle,
                    x = bounds.left.toFloat(),
                    y = bounds.top.toFloat(),
                ),
            )
        }
        // Smart merge overlapping text blocks
        translation.blocks = smartMergeBlocks(translation.blocks, 50, 30, 30)

        return translation
    }

    // KMK -->
    // DSU-based merge: builds full pairwise graph so multi-column Japanese vertical text
    // (e.g. 3 columns each 20px wide) all gets grouped into one block, not just sequential pairs.
    private fun smartMergeBlocks(
        blocks: List<TranslationBlock>,
        widthThreshold: Int,
        xThreshold: Int,
        yThreshold: Int,
    ): MutableList<TranslationBlock> {
        if (blocks.size < 2) return blocks.toMutableList()

        val parent = IntArray(blocks.size) { it }

        fun find(x: Int): Int {
            var c = x
            while (parent[c] != c) {
                parent[c] = parent[parent[c]]
                c = parent[c]
            }
            return c
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                if (shouldMergeTextBlock(blocks[i], blocks[j], widthThreshold, xThreshold, yThreshold)) {
                    union(i, j)
                }
            }
        }

        val groups = linkedMapOf<Int, MutableList<TranslationBlock>>()
        for (i in blocks.indices) groups.getOrPut(find(i)) { mutableListOf() }.add(blocks[i])

        return groups.values.map { group ->
            group.reduce { acc, b -> mergeTextBlock(acc, b) }
        }.toMutableList()
    }
    // KMK <--

    private fun shouldMergeTextBlock(
        a: TranslationBlock,
        b: TranslationBlock,
        widthThreshold: Int,
        xThreshold: Int,
        yThreshold: Int,
    ): Boolean {
        val isWidthSimilar = (b.width < a.width) || (abs(a.width - b.width) < widthThreshold)
        val isXClose = abs(a.x - b.x) < xThreshold
        val isYClose = (b.y - (a.y + a.height)) < yThreshold
        return isWidthSimilar && isXClose && isYClose
    }

    private fun mergeTextBlock(a: TranslationBlock, b: TranslationBlock): TranslationBlock {
        val newX = kotlin.math.min(a.x, b.x)
        val newY = a.y
        val newWidth = kotlin.math.max(a.x + a.width, b.x + b.width) - newX
        val newHeight = kotlin.math.max(a.y + a.height, b.y + b.height) - newY
        return TranslationBlock(
            a.text + " " + b.text,
            a.translation + " " + b.translation,
            newWidth,
            newHeight,
            newX, newY, a.symHeight,
            a.symWidth, a.angle,
        )
    }

    private fun getChapterPages(chapterPath: UniFile): List<Pair<String, () -> InputStream>> {
        if (chapterPath.isFile) {
            val reader = chapterPath.archiveReader(context)
            return reader.useEntries { entries ->
                entries.filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }.map { entry ->
                        Pair(entry.name) { reader.getInputStream(entry.name)!! }
                    }.toList()
            }
        } else {
            return chapterPath.listFiles()!!.filter { ImageUtil.isImage(it.name) }.map { entry ->
                Pair(entry.name!!) { entry.openInputStream() }
            }.toList()
        }
    }

    private fun areAllTranslationsFinished(): Boolean {
        return queueState.value.none { it.status.value <= Translation.State.TRANSLATING.value }
    }

    private fun addToQueue(translation: Translation) {
        translation.status = Translation.State.QUEUE
        _queueState.update {
            it + translation
        }
    }

    private fun removeFromQueue(translation: Translation) {
        _queueState.update {
            if (translation.status == Translation.State.TRANSLATING || translation.status == Translation.State.QUEUE) {
                translation.status = Translation.State.NOT_TRANSLATED
            }
            it - translation
        }
    }

    private inline fun removeFromQueueIf(predicate: (Translation) -> Boolean) {
        _queueState.update { queue ->
            val translations = queue.filter { predicate(it) }
            translations.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING ||
                    translation.status == Translation.State.QUEUE
                ) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            queue - translations
        }
    }

    fun removeFromQueue(chapter: Chapter) {
        removeFromQueueIf { it.chapter.id == chapter.id }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING ||
                    translation.status == Translation.State.QUEUE
                ) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            emptyList()
        }
    }
}
