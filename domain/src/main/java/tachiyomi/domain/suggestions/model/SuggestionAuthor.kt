package tachiyomi.domain.suggestions.model

data class SuggestionAuthor(
    val author: String,
    val count: Long,
    val isBlocked: Boolean,
    val isUserAdded: Boolean,
    val sortOrder: Long,
)
