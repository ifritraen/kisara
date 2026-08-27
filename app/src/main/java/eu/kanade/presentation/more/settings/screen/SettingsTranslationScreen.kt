// KMK -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoTranslateAfterDownload(),
                title = stringResource(KMR.strings.pref_translate_after_downloading),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationFont(),
                title = stringResource(KMR.strings.pref_reader_font),
                entries = entries.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
            ),
            getTranslationLangGroup(translationPreferences),
            getTranslatioEngineGroup(translationPreferences),
            getOcrGroup(translationPreferences),
            getTranslatioAdvancedGroup(translationPreferences),
            getColorizerGroup(translationPreferences),
            getSuperResolutionGroup(translationPreferences),
            getDiagnosticGroup(translationPreferences),
        )
    }

    @Composable
    private fun getTranslationLangGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val fromLangs = TextRecognizerLanguage.entries
        val toLangs = TextTranslatorLanguage.entries
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_setup),
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
        val engines = TextTranslators.entries
        val currentEngine by translationPreferences.translationEngine().collectAsState()
        val isOpenAiMode = currentEngine == TextTranslators.OPENAI_COMPATIBLE.ordinal
        val isVisionMode = currentEngine == TextTranslators.GEMINI_VISION.ordinal

        val presets = persistentListOf(
            "DeepSeek (Recommended)" to ("https://api.deepseek.com/v1/chat/completions" to "deepseek-chat"),
            "OpenAI (GPT-4o-mini)" to ("https://api.openai.com/v1/chat/completions" to "gpt-4o-mini"),
            "Anthropic Claude 3.5" to ("https://api.anthropic.com/v1/chat/completions" to "claude-3-5-sonnet-20241022"),
            "Alibaba Qwen Plus" to ("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" to "qwen-plus"),
            "Zhipu GLM 4 Flash" to ("https://open.bigmodel.cn/api/paas/v4/chat/completions" to "glm-4-flash"),
            "Moonshot Kimi" to ("https://api.moonshot.ai/v1/chat/completions" to "kimi-k1.5"),
            "OpenRouter" to ("https://openrouter.ai/api/v1/chat/completions" to "google/gemini-2.0-flash-001"),
        )

        val items = mutableListOf<Preference.PreferenceItem>()

        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationEngine(),
                title = stringResource(KMR.strings.pref_translator_engine),
                entries = engines.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
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
    private fun getOcrGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val modelManager = remember { eu.kanade.tachiyomi.data.ai.AiModelManager(context) }
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_ocr),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.ocrEngine(),
                    title = stringResource(KMR.strings.pref_ocr_engine),
                    entries = mapOf(
                        0 to stringResource(KMR.strings.pref_ocr_engine_mlkit),
                        1 to stringResource(KMR.strings.pref_ocr_engine_mangaocr),
                        2 to stringResource(KMR.strings.pref_ocr_engine_paddleocr),
                        3 to "Comic Text Detector (ONNX)",
                    ).toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.bubbleGroupingEnabled(),
                    title = "DSU Speech Bubble Grouping",
                    subtitle = "Merges multi-line vertical fragments into complete coherent sentences",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.bubbleDetectionEnabled(),
                    title = stringResource(KMR.strings.pref_bubble_detection),
                    subtitle = stringResource(KMR.strings.pref_bubble_detection_summary),
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Comic Text Detector Model",
                    content = {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.COMIC_TEXT_DETECTOR,
                                modelManager = modelManager,
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(KMR.strings.pref_download_ocr_model),
                    content = {
                        ModelDownloadPreference(
                            title = stringResource(KMR.strings.pref_download_ocr_model),
                            subtitle = "Downloads the MangaOCR models (~100MB)",
                            modelDirName = "mangaocr",
                            onDownload = { onProgress ->
                                val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
                                val recognizer = MangaOcrTextRecognizer(context, fromLang)
                                recognizer.engine.downloadModels(onProgress)
                            },
                        )
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(KMR.strings.pref_download_paddleocr_model),
                    content = {
                        ModelDownloadPreference(
                            title = stringResource(KMR.strings.pref_download_paddleocr_model),
                            subtitle = stringResource(KMR.strings.pref_download_paddleocr_model_summary),
                            modelDirName = "paddleocr",
                            onDownload = { onProgress ->
                                val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
                                val recognizer = PaddleOcrTextRecognizer(context, fromLang)
                                recognizer.ensureModels(onProgress)
                            },
                        )
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getColorizerGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val engineMode by translationPreferences.colorizerEngine().collectAsState()
        val engines = persistentListOf(
            0 to "Local On-Device AI (ONNX)",
            1 to "Remote Cloud (Kaggle Server / ngrok)",
        )
        val colorizerModels = persistentListOf(
            "manga_colorizer_v2" to "Manga Colorizer v2 (INT8 - Recommended)",
            "deoldify_artistic" to "DeOldify Artistic (INT8)",
            "ddcolor_tiny" to "DDColor Tiny (INT8)",
        )

        val items = mutableListOf<Preference.PreferenceItem>()

        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.useColorizer(),
                title = "Enable Manga Colorization",
                subtitle = "Enables colorization actions on downloaded chapters",
            ),
        )
        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.colorizerEngine(),
                title = "Colorization Backend",
                entries = engines.associate { it.first to it.second }.toImmutableMap(),
            ),
        )

        if (engineMode == 0) {
            // Local on-device AI settings
            items.add(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.colorizerModel(),
                    title = "Local Colorization Model",
                    entries = colorizerModels.associate { it.first to it.second }.toImmutableMap(),
                ),
            )
            items.add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.colorizerUseNnapi(),
                    title = "Hardware Acceleration (NNAPI)",
                    subtitle = "Uses device NPU/GPU for faster inference (auto-fallback to CPU on error)",
                ),
            )
            items.add(
                Preference.PreferenceItem.CustomPreference(
                    title = "Local AI Model Files",
                    content = {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val modelManager = remember { eu.kanade.tachiyomi.data.ai.AiModelManager(context) }
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.MANGA_COLORIZER_V2,
                                modelManager = modelManager,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.DEOLDIFY_ARTISTIC,
                                modelManager = modelManager,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ModelDownloadCard(
                                modelType = eu.kanade.tachiyomi.data.ai.AiModelManager.ModelType.DDCOLOR_TINY,
                                modelManager = modelManager,
                            )
                        }
                    },
                ),
            )
        } else {
            // Remote Kaggle / ngrok cloud server settings
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.colorizerKaggleUsername(),
                    title = "Kaggle Username",
                    subtitle = "Your Kaggle account username",
                ),
            )
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.colorizerKaggleApiKey(),
                    title = "Kaggle API Key",
                    subtitle = "API key generated from Kaggle account settings",
                ),
            )
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.colorizerNgrokAuthToken(),
                    title = "ngrok Auth Token",
                    subtitle = "ngrok tunnel auth token for Kaggle bridge",
                ),
            )
            items.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.colorizerKaggleKernelSlug(),
                    title = "Kaggle Kernel Slug",
                    subtitle = "Default: binitdox/manga-colorizer",
                ),
            )
        }

        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoColorizeAfterDownload(),
                title = "Colorize after Downloading",
                subtitle = "Automatically colorizes downloaded chapters in background",
            ),
        )

        return Preference.PreferenceGroup(
            title = "Manga Colorizer (Local / Cloud)",
            preferenceItems = items.toImmutableList(),
        )
    }

    @Composable
    private fun getSuperResolutionGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val srModels = persistentListOf(
            "anime4k_acnet" to "Anime4K ACNet (ONNX - 2x Luma)",
            "realesrgan_compact" to "Real-ESRGAN Compact (INT8 - 2x-4x RGB)",
        )
        val scales = persistentListOf(
            2 to "2x Upscale (Default)",
            4 to "4x Upscale (High Quality)",
        )

        return Preference.PreferenceGroup(
            title = "Super-Resolution (On-Device AI)",
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
            title = "Diagnostics",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.translationLoggingEnabled(),
                    title = "Enable Translation Logging",
                    subtitle = "When disabled, translation reports and logs will not be recorded.",
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = "Translation Logs & Reports",
                    content = {
                        TranslationReportPreference()
                    },
                ),
            ),
        )
    }
}

