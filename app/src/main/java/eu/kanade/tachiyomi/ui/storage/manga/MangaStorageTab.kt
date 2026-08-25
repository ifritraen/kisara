package eu.kanade.tachiyomi.ui.storage.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import tachiyomi.i18n.kmk.KMR

@Composable
fun Screen.mangaStorageTab(): TabContent {
    val screenModel = rememberScreenModel { MangaStorageScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_manga,
        content = { contentPadding, _ ->
            StorageScreenContent(
                state = state,
                isManga = true,
                contentPadding = contentPadding,
                onCategorySelected = screenModel::setSelectedCategory,
                onDelete = screenModel::deleteEntry,
            )
        },
    )
}
