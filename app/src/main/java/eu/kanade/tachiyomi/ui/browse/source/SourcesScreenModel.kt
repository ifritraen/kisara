package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class SourcesScreenModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    // SY -->
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val getShowLatest: GetShowLatest = Injekt.get(),
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    val smartSearchConfig: SourcesScreen.SmartSearchConfig?,
    // SY <--
) : StateScreenModel<SourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        // KMK -->
        combine(
            sourcePreferences.customSourceTags().changes(),
            sourcePreferences.sourceTagMappings().changes(),
        ) { tags, mappings ->
            val fromMappings = mappings.mapNotNull {
                val idx = it.indexOf(':')
                if (idx != -1 && idx < it.length - 1) it.substring(idx + 1) else null
            }
            Pair((tags + fromMappings).filter { it.isNotBlank() }.toImmutableSet(), mappings.toImmutableSet())
        }.onEach { (allTags, mappings) ->
            mutableState.update {
                it.copy(
                    allTags = allTags,
                    sourceTagMappings = mappings,
                )
            }
        }.launchIn(screenModelScope)
        // KMK <--

        // SY -->
        val searchQueryFlow = state.map { it.searchQuery }
            .distinctUntilChanged()
            .debounce(SEARCH_DEBOUNCE_MILLIS)
        val nsfwFlow = state.map { it.nsfwOnly }
            .distinctUntilChanged()
        val selectedTagFlow = state.map { it.selectedTag }
            .distinctUntilChanged()
        val tagMappingsFlow = sourcePreferences.sourceTagMappings().changes()

        val filterParamsFlow = combine(
            searchQueryFlow,
            nsfwFlow,
            selectedTagFlow,
            tagMappingsFlow,
        ) { query, nsfw, tag, mappings ->
            FilterParams(query, nsfw, tag, mappings)
        }

        combine(
            filterParamsFlow,
            getEnabledSources.subscribe(),
            getSourceCategories.subscribe(),
            getShowLatest.subscribe(smartSearchConfig != null),
            flowOf(smartSearchConfig == null),
            ::collectLatestSources,
        )
            .catch {
                logcat(LogPriority.ERROR, it)
                _events.send(Event.FailedFetchingSources)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(screenModelScope)

        sourcePreferences.dataSaver().changes()
            .onEach {
                mutableState.update {
                    it.copy(
                        dataSaverEnabled = sourcePreferences.dataSaver().get() != DataSaver.NONE,
                    )
                }
            }
            .launchIn(screenModelScope)
        // SY <--
    }

    private data class FilterParams(
        val searchQuery: String?,
        val nsfwOnly: Boolean,
        val selectedTag: String?,
        val tagMappings: Set<String>,
    )

    private fun collectLatestSources(
        // KMK -->
        filters: FilterParams,
        unfilteredSources: List<Source>,
        // sources: List<Source>,
        // KMK <--
        categories: List<String>,
        showLatest: Boolean,
        showPin: Boolean,
    ) {
        // KMK -->
        val searchQuery = filters.searchQuery
        val nsfwOnly = filters.nsfwOnly
        val selectedTag = filters.selectedTag
        val tagMappings = filters.tagMappings
        val queryFilter: (String?) -> ((Source) -> Boolean) = { query ->
            filter@{ source ->
                if (query.isNullOrBlank()) return@filter true
                query.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                        source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
        }
        val tagFilter: (Source) -> Boolean = { source ->
            if (selectedTag.isNullOrBlank()) {
                true
            } else {
                val direct = tagMappings.contains("${source.id}:$selectedTag") ||
                    tagMappings.contains("source_${source.id}:$selectedTag")
                if (direct) {
                    true
                } else {
                    val pkgName = source.installedExtension?.pkgName
                    pkgName != null && (
                        tagMappings.contains("ext_$pkgName:$selectedTag") ||
                        tagMappings.contains("$pkgName:$selectedTag")
                    )
                }
            }
        }
        val sources = unfilteredSources
            .filterNot { it.isLocal() && it.isUsedLast }
            .filter { !nsfwOnly || it.installedExtension?.isNsfw != false }
            .filter(queryFilter(searchQuery))
            .filter(tagFilter)
        // KMK <--
        mutableState.update { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                // Sources without a lang defined will be placed at the end
                when {
                    d1 == LOCAL_KEY && d2 != LOCAL_KEY -> -1
                    d2 == LOCAL_KEY && d1 != LOCAL_KEY -> 1
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    // SY -->
                    d1.startsWith(CATEGORY_KEY_PREFIX) && !d2.startsWith(CATEGORY_KEY_PREFIX) -> -1
                    d2.startsWith(CATEGORY_KEY_PREFIX) && !d1.startsWith(CATEGORY_KEY_PREFIX) -> 1
                    // SY <--
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isLocal() -> LOCAL_KEY
                    // SY -->
                    it.category != null -> "$CATEGORY_KEY_PREFIX${it.category}"
                    // SY <--
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

            val pinnedOrder = sourcePreferences.pinnedSourcesOrdered().get()
                .split(",")
                .filter { it.isNotBlank() }
            byLang[PINNED_KEY]?.let { pinnedList ->
                byLang[PINNED_KEY] = pinnedList.sortedWith { s1, s2 ->
                    val id1 = s1.id.toString()
                    val id2 = s2.id.toString()
                    val idx1 = pinnedOrder.indexOf(id1)
                    val idx2 = pinnedOrder.indexOf(id2)
                    when {
                        idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                        idx1 != -1 -> -1
                        idx2 != -1 -> 1
                        else -> s1.name.compareTo(s2.name, ignoreCase = true)
                    }
                }.toMutableList()
            }

            state.copy(
                isLoading = false,
                items = byLang
                    .flatMap {
                        listOf(
                            SourceUiModel.Header(
                                it.key.removePrefix(CATEGORY_KEY_PREFIX),
                                it.value.firstOrNull()?.category != null,
                            ),
                            *it.value.map { source ->
                                SourceUiModel.Item(source)
                            }.toTypedArray(),
                        )
                    }
                    .toImmutableList(),
                // SY -->
                categories = categories
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                    .toImmutableList(),
                showPin = showPin,
                showLatest = showLatest,
                // SY <--
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    fun movePinnedSource(source: Source, moveUp: Boolean) {
        val idStr = source.id.toString()
        val currentOrdered = sourcePreferences.pinnedSourcesOrdered().get()
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableList()

        val index = currentOrdered.indexOf(idStr)
        if (index == -1) return

        val newIndex = if (moveUp) index - 1 else index + 1
        if (newIndex in 0 until currentOrdered.size) {
            val temp = currentOrdered[index]
            currentOrdered[index] = currentOrdered[newIndex]
            currentOrdered[newIndex] = temp

            sourcePreferences.pinnedSourcesOrdered().set(currentOrdered.joinToString(","))
        }
    }

    fun reorderPinnedSources(pinnedSources: List<Source>, fromIdx: Int, toIdx: Int) {
        val currentOrdered = pinnedSources.map { it.id.toString() }.toMutableList()
        if (fromIdx in currentOrdered.indices && toIdx in currentOrdered.indices) {
            val item = currentOrdered.removeAt(fromIdx)
            currentOrdered.add(toIdx, item)
            sourcePreferences.pinnedSourcesOrdered().set(currentOrdered.joinToString(","))
        }
    }

    // SY -->
    fun toggleExcludeFromDataSaver(source: Source) {
        toggleExcludeFromDataSaver.await(source)
    }

    fun setSourceCategories(source: Source, categories: List<String>) {
        setSourceCategories.await(source, categories)
    }

    fun showSourceCategoriesDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SourceCategories(source)) }
    }
    // SY <--

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SourceLongClick(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    var dialog: Dialog?
        get() = state.value.dialog
        set(value) {
            mutableState.update { it.copy(dialog = value) }
        }

    // KMK -->
    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun toggleNsfwOnly() {
        mutableState.update {
            it.copy(nsfwOnly = !it.nsfwOnly)
        }
    }
    // KMK <--

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    sealed class Dialog {
        data class SourceLongClick(val source: Source) : Dialog()
        data class SourceCategories(val source: Source) : Dialog()
        data class SourceTags(val source: Source) : Dialog()
        data class BulkSourceTags(val sources: List<Source>) : Dialog()
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<SourceUiModel> = persistentListOf(),
        // SY -->
        val categories: ImmutableList<String> = persistentListOf(),
        val showPin: Boolean = true,
        val showLatest: Boolean = false,
        val dataSaverEnabled: Boolean = false,
        // SY <--
        // KMK -->
        val searchQuery: String? = null,
        val nsfwOnly: Boolean = false,
        val allTags: kotlinx.collections.immutable.ImmutableSet<String> = kotlinx.collections.immutable.persistentSetOf(),
        val selectedTag: String? = null,
        val sourceTagMappings: kotlinx.collections.immutable.ImmutableSet<String> = kotlinx.collections.immutable.persistentSetOf(),
        val selectedSources: kotlinx.collections.immutable.ImmutableSet<Long> = kotlinx.collections.immutable.persistentSetOf(),
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
        val isBulkMode = selectedSources.isNotEmpty()
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

    fun setSelectedTag(tag: String?) {
        mutableState.update { it.copy(selectedTag = tag) }
    }

    fun saveSourceTags(sourceId: Long, selectedTags: Set<String>, newTag: String?) {
        val currentAllTags = sourcePreferences.customSourceTags().get().toMutableSet()
        if (newTag != null) {
            currentAllTags.add(newTag)
            sourcePreferences.customSourceTags().set(currentAllTags)
        }

        val prefix = "$sourceId:"
        val currentMappings = sourcePreferences.sourceTagMappings().get().filterNot { it.startsWith(prefix) }.toMutableSet()
        selectedTags.forEach { tag ->
            currentMappings.add("$prefix$tag")
        }
        sourcePreferences.sourceTagMappings().set(currentMappings)
    }

    fun saveBulkSourceTags(sourceIds: List<Long>, tagsToAdd: Set<String>, newTag: String?) {
        val currentAllTags = sourcePreferences.customSourceTags().get().toMutableSet()
        if (newTag != null) {
            currentAllTags.add(newTag)
            sourcePreferences.customSourceTags().set(currentAllTags)
        }

        val currentMappings = sourcePreferences.sourceTagMappings().get().toMutableSet()
        sourceIds.forEach { sourceId ->
            val prefix = "$sourceId:"
            tagsToAdd.forEach { tag ->
                currentMappings.add("$prefix$tag")
            }
        }
        sourcePreferences.sourceTagMappings().set(currentMappings)
        clearSourceSelection()
    }

    fun uninstallExtension(source: Source) {
        val extension = source.installedExtension ?: return
        Injekt.get<eu.kanade.tachiyomi.extension.ExtensionManager>().uninstallExtension(extension)
    }

    fun selectAllSources() {
        val allIds = state.value.items.filterIsInstance<SourceUiModel.Item>().map { it.source.id }
        mutableState.update { it.copy(selectedSources = allIds.toImmutableSet()) }
    }

    fun bulkPinSources() {
        val selected = state.value.selectedSources
        val sources = state.value.items.filterIsInstance<SourceUiModel.Item>()
            .map { it.source }
            .filter { it.id in selected }
        screenModelScope.launchNonCancellable {
            val anyUnpinned = sources.any { Pin.Pinned !in it.pin }
            sources.forEach { source ->
                if (anyUnpinned) {
                    if (Pin.Pinned !in source.pin) toggleSourcePin.await(source)
                } else {
                    if (Pin.Pinned in source.pin) toggleSourcePin.await(source)
                }
            }
            clearSourceSelection()
        }
    }

    fun bulkUninstallSources() {
        val selected = state.value.selectedSources
        val sources = state.value.items.filterIsInstance<SourceUiModel.Item>()
            .map { it.source }
            .filter { it.id in selected }
        val extensionManager = Injekt.get<eu.kanade.tachiyomi.extension.ExtensionManager>()
        sources.mapNotNull { it.installedExtension }.distinctBy { it.pkgName }.forEach { extension ->
            extensionManager.uninstallExtension(extension)
        }
        clearSourceSelection()
    }

    fun openBulkSourceTagsDialog() {
        val selected = state.value.selectedSources
        val sources = state.value.items.filterIsInstance<SourceUiModel.Item>()
            .map { it.source }
            .filter { it.id in selected }
        dialog = Dialog.BulkSourceTags(sources)
    }

    companion object {
        const val LOCAL_KEY = "local"
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        // SY -->
        const val CATEGORY_KEY_PREFIX = "category-"
        // SY <--
    }
}
