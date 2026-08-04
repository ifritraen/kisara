package eu.kanade.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.tachiyomi.ui.reader.domain.ReaderColorFilter
import kotlin.math.roundToInt

@Composable
fun ColorFilterConfigDialog(
    initialFilter: ReaderColorFilter,
    onDismissRequest: () -> Unit,
    onFilterChanged: (ReaderColorFilter) -> Unit,
    onReset: () -> Unit,
) {
    var brightness by remember { mutableFloatStateOf(initialFilter.brightness) }
    var contrast by remember { mutableFloatStateOf(initialFilter.contrast) }
    var saturation by remember { mutableFloatStateOf(initialFilter.saturation) }
    var gamma by remember { mutableFloatStateOf(initialFilter.gamma) }
    var blackLevel by remember { mutableFloatStateOf(initialFilter.blackLevel) }
    var whiteLevel by remember { mutableFloatStateOf(initialFilter.whiteLevel) }
    var warmth by remember { mutableFloatStateOf(initialFilter.warmth) }
    var isInverted by remember { mutableStateOf(initialFilter.isInverted) }
    var isGrayscale by remember { mutableStateOf(initialFilter.isGrayscale) }
    var isBookBackground by remember { mutableStateOf(initialFilter.isBookBackground) }

    fun notifyChanged() {
        onFilterChanged(
            ReaderColorFilter(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                gamma = gamma,
                blackLevel = blackLevel,
                whiteLevel = whiteLevel,
                warmth = warmth,
                isInverted = isInverted,
                isGrayscale = isGrayscale,
                isBookBackground = isBookBackground,
            ),
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Custom Color Filter",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Special Effects Sliders
                Text("Special Effects", style = MaterialTheme.typography.titleMedium)
                SliderItem(
                    label = "Invert Colors",
                    value = if (isInverted) 1.0f else 0.0f,
                    range = 0.0f..1.0f,
                    valueText = if (isInverted) "100%" else "0%",
                ) {
                    isInverted = it >= 0.5f
                    notifyChanged()
                }
                SliderItem(
                    label = "Grayscale",
                    value = if (isGrayscale) 1.0f else 0.0f,
                    range = 0.0f..1.0f,
                    valueText = if (isGrayscale) "100%" else "0%",
                ) {
                    isGrayscale = it >= 0.5f
                    notifyChanged()
                }
                SliderItem(
                    label = "Sepia / Book Mode",
                    value = if (isBookBackground) 1.0f else 0.0f,
                    range = 0.0f..1.0f,
                    valueText = if (isBookBackground) "100%" else "0%",
                ) {
                    isBookBackground = it >= 0.5f
                    notifyChanged()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tone & Contrast
                Text("Tone & Contrast", style = MaterialTheme.typography.titleMedium)
                SliderItem(
                    label = "Brightness",
                    value = brightness,
                    range = -1.0f..1.0f,
                    valueText = "${(brightness * 100).roundToInt()}%",
                ) {
                    brightness = it
                    notifyChanged()
                }
                SliderItem(
                    label = "Contrast",
                    value = contrast,
                    range = -1.0f..1.0f,
                    valueText = "${(contrast * 100).roundToInt()}%",
                ) {
                    contrast = it
                    notifyChanged()
                }
                SliderItem(
                    label = "Saturation",
                    value = saturation,
                    range = 0.0f..2.0f,
                    valueText = "${(saturation * 100).roundToInt()}%",
                ) {
                    saturation = it
                    notifyChanged()
                }
                SliderItem(
                    label = "Gamma Midtones",
                    value = gamma,
                    range = 0.5f..2.0f,
                    valueText = String.format("%.2f", gamma),
                ) {
                    gamma = it
                    notifyChanged()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scan Cleanup
                Text("Scan Cleanup", style = MaterialTheme.typography.titleMedium)
                SliderItem(
                    label = "Black Boost (Line Art)",
                    value = blackLevel,
                    range = 0.0f..0.5f,
                    valueText = "${(blackLevel * 200).roundToInt()}%",
                ) {
                    blackLevel = it
                    notifyChanged()
                }
                SliderItem(
                    label = "White Cleanup (Paper)",
                    value = whiteLevel,
                    range = 0.5f..1.0f,
                    valueText = "${(whiteLevel * 100).roundToInt()}%",
                ) {
                    whiteLevel = it
                    notifyChanged()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Eye Comfort
                Text("Eye Comfort", style = MaterialTheme.typography.titleMedium)
                SliderItem(
                    label = "Warmth Tint",
                    value = warmth,
                    range = 0.0f..1.0f,
                    valueText = "${(warmth * 100).roundToInt()}%",
                ) {
                    warmth = it
                    notifyChanged()
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            brightness = 0f
                            contrast = 0f
                            saturation = 1.0f
                            gamma = 1.0f
                            blackLevel = 0.0f
                            whiteLevel = 1.0f
                            warmth = 0.0f
                            isInverted = false
                            isGrayscale = false
                            isBookBackground = false
                            onReset()
                        },
                    ) {
                        Text("Reset")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onDismissRequest) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
