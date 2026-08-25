package eu.kanade.tachiyomi.data.suggestions.anime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AnimeSuggestionsReport {
    val fetchedCount = MutableStateFlow(0)
    val failedCount = MutableStateFlow(0)
    val fetchedBySource = MutableStateFlow<Map<String, Int>>(emptyMap())
    val failedBySource = MutableStateFlow<Map<String, Int>>(emptyMap())
    val libraryFilteredCount = MutableStateFlow(0)
    val zeroScoreCount = MutableStateFlow(0)

    fun clear() {
        fetchedCount.value = 0
        failedCount.value = 0
        fetchedBySource.value = emptyMap()
        failedBySource.value = emptyMap()
        libraryFilteredCount.value = 0
        zeroScoreCount.value = 0
    }
}
