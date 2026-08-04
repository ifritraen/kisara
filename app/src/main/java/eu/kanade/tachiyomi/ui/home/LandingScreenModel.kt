package eu.kanade.tachiyomi.ui.home

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.interactor.GetSuggestionSources
import tachiyomi.domain.suggestions.interactor.GetSuggestionTags
import tachiyomi.domain.suggestions.interactor.GetSuggestions
import tachiyomi.domain.suggestions.model.Suggestion
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.ZonedDateTime

@Serializable
data class CachedFeedManga(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val coverLastModified: Long,
    val sourceName: String,
    val fetchTime: Long,
)

class LandingScreenModel(
    private val app: Application = Injekt.get(),
    private val getSuggestions: GetSuggestions = Injekt.get(),
    private val getSuggestionTags: GetSuggestionTags = Injekt.get(),
    private val getSuggestionSources: GetSuggestionSources = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val filterMangaByBlockedContent: tachiyomi.domain.suggestions.interactor.FilterMangaByBlockedContent = Injekt.get(),
) : StateScreenModel<LandingScreenModel.State>(State()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(app.cacheDir, "home_feed_cache.json")

    init {
        // 1. Load feed cache immediately
        loadFeedCache()

        // 2. Subscribe to suggestions
        screenModelScope.launch {
            combine(
                getSuggestions.subscribe().distinctUntilChanged(),
                getSuggestionTags.subscribe().distinctUntilChanged(),
            ) { suggestions, tags ->
                val nonBlockedTags = tags.filter { !it.isBlocked }
                val top10Tags = nonBlockedTags.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
                val activeTags = nonBlockedTags.filter { top10Tags.contains(it.tag) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionTag> { it.sortOrder }.thenByDescending { it.count })

                val finalSuggestions = suggestions.take(15)
                finalSuggestions to null
            }.collectLatest { (suggestions, tag) ->
                mutableState.update { it.copy(suggestions = suggestions.toImmutableList(), suggestionsTagName = tag) }
            }
        }

        // 3. Subscribe to history (Continue Reading)
        screenModelScope.launch {
            getHistory.subscribe("", unfinishedManga = null, unfinishedChapter = null, nonLibraryEntries = null)
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { list ->
                    // Distinct by manga to avoid multiple history items for same manga
                    val distinctHistory = list.distinctBy { it.mangaId }.take(24)
                    mutableState.update { it.copy(history = distinctHistory.toImmutableList()) }
                }
        }

        // 4. Subscribe to updates (Fresh Releases) - last 3 months
        screenModelScope.launch {
            val limit = ZonedDateTime.now().minusMonths(3).toInstant()
            getUpdates.subscribe(limit, unread = null, started = null, bookmarked = null, hideExcludedScanlators = false)
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { list ->
                    // Distinct by manga to keep it row-based
                    val distinctUpdates = list.distinctBy { it.mangaId }.take(20)
                    mutableState.update { it.copy(updates = distinctUpdates.toImmutableList()) }
                }
        }

        // 5. Load Forgotten Favorites (shuffled library manga matching top 10 tags)
        loadForgottenFavorites()

        // 6. Trigger background fetch for Explore Feed
        triggerBackgroundFeedFetch(force = false)
    }

    fun loadForgottenFavorites() {
        screenModelScope.launch {
            getLibraryManga.subscribe()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { libraryManga ->
                    if (libraryManga.isEmpty()) {
                        mutableState.update { it.copy(libraryRandom = emptyList<Manga>().toImmutableList(), isLoading = false) }
                        return@collectLatest
                    }

                    try {
                        val unreadLibrary = libraryManga.filter { it.unreadCount > 0 }
                        val candidates = unreadLibrary.ifEmpty { libraryManga }
                        val shuffled = candidates.map { it.manga }.shuffled().take(20)
                        mutableState.update { it.copy(libraryRandom = shuffled.toImmutableList(), isLoading = false) }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to load unread library manga" }
                    }
                }
        }
    }

    fun toggleFavorite(mangaId: Long, isFavorite: Boolean) {
        screenModelScope.launchIO {
            val getManga = Injekt.get<tachiyomi.domain.manga.interactor.GetManga>()
            val dbManga = getManga.await(mangaId) ?: return@launchIO

            val isFavoriteNow = !isFavorite
            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()
                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    manga = dbManga,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        updateManga.awaitUpdateFavorite(mangaId, true)
                        val setMangaCategories = Injekt.get<SetMangaCategories>()
                        setMangaCategories.await(mangaId, listOf(defaultCategory.id))
                        updateLocalFavoriteState(mangaId, true)
                    }
                    else -> {
                        updateManga.awaitUpdateFavorite(mangaId, true)
                        updateLocalFavoriteState(mangaId, true)
                    }
                }
            } else {
                updateManga.awaitUpdateFavorite(mangaId, false)
                updateLocalFavoriteState(mangaId, false)
            }
        }
    }

    fun setMangaCategories(manga: Manga, categories: List<Long>) {
        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
            val setMangaCategories = Injekt.get<SetMangaCategories>()
            setMangaCategories.await(manga.id, categories)
            if (categories.isNotEmpty()) {
                Injekt.get<eu.kanade.domain.track.interactor.TrackOnCategorySet>().execute(manga)
            }
            updateLocalFavoriteState(manga.id, true)
            dismissDialog()
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    private fun updateLocalFavoriteState(mangaId: Long, favorite: Boolean) {
        mutableState.update { currentState ->
            val updatedFeed = currentState.feed.map { item ->
                if (item.id == mangaId) item.copy(favorite = favorite) else item
            }
            val updatedSuggestions = currentState.suggestions.map { suggestion ->
                if (suggestion.manga.id == mangaId) {
                    suggestion.copy(manga = suggestion.manga.copy(favorite = favorite))
                } else {
                    suggestion
                }
            }
            val updatedLibraryRandom = currentState.libraryRandom.map { manga ->
                if (manga.id == mangaId) manga.copy(favorite = favorite) else manga
            }
            currentState.copy(
                feed = updatedFeed.toImmutableList(),
                suggestions = updatedSuggestions.toImmutableList(),
                libraryRandom = updatedLibraryRandom.toImmutableList(),
            )
        }
    }

    private fun loadFeedCache() {
        screenModelScope.launchIO {
            if (cacheFile.exists()) {
                try {
                    val text = cacheFile.readText()
                    val list = json.decodeFromString<List<CachedFeedManga>>(text)
                    mutableState.update { it.copy(feed = list.toImmutableList()) }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to parse feed cache" }
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
                // Rate-limited: skip fetch
                return@launchIO
            }

            mutableState.update { it.copy(isFeedRefreshing = true) }
            try {
                val feedSourceLimit = uiPreferences.homeFeedSourceCount().get()
                val feedItemLimit = uiPreferences.homeFeedItemsCount().get()

                val usePinned = uiPreferences.homeFeedUsePinnedSources().get()
                val pinnedSet = uiPreferences.homeFeedPinnedSources().get().mapNotNull { it.toLongOrNull() }.toSet()

                // Get extensions based on suggestion sources / pinned configurations
                val activeSources = if (usePinned && pinnedSet.isNotEmpty()) {
                    val suggestionSources = getSuggestionSources.await()
                    pinnedSet.mapNotNull { pinnedId ->
                        suggestionSources.firstOrNull { it.sourceId == pinnedId } ?: SuggestionSource(
                            sourceId = pinnedId,
                            isBlocked = false,
                            isUserAdded = true,
                            count = 0,
                            sortOrder = 0,
                        )
                    }
                } else {
                    getSuggestionSources.await()
                        .filter { !it.isBlocked }
                        .sortedWith(compareByDescending<SuggestionSource> { it.isUserAdded }.thenByDescending { it.count })
                        .take(feedSourceLimit)
                }

                if (activeSources.isEmpty()) {
                    mutableState.update { it.copy(isFeedRefreshing = false) }
                    return@launchIO
                }

                val onlineSources = sourceManager.getOnlineSources()
                val targetSources = activeSources.mapNotNull { suggestionSrc ->
                    onlineSources.firstOrNull { it.id == suggestionSrc.sourceId } as? CatalogueSource
                }

                val blockedFilters = filterMangaByBlockedContent.getBlockedFilters()
                val fetchJobs = targetSources.map { source ->
                    async {
                        try {
                            val mangaPage = if (source.supportsLatest) {
                                source.getLatestUpdates(1)
                            } else {
                                source.getPopularManga(1)
                            }

                            val mangas = mangaPage.mangas.mapNotNull { smanga ->
                                val networkManga = smanga.toDomainManga(source.id)
                                val localManga = networkToLocalManga(networkManga)
                                if (filterMangaByBlockedContent.isMangaBlocked(localManga, blockedFilters)) {
                                    null
                                } else {
                                    CachedFeedManga(
                                        id = localManga.id,
                                        sourceId = source.id,
                                        title = localManga.title,
                                        thumbnailUrl = localManga.thumbnailUrl,
                                        favorite = localManga.favorite,
                                        coverLastModified = localManga.coverLastModified,
                                        sourceName = source.name,
                                        fetchTime = now,
                                    )
                                }
                            }.take(feedItemLimit)
                            mangas
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to fetch feed updates for source: ${source.name}" }
                            emptyList()
                        }
                    }
                }

                val results = fetchJobs.awaitAll()

                // Interleave/Mix results together
                val mixedList = mutableListOf<CachedFeedManga>()
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
                    // Update cache file
                    try {
                        cacheFile.writeText(json.encodeToString(mixedList))
                    } catch (ioe: Exception) {
                        logcat(LogPriority.WARN, ioe) { "Failed to write feed cache file" }
                    }
                    mutableState.update { it.copy(feed = mixedList.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch feed" }
            } finally {
                mutableState.update { it.copy(isFeedRefreshing = false) }
            }
        }
    }

    data class State(
        val suggestions: ImmutableList<Suggestion> = emptyList<Suggestion>().toImmutableList(),
        val suggestionsTagName: String? = null,
        val history: ImmutableList<HistoryWithRelations> = emptyList<HistoryWithRelations>().toImmutableList(),
        val updates: ImmutableList<UpdatesWithRelations> = emptyList<UpdatesWithRelations>().toImmutableList(),
        val libraryRandom: ImmutableList<Manga> = emptyList<Manga>().toImmutableList(),
        val feed: ImmutableList<CachedFeedManga> = emptyList<CachedFeedManga>().toImmutableList(),
        val isFeedRefreshing: Boolean = false,
        val isLoading: Boolean = true,
        val dialog: Dialog? = null,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val manga: Manga,
                val initialSelection: ImmutableList<CheckboxState<Category>>,
            ) : Dialog
        }
    }
}
