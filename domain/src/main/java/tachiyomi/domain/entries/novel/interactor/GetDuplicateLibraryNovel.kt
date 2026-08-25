package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository

class GetDuplicateLibraryNovel(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novel: Novel): List<Novel> {
        return novelRepository.getDuplicateLibraryNovel(novel.id, novel.title.lowercase())
    }
}
