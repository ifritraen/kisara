package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

class GlobalAnimeSearchScreenModel(
    initialQuery: String = "",
) : AnimeSearchScreenModel(
    initialState = State(
        searchQuery = initialQuery,
    ),
) {
    init {
        if (initialQuery.isNotBlank()) {
            search()
        }
    }
}
