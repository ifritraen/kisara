package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.KisaraBottomSheet
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TranslationSettingsDialog(
    onDismissRequest: () -> Unit,
) {
    val translationPreferences = remember { Injekt.get<TranslationPreferences>() }

    val autoTranslate by translationPreferences.autoTranslateAfterDownload().collectAsState()
    val fontIndex by translationPreferences.translationFont().collectAsState()
    val fromLang by translationPreferences.translateFromLanguage().collectAsState()
    val toLang by translationPreferences.translateToLanguage().collectAsState()
    val engineIndex by translationPreferences.translationEngine().collectAsState()
    val apiKey by translationPreferences.translationEngineApiKey().collectAsState()
    val model by translationPreferences.translationEngineModel().collectAsState()
    val temp by translationPreferences.translationEngineTemperature().collectAsState()
    val maxTokens by translationPreferences.translationEngineMaxOutputTokens().collectAsState()

    val fonts = TranslationFont.entries
    val fromLangs = TextRecognizerLanguage.entries
    val toLangs = TextTranslatorLanguage.entries
    val engines = TextTranslators.entries
    val colorScheme = MaterialTheme.colorScheme

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(KMR.strings.pref_category_translations),
        footer = {
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SwitchPreferenceWidget(
                    title = stringResource(KMR.strings.pref_translate_after_downloading),
                    checked = autoTranslate,
                    onCheckedChanged = { translationPreferences.autoTranslateAfterDownload().set(it) },
                )

                ListPreferenceWidget(
                    value = fontIndex,
                    title = stringResource(KMR.strings.pref_reader_font),
                    subtitle = fonts.getOrNull(fontIndex)?.label ?: "",
                    icon = null,
                    entries = fonts.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
                    onValueChange = { translationPreferences.translationFont().set(it) },
                )

                ListPreferenceWidget(
                    value = fromLang,
                    title = stringResource(KMR.strings.pref_translate_from),
                    subtitle = fromLangs.find { it.name == fromLang }?.label ?: fromLang,
                    icon = null,
                    entries = fromLangs.associate { it.name to it.label }.toImmutableMap(),
                    onValueChange = { translationPreferences.translateFromLanguage().set(it) },
                )

                ListPreferenceWidget(
                    value = toLang,
                    title = stringResource(KMR.strings.pref_translate_to),
                    subtitle = toLangs.find { it.name == toLang }?.label ?: toLang,
                    icon = null,
                    entries = toLangs.associate { it.name to it.label }.toImmutableMap(),
                    onValueChange = { translationPreferences.translateToLanguage().set(it) },
                )

                ListPreferenceWidget(
                    value = engineIndex,
                    title = stringResource(KMR.strings.pref_translator_engine),
                    subtitle = engines.getOrNull(engineIndex)?.label ?: "",
                    icon = null,
                    entries = engines.withIndex().associate { it.index to it.value.label }.toImmutableMap(),
                    onValueChange = { translationPreferences.translationEngine().set(it) },
                )

                EditTextPreferenceWidget(
                    title = stringResource(KMR.strings.pref_engine_api_key),
                    subtitle = stringResource(KMR.strings.pref_sub_engine_api_key),
                    icon = null,
                    value = apiKey,
                    onConfirm = {
                        translationPreferences.translationEngineApiKey().set(it)
                        true
                    },
                )

                EditTextPreferenceWidget(
                    title = stringResource(KMR.strings.pref_engine_model),
                    subtitle = "%s",
                    icon = null,
                    value = model,
                    onConfirm = {
                        translationPreferences.translationEngineModel().set(it)
                        true
                    },
                )

                EditTextPreferenceWidget(
                    title = stringResource(KMR.strings.pref_engine_temperature),
                    subtitle = "%s",
                    icon = null,
                    value = temp,
                    onConfirm = {
                        translationPreferences.translationEngineTemperature().set(it)
                        true
                    },
                )

                EditTextPreferenceWidget(
                    title = stringResource(KMR.strings.pref_engine_max_output),
                    subtitle = "%s",
                    icon = null,
                    value = maxTokens,
                    onConfirm = {
                        translationPreferences.translationEngineMaxOutputTokens().set(it)
                        true
                    },
                )
            }
        }
    }
}
