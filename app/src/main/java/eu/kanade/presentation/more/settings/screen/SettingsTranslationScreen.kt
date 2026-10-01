// KMK -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.TranslationReport
import eu.kanade.translation.recognizer.BubbleDetector
import eu.kanade.translation.recognizer.MangaOcrTextRecognizer
import eu.kanade.translation.recognizer.PaddleOcrTextRecognizer
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsTranslationScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val entries = TranslationFont.entries
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        return listOf(
            getOverviewStatusGroup(translationPreferences),
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoTranslateAfterDownload(),
                title = stringResource(KMR.strings.pref_translate_after_downloading),
                subtitle = "Automatically runs translation when a chapter completes downloading",
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationFont(),
                title = stringResource(KMR.strings.pref_reader_font),
                entries = entries.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
            ),
            getTranslationLangGroup(translationPreferences),
            getTranslatioEngineGroup(translationPreferences),
            getBubbleDetectionGroup(translationPreferences),
            getOcrGroup(translationPreferences),
            getTranslatioAdvancedGroup(translationPreferences),
            getSuperResolutionGroup(translationPreferences),
            getDiagnosticGroup(translationPreferences),
        )
    }

    @Composable
    private fun getOverviewStatusGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "Hardware & AI Pipeline Overview",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = "Live Hardware Monitor",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            eu.kanade.presentation.components.ResourceBar(
                                onFreeRamClick = {
                                    System.gc()
                                },
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "System Status Dashboard",
                    content = {
                        PipelineStatusOverviewCard(translationPreferences = translationPreferences)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getTranslationLangGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val fromLangs = TextRecognizerLanguage.entries
        val toLangs = TextTranslatorLanguage.entries
        return Preference.PreferenceGroup(
            title = "1. Source & Target Languages",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateFromLanguage(),
                    title = stringResource(KMR.strings.pref_translate_from),
                    entries = fromLangs.associate { it.name to it.label }.toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateToLanguage(),
                    title = stringResource(KMR.strings.pref_translate_to),
                    entries = toLangs.associate { it.name to it.label }.toImmutableMap(),
                ),
            ),
        )
    }

    @Composable
    private fun getTranslatioEngineGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val currentEngine by translationPreferences.translationEngine().collectAsState()
        val isOpenAiMode = currentEngine == TextTranslators.OPENAI_COMPATIBLE.ordinal
        val isVisionMode = currentEngine == TextTranslators.GEMINI_VISION.ordinal

        val engineDisplayNames = mapOf(
            TextTranslators.GOOGLE.ordinal to "Google Translate [FREE • Ready • No Setup]",
            TextTranslators.MLKIT.ordinal to "MLKit [FREE • Offline • On-Device]",
            TextTranslators.OPENAI_COMPATIBLE.ordinal to "DeepSeek / OpenAI / Qwen [LLM • Needs API Key]",
            TextTranslators.GEMINI_VISION.ordinal to "Gemini Multimodal Vision [End-to-End • Needs Key]",
            TextTranslators.GEMINI.ordinal to "Gemini AI Text [Needs Gemini Key]",
            TextTranslators.OPENROUTER.ordinal to "OpenRouter [Multi-LLM • Needs Key]",
        )

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationEngine(),
                title = stringResource(KMR.strings.pref_translator_engine),
                entries = engineDisplayNames.toImmutableMap(),
            ),
        )

        if (isOpenAiMode) {
            items.add(
                Preference.PreferenceItem.CustomPreference(
                    title = "LLM Provider Presets",
                    content = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = "Quick Preset Configuration",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        translationPreferences.translationEngineEndpoint().set("https://api.deepseek.com/v1/chat/completions")
                                        translationPreferences.translationEngineModel().set("deepseek-chat")
                                        context.toast("DeepSeek preset loaded")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("DeepSeek")
                                }
                                Button(
                                    onClick = {
                                        translationPreferences.translationEngineEndpoint().set("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                                        translationPreferences.translationEngineModel().set("qwen-plus")
                                        context.toast("Qwen preset loaded")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Qwen")
                                }
                                Button(
                                    onClick = {
                                        translationPreferences.translationEngineEndpoint().set("https://api.openai.com/v1/chat/completions")
                                        translationPreferences.translationEngineModel().set("gpt-4o-mini")
                                        context.toast("OpenAI preset loaded")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("GPT-4o")
                                }
                            }
                        }
                    },
                ),
            )
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineEndpoint(),
                    title = "API Endpoint URL",
                    subtitle = "OpenAI-format completions URL (/v1/chat/completions)",
                ),
            )
        }

        if (currentEngine != TextTranslators.MLKIT.ordinal && currentEngine != TextTranslators.GOOGLE.ordinal) {
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineApiKey(),
                    subtitle = stringResource(KMR.strings.pref_sub_engine_api_key),
                    title = stringResource(KMR.strings.pref_engine_api_key),
                ),
            )
            items.add(
                Preference.PreferenceItem.CustomPreference(
                    title = "API Key Management",
                    content = {
                        ApiKeysPreference(translationPreferences = translationPreferences)
                    },
                ),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_engine),
            preferenceItems = items.toImmutableList(),
        )
    }

    @Composable
    private fun getTranslatioAdvancedGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineModel(),
                    title = stringResource(KMR.strings.pref_engine_model),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineTemperature(),
                    title = stringResource(KMR.strings.pref_engine_temperature),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineMaxOutputTokens(),
                    title = stringResource(KMR.strings.pref_engine_max_output),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Translation Concurrency",
                    content = {
                        ConcurrencyPreference(translationPreferences = translationPreferences)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getBubbleDetectionGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val context = androidx.compose.ui.platform.LocalContext.current
        val modelManager = remember { eu.kanade.tachiyomi.data.ai.AiModelManager(context) }
        return Preference.PreferenceGroup(
            title = "2. Speech Balloon Detection & Layout Analysis",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.bubbleDetectionEnabled(),
                    title = "Enable Speech Balloon Detection",
                    subtitle = "Detects speech balloon boundaries with Comic-Text-Detector AI before OCR",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Comic Text Detector Model (~17.5 MB)",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.COMIC_TEXT_DETECTOR,
                                modelManager = modelManager,
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.bubbleGroupingEnabled(),
                    title = "Speech Bubble Line Grouping",
                    subtitle = "Combines multi-line vertical fragments inside bubbles into coherent sentences",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.bubbleAssignmentMode(),
                    title = "Bubble Assignment Algorithm",
                    entries = mapOf(
                        0 to "Point-in-Polygon Centroid [Enclosure Contained • Recommended]",
                        1 to "Spatial Proximity DSU [Distance-based Clustering]",
                    ).toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.lineSortingOrder(),
                    title = "Line Spatial Sorting Order",
                    entries = mapOf(
                        0 to "Auto / RTL Manga (Right-to-Left Columns: X↓, Y↑)",
                        1 to "LTR Webtoon / Western (Top-to-Bottom: Y↑, X↑)",
                        2 to "Disabled (Raw Detection Order)",
                    ).toImmutableMap(),
                ),
            ),
        )
    }

    @Composable
    private fun getOcrGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val context = androidx.compose.ui.platform.LocalContext.current
        return Preference.PreferenceGroup(
            title = "3. Text Recognition Engine (OCR)",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.ocrEngine(),
                    title = stringResource(KMR.strings.pref_ocr_engine),
                    entries = mapOf(
                        0 to "MLKit OCR [FREE • Built-in • Multi-Language]",
                        1 to "MangaOCR [Offline Japanese • High Accuracy ViT]",
                        2 to "PaddleOCR [Offline Multilingual • Fast]",
                    ).toImmutableMap(),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Offline OCR Models",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ModelDownloadPreference(
                                title = "MangaOCR (Offline Japanese ViT)",
                                subtitle = "MangaOCR Japanese ViT models (tap to download)",
                                modelDirName = "mangaocr",
                                onDownload = { onProgress ->
                                    val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
                                    val recognizer = MangaOcrTextRecognizer(context, fromLang)
                                    recognizer.engine.downloadModels(onProgress)
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ModelDownloadPreference(
                                title = "PaddleOCR (PP-OCRv5 Multilingual)",
                                subtitle = "PaddleOCR PP-OCRv5 multilingual models (tap to download)",
                                modelDirName = "paddleocr",
                                onDownload = { onProgress ->
                                    val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
                                    val recognizer = PaddleOcrTextRecognizer(context, fromLang)
                                    recognizer.ensureModels(onProgress)
                                },
                            )
                        }
                    },
                ),
            ),
        )
    }



    @Composable
    private fun getSuperResolutionGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val srModels = persistentListOf(
            "anime4k_acnet" to "Anime4K ACNet (ONNX • 21.7KB • Ultra Fast 2x Luma)",
            "realesrgan_compact" to "Real-ESRGAN Compact (INT8 • 4.87MB • High Res 2x-4x RGB)",
        )
        val scales = persistentListOf(
            2 to "2x Upscale (Default)",
            4 to "4x Upscale (High Quality)",
        )

        return Preference.PreferenceGroup(
            title = "5. AI Super-Resolution (Upscaler)",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.useSuperResolution(),
                    title = "Enable On-Device Super-Resolution",
                    subtitle = "Upscales downloaded manga pages using fixed-size tiling neural networks",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.superResolutionModel(),
                    title = "Super-Resolution Model",
                    entries = srModels.associate { it.first to it.second }.toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.superResolutionScale(),
                    title = "Upscaling Factor",
                    entries = scales.associate { it.first to it.second }.toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.superResolutionUseNnapi(),
                    title = "Hardware Acceleration (NNAPI)",
                    subtitle = "Uses device NPU/GPU for super-resolution tiling inference",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.autoSuperResolveAfterDownload(),
                    title = "Super-Resolve after Downloading",
                    subtitle = "Automatically upscales downloaded chapters in background",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Super-Resolution Model Files",
                    content = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val modelManager = remember { eu.kanade.tachiyomi.data.ai.AiModelManager(context) }
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.ANIME4K_ACNET,
                                modelManager = modelManager,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.REAL_ESRGAN_COMPACT,
                                modelManager = modelManager,
                            )
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDiagnosticGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "AI & Translation Pipeline Diagnostics",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.translationLoggingEnabled(),
                    title = "Enable AI Pipeline Logging",
                    subtitle = "When disabled, logs for Translation, Colorization, and Super-Resolution will not be recorded.",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Live Engine Console",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            eu.kanade.presentation.components.LogConsoleWindow()
                        }
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "AI & Translation Process Report",
                    content = {
                        TranslationReportPreference()
                    },
                ),
            ),
        )
    }
}

