package eu.kanade.tachiyomi.ui.player

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class MotionInterpolationMode(
    val titleRes: StringResource,
) {
    Off(KMR.strings.pref_motion_interpolation_off),
    Auto(KMR.strings.pref_motion_interpolation_auto),
    Always(KMR.strings.pref_motion_interpolation_always),
    ;

    fun shouldApply(gpuNextEnabled: Boolean, deviceSupportsInterpolation: Boolean): Boolean {
        if (this == Off) return false
        if (!gpuNextEnabled) return false
        if (!deviceSupportsInterpolation) return false
        return true
    }

    fun mpvOptions(): Map<String, String>? {
        return if (this == Off) {
            null
        } else {
            mapOf(
                "tscale" to "oversample",
                "interpolation" to "yes",
            )
        }
    }
}
