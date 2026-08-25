package tachiyomi.domain.entries.novel.model

fun Novel.asNovelCover(): NovelCover {
    return NovelCover(
        novelId = id,
        sourceId = source,
        isNovelFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
