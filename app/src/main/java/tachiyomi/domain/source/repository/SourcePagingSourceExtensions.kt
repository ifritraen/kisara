package tachiyomi.domain.source.repository

import androidx.paging.PagingSource
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.domain.manga.model.Manga

typealias SourcePagingSource = PagingSource<Long, Pair<Manga, RaisedSearchMetadata?>>
