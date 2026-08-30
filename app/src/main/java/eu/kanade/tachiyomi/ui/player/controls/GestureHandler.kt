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

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.player.components.LeftSideOvalShape
import eu.kanade.presentation.player.components.RightSideOvalShape
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.tachiyomi.ui.player.LongPressGesture
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.ln

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import eu.kanade.tachiyomi.ui.player.settings.PlayerGestureZoneConfig
import eu.kanade.tachiyomi.ui.player.settings.PlayerZoneAction
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight

@Composable
fun GestureHandler(
    viewModel: PlayerViewModel,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val longPressAction by gesturePreferences.longPressGesture().collectAsStateWithLifecycle()
    val isDynamicSpeedActive by viewModel.isDynamicSpeedActive.collectAsStateWithLifecycle()

    val panelShown by viewModel.panelShown.collectAsStateWithLifecycle()
    val allowGesturesInPanels by playerPreferences.allowGestures().collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val position by viewModel.pos.collectAsStateWithLifecycle()
    val controlsShown by viewModel.controlsShown.collectAsStateWithLifecycle()
    val areControlsLocked by viewModel.areControlsLocked.collectAsStateWithLifecycle()
    val seekAmount by viewModel.doubleTapSeekAmount.collectAsStateWithLifecycle()
    val isSeekingForwards by viewModel.isSeekingForwards.collectAsStateWithLifecycle()
    var isDoubleTapSeeking by remember { mutableStateOf(false) }

    val splitZonesEnabled by gesturePreferences.gestureSplitZonesEnabled().collectAsStateWithLifecycle()
    val splitConfigJson by gesturePreferences.gestureSplitZoneConfig().collectAsStateWithLifecycle()
    val splitConfig = remember(splitConfigJson) { PlayerGestureZoneConfig.deserialize(splitConfigJson) }
    val showRipple by gesturePreferences.showGestureRipple().collectAsStateWithLifecycle()
    val showHud by gesturePreferences.showGestureHud().collectAsStateWithLifecycle()

    var activeTappedZone by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hudActionText by remember { mutableStateOf<String?>(null) }
    var isSpeedLocked by remember { mutableStateOf(false) }

    LaunchedEffect(activeTappedZone) {
        if (activeTappedZone == null) return@LaunchedEffect
        delay(400)
        activeTappedZone = null
    }

    LaunchedEffect(hudActionText) {
        if (hudActionText == null) return@LaunchedEffect
        delay(900)
        hudActionText = null
    }

    LaunchedEffect(seekAmount) {
        if (seekAmount == 0) return@LaunchedEffect
        isDoubleTapSeeking = true
        delay(800)
        isDoubleTapSeeking = false
        viewModel.updateSeekAmount(0)
        viewModel.updateSeekText(null)
        delay(100)
        viewModel.hideSeekBar()
    }

    val gestureVolumeBrightness = gesturePreferences.gestureVolumeBrightness().get()
    val swapVolumeBrightness by gesturePreferences.swapVolumeBrightness().collectAsStateWithLifecycle()
    val seekGesture by gesturePreferences.gestureHorizontalSeek().collectAsStateWithLifecycle()
    val showSeekbar by gesturePreferences.showSeekBar().collectAsStateWithLifecycle()
    var isLongPressing by remember { mutableStateOf(false) }
    val currentVolume by viewModel.currentVolume.collectAsStateWithLifecycle()
    val currentMPVVolume by viewModel.currentMPVVolume.collectAsStateWithLifecycle()
    val currentBrightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
    val volumeBoostingCap = audioPreferences.volumeBoostCap().get()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeGestures)
            .pointerInput(areControlsLocked) {
                if (areControlsLocked) return@pointerInput
                var currentScale = 1.0f
                var currentPanX = 0f
                var currentPanY = 0f
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || pan != Offset.Zero) {
                        currentScale = (currentScale * zoom).coerceIn(1.0f, 6.0f)
                        if (currentScale > 1.001f) {
                            val mpvZoom = (ln(currentScale) / ln(2.0f)).toDouble()
                            val maxPan = (currentScale - 1f) / currentScale
                            currentPanX = (currentPanX + pan.x / size.width).coerceIn(-maxPan, maxPan)
                            currentPanY = (currentPanY + pan.y / size.height).coerceIn(-maxPan, maxPan)
                            viewModel.setVideoZoom(mpvZoom)
                            viewModel.setVideoPan(currentPanX.toDouble(), currentPanY.toDouble())
                        } else {
                            currentPanX = 0f
                            currentPanY = 0f
                            viewModel.resetVideoZoomAndPan()
                        }
                    }
                }
            }
            .pointerInput(longPressAction, areControlsLocked) {
                if (areControlsLocked || longPressAction != LongPressGesture.PlaybackSpeed) return@pointerInput
                awaitPointerEventScope {
                    var startingX = 0f
                    var startingY = 0f
                    var initialSpeed = 2.0f
                    var hasLocked = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes
                        val downChange = changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (downChange != null) {
                            startingX = downChange.position.x
                            startingY = downChange.position.y
                            hasLocked = false
                            isSpeedLocked = false
                            initialSpeed = gesturePreferences.longPressCustomSpeed().get()
                        }
                        if (viewModel.isDynamicSpeedActive.value) {
                            val activeChange = changes.firstOrNull { it.pressed }
                            if (activeChange != null) {
                                val currentX = activeChange.position.x
                                val currentY = activeChange.position.y

                                // Vertical Drag for Speed (Pull UP = faster, Pull DOWN = slower)
                                val deltaY = startingY - currentY
                                val stepPx = 24.dp.toPx()
                                val stepsShifted = (deltaY / stepPx).toInt()
                                val targetSpeed = (initialSpeed + stepsShifted * 0.1f)
                                    .coerceIn(0.2f, 6.0f)
                                    .let { Math.round(it * 10f) / 10f }

                                if (viewModel.gesturePlaybackSpeed.value != targetSpeed) {
                                    viewModel.gesturePlaybackSpeed.update { targetSpeed }
                                    MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }

                                // Horizontal Drag to Left to Lock
                                val deltaX = currentX - startingX
                                val lockThreshold = 60.dp.toPx()
                                val shouldLock = deltaX < -lockThreshold || currentX < size.width * 0.2f
                                if (shouldLock != hasLocked) {
                                    hasLocked = shouldLock
                                    isSpeedLocked = shouldLock
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                activeChange.consume()
                            }
                            val upChange = changes.firstOrNull { !it.pressed && it.previousPressed }
                            if (upChange != null || changes.all { !it.pressed }) {
                                val chosenSpeed = viewModel.gesturePlaybackSpeed.value
                                val originalSpeed = viewModel.preGesturePlaybackSpeed.value
                                viewModel.isDynamicSpeedActive.update { false }
                                isLongPressing = false
                                viewModel.playerUpdate.update { PlayerUpdates.None }
                                if (hasLocked) {
                                    // User locked speed! Keep it active permanently.
                                    viewModel.playbackSpeed.update { chosenSpeed }
                                    playerPreferences.playerSpeed().set(chosenSpeed)
                                    MPVLib.setPropertyDouble("speed", chosenSpeed.toDouble())
                                    hudActionText = "Locked at ${chosenSpeed}x"
                                } else {
                                    // User released without locking - ramp back smoothly to original speed
                                    viewModel.rampPlaybackSpeed(originalSpeed)
                                }
                                isSpeedLocked = false
                            }
                        }
                    }
                }
            }
            .pointerInput(longPressAction, areControlsLocked, splitZonesEnabled, splitConfig) {
                val originalSpeed = viewModel.playbackSpeed.value
                detectTapGestures(
                    onTap = {
                        if (controlsShown) viewModel.hideControls() else viewModel.showControls()
                    },
                    onDoubleTap = { offset ->
                        if (areControlsLocked) return@detectTapGestures
                        if (splitZonesEnabled) {
                            val col = (offset.x / size.width * 5).toInt().coerceIn(0, 4)
                            val row = (offset.y / size.height * 2).toInt().coerceIn(0, 1)
                            val action = splitConfig.getAction(col, row)
                            viewModel.executeZoneAction(action)
                            if (showRipple) {
                                activeTappedZone = col to row
                            }
                            if (showHud && action != PlayerZoneAction.NONE) {
                                val actionName = when (action) {
                                    PlayerZoneAction.SEEK_BACKWARD_10 -> "-10s"
                                    PlayerZoneAction.SEEK_BACKWARD_5 -> "-5s"
                                    PlayerZoneAction.SEEK_BACKWARD_15 -> "-15s"
                                    PlayerZoneAction.SEEK_BACKWARD_30 -> "-30s"
                                    PlayerZoneAction.SEEK_FORWARD_10 -> "+10s"
                                    PlayerZoneAction.SEEK_FORWARD_5 -> "+5s"
                                    PlayerZoneAction.SEEK_FORWARD_15 -> "+15s"
                                    PlayerZoneAction.SEEK_FORWARD_30 -> "+30s"
                                    PlayerZoneAction.PLAY_PAUSE -> if (viewModel.paused.value) "Pause" else "Play"
                                    PlayerZoneAction.SPEED_UP -> "Speed: ${viewModel.playbackSpeed.value}x"
                                    PlayerZoneAction.SPEED_DOWN -> "Speed: ${viewModel.playbackSpeed.value}x"
                                    PlayerZoneAction.SPEED_RESET -> "Speed: 1.0x"
                                    PlayerZoneAction.VOLUME_UP, PlayerZoneAction.VOLUME_DOWN -> "Volume: ${currentVolume}"
                                    PlayerZoneAction.BRIGHTNESS_UP, PlayerZoneAction.BRIGHTNESS_DOWN -> "Brightness"
                                    PlayerZoneAction.TOGGLE_ASPECT_RATIO -> "Aspect Ratio"
                                    PlayerZoneAction.SCREENSHOT -> "Screenshot"
                                    PlayerZoneAction.TOGGLE_SUBTITLE -> "Subtitles"
                                    PlayerZoneAction.TOGGLE_AUDIO_TRACK -> "Audio Track"
                                    PlayerZoneAction.SHOW_CONTROLS -> "Controls"
                                    PlayerZoneAction.NONE -> ""
                                }
                                hudActionText = actionName
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            if (offset.x > size.width * 3 / 5) {
                                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleRightDoubleTap()
                                isDoubleTapSeeking = true
                            } else if (offset.x < size.width * 2 / 5) {
                                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleLeftDoubleTap()
                                isDoubleTapSeeking = true
                            } else {
                                viewModel.handleCenterDoubleTap()
                            }
                        }
                    },
                    onPress = {
                        if (panelShown != Panels.None && !allowGesturesInPanels) {
                            viewModel.panelShown.update { Panels.None }
                        }
                        val press = PressInteraction.Press(
                            it.copy(x = if (it.x > size.width * 3 / 5) it.x - size.width * 0.6f else it.x),
                        )
                        if (!areControlsLocked && isDoubleTapSeeking && seekAmount != 0) {
                            if (it.x > size.width * 3 / 5) {
                                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleRightDoubleTap()
                            } else if (it.x < size.width * 2 / 5) {
                                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleLeftDoubleTap()
                            } else {
                                viewModel.handleCenterDoubleTap()
                            }
                        }
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        if (isLongPressing) {
                            isLongPressing = false
                            if (longPressAction != LongPressGesture.PlaybackSpeed) {
                                MPVLib.setPropertyDouble("speed", originalSpeed.toDouble())
                            }
                            viewModel.playerUpdate.update { PlayerUpdates.None }
                        }
                        interactionSource.emit(PressInteraction.Release(press))
                    },
                    onLongPress = {
                        if (areControlsLocked) return@detectTapGestures
                        if (!isLongPressing) {
                            if (longPressAction == LongPressGesture.Screenshot) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLongPressing = true
                                viewModel.pause()
                                viewModel.sheetShown.update { Sheets.Screenshot }
                            } else if (longPressAction == LongPressGesture.PlaybackSpeed) {
                                val customInitialSpeed = gesturePreferences.longPressCustomSpeed().get()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLongPressing = true
                                viewModel.preGesturePlaybackSpeed.update { viewModel.playbackSpeed.value }
                                viewModel.gesturePlaybackSpeed.update { customInitialSpeed }
                                viewModel.isDynamicSpeedActive.update { true }
                                viewModel.hideControls()
                                viewModel.rampPlaybackSpeed(customInitialSpeed)
                            }
                        }
                    },
                )
            }
            .pointerInput(areControlsLocked, isDynamicSpeedActive) {
                if (!seekGesture || areControlsLocked || isDynamicSpeedActive) return@pointerInput
                var startingPosition = position.toInt()
                var targetPosition = startingPosition
                var startingX = 0f
                var wasPlayerAlreadyPause = false
                var isSpeedDragging = false
                var startingSpeed = viewModel.playbackSpeed.value

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val startRow = (offset.y / size.height * 2).toInt().coerceIn(0, 1)
                        if (splitZonesEnabled && startRow == 0) {
                            // Top Half: Playback Speed Adjustment!
                            isSpeedDragging = true
                            startingSpeed = viewModel.playbackSpeed.value
                            startingX = offset.x
                            viewModel.hideControls()
                        } else {
                            // Bottom Half: Timeline Position Seek!
                            isSpeedDragging = false
                            startingPosition = position.toInt()
                            targetPosition = startingPosition
                            startingX = offset.x
                            wasPlayerAlreadyPause = viewModel.paused.value
                            viewModel.pause()
                            if (showSeekbar) viewModel.showSeekBar()
                        }
                    },
                    onDragEnd = {
                        if (isSpeedDragging) {
                            isSpeedDragging = false
                        } else {
                            // Exact demux seek on pointer release
                            viewModel.seekTo(targetPosition, precise = true)
                            viewModel.gestureSeekAmount.update { null }
                            viewModel.hideSeekBar()
                            if (!wasPlayerAlreadyPause) viewModel.unpause()
                        }
                    },
                    onDragCancel = {
                        if (isSpeedDragging) {
                            isSpeedDragging = false
                        } else {
                            viewModel.gestureSeekAmount.update { null }
                            viewModel.hideSeekBar()
                            if (!wasPlayerAlreadyPause) viewModel.unpause()
                        }
                    },
                ) { change, dragAmount ->
                    if (isSpeedDragging) {
                        val deltaX = change.position.x - startingX
                        val stepPx = 30.dp.toPx()
                        val steps = (deltaX / stepPx).toInt()
                        val newSpeed = (startingSpeed + steps * 0.1f).coerceIn(0.2f, 6.0f).let { Math.round(it * 10f) / 10f }
                        if (viewModel.playbackSpeed.value != newSpeed) {
                            viewModel.playbackSpeed.update { newSpeed }
                            playerPreferences.playerSpeed().set(newSpeed)
                            MPVLib.setPropertyDouble("speed", newSpeed.toDouble())
                            viewModel.playerUpdate.update { PlayerUpdates.Speed }
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    } else {
                        if (position <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
                        if (position >= duration && dragAmount > 0) return@detectHorizontalDragGestures
                        targetPosition = calculateNewHorizontalGestureValue(startingPosition, startingX, change.position.x, 0.15f)
                            .coerceIn(0, duration.toInt())
                        viewModel.gestureSeekAmount.update { _ ->
                            Pair(
                                startingPosition,
                                (targetPosition - startingPosition)
                                    .coerceIn(0 - startingPosition, (duration - startingPosition).toInt()),
                            )
                        }
                        // Fast keyframe seek during active horizontal drag
                        viewModel.seekTo(targetPosition, precise = false)
                        if (showSeekbar) viewModel.showSeekBar()
                    }
                }
            }
            .pointerInput(areControlsLocked) {
                if (!gestureVolumeBrightness || areControlsLocked) return@pointerInput
                var startingY = 0f
                var mpvVolumeStartingY = 0f
                var originalVolume = currentVolume
                var originalMPVVolume = currentMPVVolume
                var originalBrightness = currentBrightness
                val brightnessGestureSens = 0.001f
                val volumeGestureSens = 0.001f * viewModel.maxVolume
                val mpvVolumeGestureSens = 0.001f * volumeBoostingCap
                val isIncreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 &&
                        currentVolume == viewModel.maxVolume &&
                        currentMPVVolume - 100 < volumeBoostingCap &&
                        it < 0
                }
                val isDecreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 &&
                        currentVolume == viewModel.maxVolume &&
                        currentMPVVolume - 100 in 1..volumeBoostingCap &&
                        it > 0
                }
                detectVerticalDragGestures(
                    onDragEnd = { startingY = 0f },
                    onDragStart = {
                        startingY = 0f
                        mpvVolumeStartingY = 0f
                        originalVolume = currentVolume
                        originalMPVVolume = currentMPVVolume
                        originalBrightness = currentBrightness
                    },
                ) { change, amount ->
                    val changeVolume: () -> Unit = {
                        if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                            if (mpvVolumeStartingY == 0f) {
                                startingY = 0f
                                originalVolume = currentVolume
                                mpvVolumeStartingY = change.position.y
                            }
                            viewModel.changeMPVVolumeTo(
                                calculateNewVerticalGestureValue(
                                    originalMPVVolume,
                                    mpvVolumeStartingY,
                                    change.position.y,
                                    mpvVolumeGestureSens,
                                )
                                    .coerceIn(100..volumeBoostingCap + 100),
                            )
                        } else {
                            if (startingY == 0f) {
                                mpvVolumeStartingY = 0f
                                originalMPVVolume = currentMPVVolume
                                startingY = change.position.y
                            }
                            viewModel.changeVolumeTo(
                                calculateNewVerticalGestureValue(
                                    originalVolume,
                                    startingY,
                                    change.position.y,
                                    volumeGestureSens,
                                ),
                            )
                        }
                        viewModel.displayVolumeSlider()
                    }
                    val changeBrightness: () -> Unit = {
                        if (startingY == 0f) startingY = change.position.y
                        viewModel.changeBrightnessTo(
                            calculateNewVerticalGestureValue(
                                originalBrightness,
                                startingY,
                                change.position.y,
                                brightnessGestureSens,
                            ),
                        )
                        viewModel.displayBrightnessSlider()
                    }

                    val col = (change.position.x / size.width * 5).toInt().coerceIn(0, 4)
                    val isLeft = col <= 1 || (col == 2 && !swapVolumeBrightness)
                    val shouldAdjustBrightness = if (swapVolumeBrightness) !isLeft else isLeft
                    if (shouldAdjustBrightness) {
                        changeBrightness()
                    } else {
                        changeVolume()
                    }
                }
            },
    ) {
        // Visual Zone Highlight Ripple on Double-Tap
        activeTappedZone?.let { (col, row) ->
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.2f)
                        .fillMaxHeight(0.5f)
                        .align(
                            when {
                                row == 0 && col == 0 -> Alignment.TopStart
                                row == 0 && col == 4 -> Alignment.TopEnd
                                row == 1 && col == 0 -> Alignment.BottomStart
                                row == 1 && col == 4 -> Alignment.BottomEnd
                                row == 0 -> Alignment.TopCenter
                                else -> Alignment.BottomCenter
                            },
                        )
                        .background(Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(12.dp)),
                )
            }
        }

        // Floating Action HUD Indicator
        AnimatedVisibility(
            visible = hudActionText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            hudActionText?.let { text ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Lock Speed Target Pill (Left Edge)
        AnimatedVisibility(
            visible = isDynamicSpeedActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp),
        ) {
            val pillColor = if (isSpeedLocked) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Black.copy(alpha = 0.65f)
            }
            val pillBorder = if (isSpeedLocked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.White.copy(alpha = 0.3f)
            }
            Surface(
                color = pillColor,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, pillBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (isSpeedLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (isSpeedLocked) {
                            stringResource(KMR.strings.player_speed_locked, viewModel.gesturePlaybackSpeed.value)
                        } else {
                            stringResource(KMR.strings.player_drag_to_lock)
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Live Dynamic Speed Top Banner
        AnimatedVisibility(
            visible = isDynamicSpeedActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "▶▶",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${viewModel.gesturePlaybackSpeed.value}x",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isSpeedLocked) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DoubleTapToSeekOvals(
    amount: Int,
    text: String?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides playerRippleConfiguration,
        ) {
            if (amount != 0 || text != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f), // 2 fifths
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                            .background(Color.White.copy(alpha))
                            .indication(interactionSource, ripple()),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoubleTapSeekTriangles(isForward = amount > 0)
                        Text(
                            text = text ?: pluralStringResource(KMR.plurals.seconds, amount, amount),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
    return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
    return originalValue + ((startingY - newY) * sensitivity)
}

fun calculateNewHorizontalGestureValue(originalValue: Int, startingX: Float, newX: Float, sensitivity: Float): Int {
    return originalValue + ((newX - startingX) * sensitivity).toInt()
}

fun calculateNewHorizontalGestureValue(originalValue: Float, startingX: Float, newX: Float, sensitivity: Float): Float {
    return originalValue + ((newX - startingX) * sensitivity)
}