private fun getFolderSizeMb(dir: File): Double {
    if (!dir.exists()) return 0.0
    val files = dir.listFiles() ?: return 0.0
    val bytes = files.sumOf { it.length() }
    return bytes.toDouble() / (1024.0 * 1024.0)
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
    val modelDir = remember { File(context.filesDir, modelDirName) }

    var sizeMb by remember { mutableStateOf(getFolderSizeMb(modelDir)) }
    var downloadStatus by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var errorDialogText by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val isDownloaded = sizeMb > 0.1

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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isDownloading) {
                        downloadStatus
                    } else if (isDownloaded) {
                        "Downloaded (${String.format("%.2f", sizeMb)} MB)"
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
                    sizeMb = getFolderSizeMb(modelDir)
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
                            sizeMb = getFolderSizeMb(modelDir)
                            context.toast("Download complete!")
                        } catch (e: CancellationException) {
                            context.toast("Download cancelled")
                            sizeMb = getFolderSizeMb(modelDir) // Refresh size to show clean state
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
                    text = "View Translation Logs",
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
        val logText = remember(logs) {
            if (logs.isEmpty()) {
                "No logs available. Run a translation first."
            } else {
                logs.joinToString("\n") { entry ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                    val ex = if (entry.exceptionTrace != null) "\n${entry.exceptionTrace}" else ""
                    "[$time] [${entry.level}] [${entry.component}] ${entry.message}$ex"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Translation Process Report") },
            text = {
                Column {
                    Text(
                        text = "Logs from the latest translation execution. Copy these to report issues or check pipeline health.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .height(300.dp)
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
                TextButton(onClick = {
                    context.copyToClipboard("Translation Logs", logText)
                    context.toast("Logs copied to clipboard")
                }) {
                    Text("Copy Logs")
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
    val scope = rememberCoroutineScope()
    var isDownloaded by remember { mutableStateOf(modelManager.isModelDownloaded(modelType)) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

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
                    val sizeMb = if (file.exists()) file.length().toDouble() / (1024 * 1024) else 0.0
                    Text(
                        text = if (isDownloading) {
                            if (downloadStatus.isNotBlank()) downloadStatus else "Downloading (${(downloadProgress * 100).toInt()}%)"
                        } else if (isDownloaded) {
                            "Ready (${String.format("%.1f", sizeMb)} MB)"
                        } else {
                            "Not downloaded (~${modelType.minSize / (1024 * 1024)} MB)"
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
                        downloadJob?.cancel()
                        isDownloading = false
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
                        isDownloading = true
                        downloadProgress = 0f
                        downloadStatus = "Connecting..."
                        downloadJob = scope.launch {
                            try {
                                val success = modelManager.downloadModel(
                                    type = modelType,
                                    onProgress = { downloadProgress = it },
                                    onStatus = { downloadStatus = it },
                                )
                                isDownloaded = success
                                if (success) {
                                    context.toast("${modelType.displayName} downloaded")
                                } else {
                                    context.toast("Download failed from all mirrors")
                                }
                            } catch (e: CancellationException) {
                                // Cancelled by user
                            } catch (e: Exception) {
                                context.toast("Error: ${e.message}")
                            } finally {
                                isDownloading = false
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
