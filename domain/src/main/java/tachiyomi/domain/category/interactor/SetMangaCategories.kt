package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaCategories(
    private val mangaRepository: MangaRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        try {
            val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
            val finalCategoryIds = if (categoryIds.any { it != defaultCategoryId && it != 0L }) {
                categoryIds.filter { it != defaultCategoryId && it != 0L }
            } else {
                categoryIds
            }
            mangaRepository.setMangaCategories(mangaId, finalCategoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
