package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class AppWakeLockMode(val titleRes: StringResource) {
    OFF(KMR.strings.pref_app_wake_lock_off),
    READER_PLAYER_ONLY(KMR.strings.pref_app_wake_lock_reader_player),
    FULL_APP(KMR.strings.pref_app_wake_lock_full_app),
}
