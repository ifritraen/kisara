package tachiyomi.domain.entries.anime.model

fun Anime.toAnimeUpdate(): AnimeUpdate {
    return AnimeUpdate(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate,
        nextUpdate = nextUpdate,
        fetchInterval = fetchInterval,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        episodeFlags = episodeFlags,
        coverLastModified = coverLastModified,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        updateStrategy = updateStrategy,
        initialized = initialized,
        version = version,
        fetchType = fetchType,
        parentId = parentId,
        seasonFlags = seasonFlags,
        seasonNumber = seasonNumber,
        seasonSourceOrder = seasonSourceOrder,
    )
}