@Composable
private fun PipelineStatusOverviewCard(
    translationPreferences: TranslationPreferences,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelManager = remember { eu.kanade.tachiyomi.data.ai.AiModelManager(context) }

    val currentEngineIndex by translationPreferences.translationEngine().collectAsState()
    val currentEngine = TextTranslators.entries.getOrElse(currentEngineIndex) { TextTranslators.GOOGLE }
    val currentOcrIndex by translationPreferences.ocrEngine().collectAsState()
    val apiKey by translationPreferences.translationEngineApiKey().collectAsState()
    val apiKeysSet by translationPreferences.translationEngineApiKeys().collectAsState()
    val hasApiKey = apiKey.isNotBlank() || apiKeysSet.any { it.isNotBlank() }

    val useColorizer by translationPreferences.useColorizer().collectAsState()
    val colorizerEngine by translationPreferences.colorizerEngine().collectAsState()
    val colorizerModelId by translationPreferences.colorizerModel().collectAsState()

    val useSuperRes by translationPreferences.useSuperResolution().collectAsState()
    val superResModelId by translationPreferences.superResolutionModel().collectAsState()

    // 1. Translation Engine Status
    val (engineTitle, engineStatus, isEngineReady) = when (currentEngine) {
        TextTranslators.GOOGLE -> Triple("Google Translate", "Ready • Free (No API Key Required)", true)
        TextTranslators.MLKIT -> Triple("MLKit (Offline)", "Ready • Free (On-Device Model)", true)
        TextTranslators.GEMINI -> if (hasApiKey) {
            Triple("Gemini AI", "Ready • API Key Configured", true)
        } else {
            Triple("Gemini AI", "⚠️ Action Required • Missing Gemini API Key", false)
        }
        TextTranslators.GEMINI_VISION -> if (hasApiKey) {
            Triple("Gemini Multimodal Vision", "Ready • API Key Configured", true)
        } else {
            Triple("Gemini Multimodal Vision", "⚠️ Action Required • Missing Gemini API Key", false)
        }
        TextTranslators.OPENROUTER -> if (hasApiKey) {
            Triple("OpenRouter", "Ready • API Key Configured", true)
        } else {
            Triple("OpenRouter", "⚠️ Action Required • Missing OpenRouter API Key", false)
        }
        TextTranslators.OPENAI_COMPATIBLE -> if (hasApiKey) {
            val model = translationPreferences.translationEngineModel().get()
            Triple("LLM (${model.ifBlank { "DeepSeek/OpenAI" }})", "Ready • API Key Configured", true)
        } else {
            Triple("OpenAI-Compatible LLM", "⚠️ Action Required • Missing API Key", false)
        }
    }

    // 2. OCR Text Recognizer Status
    val (ocrTitle, ocrStatus, isOcrReady) = when (currentOcrIndex) {
        0 -> Triple("MLKit OCR", "Ready • Built-in (No Downloads Needed)", true)
        1 -> {
            val dir = eu.kanade.tachiyomi.data.ai.AiModelManager.getModelSubdir(context, "mangaocr")
            val internalDir = File(context.filesDir, "mangaocr")
            val size = (dir.listFiles()?.sumOf { it.length() } ?: 0L).let { if (it > 0L) it else internalDir.listFiles()?.sumOf { f -> f.length() } ?: 0L }
            val ready = size > 10 * 1024 * 1024L
            if (ready) Triple("MangaOCR", "Ready • Model Downloaded (~135MB)", true)
            else Triple("MangaOCR", "⚠️ Download Needed • Download MangaOCR below", false)
        }
        2 -> {
            val dir = eu.kanade.tachiyomi.data.ai.AiModelManager.getModelSubdir(context, "paddleocr")
            val internalDir = File(context.filesDir, "paddleocr")
            val size = (dir.listFiles()?.sumOf { it.length() } ?: 0L).let { if (it > 0L) it else internalDir.listFiles()?.sumOf { f -> f.length() } ?: 0L }
            val ready = size > 10 * 1024 * 1024L
            if (ready) Triple("PaddleOCR", "Ready • Model Downloaded (~30MB)", true)
            else Triple("PaddleOCR", "⚠️ Download Needed • Download PaddleOCR below", false)
        }
        else -> Triple("MLKit OCR", "Ready", true)
    }

    // 3. Colorizer Status
    val (colorizerTitle, colorizerStatus, isColorizerReady) = if (!useColorizer) {
        Triple("Manga Colorizer", "Disabled in Settings (Tap below to enable)", true)
    } else if (colorizerEngine == 1) {
        val hasKaggle = translationPreferences.colorizerKaggleApiKey().get().isNotBlank()
        if (hasKaggle) Triple("Kaggle Remote Colorizer", "Ready • Cloud Bridge Configured", true)
        else Triple("Kaggle Remote Colorizer", "⚠️ Action Required • Missing Kaggle API Key", false)
    } else {
        val modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.MANGA_COLORIZER_V2
        val ready = modelManager.isModelDownloaded(modelType)
        if (ready) {
            val size = formatBytes(modelManager.getModelFile(modelType).length())
            Triple(modelType.displayName, "Ready • Local ONNX Model Loaded ($size)", true)
        } else {
            val expected = formatBytes(modelType.minSize)
            Triple(modelType.displayName, "⚠️ Download Needed • Download model below (~$expected)", false)
        }
    }

    // 4. Super-Resolution Status
    val (srTitle, srStatus, isSrReady) = if (!useSuperRes) {
        Triple("Super-Resolution", "Disabled in Settings (Tap below to enable)", true)
    } else {
        val modelType = if (superResModelId == "realesrgan_compact") {
            eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.REAL_ESRGAN_COMPACT
        } else {
            eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.ANIME4K_ACNET
        }
        val ready = modelManager.isModelDownloaded(modelType)
        if (ready) {
            val size = formatBytes(modelManager.getModelFile(modelType).length())
            Triple(modelType.displayName, "Ready • Local ONNX Model Loaded ($size)", true)
        } else {
            val expected = formatBytes(modelType.minSize)
            Triple(modelType.displayName, "⚠️ Download Needed • Download model below (~$expected)", false)
        }
    }

    var showGuideDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live AI & Pipeline Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Overview of active engines, downloaded models & API keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showGuideDialog = true }) {
                        Text("📖 Quick Guide")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                StatusItemRow(
                    category = "Translator",
                    name = engineTitle,
                    status = engineStatus,
                    isReady = isEngineReady,
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusItemRow(
                    category = "OCR Detector",
                    name = ocrTitle,
                    status = ocrStatus,
                    isReady = isOcrReady,
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusItemRow(
                    category = "Colorizer",
                    name = colorizerTitle,
                    status = colorizerStatus,
                    isReady = isColorizerReady,
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusItemRow(
                    category = "Upscaler (SR)",
                    name = srTitle,
                    status = srStatus,
                    isReady = isSrReady,
                )
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("How AI Translation & Enhancements Work") },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .height(380.dp)
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = "1. Zero-Setup / 100% Free Translation:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "• Set Translation Engine = 'Google Translate'\n• Set OCR Engine = 'MLKit (Built-in)'\n👉 Works immediately out-of-the-box! Zero downloads, zero API keys required.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "2. Highest-Quality AI Translation (LLM):",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "• Set Translation Engine = 'DeepSeek / OpenAI / Qwen'\n• Tap 'DeepSeek' preset button and enter your DeepSeek API key.\n• Set OCR Engine = 'Comic Text Detector (ONNX)' and download the model (~94MB).\n👉 Produces natural, context-aware manga dialogues with balloon grouping.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "3. On-Device Manga Colorizer:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "• Enable Manga Colorization.\n• Download 'Manga Colorizer v2 (INT8)' (~61.7MB).\n• Open any downloaded manga, and tap the 'Colorize' button in the chapter list or reader.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "4. AI Super-Resolution (Upscaler):",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "• Enable Super-Resolution.\n• Download 'Anime4K ACNet' (22KB, ultra-fast 2x) or 'Real-ESRGAN' (4.8MB, 4x).\n• Tap the 'Super Resolution' button on any downloaded chapter to upscale all pages.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("Got It")
                }
            },
        )
    }
}

