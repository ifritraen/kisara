package tachiyomi.domain.category.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.service.LibraryPreferences

class SetAnimeCategories(
    private val animeRepository: AnimeRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(animeId: Long, categoryIds: List<Long>) {
        try {
            val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get().toLong()
            val finalCategoryIds = if (categoryIds.any { it != defaultCategoryId && it != 0L }) {
                categoryIds.filter { it != defaultCategoryId && it != 0L }
            } else {
                categoryIds
            }
            animeRepository.setAnimeCategories(animeId, finalCategoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
