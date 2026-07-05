package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.Source

class ToggleSourcePin(
    private val preferences: SourcePreferences,
) {

    fun await(source: Source) {
        val idStr = source.id.toString()
        val isPinned = idStr in preferences.pinnedSources().get()
        preferences.pinnedSources().getAndSet { pinned ->
            if (isPinned) pinned.minus(idStr) else pinned.plus(idStr)
        }

        val currentOrdered = preferences.pinnedSourcesOrdered().get()
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableList()

        if (isPinned) {
            currentOrdered.remove(idStr)
        } else {
            if (!currentOrdered.contains(idStr)) {
                currentOrdered.add(idStr)
            }
        }
        preferences.pinnedSourcesOrdered().set(currentOrdered.joinToString(","))
    }
}
