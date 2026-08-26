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

package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import logcat.logcat

fun applyFilter(filter: VideoFilters, value: Int, prefs: DecoderPreferences) {
    val property = filter.mpvProperty
    MPVLib.setPropertyInt(property, value)
}

fun applyDebandMode(mode: Debanding, prefs: DecoderPreferences) {
    when (mode) {
        Debanding.None -> {
            MPVLib.setOptionString("deband", "no")
            MPVLib.command(arrayOf("vf", "remove", "@deband"))
        }
        Debanding.CPU -> {
            MPVLib.setOptionString("deband", "no")
            MPVLib.command(arrayOf("vf", "add", "@deband:gradfun=radius=12"))
        }
        Debanding.GPU -> {
            MPVLib.setOptionString("deband", "yes")
            MPVLib.command(arrayOf("vf", "remove", "@deband"))
            // Apply GPU debanding settings
            DebandSettings.entries.forEach {
                MPVLib.setPropertyInt(it.mpvProperty, it.preference(prefs).get())
            }
        }
    }
}

fun applyDebandSetting(setting: DebandSettings, value: Int) {
    MPVLib.setPropertyInt(setting.mpvProperty, value)
}

fun applyHighQualityScaling(enabled: Boolean, isInit: Boolean = false) {
    val scaler = if (enabled) "spline36" else "bilinear"
    val dither = if (enabled) "fruit" else "no"

    if (isInit) {
        MPVLib.setOptionString("scale", scaler)
        MPVLib.setOptionString("cscale", scaler)
        MPVLib.setOptionString("dscale", scaler)
        MPVLib.setOptionString("dither", dither)
    } else {
        MPVLib.setPropertyString("scale", scaler)
        MPVLib.setPropertyString("cscale", scaler)
        MPVLib.setPropertyString("dscale", scaler)
        MPVLib.setPropertyString("dither", dither)
    }
}

fun buildVFChain(decoderPreferences: DecoderPreferences): String {
    val useYuv420p = decoderPreferences.useYUV420P().get()
    return if (useYuv420p) {
        "format=yuv420p"
    } else {
        ""
    }
}

/**
 * Builds the mpv `vf` option value from the decoder preferences that map to libavfilter.
 * Returns null when no software filter is needed (GPU debanding uses the `deband` option,
 * not the vf chain).
 */
fun buildVideoFilterChain(debanding: Debanding, useYuv420p: Boolean): String? {
    val filters = buildList {
        when (debanding) {
            Debanding.CPU -> add("gradfun=radius=12")
            Debanding.GPU, Debanding.None -> {}
        }
        if (useYuv420p) add("format=yuv420p")
    }
    return filters.takeIf { it.isNotEmpty() }?.joinToString(",")
}

/**
 * Applies Anime4K GLSL shader chain to MPV.
 * Includes defensive check: disables Anime4K when gpu-next is active to prevent crashes.
 */
fun applyAnime4K(prefs: DecoderPreferences, manager: Anime4KManager, isInit: Boolean = false) {
    val preset = prefs.anime4kShaderPreset().get()
    val isExplicitlyEnabled = prefs.enableAnime4K().get()
    val enabled = isExplicitlyEnabled || preset != Anime4KShaderPreset.Off

    // DEFENSIVE: Anime4K is incompatible with gpu-next in current MPV builds
    val gpuNext = prefs.gpuNext().get()
    if (enabled && gpuNext) {
        logcat("Anime4K", LogPriority.WARN) { "Anime4K is incompatible with gpu-next. Skipping to prevent crashes." }
        if (isInit) {
            MPVLib.setOptionString("glsl-shaders", "")
        } else {
            MPVLib.setPropertyString("glsl-shaders", "")
        }
        return
    }

    val mode = if (isExplicitlyEnabled) {
        try {
            Anime4KManager.Mode.valueOf(prefs.anime4kMode().get())
        } catch (e: Exception) {
            Anime4KManager.Mode.OFF
        }
    } else {
        preset.mode
    }

    val quality = if (isExplicitlyEnabled) {
        try {
            Anime4KManager.Quality.valueOf(prefs.anime4kQuality().get())
        } catch (e: Exception) {
            Anime4KManager.Quality.BALANCED
        }
    } else {
        preset.quality
    }

    // Ensure shaders are unpacked to disk
    manager.initialize()

    val chain = if (enabled && mode != Anime4KManager.Mode.OFF) {
        manager.getShaderChain(mode, quality)
    } else {
        ""
    }

    logcat("Anime4K", LogPriority.DEBUG) { "Applying Anime4K chain (enabled=$enabled, mode=$mode, quality=$quality): $chain" }

    if (isInit) {
        MPVLib.setOptionString("glsl-shaders", chain)
    } else {
        MPVLib.setPropertyString("glsl-shaders", chain)
    }
}
