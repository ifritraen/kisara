package eu.kanade.tachiyomi.ui.history.anime

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.anime.AnimeHistoryScreen
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.animeHistoryTab(
    context: Context,
    fromMore: Boolean,
    screenModel: AnimeHistoryScreenModel = rememberScreenModel { AnimeHistoryScreenModel() },
): TabContent {
    val snackbarHostState = SnackbarHostState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_anime,
        searchEnabled = true,
        content = { _, _ ->
            AnimeHistoryScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onClickCover = { navigator.push(AnimeScreen(it)) },
                onClickResume = { animeId, episodeId ->
                    scope.launch {
                        val nextEp = screenModel.getNextEpisode(animeId, episodeId)
                        val targetEpisodeId = nextEp?.id ?: episodeId
                        val intent = PlayerActivity.newIntent(context, animeId, targetEpisodeId)
                        context.startActivity(intent)
                    }
                },
                onDialogChange = screenModel::setDialog,
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is AnimeHistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all: Boolean ->
                            if (all) {
                                screenModel.removeAllFromHistory(dialog.history.animeId)
                            } else {
                                screenModel.removeFromHistory(dialog.history)
                            }
                        },
                    )
                }
                is AnimeHistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                null -> Unit
            }

            LaunchedEffect(state.list) {
                if (state.list != null) {
                    (context as? MainActivity)?.ready = true
                }
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        AnimeHistoryScreenModel.Event.InternalError -> {
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                        }
                        AnimeHistoryScreenModel.Event.HistoryCleared -> {
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                        }
                    }
                }
            }
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(AnimeHistoryScreenModel.Dialog.DeleteAll) },
            ),
        ),
    )
}
