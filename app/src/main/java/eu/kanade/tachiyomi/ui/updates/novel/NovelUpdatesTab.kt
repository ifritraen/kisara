package eu.kanade.tachiyomi.ui.updates.novel

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.updates.novel.NovelUpdatesScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

import eu.kanade.tachiyomi.ui.updates.UpdatesTabEvents
import eu.kanade.tachiyomi.ui.updates.UpdatesTabViewContent

@Composable
fun Screen.novelUpdatesTab(
    context: Context,
    fromMore: Boolean,
    screenModel: NovelUpdatesScreenModel = rememberScreenModel { NovelUpdatesScreenModel() },
): TabContent {
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            UpdatesTabViewContent(
                contentPadding = contentPadding,
                novelScreenModel = screenModel,
            )
        },
        actions = if (state.selected.isNotEmpty()) {
            persistentListOf(
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = { screenModel.toggleAllSelection(true) },
                ),
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Outlined.FlipToBack,
                    onClick = { screenModel.invertSelection() },
                ),
            )
        } else {
            persistentListOf(
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_view_upcoming),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { UpdatesTabEvents.selectSubTabEvent.trySend(0) },
                ),
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_update_library),
                    icon = Icons.Outlined.Refresh,
                    onClick = { screenModel.updateLibrary() },
                ),
            )
        },
    )
}
