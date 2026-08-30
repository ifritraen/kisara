package eu.kanade.tachiyomi.ui.player.settings

import eu.kanade.tachiyomi.ui.player.Anime4KShaderPreset
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.DecoderPreset
import eu.kanade.tachiyomi.ui.player.MotionInterpolationMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class DecoderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun tryHWDecoding() = preferenceStore.getBoolean("pref_try_hwdec", true)
    fun decoderPreset() = preferenceStore.getEnum("pref_decoder_preset", DecoderPreset.Device)
    fun gpuNext() = preferenceStore.getBoolean("pref_gpu_next", false)
    fun videoDebanding() = preferenceStore.getEnum("pref_video_debanding", Debanding.None)
    fun anime4kShaderPreset() = preferenceStore.getEnum("pref_anime4k_shader_preset", Anime4KShaderPreset.Off)
    fun motionInterpolationMode() = preferenceStore.getEnum(
        "pref_motion_interpolation_mode",
        MotionInterpolationMode.Off,
    )
    fun useYUV420P() = preferenceStore.getBoolean("use_yuv420p", false)
    fun highQualityScaling() = preferenceStore.getBoolean("pref_high_quality_scaling", false)
    fun adaptiveShaderScaling() = preferenceStore.getBoolean("pref_adaptive_shader_scaling", true)

    fun enableAnime4K() = preferenceStore.getBoolean("pref_enable_anime4k", false)
    fun anime4kMode() = preferenceStore.getString("pref_anime4k_mode", "A")
    fun anime4kQuality() = preferenceStore.getString("pref_anime4k_quality", "BALANCED")

    // Non-preferences

    fun brightnessFilter() = preferenceStore.getInt("pref_player_filter_brightness")
    fun saturationFilter() = preferenceStore.getInt("pref_player_filter_saturation")
    fun contrastFilter() = preferenceStore.getInt("pref_player_filter_contrast")
    fun gammaFilter() = preferenceStore.getInt("pref_player_filter_gamma")
    fun hueFilter() = preferenceStore.getInt("pref_player_filter_hue")
    fun sharpenFilter() = preferenceStore.getInt("pref_player_filter_sharpen", 0)
    fun debandFilter() = preferenceStore.getInt("pref_player_filter_deband", 1)
    fun grainFilter() = preferenceStore.getInt("pref_player_filter_grain", 48)
    fun debandThreshold() = preferenceStore.getInt("pref_player_filter_deband_threshold", 32)
    fun debandRange() = preferenceStore.getInt("pref_player_filter_deband_range", 16)
    fun videoFilterTheme() = preferenceStore.getInt("pref_video_filter_theme", 0)
    fun casSharpening() = preferenceStore.getInt("pref_cas_sharpening", 0)
    fun warmNightLightFilter() = preferenceStore.getInt("pref_warm_night_light_filter", 0)
    fun videoScaleProfile() = preferenceStore.getString("pref_video_scale_profile", "spline36")
}
