package eu.kanade.domain.source.novel.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.novel.model.Pin
import tachiyomi.domain.source.novel.model.Pins
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.source.local.entries.novel.LocalNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GetEnabledNovelSources(
    private val repository: NovelSourceRepository = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) {

    fun subscribe(): Flow<List<Source>> {
        return combine(
            preferences.enabledLanguages().changes(),
            preferences.disabledNovelSources().changes(),
            preferences.pinnedNovelSources().changes(),
            preferences.lastUsedNovelSource().changes(),
            repository.getNovelSources(),
        ) { enabledLangs, disabledIds, pinnedIds, lastUsedId, sources ->
            sources
                .filter { it.lang in enabledLangs || it.id == LocalNovelSource.ID }
                .filterNot { it.id.toString() in disabledIds }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap { source ->
                    val flag = if ("${source.id}" in pinnedIds) Pins.pinned else Pins.unpinned
                    val updated = source.copy(pin = flag)
                    val toFlatten = mutableListOf(updated)
                    if (updated.id == lastUsedId) {
                        toFlatten.add(updated.copy(isUsedLast = true, pin = updated.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
