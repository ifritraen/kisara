package tachiyomi.domain.suggestions.model

import tachiyomi.domain.manga.model.Manga

data class Suggestion(
    val manga: Manga,
    val relevance: Double,
    val createdAt: Long,
)
