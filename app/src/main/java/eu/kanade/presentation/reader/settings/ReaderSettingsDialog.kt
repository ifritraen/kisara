package eu.kanade.presentation.reader.settings

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    val tabTitles = persistentListOf(
        stringResource(MR.strings.pref_category_reading_mode),
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.custom_filter),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val isCustomFilter = pagerState.currentPage == 2

    BoxWithConstraints {
        // KMK -->
        val targetMaxHeight = if (isCustomFilter) maxHeight * 0.40f else maxHeight * 0.75f
        val animatedMaxHeight by animateDpAsState(
            targetValue = targetMaxHeight,
            animationSpec = tween(durationMillis = 250),
            label = "settingsMaxHeight",
        )

        TabbedDialog(
            modifier = Modifier.heightIn(max = animatedMaxHeight),
            onDismissRequest = {
                onDismissRequest()
                onShowMenus()
            },
            tabTitles = tabTitles,
            pagerState = pagerState,
            actions = {
                val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                val activity = LocalActivity.current as? ReaderActivity

                DisposableEffect(window) {
                    if (window != null && activity != null) {
                        activity.activeDialogWindow = window
                        try {
                            val params = WindowManager.LayoutParams().apply {
                                copyFrom(window.attributes)
                                screenBrightness = activity.window.attributes.screenBrightness
                            }
                            window.attributes = params
                        } catch (_: Exception) {}
                    }
                    onDispose {
                        if (activity?.activeDialogWindow === window) {
                            activity?.activeDialogWindow = null
                        }
                        if (isCustomFilter) {
                            onShowMenus()
                        }
                    }
                }

                LaunchedEffect(isCustomFilter, window) {
                    updateWindowBackgroundDimAndBlur(window, isCustomFilter)
                    if (isCustomFilter) {
                        onHideMenus()
                    } else {
                        onShowMenus()
                    }
                }
            },
        ) { page ->
            Column(
                modifier = Modifier
                    .padding(vertical = TabbedDialogPaddings.Vertical)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> ReadingModePage(screenModel)
                    1 -> GeneralPage(screenModel)
                    2 -> ColorFilterPage(screenModel)
                }
            }
        }
        // KMK <--
    }
}

// KMK -->
private fun updateWindowBackgroundDimAndBlur(
    window: Window?,
    isCustomFilter: Boolean,
) {
    val win = window ?: return
    try {
        if (isCustomFilter) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = WindowManager.LayoutParams().apply {
                    copyFrom(win.attributes)
                    blurBehindRadius = 0
                }
                win.attributes = params
            }
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.5f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = WindowManager.LayoutParams().apply {
                    copyFrom(win.attributes)
                    blurBehindRadius = 60
                }
                win.attributes = params
            }
        }
    } catch (_: Exception) {}
}
// KMK <--
