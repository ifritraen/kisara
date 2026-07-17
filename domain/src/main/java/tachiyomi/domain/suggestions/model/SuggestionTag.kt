package tachiyomi.domain.suggestions.model

data class SuggestionTag(
    val tag: String,
    val count: Long,
    val isBlocked: Boolean,
    val isUserAdded: Boolean,
    val sortOrder: Long,
)
