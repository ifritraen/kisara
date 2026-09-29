package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.LogConsoleWindow
import eu.kanade.presentation.components.ResourceBar
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.ai.AiModelManager
import eu.kanade.tachiyomi.data.ai.ColorizeImageUtils
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

object SettingsColorizerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_colorizer

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val blendModes = persistentListOf(
            1 to "Optimized (Recommended) [Natural Skin + Vibrant + 0.33 Lines]",
            3 to "ColorFul [High Vibrance + Rich Color Mix]",
            2 to "Heavy Darkish Color [Deep Contrast + Heavy Inks]",
            4 to "Brighter [High-Key Bright Midtones]",
            5 to "LessColor [Subtle & Restrained Palette]",
            6 to "V20 [Baseline Natural Skin Warmth]",
            0 to "Testing [Pure AI Colors + Crisp Line Art]",
            7 to "Custom Live Tuning [User Custom Sliders]",
        )

        val speedTiers = persistentListOf(
            1 to "Low (2 Cores) [Battery Saver]",
            2 to "Mid (3 Cores) [Balanced]",
            3 to "High (4 Big Cores) [Fast • Recommended]",
            4 to "Extreme (Max Multi-Core) [Maximum Speed]",
        )

        val engines = persistentListOf(
            0 to "Local On-Device AI (ONNX) [Fast • Offline]",
            1 to "Remote Cloud (Kaggle Server / ngrok) [Needs Account]",
        )

        val engineMode by translationPreferences.colorizerEngine().collectAsState()

        val items = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        // 1. Hardware Resource Overview
        val overviewItems = persistentListOf(
            Preference.PreferenceItem.CustomPreference(
                title = "Hardware & AI Pipeline Overview",
                content = {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ResourceBar(
                            onFreeRamClick = {
                                System.gc()
                                context.toast("RAM freed and GC triggered")
                            },
                        )
                    }
                },
            ),
        )

        // 2. Main Colorizer Settings
        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.useColorizer(),
                title = "Enable Manga Colorization",
                subtitle = "Enables colorization button on downloaded chapters and manga screen",
            ),
        )

        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.colorizerEngine(),
                title = "Colorization Backend",
                entries = engines.associate { it.first to it.second }.toImmutableMap(),
            ),
        )

        items.add(
            Preference.PreferenceItem.TextPreference(
                title = "Interactive Split-Screen Tuner Studio",
                subtitle = "Open full-screen 60 FPS live slider tuner with split-screen preview and page picker",
                onClick = {
                    navigator.push(ColorizerTunerScreen())
                },
            ),
        )

        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.colorizerBlendMode(),
                title = "Color Blending & Enhancement Mode",
                entries = blendModes.associate { it.first to it.second }.toImmutableMap(),
            ),
        )

        val intensityFloat by translationPreferences.colorizerIntensity().collectAsState()
        val intensityDeci = kotlin.math.round((intensityFloat * 10f)).toInt().coerceIn(10, 300)
        items.add(
            Preference.PreferenceItem.SliderPreference(
                value = intensityDeci,
                valueRange = 10..300,
                title = "Color Saturation Intensity",
                subtitle = "Fine-tune color vibrancy across all models & presets (1X - 30X)",
                valueString = "${String.format(Locale.US, "%.1f", intensityDeci / 10.0f)}X",
                onValueChanged = { newVal ->
                    translationPreferences.colorizerIntensity().set(newVal / 10.0f)
                },
            ),
        )

        items.add(
            Preference.PreferenceItem.CustomPreference(
                title = "Granular Color & Tone Tuning Sliders (15 Sliders)",
                content = {
                    ColorizerSettingsTuningCard(translationPreferences = translationPreferences)
                },
            ),
        )

        items.add(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.colorizerSpeedTier(),
                title = "CPU Multi-Threading Speed Tier",
                entries = speedTiers.associate { it.first to it.second }.toImmutableMap(),
            ),
        )

        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoColorizeAfterDownload(),
                title = "Auto-Colorize After Download",
                subtitle = "Automatically colorizes chapter images as soon as a download completes",
            ),
        )

        items.add(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.colorizerUseNnapi(),
                title = "Hardware Acceleration (NNAPI)",
                subtitle = "Uses device NPU/GPU for faster inference (auto-fallback to CPU on error)",
            ),
        )

        // 3. Manga Colorizer Model Card
        items.add(
            Preference.PreferenceItem.CustomPreference(
                title = "Local AI Model File",
                content = {
                    val modelManager = remember { AiModelManager(context) }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ModelDownloadCard(
                            modelType = AiModelManager.ModelType.MANGA_COLORIZER_V2,
                            modelManager = modelManager,
                        )
                    }
                },
            ),
        )

        if (engineMode == 1) {
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

        val diagnosticItems = persistentListOf(
            Preference.PreferenceItem.CustomPreference(
                title = "Live Engine Console & Diagnostics",
                content = {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LogConsoleWindow()
                    }
                },
            ),
        )

        return listOf(
            Preference.PreferenceGroup(
                title = "Hardware Status",
                preferenceItems = overviewItems,
            ),
            Preference.PreferenceGroup(
                title = "Manga Colorization Controls",
                preferenceItems = items.toImmutableList(),
            ),
            Preference.PreferenceGroup(
                title = "Diagnostics & Engine Logs",
                preferenceItems = diagnosticItems,
            ),
        )
    }

    @Composable
    private fun ModelDownloadCard(
        modelType: AiModelManager.ModelType,
        modelManager: AiModelManager,
    ) {
        val context = LocalContext.current
        val downloadState by AiModelManager.getDownloadState(modelType).collectAsState()
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
                        Text(
                            text = if (isDownloading) {
                                if (downloadStatus.isNotBlank()) downloadStatus else "Downloading (${(downloadProgress * 100).toInt()}%)"
                            } else if (isDownloaded) {
                                "Downloaded ($sizeText)"
                            } else {
                                "Not downloaded (~123.4 MB FP32)"
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

    @Composable
    private fun ColorizerSettingsTuningCard(
        translationPreferences: TranslationPreferences,
    ) {
        var skinHue by remember { mutableStateOf(translationPreferences.colorizerSkinHue().get()) }
        var skinSat by remember { mutableStateOf(translationPreferences.colorizerSkinSat().get()) }
        var blueCap by remember { mutableStateOf(translationPreferences.colorizerBlueCap().get()) }
        var redFlush by remember { mutableStateOf(translationPreferences.colorizerRedFlush().get()) }
        var skinMinLum by remember { mutableStateOf(translationPreferences.colorizerSkinMinLum().get()) }
        var paperThresh by remember { mutableStateOf(translationPreferences.colorizerPaperThresh().get()) }
        var paperFeather by remember { mutableStateOf(translationPreferences.colorizerPaperFeather().get()) }
        var gamma by remember { mutableStateOf(translationPreferences.colorizerGamma().get()) }
        var contrast by remember { mutableStateOf(translationPreferences.colorizerContrast().get()) }
        var blackFloor by remember { mutableStateOf(translationPreferences.colorizerBlackFloor().get()) }
        var lineThresh by remember { mutableStateOf(translationPreferences.colorizerLineThresh().get()) }
        var lineExp by remember { mutableStateOf(translationPreferences.colorizerLineExp().get()) }
        var satMul by remember { mutableStateOf(translationPreferences.colorizerSatMul().get()) }
        var colorMix by remember { mutableStateOf(translationPreferences.colorizerColorMix().get()) }
        var clarity by remember { mutableStateOf(translationPreferences.colorizerClarity().get()) }

        var expandedSection by remember { mutableStateOf(1) }

        fun applyPreset(mode: Int) {
            translationPreferences.colorizerBlendMode().set(mode)
            val p = ColorizeImageUtils.ColorizerTuningParams.getPreset(mode)
            skinHue = p.skinHue; translationPreferences.colorizerSkinHue().set(p.skinHue)
            skinSat = p.skinSat; translationPreferences.colorizerSkinSat().set(p.skinSat)
            blueCap = p.blueCap; translationPreferences.colorizerBlueCap().set(p.blueCap)
            redFlush = p.redFlush; translationPreferences.colorizerRedFlush().set(p.redFlush)
            skinMinLum = p.skinMinLum; translationPreferences.colorizerSkinMinLum().set(p.skinMinLum)
            paperThresh = p.paperThresh; translationPreferences.colorizerPaperThresh().set(p.paperThresh)
            paperFeather = p.paperFeather; translationPreferences.colorizerPaperFeather().set(p.paperFeather)
            gamma = p.gamma; translationPreferences.colorizerGamma().set(p.gamma)
            contrast = p.contrast; translationPreferences.colorizerContrast().set(p.contrast)
            blackFloor = p.blackFloor; translationPreferences.colorizerBlackFloor().set(p.blackFloor)
            lineThresh = p.lineThresh; translationPreferences.colorizerLineThresh().set(p.lineThresh)
            lineExp = p.lineExp; translationPreferences.colorizerLineExp().set(p.lineExp)
            satMul = p.satMul; translationPreferences.colorizerSatMul().set(p.satMul)
            colorMix = p.colorMix; translationPreferences.colorizerColorMix().set(p.colorMix)
            clarity = p.clarity; translationPreferences.colorizerClarity().set(p.clarity)
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "⚡ QUICK PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        1 to "Optimized",
                        3 to "Colorful",
                        2 to "Heavy Darkish",
                        4 to "Brighter",
                        5 to "Less Color",
                        6 to "V20",
                        0 to "Testing",
                    ).forEach { (mode, name) ->
                        val isSelected = translationPreferences.colorizerBlendMode().get() == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { applyPreset(mode) },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 1: Skin & Tone
                TuningAccordionHeader(
                    title = "1. Skin & Color Tone",
                    isExpanded = expandedSection == 1,
                    onClick = { expandedSection = if (expandedSection == 1) 0 else 1 },
                )
                if (expandedSection == 1) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        TuningSliderItem("Skin Hue", "${skinHue}°", skinHue.toFloat(), -180f..180f, 72) {
                            skinHue = it.toInt()
                            translationPreferences.colorizerSkinHue().set(skinHue)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Skin Saturation", "$skinSat%", skinSat.toFloat(), 0f..100f, 100) {
                            skinSat = it.toInt()
                            translationPreferences.colorizerSkinSat().set(skinSat)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Blue / Slate Cap", "$blueCap%", blueCap.toFloat(), 0f..100f, 100) {
                            blueCap = it.toInt()
                            translationPreferences.colorizerBlueCap().set(blueCap)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Red Warmth Flush", "$redFlush%", redFlush.toFloat(), 50f..200f, 150) {
                            redFlush = it.toInt()
                            translationPreferences.colorizerRedFlush().set(redFlush)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Skin Min Luminance", "$skinMinLum%", skinMinLum.toFloat(), 0f..80f, 80) {
                            skinMinLum = it.toInt()
                            translationPreferences.colorizerSkinMinLum().set(skinMinLum)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Section 2: Paper & Contrast
                TuningAccordionHeader(
                    title = "2. Paper Whitening & Contrast",
                    isExpanded = expandedSection == 2,
                    onClick = { expandedSection = if (expandedSection == 2) 0 else 2 },
                )
                if (expandedSection == 2) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        TuningSliderItem("Paper Whitening Threshold", "$paperThresh%", paperThresh.toFloat(), 70f..100f, 30) {
                            paperThresh = it.toInt()
                            translationPreferences.colorizerPaperThresh().set(paperThresh)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Paper Feathering", "$paperFeather%", paperFeather.toFloat(), 0f..20f, 20) {
                            paperFeather = it.toInt()
                            translationPreferences.colorizerPaperFeather().set(paperFeather)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Midtone Gamma", "$gamma", gamma.toFloat(), 20f..200f, 180) {
                            gamma = it.toInt()
                            translationPreferences.colorizerGamma().set(gamma)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Contrast S-Curve", "$contrast", contrast.toFloat(), 0f..100f, 100) {
                            contrast = it.toInt()
                            translationPreferences.colorizerContrast().set(contrast)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Deep Black Floor", "$blackFloor", blackFloor.toFloat(), 0f..50f, 50) {
                            blackFloor = it.toInt()
                            translationPreferences.colorizerBlackFloor().set(blackFloor)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Section 3: Line Art & Vibrance
                TuningAccordionHeader(
                    title = "3. Line Art & Color Vibrance",
                    isExpanded = expandedSection == 3,
                    onClick = { expandedSection = if (expandedSection == 3) 0 else 3 },
                )
                if (expandedSection == 3) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        TuningSliderItem("Line Inking Threshold", "$lineThresh%", lineThresh.toFloat(), 0f..100f, 100) {
                            lineThresh = it.toInt()
                            translationPreferences.colorizerLineThresh().set(lineThresh)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Line Inking Exponent", "$lineExp%", lineExp.toFloat(), 50f..400f, 350) {
                            lineExp = it.toInt()
                            translationPreferences.colorizerLineExp().set(lineExp)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Saturation Multiplier", "$satMul%", satMul.toFloat(), 50f..300f, 250) {
                            satMul = it.toInt()
                            translationPreferences.colorizerSatMul().set(satMul)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Color Mix Ratio", "$colorMix%", colorMix.toFloat(), 0f..200f, 200) {
                            colorMix = it.toInt()
                            translationPreferences.colorizerColorMix().set(colorMix)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                        TuningSliderItem("Clarity & Sharpness", "$clarity%", clarity.toFloat(), 50f..300f, 250) {
                            clarity = it.toInt()
                            translationPreferences.colorizerClarity().set(clarity)
                            translationPreferences.colorizerBlendMode().set(7)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TuningAccordionHeader(
        title: String,
        isExpanded: Boolean,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun TuningSliderItem(
        label: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
