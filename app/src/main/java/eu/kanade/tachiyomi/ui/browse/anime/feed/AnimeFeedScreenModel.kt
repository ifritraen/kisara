package eu.kanade.tachiyomi.ui.browse.anime.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeFeedItemUI(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
    val source: AnimeCatalogueSource,
    val title: String,
    val subtitle: String,
    val results: List<Anime>?,
)

data class AnimeFeedScreenState(
    val items: List<AnimeFeedItemUI>? = null,
    val isReordering: Boolean = false,
    val dialog: AnimeFeedScreenModel.Dialog? = null,
    val isLoading: Boolean = true,
) {
    val isEmpty get() = items.isNullOrEmpty()
    val isLoadingItems get() = items?.any { it.results == null } == true
}

class AnimeFeedScreenModel(
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchById: GetSavedSearchById = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val reorderFeed: ReorderFeed = Injekt.get(),
) : StateScreenModel<AnimeFeedScreenState>(AnimeFeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    sealed interface Dialog {
        data class AddSource(val sources: List<AnimeCatalogueSource>) : Dialog
        data class AddSearch(val source: AnimeCatalogueSource, val savedSearches: List<SavedSearch>) : Dialog
        data class DeleteSource(val feed: FeedSavedSearch, val source: AnimeCatalogueSource) : Dialog
        data class ChangeCategory(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    init {
        screenModelScope.launch {
            getFeedSavedSearchGlobal.subscribe()
                .distinctUntilChanged()
                .collectLatest { feeds ->
                    sourceManager.isInitialized.first { it }
                    val items = feeds.mapNotNull { feed ->
                        val source = sourceManager.get(feed.source) as? AnimeCatalogueSource ?: return@mapNotNull null
                        val savedSearch = feed.savedSearch?.let { getSavedSearchById.await(it) }
                        AnimeFeedItemUI(
                            feed = feed,
                            savedSearch = savedSearch,
                            source = source,
                            title = savedSearch?.name ?: source.name,
                            subtitle = if (savedSearch != null) {
                                source.name
                            } else {
                                LocaleHelper.getLocalizedDisplayName(source.lang)
                            },
                            results = null,
                        )
                    }

                    mutableState.update { it.copy(items = items, isLoading = false) }
                    loadFeed(items)
                }
        }
    }

    private fun loadFeed(items: List<AnimeFeedItemUI>) {
        val hideInLibrary = sourcePreferences.hideInAnimeLibraryItems().get()
        ioCoroutineScope.launch {
            val results = items.map { item ->
                async {
                    val savedSearchId = item.feed.savedSearch
                    val page = try {
                        if (savedSearchId != null) {
                            val savedSearch = item.savedSearch ?: getSavedSearchById.await(savedSearchId)
                            if (savedSearch != null) {
                                item.source.getSearchAnime(1, savedSearch.query.orEmpty(), item.source.getFilterList())
                            } else if (item.source.supportsLatest) {
                                item.source.getLatestUpdates(1)
                            } else {
                                item.source.getPopularAnime(1)
                            }
                        } else if (item.source.supportsLatest) {
                            item.source.getLatestUpdates(1)
                        } else {
                            item.source.getPopularAnime(1)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val animes = page?.animes?.let { list ->
                        networkToLocalAnime.await(list.map { it.toDomainAnime(item.source.id) })
                    }?.filter { !hideInLibrary || !it.favorite } ?: emptyList()

                    item to animes
                }
            }.awaitAll()

            mutableState.update { state ->
                val updatedItems = state.items?.map { currentItem ->
                    val pair = results.find { it.first.feed.id == currentItem.feed.id }
                    if (pair != null) currentItem.copy(results = pair.second) else currentItem
                }
                state.copy(items = updatedItems)
            }
        }
    }

    @Composable
    fun getAnime(initialAnime: Anime): State<Anime> {
        return produceState(initialValue = initialAnime) {
            getAnime.subscribe(initialAnime.url, initialAnime.source)
                .collectLatest { value = it ?: value }
        }
    }

    fun openAddSourceDialog() {
        val currentFeedIds = mutableState.value.items?.map { it.source.id }?.toSet() ?: emptySet()
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getCatalogueSources()
            .distinctBy { it.id }
            .filter { "${it.id}" !in disabledSources }
            .filter { it.lang in enabledLanguages }
            .filter { it.id !in currentFeedIds }
            .sortedWith(compareBy { "${it.name.lowercase()} (${it.lang})" })
        mutableState.update { it.copy(dialog = Dialog.AddSource(sources)) }
    }

    fun onSourceSelected(source: AnimeCatalogueSource) {
        screenModelScope.launch {
            val savedSearches = getSavedSearchBySourceId.await(source.id)
            mutableState.update { it.copy(dialog = Dialog.AddSearch(source, savedSearches)) }
        }
    }

    fun addFeed(source: AnimeCatalogueSource, savedSearch: SavedSearch?) {
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
        val source = sourceManager.get(feed.source) as? AnimeCatalogueSource ?: return
        mutableState.update { it.copy(dialog = Dialog.DeleteSource(feed, source)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        screenModelScope.launch {
            deleteFeedSavedSearchById.await(feed.id)
            dismissDialog()
        }
    }

    fun removeSource(feed: FeedSavedSearch) {
        deleteFeed(feed)
    }

    fun toggleReordering() {
        mutableState.update { it.copy(isReordering = !it.isReordering) }
        if (!mutableState.value.isReordering) refresh()
    }

    fun reorderFeed(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex)
        }
    }

    fun refresh() {
        val currentItems = mutableState.value.items ?: return
        val resetItems = currentItems.map { it.copy(results = null) }
        mutableState.update { it.copy(items = resetItems) }
        loadFeed(resetItems)
    }

    fun onAnimeLongClick(anime: Anime) {
        screenModelScope.launch {
            val getAnimeCategories = Injekt.get<GetAnimeCategories>()
            val categories = getAnimeCategories.await().filterNot { it.isSystemCategory }
            if (anime.favorite) {
                val preselectedIds = getAnimeCategories.await(anime.id).map { it.id }
                mutableState.update {
                    it.copy(
                        dialog = Dialog.ChangeCategory(
                            anime = anime,
                            initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            } else {
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }
                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        mutableState.update {
                            it.copy(
                                dialog = Dialog.ChangeCategory(
                                    anime = anime,
                                    initialSelection = categories.mapAsCheckboxState { false }.toImmutableList(),
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        val updateAnime = Injekt.get<UpdateAnime>()
                        updateAnime.await(
                            AnimeUpdate(
                                id = anime.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setAnimeCategories = Injekt.get<SetAnimeCategories>()
                        setAnimeCategories.await(anime.id, listOf(defaultCategory.id))
                    }
                    else -> {
                        val updateAnime = Injekt.get<UpdateAnime>()
                        updateAnime.await(
                            AnimeUpdate(
                                id = anime.id,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setAnimeCategories(anime: Anime, categories: List<Long>) {
        screenModelScope.launch {
            val updateAnime = Injekt.get<UpdateAnime>()
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setAnimeCategories = Injekt.get<SetAnimeCategories>()
            setAnimeCategories.await(anime.id, categories)
            dismissDialog()
        }
    }

    fun deleteAnime(anime: Anime) {
        screenModelScope.launch {
            val updateAnime = Injekt.get<UpdateAnime>()
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
            dismissDialog()
        }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launch {
            val createCategory = Injekt.get<CreateAnimeCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }
}
