package eu.kanade.tachiyomi.ui.suggestions.novel

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.novelSuggestionsTab(
    screenModel: NovelSuggestionsScreenModel = rememberScreenModel { NovelSuggestionsScreenModel() },
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val haptic = LocalHapticFeedback.current
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = KMR.strings.action_suggestions,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_update_library),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::loadSuggestions,
            ),
        ),
        content = { contentPadding, _ ->
            val dialog = state.dialog
            if (dialog is NovelSuggestionsScreenModel.State.Dialog.ChangeCategory) {
                val changeCategoryNovel = dialog.novel
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.novel.NovelCategoryScreen()) },
                    onConfirm = { included, _ ->
                        screenModel.setNovelCategories(changeCategoryNovel, included)
                    },
                    onDuplicateCheck = {
                        screenModel.dismissDialog()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.novel.duplicate.DuplicateNovelScreen(changeCategoryNovel.id))
                    },
                    onDelete = {
                        screenModel.deleteNovel(changeCategoryNovel)
                    },
                    novel = changeCategoryNovel,
                    onCreateCategory = screenModel::createCategory,
                )
            }

            when {
                state.isLoading && state.suggestions.isEmpty() -> LoadingScreen()
                state.suggestions.isEmpty() -> EmptyScreen(
                    stringRes = KMR.strings.pref_suggestions_summary,
                )
                else -> {
                    PullRefresh(
                        refreshing = state.isLoading,
                        enabled = true,
                        onRefresh = screenModel::loadSuggestions,
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(128.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.suggestions, key = { it.id }) { novel ->
                                val isFavorite = state.favoriteUrls.contains(novel.url)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { navigator.push(NovelScreen(novel.id)) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                screenModel.toggleFavorite(novel, isFavorite)
                                            },
                                        ),
                                ) {
                                    Column {
                                        Box {
                                            MangaCover.Book(
                                                data = novel.asNovelCover(),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            if (isFavorite) {
                                                Badge(
                                                    text = stringResource(MR.strings.in_library),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textColor = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(4.dp),
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = novel.title,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
