package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class Anime4KShaderPreset(
    val titleRes: StringResource,
    val mode: Anime4KManager.Mode = Anime4KManager.Mode.OFF,
    val quality: Anime4KManager.Quality = Anime4KManager.Quality.BALANCED,
) {
    Off(
        KMR.strings.pref_anime4k_off,
        Anime4KManager.Mode.OFF,
        Anime4KManager.Quality.BALANCED,
    ),
    Balanced(
        KMR.strings.pref_anime4k_balanced,
        Anime4KManager.Mode.B,
        Anime4KManager.Quality.BALANCED,
    ),
    Quality(
        KMR.strings.pref_anime4k_quality,
        Anime4KManager.Mode.A,
        Anime4KManager.Quality.HIGH,
    ),
    ModeA_Fast(
        KMR.strings.pref_anime4k_mode_a,
        Anime4KManager.Mode.A,
        Anime4KManager.Quality.FAST,
    ),
    ModeA_Balanced(
        KMR.strings.pref_anime4k_mode_a,
        Anime4KManager.Mode.A,
        Anime4KManager.Quality.BALANCED,
    ),
    ModeA_High(
        KMR.strings.pref_anime4k_mode_a,
        Anime4KManager.Mode.A,
        Anime4KManager.Quality.HIGH,
    ),
    ModeB_Fast(
        KMR.strings.pref_anime4k_mode_b,
        Anime4KManager.Mode.B,
        Anime4KManager.Quality.FAST,
    ),
    ModeB_Balanced(
        KMR.strings.pref_anime4k_mode_b,
        Anime4KManager.Mode.B,
        Anime4KManager.Quality.BALANCED,
    ),
    ModeB_High(
        KMR.strings.pref_anime4k_mode_b,
        Anime4KManager.Mode.B,
        Anime4KManager.Quality.HIGH,
    ),
    ModeC_Fast(
        KMR.strings.pref_anime4k_mode_c,
        Anime4KManager.Mode.C,
        Anime4KManager.Quality.FAST,
    ),
    ModeC_Balanced(
        KMR.strings.pref_anime4k_mode_c,
        Anime4KManager.Mode.C,
        Anime4KManager.Quality.BALANCED,
    ),
    ModeC_High(
        KMR.strings.pref_anime4k_mode_c,
        Anime4KManager.Mode.C,
        Anime4KManager.Quality.HIGH,
    ),
    ModeAA_High(
        KMR.strings.pref_anime4k_mode_a_plus,
        Anime4KManager.Mode.A_PLUS,
        Anime4KManager.Quality.HIGH,
    ),
    ModeBB_High(
        KMR.strings.pref_anime4k_mode_b_plus,
        Anime4KManager.Mode.B_PLUS,
        Anime4KManager.Quality.HIGH,
    ),
    ModeCA_High(
        KMR.strings.pref_anime4k_mode_c_plus,
        Anime4KManager.Mode.C_PLUS,
        Anime4KManager.Quality.HIGH,
    ),
    ;

    fun shaderPaths(): List<String> {
        val q = quality.suffix
        if (mode == Anime4KManager.Mode.OFF) return emptyList()
        val list = mutableListOf("Anime4K_Clamp_Highlights.glsl")
        when (mode) {
            Anime4KManager.Mode.A -> {
                list.add("Anime4K_Restore_CNN_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.B -> {
                list.add("Anime4K_Restore_CNN_Soft_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.C -> {
                list.add("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.A_PLUS -> {
                list.add("Anime4K_Restore_CNN_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Restore_CNN_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.B_PLUS -> {
                list.add("Anime4K_Restore_CNN_Soft_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Restore_CNN_Soft_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.C_PLUS -> {
                list.add("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl")
                list.add("Anime4K_AutoDownscalePre_x2.glsl")
                list.add("Anime4K_Restore_CNN_$q.glsl")
                list.add("Anime4K_Upscale_CNN_x2_$q.glsl")
            }
            Anime4KManager.Mode.OFF -> {}
        }
        return list
    }

    fun mpvShaderOption(): String? = shaderPaths().takeIf { it.isNotEmpty() }?.joinToString(":") {
        "~~/shaders/$it"
    }
}
