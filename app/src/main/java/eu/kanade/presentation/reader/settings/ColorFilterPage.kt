package eu.kanade.presentation.reader.settings

import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.ColorFilterMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SwitchItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
    // 1. Brightness & Contrast Sliders
    val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
    SliderItem(
        value = customBrightnessValue,
        valueRange = -75..100,
        steps = 0,
        label = stringResource(MR.strings.pref_custom_brightness),
        onChange = {
            screenModel.preferences.customBrightness().set(it != 0)
            screenModel.preferences.customBrightnessValue().set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    // 3. Image Processing Sliders
    val contrast by screenModel.preferences.colorFilterContrast().collectAsState()
    SliderItem(
        value = (contrast * 100).toInt(),
        valueRange = -100..100,
        steps = 0,
        label = "Contrast",
        onChange = { screenModel.preferences.colorFilterContrast().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val saturation by screenModel.preferences.colorFilterSaturation().collectAsState()
    SliderItem(
        value = (saturation * 100).toInt(),
        valueRange = 0..200,
        steps = 0,
        label = "Saturation",
        onChange = { screenModel.preferences.colorFilterSaturation().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val gamma by screenModel.preferences.colorFilterGamma().collectAsState()
    SliderItem(
        value = (gamma * 100).toInt(),
        valueRange = 50..200,
        steps = 0,
        label = "Gamma Midtones",
        onChange = { screenModel.preferences.colorFilterGamma().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val blackLevel by screenModel.preferences.colorFilterBlackLevel().collectAsState()
    SliderItem(
        value = (blackLevel * 100).toInt(),
        valueRange = 0..50,
        steps = 0,
        label = "Line Art Boost (Blacks)",
        onChange = { screenModel.preferences.colorFilterBlackLevel().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val whiteLevel by screenModel.preferences.colorFilterWhiteLevel().collectAsState()
    SliderItem(
        value = (whiteLevel * 100).toInt(),
        valueRange = 50..100,
        steps = 0,
        label = "Paper Cleaner (Whites)",
        onChange = { screenModel.preferences.colorFilterWhiteLevel().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val warmth by screenModel.preferences.colorFilterWarmth().collectAsState()
    SliderItem(
        value = (warmth * 100).toInt(),
        valueRange = 0..100,
        steps = 0,
        label = "Warmth Eye Comfort",
        onChange = { screenModel.preferences.colorFilterWarmth().set(it / 100f) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    // 4. Custom Color Filter Channels (RGBA)
    val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
        SliderItem(
            value = colorFilterValue.red,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_r_value),
            onChange = { newRValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newRValue, RED_MASK, 16)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.green,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_g_value),
            onChange = { newGValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newGValue, GREEN_MASK, 8)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.blue,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_b_value),
            onChange = { newBValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newBValue, BLUE_MASK, 0)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.alpha,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_a_value),
            onChange = { newAValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newAValue, ALPHA_MASK, 24)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
        SettingsChipRow(MR.strings.pref_color_filter_mode) {
            ColorFilterMode.mapIndexed { index, it ->
                FilterChip(
                    selected = colorFilterMode == index,
                    onClick = { screenModel.preferences.colorFilterMode().set(index) },
                    label = { Text(stringResource(it.first)) },
                )
            }
        }
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
