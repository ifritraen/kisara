package eu.kanade.tachiyomi.ui.browse.anime.duplicate

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeDuplicateGroupUI(
    val id: String,
    val main: LibraryAnime,
    val duplicates: List<LibraryAnime>,
    val isSkipped: Boolean = false,
    val isResolved: Boolean = false,
)

data class AnimeDuplicateScreenState(
    val isLoading: Boolean = true,
    val groups: List<AnimeDuplicateGroupUI> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val activeCategoryAnime: Anime? = null,
    val categories: List<Category> = emptyList(),
)

class AnimeDuplicateScreenModel(
    val targetAnimeId: Long? = null,
    val targetAnimeIds: List<Long>? = null,
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<AnimeDuplicateScreenState>(AnimeDuplicateScreenState()) {

    init {
        loadDuplicates()
    }

    fun loadDuplicates() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, groups = emptyList()) }
            val allLibrary = getLibraryAnime.await()
            val categories = try { getCategories.await() } catch (e: Exception) { emptyList() }
            mutableState.update { it.copy(categories = categories) }

            val searchIds = targetAnimeIds ?: targetAnimeId?.let { listOf(it) }
            val isManualTargetCheck = !searchIds.isNullOrEmpty()

            val history = uiPreferences.duplicateHistory().get()
            val maxScan = uiPreferences.duplicateMaxScanCount().get()
            val limit = if (maxScan > 0) maxScan else Int.MAX_VALUE

            val filteredLibrary = if (isManualTargetCheck) {
                allLibrary
            } else {
                allLibrary.filter { it.anime.id.toString() !in history }
            }

            val tracksMap = try {
                val getTracks = Injekt.get<GetAnimeTracks>()
                filteredLibrary.associate { it.anime.id to getTracks.await(it.anime.id) }
            } catch (e: Exception) {
                emptyMap()
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

            filteredLibrary.forEach { parent[it.anime.id] = it.anime.id }

            // Group 1: By cleaned title
            val byTitle = filteredLibrary.groupBy { cleanTitle(it.anime.title) }
            byTitle.forEach { (title, list) ->
                if (title.isNotEmpty() && list.size > 1) {
                    val firstId = list.first().anime.id
                    list.drop(1).forEach { union(firstId, it.anime.id) }
                }
            }

            // Group 2: By trackers
            val byTracker = mutableMapOf<Pair<Long, Long>, MutableList<Long>>()
            filteredLibrary.forEach { anime ->
                val tracks = tracksMap[anime.anime.id] ?: emptyList()
                tracks.forEach { track ->
                    val key = Pair(track.trackerId, track.remoteId)
                    byTracker.getOrPut(key) { mutableListOf() }.add(anime.anime.id)
                }
            }
            byTracker.forEach { (_, ids) ->
                if (ids.size > 1) {
                    val firstId = ids.first()
                    ids.drop(1).forEach { union(firstId, it) }
                }
            }

            // Collect grouped duplicates
            val groupsMap = filteredLibrary.groupBy { find(it.anime.id) }
            val groupsList = groupsMap.values
                .filter { it.size > 1 }
                .map { group ->
                    AnimeDuplicateGroupUI(
                        id = group.first().anime.id.toString(),
                        main = group.first(),
                        duplicates = group.drop(1),
                    )
                }
                .filter { group ->
                    searchIds.isNullOrEmpty() ||
                        searchIds.contains(group.main.anime.id) ||
                        group.duplicates.any { searchIds.contains(it.anime.id) }
                }
                .take(limit)

            mutableState.update { it.copy(isLoading = false, groups = groupsList) }
        }
    }

    fun refreshDuplicates() {
        uiPreferences.duplicateHistory().set(emptySet())
        loadDuplicates()
    }

    fun toggleSelection(animeId: Long) {
        mutableState.update { state ->
            val selected = state.selectedIds
            if (animeId in selected) {
                state.copy(selectedIds = selected - animeId)
            } else {
                state.copy(selectedIds = selected + animeId)
            }
        }
    }

    fun skipGroup(groupId: String) {
        val group = state.value.groups.firstOrNull { it.id == groupId }
        if (group != null) {
            val idsToSkip = (listOf(group.main) + group.duplicates).map { it.anime.id.toString() }
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

    fun showChangeCategory(anime: Anime) {
        mutableState.update { it.copy(activeCategoryAnime = anime) }
    }

    fun closeChangeCategory() {
        mutableState.update { it.copy(activeCategoryAnime = null) }
    }

    fun changeAnimeCategories(anime: Anime, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCategories.await(anime.id, categoryIds)
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
                val selectedInGroup = groupItems.filter { it.anime.id in selected }

                if (selectedInGroup.isNotEmpty()) {
                    val toDelete = groupItems.filter { it.anime.id !in selected }
                    toDelete.forEach { item ->
                        updateAnime.await(
                            AnimeUpdate(
                                id = item.anime.id,
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
