// KMK -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
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
            getDiagnosticGroup(),
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
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_engine),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translationEngine(),
                    title = stringResource(KMR.strings.pref_translator_engine),
                    entries = engines.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineApiKey(),
                    subtitle = stringResource(KMR.strings.pref_sub_engine_api_key),
                    title = stringResource(KMR.strings.pref_engine_api_key),
                ),
            ),
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
            ),
        )
    }

    @Composable
    private fun getOcrGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_group_ocr),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.ocrEngine(),
                    title = stringResource(KMR.strings.pref_ocr_engine),
                    entries = mapOf(
                        0 to stringResource(KMR.strings.pref_ocr_engine_mlkit),
                        1 to stringResource(KMR.strings.pref_ocr_engine_mangaocr),
                    ).toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.bubbleDetectionEnabled(),
                    title = stringResource(KMR.strings.pref_bubble_detection),
                    subtitle = stringResource(KMR.strings.pref_bubble_detection_summary),
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
                    title = stringResource(KMR.strings.pref_download_bubble_model),
                    content = {
                        ModelDownloadPreference(
                            title = stringResource(KMR.strings.pref_download_bubble_model),
                            subtitle = "Downloads the Bubble Detector model (~10MB)",
                            modelDirName = "bubbledetector",
                            onDownload = { onProgress ->
                                BubbleDetector(context).downloadModel(onProgress)
                            },
                        )
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDiagnosticGroup(): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "Diagnostics",
            preferenceItems = persistentListOf(
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
            } else if (!isDownloading) {
                IconButton(onClick = {
                    isDownloading = true
                    downloadStatus = "Starting download..."
                    scope.launch {
                        try {
                            onDownload { progress ->
                                downloadStatus = progress
                            }
                            sizeMb = getFolderSizeMb(modelDir)
                            context.toast("Download complete!")
                        } catch (e: Exception) {
                            errorDialogText = e.stackTraceToString()
                            context.toast("Download failed!")
                        } finally {
                            isDownloading = false
                        }
                    }
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
// KMK <--
