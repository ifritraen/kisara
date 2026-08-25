package eu.kanade.tachiyomi.ui.browse.novel.duplicate

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetDuplicateLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.track.novel.interactor.GetTracksPerNovel
import tachiyomi.domain.track.novel.model.NovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelDuplicateGroupUI(
    val id: String,
    val main: LibraryNovel,
    val duplicates: List<LibraryNovel>,
    val isSkipped: Boolean = false,
    val isResolved: Boolean = false,
)

data class NovelDuplicateScreenState(
    val isLoading: Boolean = true,
    val groups: List<NovelDuplicateGroupUI> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val activeCategoryNovel: Novel? = null,
    val categories: List<Category> = emptyList(),
)

class NovelDuplicateScreenModel(
    val targetNovelId: Long? = null,
    val targetNovelIds: List<Long>? = null,
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getDuplicateLibraryNovel: GetDuplicateLibraryNovel = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<NovelDuplicateScreenState>(NovelDuplicateScreenState()) {

    init {
        loadDuplicates()
    }

    fun loadDuplicates() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, groups = emptyList()) }
            val allLibrary = getLibraryNovel.await()
            val categories = try {
                getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        parentId = it.parentId,
                        hidden = it.hidden,
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
            mutableState.update { it.copy(categories = categories) }

            val tracksMap = try {
                Injekt.get<GetTracksPerNovel>().subscribe().first()
            } catch (e: Exception) {
                emptyMap<Long, List<NovelTrack>>()
            }

            val searchIds = targetNovelIds ?: targetNovelId?.let { listOf(it) }
            val isManualTargetCheck = !searchIds.isNullOrEmpty()

            val history = uiPreferences.duplicateHistory().get()
            val maxScan = uiPreferences.duplicateMaxScanCount().get()
            val limit = if (maxScan > 0) maxScan else Int.MAX_VALUE

            val filteredLibrary = if (isManualTargetCheck) {
                allLibrary
            } else {
                allLibrary.filter { it.novel.id.toString() !in history }
            }

            // Union-Find implementation
            val parent = mutableMapOf<Long, Long>()
            fun find(i: Long): Long {
                var root = i
                while (root != parent[root]) {
                    root = parent[root] ?: root
                }
                var curr = i
                while (curr != root) {
                    val nxt = parent[curr] ?: curr
                    parent[curr] = root
                    curr = nxt
                }
                return root
            }
            fun union(i: Long, j: Long) {
                val rootI = find(i)
                val rootJ = find(j)
                if (rootI != rootJ) {
                    parent[rootI] = rootJ
                }
            }

            filteredLibrary.forEach { parent[it.novel.id] = it.novel.id }

            // Group 1: By cleaned title
            val byTitle = filteredLibrary.groupBy { cleanTitle(it.novel.title) }
            byTitle.forEach { (title, list) ->
                if (title.isNotEmpty() && list.size > 1) {
                    val firstId = list.first().novel.id
                    list.drop(1).forEach { union(firstId, it.novel.id) }
                }
            }

            // Group 2: By trackers
            val byTracker = mutableMapOf<Pair<Long, Long>, MutableList<Long>>()
            filteredLibrary.forEach { novel ->
                val tracks = tracksMap[novel.novel.id] ?: emptyList()
                tracks.forEach { track ->
                    val key = Pair(track.trackerId, track.remoteId)
                    byTracker.getOrPut(key) { mutableListOf() }.add(novel.novel.id)
                }
            }
            byTracker.forEach { (_, ids) ->
                if (ids.size > 1) {
                    val firstId = ids.first()
                    ids.drop(1).forEach { union(firstId, it) }
                }
            }

            // Collect grouped duplicates
            val groupsMap = filteredLibrary.groupBy { find(it.novel.id) }
            val groupsList = groupsMap.values
                .filter { it.size > 1 }
                .map { group ->
                    NovelDuplicateGroupUI(
                        id = group.first().novel.id.toString(),
                        main = group.first(),
                        duplicates = group.drop(1),
                    )
                }
                .filter { group ->
                    searchIds.isNullOrEmpty() ||
                        searchIds.contains(group.main.novel.id) ||
                        group.duplicates.any { searchIds.contains(it.novel.id) }
                }
                .take(limit)

            mutableState.update { it.copy(isLoading = false, groups = groupsList) }
        }
    }

    fun refreshDuplicates() {
        uiPreferences.duplicateHistory().set(emptySet())
        loadDuplicates()
    }

    fun toggleSelection(novelId: Long) {
        mutableState.update { state ->
            val selected = state.selectedIds
            if (novelId in selected) {
                state.copy(selectedIds = selected - novelId)
            } else {
                state.copy(selectedIds = selected + novelId)
            }
        }
    }

    fun skipGroup(groupId: String) {
        val group = state.value.groups.firstOrNull { it.id == groupId }
        if (group != null) {
            val idsToSkip = (listOf(group.main) + group.duplicates).map { it.novel.id.toString() }
            val currentHistory = uiPreferences.duplicateHistory().get()
            uiPreferences.duplicateHistory().set(currentHistory + idsToSkip)
        }

        mutableState.update { state ->
            state.copy(
                groups = state.groups.map {
                    if (it.id == groupId) it.copy(isSkipped = true) else it
                },
            )
        }
    }

    fun showChangeCategory(novel: Novel) {
        mutableState.update { it.copy(activeCategoryNovel = novel) }
    }

    fun closeChangeCategory() {
        mutableState.update { it.copy(activeCategoryNovel = null) }
    }

    fun changeNovelCategories(novel: Novel, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setNovelCategories.await(novel.id, categoryIds)
            closeChangeCategory()
        }
    }

    fun processResolvedGroups() {
        screenModelScope.launchIO {
            val state = mutableState.value
            val selected = state.selectedIds
            val groups = state.groups

            groups.forEach { group ->
                if (group.isResolved || group.isSkipped) return@forEach

                val groupItems = listOf(group.main) + group.duplicates
                val selectedInGroup = groupItems.filter { it.novel.id in selected }

                if (selectedInGroup.isNotEmpty()) {
                    val toDelete = groupItems.filter { it.novel.id !in selected }
                    toDelete.forEach { item ->
                        updateNovel.await(
                            NovelUpdate(
                                id = item.novel.id,
                                favorite = false,
                                dateAdded = 0L,
                            ),
                        )
                    }
                }
            }

            loadDuplicates()
        }
    }

    private fun cleanTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\[[^]]*\]"""), "")
            .replace(Regex("""\{[^}]*\}"""), "")
            .replace(Regex("""[^a-zA-Z0-9]"""), "")
            .trim()
    }
}
