package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ModifySuggestionTag(
    private val repository: SuggestionRepository,
) {
    suspend fun addTag(tagText: String) {
        val cleanTag = tagText.trim().lowercase()
        if (cleanTag.isEmpty()) return
        val existing = repository.getTags()
        val match = existing.find { it.tag == cleanTag }
        if (match != null) {
            repository.insertTag(match.copy(isBlocked = false, isUserAdded = true))
        } else {
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0L
            repository.insertTag(
                SuggestionTag(
                    tag = cleanTag,
                    count = 1,
                    isBlocked = false,
                    isUserAdded = true,
                    sortOrder = maxSort + 1,
                ),
            )
        }
    }

    suspend fun toggleBlock(tagText: String) {
        val existing = repository.getTags()
        val match = existing.find { it.tag == tagText } ?: return
        repository.insertTag(match.copy(isBlocked = !match.isBlocked))
    }

    suspend fun reorder(tags: List<SuggestionTag>) {
        tags.forEachIndexed { index, tag ->
            repository.insertTag(tag.copy(sortOrder = index.toLong()))
        }
    }

    suspend fun delete(tagText: String) {
        repository.deleteTag(tagText)
    }

    suspend fun clear() {
        repository.clearTags()
    }
}
