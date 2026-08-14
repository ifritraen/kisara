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
                if (activeGroupId.startsWith("tag_")) {
                    val tag = activeGroupId.removePrefix("tag_")
                    val sourceMappings = preferences.sourceTagMappings().get()
                    val extPrefix = "ext_"
                    val sourcePrefix = "source_"
                    val installedExts = extensionManager.installedExtensionsFlow.value

                    base.filter { source ->
                        val hasDirectTag = sourceMappings.contains("${source.id}:$tag") ||
                            sourceMappings.contains("$sourcePrefix${source.id}:$tag")
                        if (hasDirectTag) return@filter true
                        val parentExt = installedExts.find { ext -> ext.sources.any { it.id == source.id } }
                        if (parentExt != null) {
                            sourceMappings.contains("$extPrefix${parentExt.pkgName}:$tag")
                        } else {
                            false
                        }
                    }
                } else {
                    val customGroup = preferences.customSearchGroups().get().firstOrNull { it.id == activeGroupId }
                    if (customGroup != null) {
                        base.filter { it.id in customGroup.sourceIds }
                    } else {
                        base
                    }
                }
            }
            SourceFilter.All -> base
        }
    }
}
