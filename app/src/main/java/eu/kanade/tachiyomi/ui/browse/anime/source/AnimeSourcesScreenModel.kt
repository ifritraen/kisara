package eu.kanade.tachiyomi.ui.browse.anime.source

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel.Companion.PINNED_KEY
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledAnimeSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val togglePin: ToggleAnimeSourcePin = Injekt.get(),
) : StateScreenModel<SourcesScreenModel.State>(SourcesScreenModel.State()) {

    private val _events = Channel<SourcesScreenModel.Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private var rawSources: List<AnimeSource> = emptyList()

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(SourcesScreenModel.Event.FailedFetchingSources)
                }
                .collectLatest { sources ->
                    rawSources = sources
                    updateState()
                }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                state.map { it.nsfwOnly }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                state.map { it.selectedTag }.distinctUntilChanged(),
                preferences.customAnimeSourceTags().changes(),
                preferences.animeSourceTagMappings().changes(),
            ) { _, _, _, _, _ ->
                updateState()
            }.collectLatest { }
        }
    }

    private fun updateState() {
        val query = state.value.searchQuery.orEmpty()
        val nsfwOnly = state.value.nsfwOnly
        val selectedTag = state.value.selectedTag
        val allTags = preferences.customAnimeSourceTags().get()
        val sourceTagMappings = preferences.animeSourceTagMappings().get()

        val animeExtensionManager = Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
        val tagFilter: (Source) -> Boolean = { source ->
            if (selectedTag.isNullOrBlank()) {
                true
            } else {
                val direct = sourceTagMappings.contains("${source.id}:$selectedTag") ||
                    sourceTagMappings.contains("source_${source.id}:$selectedTag")
                if (direct) {
                    true
                } else {
                    val pkgName = animeExtensionManager.installedExtensionsFlow.value
                        .find { ext -> ext.sources.any { it.id == source.id } }?.pkgName
                    pkgName != null && (
                        sourceTagMappings.contains("ext_$pkgName:$selectedTag") ||
                        sourceTagMappings.contains("$pkgName:$selectedTag")
                    )
                }
            }
        }

        val isNsfwMap = animeExtensionManager.installedExtensionsFlow.value
            .flatMap { ext -> ext.sources.map { it.id to ext.isNsfw } }
            .toMap()

        val mappedAll = rawSources.map { it.toSource() }
        val filtered = mappedAll
            .filter { !nsfwOnly || isNsfwMap[it.id] != false }
            .filter(tagFilter)

        val (pinned, others) = if (query.isBlank()) {
            filtered.partition { source ->
                rawSources.find { s -> s.id == source.id }?.pin?.contains(Pin.Actual) == true
            }
        } else {
            Pair(emptyList(), filtered)
        }

        val items = buildList {
            val queryFiltered = if (query.isNotBlank()) {
                others.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.visualName.contains(query, ignoreCase = true) ||
                        it.id.toString() == query
                }
            } else {
                others
            }

            if (pinned.isNotEmpty() && query.isBlank()) {
                add(SourceUiModel.Header(PINNED_KEY, false))
                pinned.forEach { add(SourceUiModel.Item(it)) }
            }

            val byLang = queryFiltered.groupBy { it.lang }
                .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

            byLang.forEach { (lang, sources) ->
                if (sources.isNotEmpty()) {
                    add(SourceUiModel.Header(lang, false))
                    sources.forEach { add(SourceUiModel.Item(it)) }
                }
            }
        }

        mutableState.update {
            it.copy(
                isLoading = false,
                items = items.toImmutableList(),
                allTags = allTags.toImmutableSet(),
                sourceTagMappings = sourceTagMappings.toImmutableSet(),
            )
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun toggleNsfwOnly() {
        mutableState.update { it.copy(nsfwOnly = !it.nsfwOnly) }
    }

    fun togglePin(source: Source) {
        screenModelScope.launchIO {
            rawSources.find { it.id == source.id }?.let {
                togglePin.await(it)
            }
        }
    }

    fun toggleSource(source: Source) {
        screenModelScope.launchIO {
            toggleSource.await(source.id)
        }
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = SourcesScreenModel.Dialog.SourceLongClick(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    var dialog: SourcesScreenModel.Dialog?
        get() = state.value.dialog
        set(value) {
            mutableState.update { it.copy(dialog = value) }
        }

    fun setSelectedTag(tag: String?) {
        mutableState.update { it.copy(selectedTag = tag) }
    }

    fun toggleSourceSelection(sourceId: Long) {
        mutableState.update {
            val current = it.selectedSources
            val updated = if (current.contains(sourceId)) current - sourceId else current + sourceId
            it.copy(selectedSources = updated.toImmutableSet())
        }
    }

    fun clearSourceSelection() {
        mutableState.update { it.copy(selectedSources = persistentSetOf()) }
    }

    fun saveSourceTags(sourceId: Long, selectedTags: Set<String>, newTag: String?) {
        val currentAllTags = preferences.customAnimeSourceTags().get().toMutableSet()
        if (newTag != null) {
            currentAllTags.add(newTag)
            preferences.customAnimeSourceTags().set(currentAllTags)
        }

        val prefix = "$sourceId:"
        val currentMappings = preferences.animeSourceTagMappings().get().filterNot { it.startsWith(prefix) }.toMutableSet()
        selectedTags.forEach { tag ->
            currentMappings.add("$prefix$tag")
        }
        preferences.animeSourceTagMappings().set(currentMappings)
    }

    fun saveBulkSourceTags(sourceIds: List<Long>, tagsToAdd: Set<String>, newTag: String?) {
        val currentAllTags = preferences.customAnimeSourceTags().get().toMutableSet()
        if (newTag != null) {
            currentAllTags.add(newTag)
            preferences.customAnimeSourceTags().set(currentAllTags)
        }

        val currentMappings = preferences.animeSourceTagMappings().get().toMutableSet()
        sourceIds.forEach { sourceId ->
            val prefix = "$sourceId:"
            tagsToAdd.forEach { tag ->
                currentMappings.add("$prefix$tag")
            }
        }
        preferences.animeSourceTagMappings().set(currentMappings)
        clearSourceSelection()
    }

    fun uninstallExtension(source: Source) {
        val extension = Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
            .installedExtensionsFlow.value.find { ext -> ext.sources.any { it.id == source.id } } ?: return
        Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>().uninstallExtension(extension)
    }

    private fun AnimeSource.toSource(): Source {
        val flags = when {
            Pin.Actual in pin -> Pins.pinned
            else -> Pins.unpinned
        }
        return Source(
            id = id,
            lang = lang,
            name = name,
            supportsLatest = supportsLatest,
            isStub = isStub,
            pin = flags,
            isUsedLast = isUsedLast,
        )
    }
}
