package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ModifySuggestionSource(
    private val repository: SuggestionRepository,
) {
    suspend fun addSource(sourceId: Long) {
        val existing = repository.getSources()
        val match = existing.find { it.sourceId == sourceId }
        if (match != null) {
            repository.insertSource(match.copy(isBlocked = false, isUserAdded = true))
        } else {
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0L
            repository.insertSource(
                SuggestionSource(
                    sourceId = sourceId,
                    count = 1,
                    isBlocked = false,
                    isUserAdded = true,
                    sortOrder = maxSort + 1,
                ),
            )
        }
    }

    suspend fun toggleBlock(sourceId: Long) {
        val existing = repository.getSources()
        val match = existing.find { it.sourceId == sourceId } ?: return
        repository.insertSource(match.copy(isBlocked = !match.isBlocked))
    }

    suspend fun reorder(sources: List<SuggestionSource>) {
        sources.forEachIndexed { index, source ->
            repository.insertSource(source.copy(sortOrder = index.toLong()))
        }
    }

    suspend fun delete(sourceId: Long) {
        repository.deleteSource(sourceId)
    }

    suspend fun clear() {
        repository.clearSources()
    }
}
