package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

class CreateNovelCategoryWithName(
    private val repository: NovelCategoryRepository,
    private val preferences: LibraryPreferences,
) {
    private val initialFlags: Long
        get() {
            val sort = preferences.novelSortingMode().get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String, parentId: Long? = null): Long? {
        val categories = repository.getCategories()
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        return repository.insertCategory(
            NovelCategory(
                id = 0,
                name = name,
                order = nextOrder,
                flags = initialFlags,
                hidden = false,
                hiddenFromHomeHub = false,
                parentId = parentId,
            ),
        )
    }

    suspend fun await(name: String, order: Long, flags: Long, parentId: Long? = null): Long? {
        return repository.insertCategory(
            NovelCategory(
                id = 0,
                name = name,
                order = order,
                flags = flags,
                hidden = false,
                hiddenFromHomeHub = false,
                parentId = parentId,
            ),
        )
    }
}
