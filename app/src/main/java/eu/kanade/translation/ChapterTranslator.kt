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
import eu.kanade.translation.model.TranslationReport
import eu.kanade.translation.recognizer.BubbleDetector
import eu.kanade.translation.recognizer.MangaOcrTextRecognizer
import eu.kanade.translation.recognizer.PaddleOcrTextRecognizer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _isRunning = MutableStateFlow(false)
    val isRunning: Boolean
        get() = _isRunning.value

    @Volatile
    var isPaused: Boolean = false

    private var textRecognizer: TextRecognizer
    private var textTranslator: TextTranslator

    // KMK --> optional advanced OCR pipeline
    private var mangaOcrRecognizer: MangaOcrTextRecognizer? = null
    private var paddleOcrRecognizer: PaddleOcrTextRecognizer? = null
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
        TranslationJob.start(context)
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

    suspend fun translateQueue() = withContext(Dispatchers.IO) {
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

        try {
            _isRunning.value = true
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
        } finally {
            _isRunning.value = false
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
        TranslationJob.stop(context)
    }

    fun queueChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        if (provider.findTranslationFile(chapter.name, chapter.scanlator, manga.ogTitle, source) != null) return
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
            TranslationReport.clear()
            TranslationReport.log("INFO", "Pipeline", "Starting translation for chapter: ${translation.chapter.name}")
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
            val translationMangaDir = provider.getMangaDir(translation.manga.ogTitle, translation.source)

            // translations save file
            val saveFile = provider.getTranslationFileName(translation.chapter.name, translation.chapter.scanlator)

            // Directory where chapter images is stored
            val chapterPath = downloadProvider.findChapterDir(
                chapterName = translation.chapter.name,
                chapterScanlator = translation.chapter.scanlator,
                chapterUrl = translation.chapter.url,
                mangaTitle = translation.manga.ogTitle,
                source = translation.source,
            )!!

            val pages = mutableMapOf<String, PageTranslation>()
            val streams = getChapterPages(chapterPath)
            // saving the stream to tmp file cuz i can't get the
            // BitmapFactory.decodeStream() to work with the stream from .cbz archive
            // KMK --> optional advanced pipeline: MangaOCR + PaddleOCR + BubbleDetector
            val ocrEngine = translationPreferences.ocrEngine().get()
            val useMangaOcr = ocrEngine == 1
            val usePaddleOcr = ocrEngine == 2
            val useBubbleDetection = translationPreferences.bubbleDetectionEnabled().get()

            val ocrEngineReady = if (useMangaOcr) {
                val recognizer = mangaOcrRecognizer ?: MangaOcrTextRecognizer(context, translation.fromLang).also { mangaOcrRecognizer = it }
                recognizer.isReady
            } else if (usePaddleOcr) {
                val recognizer = paddleOcrRecognizer ?: PaddleOcrTextRecognizer(context, translation.fromLang).also { paddleOcrRecognizer = it }
                recognizer.isReady
            } else {
                true
            }

            val detector = if (useBubbleDetection) {
                try {
                    bubbleDetector ?: BubbleDetector(context).also { bubbleDetector = it }
                } catch (e: Throwable) {
                    TranslationReport.log("ERROR", "BubbleDetector", "Failed to initialize BubbleDetector, disabling it", e)
                    null
                }
            } else {
                null
            }

            val concurrencyLimit = translationPreferences.translationConcurrency().get().coerceIn(1, 8)
            val semaphore = Semaphore(concurrencyLimit)
            val completedPages = java.util.concurrent.atomic.AtomicInteger(0)

            withContext(Dispatchers.IO) {
                val deferreds = streams.mapIndexed { index, (fileName, streamFn) ->
                    async {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            TranslationReport.log("INFO", "OCR", "Processing page ${index + 1}/${streams.size} ($fileName)")
                            val activeProgress = (completedPages.get() + 1).coerceAtMost(streams.size)
                            _progressState.value = Progress(
                                chapterId = translation.chapter.id,
                                chapterName = translation.chapter.name,
                                currentPage = activeProgress,
                                totalPages = streams.size,
                                step = "Loading page ${index + 1}...",
                            )
                            val pageTmpFile = translationMangaDir.createFile("tmp_page_$index")!!
                            try {
                                streamFn().use { pageInput -> pageTmpFile.openOutputStream().use { out -> pageInput.copyTo(out) } }
                                val fullBitmap = try {
                                    pageTmpFile.openInputStream().use { BitmapFactory.decodeStream(it) }
                                } catch (e: Throwable) {
                                    TranslationReport.log("ERROR", "OCR", "Failed to decode page ${index + 1}", e)
                                    null
                                } ?: return@withPermit null

                                val regions = if (detector != null && detector.isReady) {
                                    val activeProgress = (completedPages.get() + 1).coerceAtMost(streams.size)
                                    _progressState.value = Progress(
                                        chapterId = translation.chapter.id,
                                        chapterName = translation.chapter.name,
                                        currentPage = activeProgress,
                                        totalPages = streams.size,
                                        step = "Detecting speech bubbles on page ${index + 1}...",
                                    )
                                    try {
                                        val detected = detector.detect(fullBitmap)
                                        TranslationReport.log("INFO", "BubbleDetector", "Detected ${detected.size} speech bubbles/regions on page ${index + 1}")
                                        _progressState.value = Progress(
                                            chapterId = translation.chapter.id,
                                            chapterName = translation.chapter.name,
                                            currentPage = activeProgress,
                                            totalPages = streams.size,
                                            step = "Bubble detection done on page ${index + 1} (${detected.size} found)",
                                        )
                                        detected.ifEmpty {
                                            TranslationReport.log("INFO", "BubbleDetector", "No bubbles detected on page ${index + 1}, using full page")
                                            listOf(BubbleDetector.DetectedRegion(android.graphics.Rect(0, 0, fullBitmap.width, fullBitmap.height), 1f, false))
                                        }
                                    } catch (e: Throwable) {
                                        TranslationReport.log("ERROR", "BubbleDetector", "Bubble detector crashed on page ${index + 1}, fallback to full page", e)
                                        listOf(BubbleDetector.DetectedRegion(android.graphics.Rect(0, 0, fullBitmap.width, fullBitmap.height), 1f, false))
                                    }
                                } else {
                                    listOf(BubbleDetector.DetectedRegion(android.graphics.Rect(0, 0, fullBitmap.width, fullBitmap.height), 1f, false))
                                }

                                val pageTranslation = PageTranslation(imgWidth = fullBitmap.width.toFloat(), imgHeight = fullBitmap.height.toFloat())
                                val activeProgress = (completedPages.get() + 1).coerceAtMost(streams.size)
                                for ((regionIndex, regionObj) in regions.withIndex()) {
                                    val region = regionObj.rect
                                    val isBubbleRegion = regionObj.isBubble
                                    _progressState.value = Progress(
                                        chapterId = translation.chapter.id,
                                        chapterName = translation.chapter.name,
                                        currentPage = activeProgress,
                                        totalPages = streams.size,
                                        step = "Recognizing text block ${regionIndex + 1}/${regions.size} on page ${index + 1}...",
                                    )
                                    val crop = try {
                                        android.graphics.Bitmap.createBitmap(
                                            fullBitmap,
                                            region.left.coerceAtLeast(0),
                                            region.top.coerceAtLeast(0),
                                            region.width().coerceAtMost(fullBitmap.width - region.left),
                                            region.height().coerceAtMost(fullBitmap.height - region.top),
                                        )
                                    } catch (e: Throwable) {
                                        TranslationReport.log("ERROR", "OCR", "Failed to crop region ${regionIndex + 1} on page ${index + 1}", e)
                                        continue
                                    }

                                    val rawBlocks = if (usePaddleOcr && ocrEngineReady) {
                                        runPaddleOcrOnCrop(crop, region)
                                    } else if (useMangaOcr && ocrEngineReady) {
                                        val recognizer = mangaOcrRecognizer!!
                                        val text = try {
                                            recognizer.engine.recognize(crop).also {
                                                TranslationReport.log("INFO", "MangaOCR", "MangaOCR recognized crop ${regionIndex + 1} text: $it")
                                            }
                                        } catch (e: Throwable) {
                                            TranslationReport.log("ERROR", "MangaOCR", "MangaOCR failed on page ${index + 1} crop ${regionIndex + 1}, fallback to MLKit", e)
                                            val fallbackBlocks = runMlKitOcrOnCrop(crop, region)
                                            fallbackBlocks.joinToString("\n") { it.text }
                                        }

                                        if (text.isNotBlank()) {
                                            listOf(
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
                                        } else {
                                            emptyList()
                                        }
                                    } else {
                                        runMlKitOcrOnCrop(crop, region)
                                    }

                                    val blocks = if (isBubbleRegion && rawBlocks.isNotEmpty()) {
                                        val isRtl = translation.fromLang.code.startsWith("ja", ignoreCase = true) ||
                                            translation.fromLang.code.startsWith("zh", ignoreCase = true) ||
                                            translation.fromLang.code.startsWith("ko", ignoreCase = true)

                                        val sortedBlocks = if (isRtl) {
                                            rawBlocks.sortedWith(compareByDescending<TranslationBlock> { it.x }.thenBy { it.y })
                                        } else {
                                            rawBlocks.sortedWith(compareBy<TranslationBlock> { it.y }.thenBy { it.x })
                                        }

                                        val merged = sortedBlocks.reduce { acc, b -> mergeTextBlock(acc, b) }
                                        merged.isBubble = true
                                        merged.width = region.width().toFloat()
                                        merged.height = region.height().toFloat()
                                        merged.x = region.left.toFloat()
                                        merged.y = region.top.toFloat()
                                        listOf(merged)
                                    } else {
                                        rawBlocks
                                    }

                                    for (block in blocks) {
                                        pageTranslation.blocks.add(
                                            TranslationBlock(
                                                text = block.text,
                                                width = block.width,
                                                height = block.height,
                                                x = block.x,
                                                y = block.y,
                                                symWidth = block.symWidth,
                                                symHeight = block.symHeight,
                                                angle = block.angle,
                                                isBubble = block.isBubble,
                                            ),
                                        )
                                    }
                                }

                                if (pageTranslation.blocks.isNotEmpty()) {
                                    val deduped = deduplicateBlocks(pageTranslation.blocks)
                                    pageTranslation.blocks = smartMergeBlocks(deduped, 50, 30, 30)
                                    Pair(fileName, pageTranslation)
                                } else {
                                    null
                                }
                            } finally {
                                try {
                                    pageTmpFile.delete()
                                } catch (e: Exception) {}
                                val completed = completedPages.incrementAndGet()
                                _progressState.value = Progress(
                                    chapterId = translation.chapter.id,
                                    chapterName = translation.chapter.name,
                                    currentPage = completed,
                                    totalPages = streams.size,
                                    step = "Recognized $completed/${streams.size} pages",
                                )
                            }
                        }
                    }
                }
                deferreds.awaitAll().filterNotNull().forEach { (fileName, pageTrans) ->
                    pages[fileName] = pageTrans
                }
            }
            _progressState.value = Progress(
                chapterId = translation.chapter.id,
                chapterName = translation.chapter.name,
                currentPage = streams.size,
                totalPages = streams.size,
                step = "Translating text blocks...",
            )
            withContext(Dispatchers.IO) {
                try {
                    TranslationReport.log("INFO", "Translator", "Translating text blocks using ${textTranslator.javaClass.simpleName}")
                    textTranslator.translate(pages) { completed, total ->
                        _progressState.value = Progress(
                            chapterId = translation.chapter.id,
                            chapterName = translation.chapter.name,
                            currentPage = streams.size,
                            totalPages = streams.size,
                            step = "Translating text blocks ($completed/$total)...",
                        )
                    }
                    TranslationReport.log("INFO", "Translator", "Translation completed successfully")
                } catch (e: Throwable) {
                    TranslationReport.log("ERROR", "Translator", "Translator engine ${textTranslator.javaClass.simpleName} failed", e)
                    if (textTranslator.javaClass.simpleName != "MlKitTranslator") {
                        TranslationReport.log("INFO", "Translator", "Attempting fallback translation with ML Kit")
                        try {
                            val fallbackTranslator = TextTranslators.MLKIT.build(translationPreferences, translation.fromLang, translation.toLang)
                            fallbackTranslator.translate(pages) { completed, total ->
                                _progressState.value = Progress(
                                    chapterId = translation.chapter.id,
                                    chapterName = translation.chapter.name,
                                    currentPage = streams.size,
                                    totalPages = streams.size,
                                    step = "Translating text blocks ($completed/$total)...",
                                )
                            }
                            TranslationReport.log("INFO", "Translator", "Fallback ML Kit translation succeeded")
                        } catch (mlKitErr: Throwable) {
                            TranslationReport.log("ERROR", "Translator", "Fallback ML Kit translation also failed", mlKitErr)
                            throw mlKitErr
                        }
                    } else {
                        throw e
                    }
                }
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

    private fun getOverlapRatio(a: TranslationBlock, b: TranslationBlock): Float {
        val ax1 = a.x
        val ay1 = a.y
        val ax2 = a.x + a.width
        val ay2 = a.y + a.height

        val bx1 = b.x
        val by1 = b.y
        val bx2 = b.x + b.width
        val by2 = b.y + b.height

        val ix1 = maxOf(ax1, bx1)
        val iy1 = maxOf(ay1, by1)
        val ix2 = minOf(ax2, bx2)
        val iy2 = minOf(ay2, by2)

        if (ix1 >= ix2 || iy1 >= iy2) return 0f

        val intersectionArea = (ix2 - ix1) * (iy2 - iy1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height

        if (areaA <= 0f || areaB <= 0f) return 0f

        return intersectionArea / minOf(areaA, areaB)
    }

    private fun deduplicateBlocks(blocks: List<TranslationBlock>): List<TranslationBlock> {
        if (blocks.size < 2) return blocks
        val result = mutableListOf<TranslationBlock>()
        val sorted = blocks.sortedByDescending { it.width * it.height }
        for (block in sorted) {
            var isDuplicate = false
            for (existing in result) {
                val overlap = getOverlapRatio(block, existing)
                if (overlap > 0.6f) {
                    isDuplicate = true
                    if (block.text.length > existing.text.length) {
                        val idx = result.indexOf(existing)
                        if (idx != -1) {
                            result[idx] = block
                        }
                    }
                    break
                }
            }
            if (!isDuplicate) {
                result.add(block)
            }
        }
        return result
    }

    private fun shouldMergeTextBlock(
        a: TranslationBlock,
        b: TranslationBlock,
        widthThreshold: Int,
        xThreshold: Int,
        yThreshold: Int,
    ): Boolean {
        // Condition 1: Vertically sequential (one below the other in the same column)
        val isWidthSimilar = (b.width < a.width) || (abs(a.width - b.width) < widthThreshold)
        val isXClose = abs(a.x - b.x) < xThreshold
        val isYClose = (b.y - (a.y + a.height)) < yThreshold
        if (isWidthSimilar && isXClose && isYClose) return true

        // Condition 2: Side-by-side vertical columns (overlapping vertically, close horizontally)
        val bothVertical = (a.height > a.width * 1.2f) && (b.height > b.width * 1.2f)
        if (bothVertical) {
            val verticalOverlap = maxOf(a.y, b.y) < minOf(a.y + a.height, b.y + b.height)
            val horizontalGap = if (a.x < b.x) b.x - (a.x + a.width) else a.x - (b.x + b.width)
            if (verticalOverlap && horizontalGap < 50) {
                return true
            }
        }

        return false
    }

    private fun mergeTextBlock(a: TranslationBlock, b: TranslationBlock): TranslationBlock {
        val newX = kotlin.math.min(a.x, b.x)
        val newY = a.y
        val newWidth = kotlin.math.max(a.x + a.width, b.x + b.width) - newX
        val newHeight = kotlin.math.max(a.y + a.height, b.y + b.height) - newY
        val mergedText = if (a.text.contains(b.text)) {
            a.text
        } else if (b.text.contains(a.text)) {
            b.text
        } else {
            a.text + " " + b.text
        }
        val mergedTranslation = if (a.translation.contains(b.translation)) {
            a.translation
        } else if (b.translation.contains(a.translation)) {
            b.translation
        } else {
            a.translation + " " + b.translation
        }
        return TranslationBlock(
            mergedText,
            mergedTranslation,
            newWidth,
            newHeight,
            newX, newY, a.symHeight,
            a.symWidth, a.angle,
        )
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

    private suspend fun runMlKitOcrOnCrop(crop: android.graphics.Bitmap, region: android.graphics.Rect): List<TranslationBlock> {
        val isPadded = crop.width < 32 || crop.height < 32
        val finalCrop = if (isPadded) {
            try {
                android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).also { padded ->
                    val canvas = android.graphics.Canvas(padded)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(crop, 0f, 0f, null)
                }
            } catch (e: Throwable) {
                crop
            }
        } else {
            crop
        }

        return try {
            val image = InputImage.fromBitmap(finalCrop, 0)
            val result = textRecognizer.recognize(image)
            val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
            blocks.map { block ->
                val bounds = block.boundingBox!!
                val symBounds = block.lines.firstOrNull()?.elements?.firstOrNull()?.symbols?.firstOrNull()?.boundingBox ?: bounds
                TranslationBlock(
                    text = block.text,
                    width = bounds.width().toFloat(),
                    height = bounds.height().toFloat(),
                    x = (region.left + bounds.left).toFloat(),
                    y = (region.top + bounds.top).toFloat(),
                    symWidth = symBounds.width().toFloat(),
                    symHeight = symBounds.height().toFloat(),
                    angle = block.lines.firstOrNull()?.angle ?: 0f,
                )
            }
        } catch (e: Throwable) {
            TranslationReport.log("ERROR", "MLKitOCR", "MLKit OCR failed on crop region", e)
            emptyList()
        } finally {
            if (isPadded && finalCrop !== crop) {
                finalCrop.recycle()
            }
        }
    }

    private suspend fun runPaddleOcrOnCrop(crop: android.graphics.Bitmap, region: android.graphics.Rect): List<TranslationBlock> {
        return try {
            val image = InputImage.fromBitmap(crop, 0)
            val recognizer = paddleOcrRecognizer ?: PaddleOcrTextRecognizer(context, TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())).also { paddleOcrRecognizer = it }
            val results = recognizer.recognize(image)
            results.map { (text, bounds) ->
                TranslationBlock(
                    text = text,
                    width = bounds.width().toFloat(),
                    height = bounds.height().toFloat(),
                    x = (region.left + bounds.left).toFloat(),
                    y = (region.top + bounds.top).toFloat(),
                    symWidth = (bounds.width() / text.length.coerceAtLeast(1)).toFloat(),
                    symHeight = (bounds.height() / text.length.coerceAtLeast(1)).toFloat(),
                    angle = if (bounds.height() > bounds.width() * 1.3f) 90f else 0f,
                )
            }
        } catch (e: Throwable) {
            TranslationReport.log("ERROR", "PaddleOCR", "PaddleOCR failed on crop region", e)
            emptyList()
        }
    }
}
