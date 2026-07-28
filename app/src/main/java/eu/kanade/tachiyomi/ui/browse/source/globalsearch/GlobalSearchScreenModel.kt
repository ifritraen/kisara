package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.tachiyomi.source.CatalogueSource

class GlobalSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : SearchScreenModel(State(searchQuery = initialQuery)) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }

        // KMK -->
        shouldPinnedSourcesHidden()
        // KMK <--
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        val base = super.getEnabledSources()
        return when (state.value.sourceFilter) {
            SourceFilter.PinnedOnly -> base.filter { "${it.id}" in pinnedSources }
            SourceFilter.Custom -> {
                val activeGroupId = preferences.globalSearchActiveCustomGroupId().get()
                val customGroup = preferences.customSearchGroups().get().firstOrNull { it.id == activeGroupId }
                if (customGroup != null) {
                    base.filter { it.id in customGroup.sourceIds }
                } else {
                    base
                }
            }
            SourceFilter.All -> base
        }
    }
}
