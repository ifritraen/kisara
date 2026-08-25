package tachiyomi.domain.entries.anime.model

fun Anime.asAnimeCover(): AnimeCover {
    return AnimeCover(
        animeId = id,
        sourceId = source,
        isAnimeFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
