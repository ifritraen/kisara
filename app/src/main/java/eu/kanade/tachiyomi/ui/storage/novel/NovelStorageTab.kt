package eu.kanade.tachiyomi.ui.storage.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import tachiyomi.i18n.kmk.KMR

@Composable
fun Screen.novelStorageTab(): TabContent {
    val screenModel = rememberScreenModel { NovelStorageScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_novel,
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
