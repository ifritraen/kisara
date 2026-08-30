package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.library.anime.AnimeLibraryItem
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.browse.bulk.parseQueries
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getVisibleAnimeCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedAnimeCategory().asState(screenModelScope)

    init {
        // Collect Library anime and categories
        combine(
            getLibraryAnime.subscribe(),
            getAnimeCategories.subscribe(),
            getVisibleAnimeCategories.subscribe(),
            state.mapDistinct { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
            libraryPreferences.animeSortingMode().changes(),
        ) { libraryAnime, allCategories, visibleCategories, searchQuery, sortMode ->
            val categories = if (allCategories.isNotEmpty()) visibleCategories else listOf(Category(0L, "", 0L, 0L, null, false))
            val items = libraryAnime.map { AnimeLibraryItem(it) }

            val languages = items.mapNotNull {
                val src = sourceManager.get(it.anime.source)
                src?.lang
            }.distinct()

            val libraryMap = mutableMapOf<Category, List<AnimeLibraryItem>>()

            for (category in categories) {
                val filtered = items.filter { item ->
                    val inCategory = if (category.isSystemCategory) {
                        item.category == 0L || item.category == category.id
                    } else {
                        item.category == category.id
                    }
                    if (!inCategory) return@filter false

                    val subQueries = if (!searchQuery.isNullOrBlank()) parseQueries(searchQuery) else emptyList()
                    val matchesQuery = subQueries.isEmpty() || subQueries.any { item.matches(it, sourceManager) }
                    val matchesUnseen = when (state.value.unseenFilter) {
                        TriState.DISABLED -> true
                        TriState.ENABLED_IS -> item.unseenCount > 0
                        TriState.ENABLED_NOT -> item.unseenCount == 0L
                    }
                    val matchesDownloaded = when (state.value.downloadedFilter) {
                        TriState.DISABLED -> true
                        TriState.ENABLED_IS -> item.isDownloaded
                        TriState.ENABLED_NOT -> !item.isDownloaded
                    }
                    val matchesStarted = when (state.value.startedFilter) {
                        TriState.DISABLED -> true
                        TriState.ENABLED_IS -> item.hasStarted
                        TriState.ENABLED_NOT -> !item.hasStarted
                    }
                    val matchesBookmarked = when (state.value.bookmarkedFilter) {
                        TriState.DISABLED -> true
                        TriState.ENABLED_IS -> item.hasBookmarks
                        TriState.ENABLED_NOT -> !item.hasBookmarks
                    }
                    val matchesCompleted = when (state.value.completedFilter) {
                        TriState.DISABLED -> true
                        TriState.ENABLED_IS -> item.anime.status == 2L // COMPLETED
                        TriState.ENABLED_NOT -> item.anime.status != 2L
                    }
                    val matchesLanguage = state.value.languageFilter.isEmpty() ||
                        sourceManager.get(item.anime.source)?.lang in state.value.languageFilter

                    matchesQuery && matchesUnseen && matchesDownloaded && matchesStarted &&
                        matchesBookmarked && matchesCompleted && matchesLanguage
                }

                val categorySort = AnimeLibrarySort.valueOf(category.flags)
                val effectiveSort = if (categorySort != AnimeLibrarySort.default) categorySort else sortMode
                val sorted = sortItems(filtered, effectiveSort)
                libraryMap[category] = sorted
            }

            mutableState.update { state ->
                state.copy(
                    isLoading = false,
                    hasLoaded = true,
                    categories = categories.toPersistentList(),
                    library = libraryMap.toPersistentMap(),
                    libraryLanguages = languages.toPersistentList(),
                    sort = sortMode,
                )
            }
        }
            .flowOn(Dispatchers.IO)
            .launchIn(screenModelScope)

        // Observe filter preferences
        combine(
            listOf(
                libraryPreferences.filterDownloadedAnime().changes(),
                libraryPreferences.filterUnseenAnime().changes(),
                libraryPreferences.filterStartedAnime().changes(),
                libraryPreferences.filterBookmarkedAnime().changes(),
                libraryPreferences.filterCompletedAnime().changes(),
                libraryPreferences.filterAnimeLanguages().changes(),
            ),
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            val downloaded = array[0] as TriState
            @Suppress("UNCHECKED_CAST")
            val unseen = array[1] as TriState
            @Suppress("UNCHECKED_CAST")
            val started = array[2] as TriState
            @Suppress("UNCHECKED_CAST")
            val bookmarked = array[3] as TriState
            @Suppress("UNCHECKED_CAST")
            val completed = array[4] as TriState
            @Suppress("UNCHECKED_CAST")
            val languages = array[5] as Set<String>
            mutableState.update { state ->
                state.copy(
                    downloadedFilter = downloaded,
                    unseenFilter = unseen,
                    startedFilter = started,
                    bookmarkedFilter = bookmarked,
                    completedFilter = completed,
                    languageFilter = languages,
                )
            }
        }.launchIn(screenModelScope)
    }

    private fun sortItems(items: List<AnimeLibraryItem>, sort: AnimeLibrarySort): List<AnimeLibraryItem> {
        val comparator: Comparator<AnimeLibraryItem> = when (sort.type) {
            AnimeLibrarySort.Type.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            AnimeLibrarySort.Type.LastSeen -> compareBy { it.lastSeen }
            AnimeLibrarySort.Type.LastUpdate -> compareBy { it.anime.lastUpdate }
            AnimeLibrarySort.Type.UnseenCount -> compareBy { it.unseenCount }
            AnimeLibrarySort.Type.TotalEpisodes -> compareBy { it.totalEpisodes }
            AnimeLibrarySort.Type.LatestEpisode -> compareBy { it.libraryAnime.latestUpload }
            AnimeLibrarySort.Type.EpisodeFetchDate -> compareBy { it.libraryAnime.episodeFetchedAt }
            AnimeLibrarySort.Type.DateAdded -> compareBy { it.dateAdded }
            AnimeLibrarySort.Type.Random -> compareBy { it.id }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        }

        val sorted = if (sort.isAscending) items.sortedWith(comparator) else items.sortedWith(comparator.reversed())
        return if (sort.type != AnimeLibrarySort.Type.Random) {
            sorted.sortedByDescending { it.pinned }
        } else {
            sorted
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSort(type: AnimeLibrarySort.Type, direction: AnimeLibrarySort.Direction) {
        val newSort = AnimeLibrarySort(type, direction)
        libraryPreferences.animeSortingMode().set(newSort)
    }

    fun toggleSelection(item: AnimeLibraryItem) {
        mutableState.update { state ->
            val newSelection = if (state.selection.any { it.id == item.id }) {
                state.selection.filterNot { it.id == item.id }
            } else {
                state.selection + item
            }
            state.copy(selection = newSelection.toPersistentList())
        }
    }

    fun toggleRangeSelection(item: AnimeLibraryItem) {
        val currentCategory = state.value.categories.getOrNull(activeCategoryIndex) ?: return
        val currentItems = state.value.library[currentCategory] ?: return
        val lastSelected = state.value.selection.lastOrNull() ?: item
        val startIndex = currentItems.indexOfFirst { it.id == lastSelected.id }.coerceAtLeast(0)
        val endIndex = currentItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        val range = if (startIndex <= endIndex) startIndex..endIndex else endIndex..startIndex
        val itemsInRange = currentItems.slice(range)

        mutableState.update { state ->
            val newSelection = (state.selection + itemsInRange).distinctBy { it.id }
            state.copy(selection = newSelection.toPersistentList())
        }
    }

    fun selectAll(pageIndex: Int) {
        val category = state.value.categories.getOrNull(pageIndex) ?: return
        val items = state.value.library[category] ?: return
        mutableState.update { it.copy(selection = items.toPersistentList()) }
    }

    fun invertSelection(pageIndex: Int) {
        val category = state.value.categories.getOrNull(pageIndex) ?: return
        val items = state.value.library[category] ?: return
        mutableState.update { state ->
            val newSelection = items.filterNot { item -> state.selection.any { it.id == item.id } }
            state.copy(selection = newSelection.toPersistentList())
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launchIO {
            val createCategory = Injekt.get<tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName>()
            createCategory.await(name, parentId)
            openChangeCategoryDialog()
        }
    }

    fun openChangeCategoryDialog() {
        val animes = state.value.selection.map { it.anime }
        if (animes.isEmpty()) return
        screenModelScope.launchIO {
            val allCategories = getAnimeCategories.await()
            val common = animes.map { getAnimeCategories.await(it.id).toSet() }
                .reduce { left, right -> left.intersect(right) }
            val mix = animes.map { getAnimeCategories.await(it.id).toSet() }
                .flatten()
                .distinct()
                .subtract(common)

            val preselected = allCategories
                .filterNot { it.isSystemCategory }
                .map { category ->
                    when (category) {
                        in common -> CheckboxState.State.Checked(category)
                        in mix -> CheckboxState.TriState.Exclude(category)
                        else -> CheckboxState.State.None(category)
                    }
                }
                .toImmutableList()

            mutableState.update {
                it.copy(dialog = Dialog.ChangeCategory(animes, preselected))
            }
        }
    }

    fun openDeleteAnimeDialog() {
        val animes = state.value.selection.map { it.anime }
        if (animes.isNotEmpty()) {
            mutableState.update { it.copy(dialog = Dialog.DeleteAnime(animes)) }
        }
    }

    fun updateAnimeCategories(
        animes: List<Anime>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        if (animes.isEmpty()) return
        screenModelScope.launchIO {
            animes.forEach { anime ->
                val categoryIds = getAnimeCategories.await(anime.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()
                setAnimeCategories.await(anime.id, categoryIds)
            }
            clearSelection()
            closeDialog()
        }
    }

    fun markSeenSelection(seen: Boolean) {
        val selected = state.value.selection.map { it.anime }
        if (selected.isEmpty()) return
        screenModelScope.launchIO {
            selected.forEach { anime ->
                setSeenStatus.await(anime.id, seen)
            }
            clearSelection()
        }
    }

    fun removeAnime(
        animes: List<Anime>,
        deleteFromLibrary: Boolean,
        deleteEpisodes: Boolean,
    ) {
        screenModelScope.launchIO {
            val toDelete = animes.distinctBy { it.id }
            if (deleteFromLibrary) {
                toDelete.forEach { anime ->
                    updateAnime.await(
                        AnimeUpdate(
                            id = anime.id,
                            favorite = false,
                        ),
                    )
                }
            }
            if (deleteEpisodes) {
                toDelete.forEach { anime ->
                    animeDownloadManager.deleteAnime(anime, sourceManager.get(anime.source) ?: return@forEach)
                }
            }
            clearSelection()
            closeDialog()
        }
    }

    fun resetFilters() {
        libraryPreferences.filterDownloadedAnime().set(TriState.DISABLED)
        libraryPreferences.filterUnseenAnime().set(TriState.DISABLED)
        libraryPreferences.filterStartedAnime().set(TriState.DISABLED)
        libraryPreferences.filterBookmarkedAnime().set(TriState.DISABLED)
        libraryPreferences.filterCompletedAnime().set(TriState.DISABLED)
        libraryPreferences.filterAnimeLanguages().set(emptySet())
    }

    fun setUnseenFilter(state: TriState) = libraryPreferences.filterUnseenAnime().set(state)
    fun setDownloadedFilter(state: TriState) = libraryPreferences.filterDownloadedAnime().set(state)
    fun setStartedFilter(state: TriState) = libraryPreferences.filterStartedAnime().set(state)
    fun setBookmarkedFilter(state: TriState) = libraryPreferences.filterBookmarkedAnime().set(state)
    fun setCompletedFilter(state: TriState) = libraryPreferences.filterCompletedAnime().set(state)

    fun toggleLanguage(language: String) {
        val current = libraryPreferences.filterAnimeLanguages().get()
        val newSet = if (language in current) current - language else current + language
        libraryPreferences.filterAnimeLanguages().set(newSet)
    }

    fun setAllLanguages(languages: Set<String>) = libraryPreferences.filterAnimeLanguages().set(languages)
    fun clearLanguageFilter() = libraryPreferences.filterAnimeLanguages().set(emptySet())

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object Settings : Dialog
        data class ChangeCategory(
            val animes: List<Anime>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAnime(val animes: List<Anime>) : Dialog
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val hasLoaded: Boolean = false,
        val categories: PersistentList<Category> = persistentListOf(),
        val library: PersistentMap<Category, List<AnimeLibraryItem>> = persistentMapOf(),
        val selection: PersistentList<AnimeLibraryItem> = persistentListOf(),
        val searchQuery: String? = null,
        val downloadedFilter: TriState = TriState.DISABLED,
        val unseenFilter: TriState = TriState.DISABLED,
        val startedFilter: TriState = TriState.DISABLED,
        val bookmarkedFilter: TriState = TriState.DISABLED,
        val completedFilter: TriState = TriState.DISABLED,
        val languageFilter: Set<String> = emptySet(),
        val libraryLanguages: PersistentList<String> = persistentListOf(),
        val sort: AnimeLibrarySort = AnimeLibrarySort.default,
        val dialog: Dialog? = null,
    ) {
        val hasActiveFilters: Boolean
            get() = downloadedFilter != TriState.DISABLED ||
                unseenFilter != TriState.DISABLED ||
                startedFilter != TriState.DISABLED ||
                bookmarkedFilter != TriState.DISABLED ||
                completedFilter != TriState.DISABLED ||
                languageFilter.isNotEmpty()

        val isLibraryEmpty: Boolean
            get() = library.values.all { it.isEmpty() }

        fun getAnimeCountForCategory(category: Category): Int? {
            return library[category]?.size
        }

        fun getToolbarTitle(defaultTitle: String, defaultCategoryTitle: String, page: Int): LibraryToolbarTitle {
            val category = categories.getOrNull(page)
            val title = if (category == null || category.id == 0L) defaultTitle else category.name
            return LibraryToolbarTitle(title, library[category]?.size)
        }
    }
}

private fun <T, R> kotlinx.coroutines.flow.Flow<T>.mapDistinct(transform: (T) -> R): kotlinx.coroutines.flow.Flow<R> {
    return this.map(transform).distinctUntilChanged()
}