@Composable
private fun StatusItemRow(
    category: String,
    name: String,
    status: String,
    isReady: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isReady) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$category: $name",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 KB"
        bytes < 1024L * 1024L -> String.format("%.1f KB", bytes.toDouble() / 1024.0)
        else -> String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    }
}

private fun getFolderSizeBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    val files = dir.listFiles() ?: return 0L
    return files.sumOf { it.length() }
}

private fun deleteFolder(dir: File) {
    if (dir.exists()) {
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }
}

@Composable
private fun ModelDownloadPreference(
    title: String,
    subtitle: String,
    modelDirName: String,
    onDownload: suspend (onProgress: (String) -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val modelDir = remember { eu.kanade.tachiyomi.data.ai.AiModelManager.getModelSubdir(context, modelDirName) }
    val internalDir = remember { File(context.filesDir, modelDirName) }

    var sizeBytes by remember {
        val s = getFolderSizeBytes(modelDir)
        mutableStateOf(if (s > 0L) s else getFolderSizeBytes(internalDir))
    }
    var downloadStatus by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val isDownloaded = sizeBytes > 1024L

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isDownloading) {
                            downloadStatus
                        } else if (isDownloaded) {
                            "Downloaded (${formatBytes(sizeBytes)})"
                        } else {
                            subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (isDownloaded && !isDownloading) {
                IconButton(onClick = {
                    deleteFolder(modelDir)
                    deleteFolder(internalDir)
                    sizeBytes = getFolderSizeBytes(modelDir)
                    context.toast("Model files deleted")
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (isDownloading) {
                IconButton(onClick = {
                    downloadJob?.cancel()
                    context.toast("Cancelling download...")
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(onClick = {
                    isDownloading = true
                    downloadStatus = "Starting download..."
                    val job = scope.launch {
                        try {
                            onDownload { progress ->
                                downloadStatus = progress
                            }
                            sizeBytes = getFolderSizeBytes(modelDir)
                            context.toast("Download complete!")
                        } catch (e: CancellationException) {
                            context.toast("Download cancelled")
                            sizeBytes = getFolderSizeBytes(modelDir) // Refresh size to show clean state
                        } catch (e: Exception) {
                            errorDialogText = e.stackTraceToString()
                            context.toast("Download failed!")
                        } finally {
                            isDownloading = false
                            downloadJob = null
                        }
                    }
                    downloadJob = job
                }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                    )
                }
            }
        }
        if (isDownloading) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
    }

    errorDialogText?.let { errorText ->
        AlertDialog(
            onDismissRequest = { errorDialogText = null },
            title = { Text("Download Failed") },
            text = {
                Column {
                    Text(
                        text = "An error occurred while downloading the model files. You can copy the logs below to report the issue.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .height(200.dp)
                            .verticalScroll(scrollState)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.copyToClipboard("Model Download Error", errorText)
                    context.toast("Copied to clipboard")
                }) {
                    Text("Copy Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { errorDialogText = null }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun TranslationReportPreference() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs by TranslationReport.logs.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "View AI & Translation Logs",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (logs.isEmpty()) "No logs recorded yet" else "${logs.size} log entries recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View Logs",
                )
            }
        }
    }

    if (showDialog) {
        val filteredLogs = remember(logs, selectedFilter) {
            when (selectedFilter) {
                "COLORIZER" -> logs.filter { it.component.contains("Colorizer", ignoreCase = true) }
                "SUPER_RES" -> logs.filter { it.component.contains("SuperRes", ignoreCase = true) }
                "TRANSLATION" -> logs.filter { !it.component.contains("Colorizer", ignoreCase = true) && !it.component.contains("SuperRes", ignoreCase = true) }
                "ERRORS" -> logs.filter { it.level == "ERROR" || it.level == "WARNING" }
                else -> logs
            }
        }

        val logText = remember(filteredLogs) {
            if (filteredLogs.isEmpty()) {
                "No logs matching filter '$selectedFilter'."
            } else {
                filteredLogs.joinToString("\n") { entry ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                    val ex = if (entry.exceptionTrace != null) "\n${entry.exceptionTrace}" else ""
                    "[$time] [${entry.level}] [${entry.component}] ${entry.message}$ex"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("AI Pipeline Process Report") },
            text = {
                Column {
                    Text(
                        text = "Real-time diagnostic logs from Translation, Colorization, and Super-Resolution.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            "ALL" to "All (${logs.size})",
                            "COLORIZER" to "Colorizer",
                            "SUPER_RES" to "Super-Res",
                            "TRANSLATION" to "Translation",
                            "ERRORS" to "Errors/Warnings",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .height(280.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(scrollState)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        TranslationReport.clear()
                        context.toast("Logs cleared")
                    }) {
                        Text("Clear")
                    }
                    TextButton(onClick = {
                        context.copyToClipboard("AI Pipeline Logs", logText)
                        context.toast("Logs copied to clipboard")
                    }) {
                        Text("Copy")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun ApiKeysPreference(
    translationPreferences: TranslationPreferences,
) {
    val apiKeysSet by translationPreferences.translationEngineApiKeys().collectAsState()
    var keysList by remember(apiKeysSet) { mutableStateOf(apiKeysSet.toList()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = "API Key Rotation Queue",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Enter multiple keys to cycle through them on rate limits/failures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        keysList.forEachIndexed { index, key ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = key,
                    onValueChange = { newValue ->
                        val updated = keysList.toMutableList().apply { set(index, newValue) }
                        keysList = updated
                        translationPreferences.translationEngineApiKeys().set(updated.filter { it.isNotBlank() }.toSet())
                    },
                    label = { Text("API Key #${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = {
                    val updated = keysList.toMutableList().apply { removeAt(index) }
                    keysList = updated
                    translationPreferences.translationEngineApiKeys().set(updated.filter { it.isNotBlank() }.toSet())
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        TextButton(onClick = {
            val updated = keysList + ""
            keysList = updated
        }) {
            Text("+ Add API Key")
        }
    }
}

@Composable
private fun ConcurrencyPreference(
    translationPreferences: TranslationPreferences,
) {
    val concurrency by translationPreferences.translationConcurrency().collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Parallel Translation Pages",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Number of pages processed concurrently (1-8)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = concurrency.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        androidx.compose.material3.Slider(
            value = concurrency.toFloat(),
            onValueChange = { newValue ->
                translationPreferences.translationConcurrency().set(newValue.toInt())
            },
            valueRange = 1f..8f,
            steps = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelDownloadCard(
    modelType: eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType,
    modelManager: eu.kanade.tachiyomi.data.ai.AiModelManager,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloadState by eu.kanade.tachiyomi.data.ai.AiModelManager.getDownloadState(modelType).collectAsState()
    var isDownloaded by remember { mutableStateOf(modelManager.isModelDownloaded(modelType)) }

    LaunchedEffect(downloadState.isDownloading) {
        if (!downloadState.isDownloading) {
            isDownloaded = modelManager.isModelDownloaded(modelType)
        }
    }

    val isDownloading = downloadState.isDownloading
    val downloadProgress = downloadState.progress
    val downloadStatus = downloadState.status

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val file = modelManager.getModelFile(modelType)
                    val sizeText = if (file.exists()) formatBytes(file.length()) else "0 KB"
                    val expectedSizeText = formatBytes(modelType.minSize)
                    Text(
                        text = if (isDownloading) {
                            if (downloadStatus.isNotBlank()) downloadStatus else "Downloading (${(downloadProgress * 100).toInt()}%)"
                        } else if (isDownloaded) {
                            "Downloaded ($sizeText)"
                        } else {
                            "Not downloaded (tap to download)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isDownloaded && !isDownloading) {
                    IconButton(onClick = {
                        modelManager.deleteModel(modelType)
                        isDownloaded = modelManager.isModelDownloaded(modelType)
                        context.toast("Model deleted")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Model",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (isDownloading) {
                    IconButton(onClick = {
                        modelManager.cancelDownload(modelType)
                        context.toast("Download cancelled")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Download",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(onClick = {
                        modelManager.startDownload(modelType) { success ->
                            isDownloaded = success
                            if (success) {
                                context.toast("${modelType.displayName} downloaded")
                            } else {
                                context.toast("Download failed from all mirrors")
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Model",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
// KMK <--
