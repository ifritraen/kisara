package tachiyomi.data.source

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.log.xLogE
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceSearchPagingSource(
    source: CatalogueSource,
    private val query: String,
    private val filters: FilterList,
) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query.sanitize(), filters)
    }
}

class SourcePopularPagingSource(source: CatalogueSource) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(source: CatalogueSource) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    protected val source: CatalogueSource,
    protected val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : PagingSource<Long, Pair<Manga, RaisedSearchMetadata?>>() {

    protected val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1L

        return try {
            val mangasPage = withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            // SY -->
            getPageLoadResult(params, mangasPage)
            // SY <--
        } catch (e: Throwable) {
            xLogE("${this::class.simpleName}: Failed to load paging source", e)
            LoadResult.Error(e)
        }
    }

    // SY -->
    protected open suspend fun getPageLoadResult(
        @Suppress("UNUSED_PARAMETER")
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, Pair<Manga, RaisedSearchMetadata?>> {
        val page = params.key ?: 1L
        val metadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else {
            emptyList()
        }
        // SY <--

        val filterMangaByBlockedContent = Injekt.get<tachiyomi.domain.suggestions.interactor.FilterMangaByBlockedContent>()
        val filters = filterMangaByBlockedContent.getBlockedFilters()

        val mangaPairs = mangasPage.mangas
            .mapIndexed { index, sManga -> sManga.toDomainManga(source.id) to metadata.getOrNull(index) }
            .filter { seenManga.add(it.first.url) }

        val localMangas = networkToLocalManga(mangaPairs.map { it.first })
        val manga = localMangas.zip(mangaPairs.map { it.second })
            .filterNot { (mangaItem, _) -> filterMangaByBlockedContent.isMangaBlocked(mangaItem, filters) }

        return LoadResult.Page(
            data = manga,
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

fun eu.kanade.tachiyomi.source.model.SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogDescription = description,
        ogGenre = getGenres(),
        ogStatus = status.toLong(),
        ogThumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}

class NoResultsException : Exception()
