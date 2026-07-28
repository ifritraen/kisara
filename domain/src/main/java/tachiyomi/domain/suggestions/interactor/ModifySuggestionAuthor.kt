package tachiyomi.domain.suggestions.interactor

import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.repository.SuggestionRepository

class ModifySuggestionAuthor(
    private val repository: SuggestionRepository,
) {
    suspend fun addAuthor(authorText: String) {
        val cleanAuthor = authorText.trim().lowercase()
        if (cleanAuthor.isEmpty()) return
        val existing = repository.getAuthors()
        val match = existing.find { it.author == cleanAuthor }
        if (match != null) {
            repository.insertAuthor(match.copy(isBlocked = false, isUserAdded = true))
        } else {
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0L
            repository.insertAuthor(
                SuggestionAuthor(
                    author = cleanAuthor,
                    count = 1,
                    isBlocked = false,
                    isUserAdded = true,
                    sortOrder = maxSort + 1,
                ),
            )
        }
    }

    suspend fun toggleBlock(authorText: String) {
        val existing = repository.getAuthors()
        val match = existing.find { it.author == authorText } ?: return
        repository.insertAuthor(match.copy(isBlocked = !match.isBlocked))
    }

    suspend fun reorder(authors: List<SuggestionAuthor>) {
        authors.forEachIndexed { index, author ->
            repository.insertAuthor(author.copy(sortOrder = index.toLong()))
        }
    }

    suspend fun delete(authorText: String) {
        repository.deleteAuthor(authorText)
    }

    suspend fun clear() {
        repository.clearAuthors()
    }
}
