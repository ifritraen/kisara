package tachiyomi.domain.suggestions.model

data class SuggestionArtist(
    val artist: String,
    val count: Long,
    val isBlocked: Boolean,
    val isUserAdded: Boolean,
    val sortOrder: Long,
)
