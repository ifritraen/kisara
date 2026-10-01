package eu.kanade.tachiyomi.ui.home

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.data.ai.NsfwTagClassifier
import eu.kanade.tachiyomi.util.NsfwDetector
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
import tachiyomi.domain.suggestions.repository.SuggestionRepository
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
    private val getTrackerRecommendations: eu.kanade.domain.manga.interactor.GetTrackerRecommendations = Injekt.get(),
    private val getTrackerContinueReading: eu.kanade.domain.manga.interactor.GetTrackerContinueReading = Injekt.get(),
    private val suggestionRepository: SuggestionRepository = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<LandingScreenModel.State>(State()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(app.cacheDir, "home_feed_cache.json")

    init {
        val nsfwFilterTrigger = combine(
            uiPreferences.kisaraHideNsfwSuggestions().changes(),
            sourcePreferences.nsfwOverrideSfwExtensions().changes(),
            sourcePreferences.nsfwOverrideNsfwExtensions().changes(),
        ) { hideNsfw, _, _ -> hideNsfw }

        // 1. Load feed cache immediately
        loadFeedCache()

        // 2. Load tracker recommendations & continue reading immediately
        loadTrackerRecommendations(force = false)
        loadTrackerContinue(force = false)

        // 3. Subscribe to suggestions (dynamically filtered against active library, dismissed items & NSFW preference)
        screenModelScope.launch {
            combine(
                getSuggestions.subscribe().distinctUntilChanged(),
                getSuggestionTags.subscribe().distinctUntilChanged(),
                getLibraryManga.subscribe().distinctUntilChanged(),
                nsfwFilterTrigger,
                suggestionRepository.observeDismissed().distinctUntilChanged(),
            ) { suggestions, tags, libraryManga, hideNsfw, dismissedUrls ->
                val dismissedUrlSet = dismissedUrls.toSet()
                val libraryIds = libraryManga.map { it.id }.toSet()
                val libraryUrls = libraryManga.map { it.manga.url }.toSet()
                val nonLibrarySuggestions = suggestions.filter {
                    !it.manga.favorite &&
                        it.manga.id !in libraryIds &&
                        it.manga.url !in libraryUrls &&
                        it.manga.url !in dismissedUrlSet
                }
                val nonBlockedTags = tags.filter { !it.isBlocked }
                val eligibleTags = if (hideNsfw) nonBlockedTags.filterNot { NsfwTagClassifier.is18PlusTag(it.tag) } else nonBlockedTags
                val top10Tags = eligibleTags.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
                val activeTags = eligibleTags.filter { top10Tags.contains(it.tag) || it.isUserAdded }
                    .sortedWith(compareBy<SuggestionTag> { it.sortOrder }.thenByDescending { it.count })

                val hasUnfilteredSuggestions = nonLibrarySuggestions.isNotEmpty()
                val filteredSuggestions = if (hideNsfw) {
                    nonLibrarySuggestions.filterNot { NsfwDetector.isNsfw(it.manga, it.manga.title) }
                } else {
                    nonLibrarySuggestions
                }

                val finalSuggestions = filteredSuggestions.take(15)
                // KMK --> Derive meaningful recommendation tag rather than statically defaulting:
                val suggestionsGenres = finalSuggestions.flatMap { it.manga.genre.orEmpty() }
                    .map { it.lowercase().trim() }
                    .filter { it !in setOf("manga", "webtoon", "comic", "scanlation", "english") }
                val genreCounts = suggestionsGenres.groupingBy { it }.eachCount()
                val topMatchingActiveTag = activeTags.firstOrNull { genreCounts.containsKey(it.tag.lowercase().trim()) }?.tag
                val mostFrequentGenre = genreCounts.maxByOrNull { it.value }?.key?.replaceFirstChar { it.uppercase() }
                val tagName = topMatchingActiveTag ?: mostFrequentGenre ?: activeTags.firstOrNull()?.tag
                Triple(finalSuggestions, tagName, hasUnfilteredSuggestions)
                // KMK <--
            }.collectLatest { (suggestions, tag, hasUnfiltered) ->
                mutableState.update {
                    it.copy(
                        suggestions = suggestions.toImmutableList(),
                        suggestionsTagName = tag,
                        hasUnfilteredSuggestions = hasUnfiltered,
                    )
                }
            }
        }

        // 4. Subscribe to history (Continue Reading)
        screenModelScope.launch {
            combine(
                getHistory.subscribe("", unfinishedManga = null, unfinishedChapter = null, nonLibraryEntries = null).distinctUntilChanged(),
                nsfwFilterTrigger,
                getLibraryManga.subscribe().distinctUntilChanged(),
            ) { list, hideNsfw, libraryManga ->
                val libraryMap = libraryManga.associate { it.id to it.manga }
                val distinctHistory = list.distinctBy { it.mangaId }
                val filtered = if (hideNsfw) {
                    distinctHistory.filterNot { item ->
                        if (NsfwTagClassifier.is18PlusSource(item.coverData.sourceId)) return@filterNot true
                        val manga = libraryMap[item.mangaId] ?: runCatching { getManga.await(item.mangaId) }.getOrNull()
                        NsfwDetector.isNsfw(manga, item.title)
                    }
                } else {
                    distinctHistory
                }
                filtered.take(24)
            }
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { distinctHistory ->
                    mutableState.update { it.copy(history = distinctHistory.toImmutableList()) }
                }
        }

        // 5. Subscribe to updates (Fresh Releases) - last 3 months
        screenModelScope.launch {
            val limit = ZonedDateTime.now().minusMonths(3).toInstant()
            combine(
                getUpdates.subscribe(limit, unread = null, started = null, bookmarked = null, hideExcludedScanlators = false).distinctUntilChanged(),
                nsfwFilterTrigger,
                getLibraryManga.subscribe().distinctUntilChanged(),
            ) { list, hideNsfw, libraryManga ->
                val libraryMap = libraryManga.associate { it.id to it.manga }
                val distinctUpdates = list.distinctBy { it.mangaId }
                val filtered = if (hideNsfw) {
                    distinctUpdates.filterNot { item ->
                        if (NsfwTagClassifier.is18PlusSource(item.sourceId)) return@filterNot true
                        val manga = libraryMap[item.mangaId] ?: runCatching { getManga.await(item.mangaId) }.getOrNull()
                        NsfwDetector.isNsfw(manga, item.mangaTitle)
                    }
                } else {
                    distinctUpdates
                }
                filtered.take(20)
            }
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { distinctUpdates ->
                    mutableState.update { it.copy(updates = distinctUpdates.toImmutableList()) }
                }
        }

        // 6. Load Forgotten Favorites (shuffled library manga matching top 10 tags)
        loadForgottenFavorites()

        // 7. Trigger background fetch for Explore Feed
        triggerBackgroundFeedFetch(force = false)
    }

    fun loadTrackerRecommendations(force: Boolean = false) {
        screenModelScope.launchIO {
            try {
                val cached = getTrackerRecommendations.getCached()
                if (cached.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = cached.toImmutableList()) }
                }
                val fresh = getTrackerRecommendations.fetch(force = force)
                if (fresh.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = fresh.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load tracker recommendations" }
            }
        }
    }

    fun loadTrackerContinue(force: Boolean = false) {
        screenModelScope.launchIO {
            try {
                val cached = getTrackerContinueReading.getCached(MediaType.MANGA)
                if (cached.isNotEmpty()) {
                    mutableState.update { it.copy(trackerContinue = cached.toImmutableList()) }
                }
                val fresh = getTrackerContinueReading.fetch(MediaType.MANGA, force = force)
                if (fresh.isNotEmpty()) {
                    mutableState.update { it.copy(trackerContinue = fresh.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load tracker continue reading" }
            }
        }
    }

    fun loadForgottenFavorites() {
        val nsfwFilterTrigger = combine(
            uiPreferences.kisaraHideNsfwSuggestions().changes(),
            sourcePreferences.nsfwOverrideSfwExtensions().changes(),
            sourcePreferences.nsfwOverrideNsfwExtensions().changes(),
        ) { hideNsfw, _, _ -> hideNsfw }

        screenModelScope.launch {
            combine(
                getLibraryManga.subscribe().distinctUntilChanged(),
                nsfwFilterTrigger,
            ) { libraryManga, hideNsfw ->
                if (libraryManga.isEmpty()) {
                    return@combine emptyList<Manga>()
                }

                val eligible = if (hideNsfw) {
                    libraryManga.filterNot { NsfwDetector.isNsfw(it.manga, it.manga.title) }
                } else {
                    libraryManga
                }
                val unreadLibrary = eligible.filter { it.unreadCount > 0 }
                val candidates = unreadLibrary.ifEmpty { eligible }
                candidates.map { it.manga }.shuffled().take(20)
            }
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { shuffled ->
                    mutableState.update { it.copy(libraryRandom = shuffled.toImmutableList(), isLoading = false) }
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

    fun dismissSuggestion(manga: Manga) {
        screenModelScope.launchIO {
            try {
                suggestionRepository.dismiss(manga.url, manga.title)
                mutableState.update { state ->
                    val remaining = state.suggestions.filterNot { it.manga.id == manga.id || it.manga.url == manga.url }
                    state.copy(
                        suggestions = remaining.toImmutableList(),
                        hasUnfilteredSuggestions = remaining.isNotEmpty() && state.hasUnfilteredSuggestions,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to dismiss suggestion: ${manga.title}" }
            }
        }
    }

    fun triggerSuggestionsRefresh() {
        eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker.triggerOnAppStart(app, force = true)
    }

    private fun updateLocalFavoriteState(mangaId: Long, favorite: Boolean) {
        mutableState.update { currentState ->
            val updatedFeed = currentState.feed.map { item ->
                if (item.id == mangaId) item.copy(favorite = favorite) else item
            }
            val updatedSuggestions = if (favorite) {
                currentState.suggestions.filterNot { it.manga.id == mangaId }
            } else {
                currentState.suggestions
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
            val nsfwFilterTrigger = combine(
                uiPreferences.kisaraHideNsfwSuggestions().changes(),
                sourcePreferences.nsfwOverrideSfwExtensions().changes(),
                sourcePreferences.nsfwOverrideNsfwExtensions().changes(),
            ) { hideNsfw, _, _ -> hideNsfw }

            nsfwFilterTrigger.collectLatest { isHideNsfw ->
                if (cacheFile.exists()) {
                    try {
                        val text = cacheFile.readText()
                        val list = json.decodeFromString<List<CachedFeedManga>>(text)
                        val filtered = if (isHideNsfw) {
                            list.filterNot { NsfwTagClassifier.is18PlusSource(it.sourceId) || NsfwDetector.isNsfw(null, it.title) }
                        } else {
                            list
                        }
                        mutableState.update { it.copy(feed = filtered.toImmutableList()) }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to parse feed cache" }
                    }
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

                val isHideNsfw = uiPreferences.kisaraHideNsfwSuggestions().get()
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
                                if (filterMangaByBlockedContent.isMangaBlocked(localManga, blockedFilters) ||
                                    (isHideNsfw && NsfwDetector.isNsfw(localManga, localManga.title))
                                ) {
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
                        } catch (e: Throwable) {
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
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch feed" }
            } finally {
                mutableState.update { it.copy(isFeedRefreshing = false) }
            }
        }
    }

    data class State(
        val suggestions: ImmutableList<Suggestion> = emptyList<Suggestion>().toImmutableList(),
        val suggestionsTagName: String? = null,
        val hasUnfilteredSuggestions: Boolean = false,
        val history: ImmutableList<HistoryWithRelations> = emptyList<HistoryWithRelations>().toImmutableList(),
        val updates: ImmutableList<UpdatesWithRelations> = emptyList<UpdatesWithRelations>().toImmutableList(),
        val libraryRandom: ImmutableList<Manga> = emptyList<Manga>().toImmutableList(),
        val trackerRecommendations: ImmutableList<eu.kanade.domain.manga.interactor.TrackerRecommendation> = emptyList<eu.kanade.domain.manga.interactor.TrackerRecommendation>().toImmutableList(),
        val trackerContinue: ImmutableList<eu.kanade.domain.manga.interactor.TrackerContinueItem> = emptyList<eu.kanade.domain.manga.interactor.TrackerContinueItem>().toImmutableList(),
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
