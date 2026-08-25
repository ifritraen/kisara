package eu.kanade.tachiyomi.ui.category.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoryScreenModel(
    private val getAllCategories: GetAnimeCategories = Injekt.get(),
    private val createCategoryWithName: CreateAnimeCategoryWithName = Injekt.get(),
    private val hideCategory: HideAnimeCategory = Injekt.get(),
    private val deleteCategory: DeleteAnimeCategory = Injekt.get(),
    private val reorderCategory: ReorderAnimeCategory = Injekt.get(),
    private val renameCategory: RenameAnimeCategory = Injekt.get(),
    private val updateCategory: UpdateAnimeCategory = Injekt.get(),
) : ScreenModel {

    private val _events: Channel<AnimeCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialogState = MutableStateFlow<AnimeCategoryDialog?>(null)

    private val categoriesFlow: Flow<ImmutableList<Category>> = getAllCategories.subscribe()
        .map { categories ->
            categories
                .filterNot(Category::isSystemCategory)
                .toImmutableList()
        }

    val state: StateFlow<AnimeCategoryScreenState> = combine(
        categoriesFlow,
        dialogState,
    ) { categories, dialog ->
        AnimeCategoryScreenState.Success(
            categories = categories,
            dialog = dialog,
        )
    }.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        AnimeCategoryScreenState.Loading,
    )

    fun createCategory(name: String, parentId: Long? = null) {
        screenModelScope.launch {
            when (createCategoryWithName.await(name, parentId)) {
                is CreateAnimeCategoryWithName.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                is CreateAnimeCategoryWithName.Result.Success -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            when (hideCategory.await(category)) {
                is HideAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                DeleteAnimeCategory.Result.Success -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                ReorderAnimeCategory.Result.Success,
                ReorderAnimeCategory.Result.Unchanged,
                -> {}
            }
        }
    }

    fun changeParent(category: Category, newParentId: Long?) {
        screenModelScope.launch {
            updateCategory.await(CategoryUpdate(id = category.id, parentId = newParentId))
        }
    }

    fun changeOrderBatch(changes: List<Pair<Category, Int>>) {
        screenModelScope.launch {
            changes.forEach { (cat, newOrder) ->
                updateCategory.await(CategoryUpdate(id = cat.id, order = newOrder.toLong()))
            }
        }
    }

    fun renameCategory(category: Category, name: String, parentId: Long? = category.parentId) {
        screenModelScope.launch {
            when (renameCategory.await(category.id, name, parentId)) {
                is RenameAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                RenameAnimeCategory.Result.Success -> {}
            }
        }
    }

    fun showDialog(dialog: AnimeCategoryDialog) {
        dialogState.update { dialog }
    }

    fun dismissDialog() {
        dialogState.update { null }
    }
}

sealed interface AnimeCategoryDialog {
    data object Create : AnimeCategoryDialog
    data class Rename(val category: Category) : AnimeCategoryDialog
    data class Delete(val category: Category) : AnimeCategoryDialog
}

sealed interface AnimeCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCategoryScreenState {
    @Immutable
    data object Loading : AnimeCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: AnimeCategoryDialog? = null,
    ) : AnimeCategoryScreenState {
        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
