package tachiyomi.domain.suggestions.model

data class SuggestionSource(
    val sourceId: Long,
    val count: Long,
    val isBlocked: Boolean,
    val isUserAdded: Boolean,
    val sortOrder: Long,
)
