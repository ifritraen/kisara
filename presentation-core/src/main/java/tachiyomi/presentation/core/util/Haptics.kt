package tachiyomi.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
enum class HapticFeedbackMode {
    PARTIAL,
    FULL,
}

interface AppHaptics {
    fun tap()
    fun longPress()
}

private object NoOpAppHaptics : AppHaptics {
    override fun tap() = Unit
    override fun longPress() = Unit
}

private class AppHapticsImpl(
    private val hapticFeedback: HapticFeedback,
    private val hapticFeedbackMode: HapticFeedbackMode,
    private val isEInkMode: Boolean,
) : AppHaptics {
    override fun tap() {
        if (!isEInkMode && hapticFeedbackMode == HapticFeedbackMode.FULL) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
        }
    }

    override fun longPress() {
        if (!isEInkMode && (hapticFeedbackMode == HapticFeedbackMode.FULL || hapticFeedbackMode == HapticFeedbackMode.PARTIAL)) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

val LocalAppHaptics = staticCompositionLocalOf<AppHaptics> { NoOpAppHaptics }

@Composable
fun AppHapticsProvider(
    hapticFeedbackMode: HapticFeedbackMode,
    isEInkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val appHaptics = remember(hapticFeedback, hapticFeedbackMode, isEInkMode) {
        createAppHaptics(
            hapticFeedback = hapticFeedback,
            hapticFeedbackMode = hapticFeedbackMode,
            isEInkMode = isEInkMode,
        )
    }

    CompositionLocalProvider(LocalAppHaptics provides appHaptics) {
        content()
    }
}

fun createAppHaptics(
    hapticFeedback: HapticFeedback,
    hapticFeedbackMode: HapticFeedbackMode,
    isEInkMode: Boolean = false,
): AppHaptics = AppHapticsImpl(
    hapticFeedback = hapticFeedback,
    hapticFeedbackMode = hapticFeedbackMode,
    isEInkMode = isEInkMode,
)
