/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.ui.player.Anime4KManager
import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.applyAnime4K
import eu.kanade.tachiyomi.ui.player.applyCasSharpening
import eu.kanade.tachiyomi.ui.player.applyDebandMode
import eu.kanade.tachiyomi.ui.player.applyMotionInterpolation
import eu.kanade.tachiyomi.ui.player.applyScaleProfile
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.core.common.preference.deleteAndGet
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun VideoFiltersPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.padding.medium),
    ) {
        val filtersCard = createRef()

        FiltersCard(
            Modifier.constrainAs(filtersCard) {
                linkTo(parent.top, parent.bottom, bias = 0.8f)
                end.linkTo(parent.end)
            },
            onClose = onDismissRequest,
        )
    }
}

@Composable
fun FiltersCard(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
    val anime4kManager = remember { Injekt.get<Anime4KManager>() }
    var selectedTab by remember { mutableIntStateOf(0) }

    Card(
        colors = panelCardsColors(),
        modifier = modifier
            .widthIn(max = CARDS_MAX_WIDTH + 60.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(KMR.strings.player_sheets_filters_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    if (selectedTab == 1) {
                        TextButton(
                            onClick = {
                                VideoFilters.entries.forEach {
                                    MPVLib.setPropertyInt(it.mpvProperty, it.preference(decoderPreferences).deleteAndGet())
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_reset))
                        }
                    }
                    ControlsButton(Icons.Default.Close, onClose)
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(KMR.strings.player_tab_shaders)) },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(KMR.strings.player_tab_adjustments)) },
                    icon = { Icon(Icons.Outlined.Tune, null) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(KMR.strings.player_tab_deband_effects)) },
                    icon = { Icon(Icons.Outlined.BlurOn, null) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> ShadersTabContent(decoderPreferences, anime4kManager)
                1 -> AdjustmentsTabContent(decoderPreferences)
                2 -> DebandEffectsTabContent(decoderPreferences)
            }
        }
    }
}

@Composable
private fun ShadersTabContent(
    decoderPreferences: DecoderPreferences,
    anime4kManager: Anime4KManager,
) {
    val anime4kPreset by decoderPreferences.anime4kShaderPreset().collectAsState()
    val isAnime4kExplicit by decoderPreferences.enableAnime4K().collectAsState()
    val anime4kMode by decoderPreferences.anime4kMode().collectAsState()
    val anime4kQuality by decoderPreferences.anime4kQuality().collectAsState()
    val casSharpening by decoderPreferences.casSharpening().collectAsState()
    val scalerProfile by decoderPreferences.videoScaleProfile().collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = stringResource(KMR.strings.player_shader_anime4k),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val presets = listOf(
                        Anime4KShaderPreset.Off to "Off",
                        Anime4KShaderPreset.ModeA_Balanced to "Mode A",
                        Anime4KShaderPreset.ModeB_Balanced to "Mode B",
                        Anime4KShaderPreset.ModeC_Balanced to "Mode C",
                    )
                    presets.forEach { (preset, label) ->
                        val isSelected = anime4kPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                decoderPreferences.anime4kShaderPreset().set(preset)
                                applyAnime4K(decoderPreferences, anime4kManager)
                            },
                            label = { Text(label, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = stringResource(KMR.strings.player_shader_quality),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("FAST" to "Fast (S)", "BALANCED" to "Balanced (M)", "HIGH" to "High (L)").forEach { (qKey, qLabel) ->
                        val isSelected = anime4kQuality == qKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                decoderPreferences.anime4kQuality().set(qKey)
                                applyAnime4K(decoderPreferences, anime4kManager)
                            },
                            label = { Text(qLabel, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }

        item {
            SliderItem(
                label = stringResource(KMR.strings.player_shader_cas),
                value = casSharpening,
                valueText = "${casSharpening}%",
                onChange = {
                    decoderPreferences.casSharpening().set(it)
                    applyCasSharpening(it)
                },
                max = 100,
                min = 0,
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = stringResource(KMR.strings.player_scaler_profile),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("spline36" to "Spline36", "lanczos" to "Lanczos", "ewa_lanczos" to "EWA Lanczos", "bicubic" to "Bicubic", "bilinear" to "Bilinear").forEach { (sKey, sLabel) ->
                        val isSelected = scalerProfile == sKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                decoderPreferences.videoScaleProfile().set(sKey)
                                applyScaleProfile(sKey)
                            },
                            label = { Text(sLabel, fontSize = 11.sp) },
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AdjustmentsTabContent(
    decoderPreferences: DecoderPreferences,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        items(VideoFilters.entries) { filter ->
            val value by filter.preference(decoderPreferences).collectAsState()
            SliderItem(
                label = stringResource(filter.titleRes),
                value = value,
                valueText = value.toString(),
                onChange = {
                    filter.preference(decoderPreferences).set(it)
                    MPVLib.setPropertyInt(filter.mpvProperty, it)
                },
                max = filter.max,
                min = filter.min,
            )
        }
        item {
            if (decoderPreferences.gpuNext().get()) return@item
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(Icons.Outlined.Info, null)
                Text(stringResource(KMR.strings.player_sheets_filters_warning))
            }
        }
    }
}

@Composable
private fun DebandEffectsTabContent(
    decoderPreferences: DecoderPreferences,
) {
    val debandMode by decoderPreferences.videoDebanding().collectAsState()
    val isDebandEnabled = debandMode != Debanding.None
    val debandThreshold by decoderPreferences.debandThreshold().collectAsState()
    val debandRange by decoderPreferences.debandRange().collectAsState()
    val debandGrain by decoderPreferences.grainFilter().collectAsState()
    val warmTint by decoderPreferences.warmNightLightFilter().collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(KMR.strings.player_deband_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Reduce color banding & artifacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isDebandEnabled,
                    onCheckedChange = { enabled ->
                        val newMode = if (enabled) Debanding.GPU else Debanding.None
                        decoderPreferences.videoDebanding().set(newMode)
                        applyDebandMode(newMode, decoderPreferences)
                    },
                )
            }
        }

        if (isDebandEnabled) {
            item {
                SliderItem(
                    label = stringResource(KMR.strings.player_deband_threshold),
                    value = debandThreshold,
                    valueText = debandThreshold.toString(),
                    onChange = {
                        decoderPreferences.debandThreshold().set(it)
                        MPVLib.setPropertyInt("deband-threshold", it)
                    },
                    max = 128,
                    min = 0,
                )
            }

            item {
                SliderItem(
                    label = stringResource(KMR.strings.player_deband_range),
                    value = debandRange,
                    valueText = debandRange.toString(),
                    onChange = {
                        decoderPreferences.debandRange().set(it)
                        MPVLib.setPropertyInt("deband-range", it)
                    },
                    max = 64,
                    min = 1,
                )
            }

            item {
                SliderItem(
                    label = stringResource(KMR.strings.player_deband_grain),
                    value = debandGrain,
                    valueText = debandGrain.toString(),
                    onChange = {
                        decoderPreferences.grainFilter().set(it)
                        MPVLib.setPropertyInt("deband-grain", it)
                    },
                    max = 100,
                    min = 0,
                )
            }
        }

        item {
            SliderItem(
                label = stringResource(KMR.strings.player_warm_tint),
                value = warmTint,
                valueText = "${warmTint}%",
                onChange = {
                    decoderPreferences.warmNightLightFilter().set(it)
                },
                max = 100,
                min = 0,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
