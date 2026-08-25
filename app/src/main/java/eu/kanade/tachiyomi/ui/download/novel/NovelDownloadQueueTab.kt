package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.kmk.KMR

@Composable
fun Screen.novelDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val screenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_novel,
        badgeNumber = state.queueCount.takeIf { it > 0 },
        searchEnabled = false,
        content = { contentPadding, _ ->
            NovelDownloadQueueScreen(
                contentPadding = contentPadding,
                screenModel = screenModel,
                state = state,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
    )
}
