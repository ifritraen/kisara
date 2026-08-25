package eu.kanade.tachiyomi.ui.updates.anime

import android.content.Context
import androidx.compose.material.icons.Icons
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
import eu.kanade.presentation.updates.anime.AnimeUpdatesScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

@Composable
fun Screen.animeUpdatesTab(
    context: Context,
    fromMore: Boolean,
    screenModel: AnimeUpdatesScreenModel = rememberScreenModel { AnimeUpdatesScreenModel() },
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_anime,
        searchEnabled = false,
        content = { contentPadding, _ ->
            AnimeUpdatesScreen(
                state = state,
                contentPadding = contentPadding,
                onAnimeClick = { animeId -> navigator.push(AnimeScreen(animeId)) },
                onPlayClick = { animeId, episodeId ->
                    val intent = PlayerActivity.newIntent(context, animeId, episodeId)
                    context.startActivity(intent)
                },
                onToggleSelection = screenModel::toggleSelection,
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
                    title = context.stringResource(MR.strings.action_update_library),
                    icon = Icons.Outlined.Refresh,
                    onClick = { screenModel.updateLibrary(context) },
                ),
            )
        },
    )
}
