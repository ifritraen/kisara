package tachiyomi.domain.library.model

import tachiyomi.domain.category.model.Category

val Category?.sort: LibrarySort
    get() = LibrarySort.valueOf(this?.flags)
