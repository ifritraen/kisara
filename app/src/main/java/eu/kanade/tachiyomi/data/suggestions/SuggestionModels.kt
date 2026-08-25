package eu.kanade.tachiyomi.data.suggestions

sealed interface SuggestionState {
    data object Idle : SuggestionState
    data object Disabled : SuggestionState
    data object Loading : SuggestionState
    data class Empty(val message: String? = null) : SuggestionState
    data class Success(val items: List<SuggestionItem>) : SuggestionState
    data class Error(val error: Throwable? = null, val message: String = "") : SuggestionState
}

data class SuggestionItem(
    val id: Long = 0L,
    val sourceId: Long = 0L,
    val title: String = "",
    val thumbnailUrl: String? = null,
    val url: String = "",
    val favorite: Boolean = false,
    val providerId: String? = null,
    val providerUrl: String? = null,
)

fun suggestionCoverModel(item: SuggestionItem): Any? = item.thumbnailUrl
