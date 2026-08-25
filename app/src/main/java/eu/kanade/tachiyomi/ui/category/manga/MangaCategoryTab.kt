package eu.kanade.tachiyomi.ui.category.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.category.CategoryDialog
import eu.kanade.tachiyomi.ui.category.CategoryEvent
import eu.kanade.tachiyomi.ui.category.CategoryScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.mangaCategoryTab(): TabContent {
    val context = LocalContext.current
    val screenModel = rememberScreenModel { CategoryScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.manga,
        searchEnabled = false,
        content = { _, _ ->
            if (state is CategoryScreenState.Loading) {
                LoadingScreen()
            } else {
                val successState = state as CategoryScreenState.Success
                val expanded = remember { mutableStateOf(setOf<Long>()) }

                fun toggle(categoryId: Long) {
                    expanded.value =
                        if (expanded.value.contains(categoryId)) {
                            expanded.value - categoryId
                        } else {
                            expanded.value + categoryId
                        }
                }

                CategoryScreen(
                    state = successState,
                    onClickCreate = { screenModel.showDialog(CategoryDialog.Create) },
                    onClickRename = { screenModel.showDialog(CategoryDialog.Rename(it)) },
                    onClickDelete = { screenModel.showDialog(CategoryDialog.Delete(it)) },
                    onChangeOrder = screenModel::changeOrder,
                    onChangeParent = screenModel::changeParent,
                    onClickHide = screenModel::hideCategory,
                    onCommitOrder = { changes -> screenModel.changeOrderBatch(changes) },
                    expanded = expanded.value,
                    onToggleExpand = ::toggle,
                )

                when (val dialog = successState.dialog) {
                    null -> {}
                    CategoryDialog.Create -> {
                        CategoryCreateDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onCreate = screenModel::createCategory,
                            categories = successState.categories.fastMap { it.name }.toImmutableList(),
                            parentOptions = successState.categories
                                .filter { it.parentId == null }
                                .filterNot { it.isSystemCategory }
                                .toImmutableList(),
                        )
                    }
                    is CategoryDialog.Rename -> {
                        CategoryRenameDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onRename = { newName, parentId -> screenModel.renameCategory(dialog.category, newName, parentId) },
                            categories = successState.categories.fastMap { it.name }.toImmutableList(),
                            category = dialog.category.name,
                            parentOptions = successState.categories
                                .filterNot { candidate ->
                                    candidate.id == dialog.category.id ||
                                        candidate.isSystemCategory ||
                                        candidate.parentId != null ||
                                        isDescendantOf(candidate, dialog.category, successState.categories)
                                }
                                .toImmutableList(),
                            initialParentId = dialog.category.parentId,
                            categoryHasChildren = hasCategoryChildren(dialog.category, successState.categories),
                        )
                    }
                    is CategoryDialog.Delete -> {
                        CategoryDeleteDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onDelete = { screenModel.deleteCategory(dialog.category.id) },
                            category = dialog.category.name,
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    screenModel.events.collectLatest { event ->
                        if (event is CategoryEvent.LocalizedMessage) {
                            context.toast(event.stringRes)
                        }
                    }
                }
            }
        },
    )
}

private fun hasCategoryChildren(category: Category, allCategories: List<Category>): Boolean {
    return allCategories.any { it.parentId == category.id }
}

private fun isDescendantOf(candidate: Category, parent: Category, allCategories: List<Category>): Boolean {
    var currentParentId = candidate.parentId
    while (currentParentId != null) {
        if (currentParentId == parent.id) return true
        currentParentId = allCategories.firstOrNull { it.id == currentParentId }?.parentId
    }
    return false
}
