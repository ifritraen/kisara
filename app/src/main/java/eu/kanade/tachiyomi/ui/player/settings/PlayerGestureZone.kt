package eu.kanade.tachiyomi.ui.player.settings

import dev.icerock.moko.resources.StringResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.i18n.kmk.KMR

@Serializable
enum class PlayerZoneAction(val titleRes: StringResource) {
    NONE(KMR.strings.single_action_none),
    PLAY_PAUSE(KMR.strings.single_action_playpause),
    SEEK_BACKWARD_5(KMR.strings.gesture_action_seek_b5),
    SEEK_BACKWARD_10(KMR.strings.gesture_action_seek_b10),
    SEEK_BACKWARD_15(KMR.strings.gesture_action_seek_b15),
    SEEK_BACKWARD_30(KMR.strings.gesture_action_seek_b30),
    SEEK_FORWARD_5(KMR.strings.gesture_action_seek_f5),
    SEEK_FORWARD_10(KMR.strings.gesture_action_seek_f10),
    SEEK_FORWARD_15(KMR.strings.gesture_action_seek_f15),
    SEEK_FORWARD_30(KMR.strings.gesture_action_seek_f30),
    SPEED_UP(KMR.strings.gesture_action_speed_up),
    SPEED_DOWN(KMR.strings.gesture_action_speed_down),
    SPEED_RESET(KMR.strings.gesture_action_speed_reset),
    VOLUME_UP(KMR.strings.gesture_action_volume_up),
    VOLUME_DOWN(KMR.strings.gesture_action_volume_down),
    BRIGHTNESS_UP(KMR.strings.gesture_action_brightness_up),
    BRIGHTNESS_DOWN(KMR.strings.gesture_action_brightness_down),
    TOGGLE_ASPECT_RATIO(KMR.strings.gesture_action_toggle_aspect),
    SCREENSHOT(KMR.strings.long_press_action_screenshot),
    TOGGLE_SUBTITLE(KMR.strings.gesture_action_toggle_subtitle),
    TOGGLE_AUDIO_TRACK(KMR.strings.gesture_action_toggle_audio),
    SHOW_CONTROLS(KMR.strings.gesture_action_show_controls),
}

@Serializable
data class PlayerGestureZoneConfig(
    // 5 columns (0..4) x 2 rows (0..1)
    // Row 0 = Top, Row 1 = Bottom
    // Col 0 = Far Left, Col 1 = Center Left, Col 2 = Middle, Col 3 = Center Right, Col 4 = Far Right
    val topFarLeft: PlayerZoneAction = PlayerZoneAction.SEEK_BACKWARD_10,
    val topCenterLeft: PlayerZoneAction = PlayerZoneAction.SEEK_BACKWARD_5,
    val topMiddle: PlayerZoneAction = PlayerZoneAction.PLAY_PAUSE,
    val topCenterRight: PlayerZoneAction = PlayerZoneAction.SEEK_FORWARD_5,
    val topFarRight: PlayerZoneAction = PlayerZoneAction.SEEK_FORWARD_10,

    val bottomFarLeft: PlayerZoneAction = PlayerZoneAction.SEEK_FORWARD_10,
    val bottomCenterLeft: PlayerZoneAction = PlayerZoneAction.SEEK_FORWARD_5,
    val bottomMiddle: PlayerZoneAction = PlayerZoneAction.PLAY_PAUSE,
    val bottomCenterRight: PlayerZoneAction = PlayerZoneAction.SEEK_BACKWARD_5,
    val bottomFarRight: PlayerZoneAction = PlayerZoneAction.SEEK_BACKWARD_10,
) {
    fun getAction(col: Int, row: Int): PlayerZoneAction {
        return when (row) {
            0 -> when (col) {
                0 -> topFarLeft
                1 -> topCenterLeft
                2 -> topMiddle
                3 -> topCenterRight
                4 -> topFarRight
                else -> topMiddle
            }
            else -> when (col) {
                0 -> bottomFarLeft
                1 -> bottomCenterLeft
                2 -> bottomMiddle
                3 -> bottomCenterRight
                4 -> bottomFarRight
                else -> bottomMiddle
            }
        }
    }

    fun withAction(col: Int, row: Int, action: PlayerZoneAction): PlayerGestureZoneConfig {
        return when (row) {
            0 -> when (col) {
                0 -> copy(topFarLeft = action)
                1 -> copy(topCenterLeft = action)
                2 -> copy(topMiddle = action)
                3 -> copy(topCenterRight = action)
                4 -> copy(topFarRight = action)
                else -> this
            }
            else -> when (col) {
                0 -> copy(bottomFarLeft = action)
                1 -> copy(bottomCenterLeft = action)
                2 -> copy(bottomMiddle = action)
                3 -> copy(bottomCenterRight = action)
                4 -> copy(bottomFarRight = action)
                else -> this
            }
        }
    }

    fun serialize(): String = Json.encodeToString(this)

    companion object {
        val DEFAULT = PlayerGestureZoneConfig()

        fun deserialize(json: String?): PlayerGestureZoneConfig {
            if (json.isNullOrBlank()) return DEFAULT
            return try {
                Json.decodeFromString(json)
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}
