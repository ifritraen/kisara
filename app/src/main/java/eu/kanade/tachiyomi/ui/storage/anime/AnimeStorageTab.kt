package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import tachiyomi.i18n.kmk.KMR

@Composable
fun Screen.animeStorageTab(): TabContent {
    val screenModel = rememberScreenModel { AnimeStorageScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_anime,
        content = { contentPadding, _ ->
            StorageScreenContent(
                state = state,
                isManga = false,
                contentPadding = contentPadding,
                onCategorySelected = screenModel::setSelectedCategory,
                onDelete = screenModel::deleteEntry,
            )
        },
    )
}
