package tachiyomi.domain.category.novel.interactor

import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

class SetNovelCategories(
    private val repository: NovelCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
) {
    suspend fun await(novelId: Long, categoryIds: List<Long>) {
        val defaultCategoryId = libraryPreferences.defaultNovelCategory().get().toLong()
        val finalCategoryIds = if (categoryIds.any { it != defaultCategoryId && it != 0L }) {
            categoryIds.filter { it != defaultCategoryId && it != 0L }
        } else {
            categoryIds
        }
        repository.setNovelCategories(novelId, finalCategoryIds)
    }
}
