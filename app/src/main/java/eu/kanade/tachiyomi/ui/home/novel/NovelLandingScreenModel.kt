package eu.kanade.tachiyomi.ui.home.novel

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.manga.interactor.GetTrackerRecommendations
import eu.kanade.domain.manga.interactor.TrackerRecommendation
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@Serializable
data class CachedFeedNovel(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val coverLastModified: Long,
    val sourceName: String,
    val fetchTime: Long,
)

class NovelLandingScreenModel(
    private val app: Application = Injekt.get(),
    private val getLibraryNovel: GetLibraryNovel = Injekt.get(),
    private val novelHistoryRepository: tachiyomi.domain.history.novel.repository.NovelHistoryRepository = Injekt.get(),
    private val getNovelUpdates: GetNovelUpdates = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getTrackerRecommendations: GetTrackerRecommendations = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<NovelLandingScreenModel.State>(State()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile by lazy { File(app.cacheDir, "novel_feed_cache.json") }

    init {
        // 1. Subscribe to Novel Library for Unread / Forgotten Favorites
        screenModelScope.launch {
            getLibraryNovel.subscribe()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { libraryNovelList ->
                    if (libraryNovelList.isEmpty()) {
                        mutableState.update { it.copy(libraryRandom = persistentListOf()) }
                        return@collectLatest
                    }
                    val unreadNovels = libraryNovelList.filter { it.unreadCount > 0 }
                    val candidates = unreadNovels.ifEmpty { libraryNovelList }
                    val random = candidates.map { it.novel }.shuffled().take(20)
                    mutableState.update {
                        it.copy(libraryRandom = random.toImmutableList())
                    }
                }
        }

        // 2. Load Spotlight from Dynamic Suggestions
        loadSpotlightSuggestions()

        // 3. Subscribe to Continue Reading (Novel History)
        screenModelScope.launch {
            novelHistoryRepository.getNovelHistory("")
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collectLatest { historyList ->
                    val recent = historyList.take(20)
                    mutableState.update {
                        it.copy(history = recent.toImmutableList())
                    }
                }
        }

        // 4. Subscribe to Fresh Releases (Novel Updates)
        screenModelScope.launch {
            val after = Instant.now().minus(30, ChronoUnit.DAYS)
            getNovelUpdates.subscribe(after)
                .catch { logcat(LogPriority.ERROR, it) }
                .flowOn(Dispatchers.IO)
                .collectLatest { updatesList ->
                    val recent = updatesList.take(20)
                    mutableState.update {
                        it.copy(updates = recent.toImmutableList())
                    }
                }
        }

        // 5. Load Tracker Recommendations & Feed Cache
        loadTrackerRecommendations(force = false)
        loadFeedCache()
        triggerBackgroundFeedFetch(force = false)
    }

    private fun extractTopTags(library: List<tachiyomi.domain.library.novel.LibraryNovel>): List<String> {
        return library.flatMap { item ->
            item.novel.genre.orEmpty()
                .flatMap { it.split(",", ";", "/").map(String::trim) }
                .filter { it.isNotBlank() }
        }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    fun loadSpotlightSuggestions() {
        screenModelScope.launchIO {
            try {
                val library = getLibraryNovel.await()
                val topTags = extractTopTags(library)
                val topTag = topTags.firstOrNull()

                val sources = sourceManager.getCatalogueSources()
                val suggestions = mutableListOf<Novel>()
                val favoriteUrls = library.map { it.novel.url }.toSet()
                val favoriteTitles = library.map { it.novel.title.lowercase().trim() }.toSet()
                val favoriteIds = library.map { it.novel.id }.toSet()

                for (tag in topTags.take(3)) {
                    val jobs = sources.take(4).map { source ->
                        async {
                            try {
                                val result = source.getSearchNovels(1, tag, source.getFilterList())
                                val domainNovels = result.novels.map { it.toDomainNovel(source.id) }
                                networkToLocalNovel.await(domainNovels)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val batch = jobs.awaitAll().flatten()
                    suggestions.addAll(batch.filterNot { 
                        it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                    })
                }

                if (suggestions.isEmpty()) {
                    val jobs = sources.take(4).map { source ->
                        async {
                            try {
                                val result = source.getPopularNovels(1)
                                val domainNovels = result.novels.map { it.toDomainNovel(source.id) }
                                networkToLocalNovel.await(domainNovels)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val batch = jobs.awaitAll().flatten()
                    suggestions.addAll(batch.filterNot { 
                        it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                    })
                }

                val finalSpotlight = suggestions.distinctBy { it.id }.shuffled().take(10)
                if (finalSpotlight.isNotEmpty()) {
                    mutableState.update {
                        it.copy(
                            spotlightNovel = finalSpotlight.toImmutableList(),
                            spotlightTagName = topTag?.let { tag -> "Spotlight: $tag" },
                        )
                    }
                    fetchMissingSpotlightDetails(finalSpotlight)
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load novel spotlight suggestions" }
            }
        }
    }

    private fun fetchMissingSpotlightDetails(spotlightList: List<Novel>) {
        screenModelScope.launchIO {
            val updated = spotlightList.map { novel ->
                if (novel.genre.isNullOrEmpty() || novel.author.isNullOrBlank()) {
                    try {
                        val source = sourceManager.get(novel.source) as? NovelCatalogueSource
                        if (source != null) {
                            val networkNovel = source.getNovelDetails(novel.toSNovel())
                            val updatedNovel = novel.copy(
                                author = networkNovel.author?.ifBlank { novel.author } ?: novel.author,
                                genre = networkNovel.getGenres()?.ifEmpty { novel.genre } ?: novel.genre,
                                description = networkNovel.description?.ifBlank { novel.description } ?: novel.description,
                                status = networkNovel.status.toLong(),
                            )
                            updateNovel.await(
                                NovelUpdate(
                                    id = novel.id,
                                    author = updatedNovel.author,
                                    genre = updatedNovel.genre,
                                    description = updatedNovel.description,
                                    status = updatedNovel.status,
                                ),
                            )
                            updatedNovel
                        } else {
                            novel
                        }
                    } catch (_: Exception) {
                        novel
                    }
                } else {
                    novel
                }
            }
            mutableState.update { state ->
                state.copy(spotlightNovel = updated.toImmutableList())
            }
        }
    }

    fun loadTrackerRecommendations(force: Boolean = false) {
        screenModelScope.launchIO {
            try {
                val cached = getTrackerRecommendations.getCached(MediaType.NOVEL)
                if (cached.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = cached.toImmutableList()) }
                }
                val fresh = getTrackerRecommendations.fetch(MediaType.NOVEL, force = force)
                if (fresh.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = fresh.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load novel tracker recommendations" }
            }
        }
    }

    private fun loadFeedCache() {
        screenModelScope.launchIO {
            if (cacheFile.exists()) {
                try {
                    val text = cacheFile.readText()
                    val list = json.decodeFromString<List<CachedFeedNovel>>(text)
                    mutableState.update { it.copy(feed = list.toImmutableList()) }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to parse novel feed cache" }
                }
            }
        }
    }

    fun triggerBackgroundFeedFetch(force: Boolean) {
        if (state.value.isFeedRefreshing) return

        screenModelScope.launchIO {
            val lastFetch = state.value.feed.firstOrNull()?.fetchTime ?: 0L
            val now = System.currentTimeMillis()
            if (!force && now - lastFetch < 15 * 60 * 1000) {
                return@launchIO
            }

            mutableState.update { it.copy(isFeedRefreshing = true) }
            try {
                val feedSourceLimit = uiPreferences.homeFeedSourceCount().get()
                val feedItemLimit = uiPreferences.homeFeedItemsCount().get()

                val usePinned = uiPreferences.homeFeedUsePinnedSources().get()
                val pinnedSet = uiPreferences.homeFeedPinnedSources().get().mapNotNull { it.toLongOrNull() }.toSet()

                val catalogueSources = sourceManager.getCatalogueSources()
                val sourcesToQuery = if (usePinned && pinnedSet.isNotEmpty()) {
                    pinnedSet.mapNotNull { pinnedId -> catalogueSources.firstOrNull { it.id == pinnedId } }
                } else {
                    catalogueSources.take(feedSourceLimit)
                }

                if (sourcesToQuery.isEmpty()) {
                    mutableState.update { it.copy(isFeedRefreshing = false) }
                    return@launchIO
                }

                val library = getLibraryNovel.await()
                val topTags = extractTopTags(library)
                val favoriteUrls = library.map { it.novel.url }.toSet()
                val favoriteTitles = library.map { it.novel.title.lowercase().trim() }.toSet()
                val favoriteIds = library.map { it.novel.id }.toSet()

                val fetchJobs = sourcesToQuery.map { source ->
                    async {
                        try {
                            val itemsFromTags = mutableListOf<Novel>()
                            if (topTags.isNotEmpty()) {
                                for (tag in topTags.take(2)) {
                                    try {
                                        val tagResult = source.getSearchNovels(1, tag, source.getFilterList())
                                        val domainNovels = tagResult.novels.map { it.toDomainNovel(source.id) }
                                        itemsFromTags.addAll(networkToLocalNovel.await(domainNovels))
                                    } catch (_: Throwable) {}
                                }
                            }

                            val novelPage = if (source.supportsLatest) {
                                source.getLatestUpdates(1)
                            } else {
                                source.getPopularNovels(1)
                            }
                            val latestDomain = novelPage.novels.map { snovel ->
                                networkToLocalNovel.await(snovel.toDomainNovel(source.id))
                            }

                            val combined = (itemsFromTags + latestDomain)
                                .filterNot { 
                                    it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                                }
                                .distinctBy { it.id }
                            combined.map { localNovel ->
                                CachedFeedNovel(
                                    id = localNovel.id,
                                    sourceId = source.id,
                                    title = localNovel.title,
                                    thumbnailUrl = localNovel.thumbnailUrl,
                                    favorite = localNovel.favorite,
                                    coverLastModified = localNovel.coverLastModified,
                                    sourceName = source.name,
                                    fetchTime = now,
                                )
                            }.take(feedItemLimit)
                        } catch (e: Throwable) {
                            emptyList()
                        }
                    }
                }

                val results = fetchJobs.awaitAll()

                // Interleave/Mix results together
                val mixedList = mutableListOf<CachedFeedNovel>()
                val seenIds = mutableSetOf<Long>()
                for (i in 0 until feedItemLimit) {
                    for (sourceList in results) {
                        if (i < sourceList.size) {
                            val item = sourceList[i]
                            if (seenIds.add(item.id)) {
                                mixedList.add(item)
                            }
                        }
                    }
                }

                if (mixedList.isNotEmpty()) {
                    try {
                        cacheFile.writeText(json.encodeToString(mixedList))
                    } catch (_: Exception) {}
                    mutableState.update { it.copy(feed = mixedList.toImmutableList()) }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch novel feed" }
            } finally {
                mutableState.update { it.copy(isFeedRefreshing = false) }
            }
        }
    }

    fun toggleFavorite(novelId: Long, currentFavorite: Boolean) {
        screenModelScope.launchIO {
            val getNovel = Injekt.get<GetNovel>()
            val dbNovel = getNovel.await(novelId) ?: return@launchIO

            val isFavoriteNow = !currentFavorite
            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetNovelCategories>()
                val categories = getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        hidden = it.hidden,
                        parentId = it.parentId,
                    )
                }.filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultNovelCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()
                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    novel = dbNovel,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        updateNovel.await(
                            NovelUpdate(
                                id = novelId,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setNovelCategories = Injekt.get<SetNovelCategories>()
                        setNovelCategories.await(novelId, listOf(defaultCategory.id))
                        updateLocalFavoriteState(novelId, true)
                    }
                    else -> {
                        updateNovel.await(
                            NovelUpdate(
                                id = novelId,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        updateLocalFavoriteState(novelId, true)
                    }
                }
            } else {
                val getCategories = Injekt.get<GetNovelCategories>()
                val categories = getCategories.await().map {
                    Category(
                        id = it.id,
                        name = it.name,
                        order = it.order,
                        flags = it.flags,
                        hidden = it.hidden,
                        parentId = it.parentId,
                    )
                }.filterNot { it.isSystemCategory }
                val preselectedIds = getCategories.await(novelId).map { it.id }
                if (categories.isNotEmpty()) {
                    mutableState.update { state ->
                        state.copy(
                            dialog = State.Dialog.ChangeCategory(
                                novel = dbNovel,
                                initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                            ),
                        )
                    }
                } else {
                    updateNovel.await(
                        NovelUpdate(
                            id = novelId,
                            favorite = false,
                            dateAdded = 0L,
                        ),
                    )
                    updateLocalFavoriteState(novelId, false)
                }
            }
        }
    }

    fun setNovelCategories(novel: Novel, categories: List<Long>) {
        screenModelScope.launchIO {
            updateNovel.await(
                NovelUpdate(
                    id = novel.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setNovelCategories = Injekt.get<SetNovelCategories>()
            setNovelCategories.await(novel.id, categories)
            updateLocalFavoriteState(novel.id, true)
        }
    }

    private fun updateLocalFavoriteState(novelId: Long, favorite: Boolean) {
        mutableState.update { state ->
            val updatedFeed = state.feed.map { item ->
                if (item.id == novelId) item.copy(favorite = favorite) else item
            }
            val updatedSpotlight = if (favorite) state.spotlightNovel else state.spotlightNovel.filterNot { it.id == novelId }
            state.copy(
                feed = updatedFeed.toImmutableList(),
                spotlightNovel = updatedSpotlight.toImmutableList(),
            )
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launchIO {
            val createCategory = Injekt.get<CreateNovelCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }

    fun dismissSpotlight(novel: Novel) {
        mutableState.update { state ->
            val updated = state.spotlightNovel.filterNot { it.id == novel.id }
            state.copy(spotlightNovel = updated.toImmutableList())
        }
    }

    @Immutable
    data class State(
        val spotlightNovel: ImmutableList<Novel> = persistentListOf(),
        val spotlightTagName: String? = null,
        val history: ImmutableList<NovelHistoryWithRelations> = persistentListOf(),
        val updates: ImmutableList<NovelUpdatesWithRelations> = persistentListOf(),
        val libraryRandom: ImmutableList<Novel> = persistentListOf(),
        val trackerRecommendations: ImmutableList<TrackerRecommendation> = persistentListOf(),
        val feed: ImmutableList<CachedFeedNovel> = persistentListOf(),
        val isFeedRefreshing: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val novel: Novel,
                val initialSelection: ImmutableList<CheckboxState<Category>>,
            ) : Dialog
        }
    }
}
