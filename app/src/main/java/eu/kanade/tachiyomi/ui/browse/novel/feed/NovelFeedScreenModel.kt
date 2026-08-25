package eu.kanade.tachiyomi.ui.browse.novel.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelFeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: NovelCatalogueSource,
    val title: String,
    val subtitle: String,
    val results: List<Novel>?,
)

data class NovelFeedScreenState(
    val items: List<NovelFeedItemUI>? = null,
    val isReordering: Boolean = false,
    val dialog: NovelFeedScreenModel.Dialog? = null,
) {
    val isLoading get() = items == null
    val isEmpty get() = items.isNullOrEmpty()
    val isLoadingItems get() = items?.any { it.results == null } == true
}

class NovelFeedScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
) : StateScreenModel<NovelFeedScreenState>(NovelFeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    sealed interface Dialog {
        data class AddSource(val sources: List<NovelCatalogueSource>) : Dialog
        data class AddSearch(val source: NovelCatalogueSource, val savedSearches: List<SavedSearch>) : Dialog
        data class DeleteSource(val feed: FeedSavedSearch, val source: NovelCatalogueSource) : Dialog
        data class ChangeCategory(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    init {
        getFeedSavedSearchGlobal.subscribe()
            .distinctUntilChanged()
            .onEach { feedEntries ->
                sourceManager.isInitialized.first { it }
                val items = resolveFeedItems(feedEntries)
                mutableState.update { it.copy(items = items) }
                loadFeed(items)
            }
            .catch { _events.send(Event.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    private fun resolveFeedItems(feedEntries: List<FeedSavedSearch>): List<NovelFeedItemUI> {
        return feedEntries.mapNotNull { feed ->
            val source = sourceManager.get(feed.source) as? NovelCatalogueSource ?: return@mapNotNull null
            NovelFeedItemUI(
                feed = feed,
                savedSearch = null,
                source = source,
                title = source.name,
                subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                results = null,
            )
        }
    }

    private fun loadFeed(items: List<NovelFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInNovelLibraryItems().get()
        ioCoroutineScope.launch {
            val results = items.map { itemUI ->
                async {
                    try {
                        val novels = if (itemUI.feed.savedSearch != null) {
                            val feed = itemUI.feed
                            val savedSearchId = feed.savedSearch
                            val ss = if (savedSearchId != null) getSavedSearchById.await(savedSearchId) else null
                            if (ss != null) {
                                val baseFilters = itemUI.source.getFilterList()
                                itemUI.source.getSearchNovels(1, ss.query ?: "", baseFilters).novels
                            } else {
                                itemUI.source.getLatestUpdates(1).novels
                            }
                        } else if (itemUI.source.supportsLatest) {
                            itemUI.source.getLatestUpdates(1).novels
                        } else {
                            itemUI.source.getPopularNovels(1).novels
                        }
                        val converted = novels.map { snovel ->
                            networkToLocalNovel.await(snovel.toDomainNovel(itemUI.source.id))
                        }.filter { !hideInLibrary || !it.favorite }
                        itemUI to converted
                    } catch (_: Exception) {
                        itemUI to emptyList<Novel>()
                    }
                }
            }.awaitAll()
            mutableState.update { state ->
                val updatedItems = state.items?.map { item ->
                    val pair = results.find { it.first.source.id == item.source.id }
                    if (pair != null) item.copy(results = pair.second) else item
                }
                state.copy(items = updatedItems)
            }
        }
    }

    fun refresh() {
        val currentItems = mutableState.value.items
        if (currentItems != null) {
            val resetItems = currentItems.map { it.copy(results = null) }
            mutableState.update { it.copy(items = resetItems) }
            loadFeed(resetItems)
        }
    }

    fun openAddSourceDialog() {
        val currentFeedIds = mutableState.value.items?.map { it.source.id }?.toSet() ?: emptySet()
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledNovelSources().get()
        val sources = sourceManager.getCatalogueSources()
            .distinctBy { it.id }
            .filter { "${it.id}" !in disabledSources }
            .filter { it.lang in enabledLanguages }
            .filter { it.id !in currentFeedIds }
            .sortedWith(compareBy { "${it.name.lowercase()} (${it.lang})" })
        mutableState.update { it.copy(dialog = Dialog.AddSource(sources)) }
    }

    fun onSourceSelected(source: NovelCatalogueSource) {
        screenModelScope.launch {
            val savedSearches = getSavedSearchBySourceId.await(source.id)
            mutableState.update { it.copy(dialog = Dialog.AddSearch(source, savedSearches)) }
        }
    }

    fun addFeed(source: NovelCatalogueSource, savedSearch: SavedSearch?) {
        val feed = FeedSavedSearch(
            id = -1,
            source = source.id,
            savedSearch = savedSearch?.id,
            global = true,
            feedOrder = 0,
        )
        screenModelScope.launch { insertFeedSavedSearch.await(feed) }
        dismissDialog()
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        val source = sourceManager.get(feed.source) as? NovelCatalogueSource ?: return
        mutableState.update { it.copy(dialog = Dialog.DeleteSource(feed = feed, source = source)) }
    }

    fun removeSource(feed: FeedSavedSearch) {
        screenModelScope.launch { deleteFeedSavedSearchById.await(feed.id) }
        dismissDialog()
    }

    fun toggleReordering() {
        mutableState.update { it.copy(isReordering = !it.isReordering) }
        if (!mutableState.value.isReordering) refresh()
    }

    fun reorderFeed(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch { reorderFeed.changeOrder(feed, newIndex) }
    }

    @Composable
    fun getNovel(initialNovel: Novel): State<Novel> {
        return produceState(initialValue = initialNovel) {
            getNovel.subscribe(initialNovel.url, initialNovel.source)
                .filterNotNull()
                .collectLatest { novel -> value = novel }
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun onNovelLongClick(novel: Novel) {
        screenModelScope.launch {
            val getNovelCategories = Injekt.get<GetNovelCategories>()
            val categories = getNovelCategories.await().map {
                Category(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                    hidden = it.hidden,
                    parentId = it.parentId,
                )
            }.filterNot { it.isSystemCategory }
            if (novel.favorite) {
                val preselectedIds = getNovelCategories.await(novel.id).map { it.id }
                mutableState.update {
                    it.copy(
                        dialog = Dialog.ChangeCategory(
                            novel = novel,
                            initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            } else {
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultNovelCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }
                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        mutableState.update {
                            it.copy(
                                dialog = Dialog.ChangeCategory(
                                    novel = novel,
                                    initialSelection = categories.mapAsCheckboxState { false }.toImmutableList(),
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        val updateNovel = Injekt.get<UpdateNovel>()
                        updateNovel.await(
                            NovelUpdate(
                                id = novel.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setNovelCategories = Injekt.get<SetNovelCategories>()
                        setNovelCategories.await(novel.id, listOf(defaultCategory.id))
                    }
                    else -> {
                        val updateNovel = Injekt.get<UpdateNovel>()
                        updateNovel.await(
                            NovelUpdate(
                                id = novel.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setNovelCategories(novel: Novel, categories: List<Long>) {
        screenModelScope.launch {
            val updateNovel = Injekt.get<UpdateNovel>()
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setNovelCategories = Injekt.get<SetNovelCategories>()
            setNovelCategories.await(novel.id, categories)
            dismissDialog()
        }
    }

    fun deleteNovel(novel: Novel) {
        screenModelScope.launch {
            val updateNovel = Injekt.get<UpdateNovel>()
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
            dismissDialog()
        }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launch {
            val createCategory = Injekt.get<CreateNovelCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }
}
