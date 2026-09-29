package eu.kanade.tachiyomi.ui.home.anime

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.manga.interactor.GetTrackerRecommendations
import eu.kanade.domain.manga.interactor.TrackerRecommendation
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@Serializable
data class CachedFeedAnime(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val coverLastModified: Long,
    val sourceName: String,
    val fetchTime: Long,
)

class AnimeLandingScreenModel(
    private val app: Application = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getAnimeUpdates: GetAnimeUpdates = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getTrackerRecommendations: GetTrackerRecommendations = Injekt.get(),
    private val getTrackerContinueReading: eu.kanade.domain.manga.interactor.GetTrackerContinueReading = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<AnimeLandingScreenModel.State>(State()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile by lazy { File(app.cacheDir, "anime_feed_cache.json") }

    init {
        // 1. Subscribe to Anime Library for Unread / Forgotten Favorites
        screenModelScope.launch {
            getLibraryAnime.subscribe()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .catch { logcat(LogPriority.ERROR, it) }
                .collectLatest { libraryAnimeList ->
                    if (libraryAnimeList.isEmpty()) {
                        mutableState.update { it.copy(libraryRandom = persistentListOf()) }
                        return@collectLatest
                    }
                    val unreadAnime = libraryAnimeList.filter { it.unseenCount > 0 }
                    val candidates = unreadAnime.ifEmpty { libraryAnimeList }
                    val random = candidates.map { it.anime }.shuffled().take(20)
                    mutableState.update {
                        it.copy(libraryRandom = random.toImmutableList())
                    }
                }
        }

        // 2. Load Spotlight from Dynamic Suggestions
        loadSpotlightSuggestions()

        // 3. Subscribe to Continue Watching (Anime History)
        screenModelScope.launch {
            getAnimeHistory.subscribe("")
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collectLatest { historyList ->
                    val recent = historyList.take(20)
                    mutableState.update {
                        it.copy(history = recent.toImmutableList())
                    }
                }
        }

        // 4. Subscribe to Fresh Releases (Anime Updates)
        screenModelScope.launch {
            val after = Instant.now().minus(30, ChronoUnit.DAYS)
            getAnimeUpdates.subscribe(after)
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
        loadTrackerContinue(force = false)
        loadFeedCache()
        triggerBackgroundFeedFetch(force = false)
    }

    private fun extractTopTags(library: List<tachiyomi.domain.library.anime.LibraryAnime>): List<String> {
        val extracted = library.flatMap { item ->
            item.anime.genre.orEmpty()
                .flatMap { it.split(",", ";", "/").map(String::trim) }
                .filter { it.isNotBlank() }
        }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }

        return if (extracted.isNotEmpty()) {
            extracted
        } else {
            listOf("Action", "Romance", "Fantasy", "Comedy", "Sci-Fi", "Adventure", "Drama", "Mystery", "Supernatural", "Slice of Life", "Isekai", "Shounen")
        }
    }

    fun loadSpotlightSuggestions() {
        screenModelScope.launchIO {
            try {
                sourceManager.isInitialized.first { it }
                val library = getLibraryAnime.await()
                val topTags = extractTopTags(library)

                val sources = sourceManager.getCatalogueSources()
                val suggestions = mutableListOf<Anime>()
                val favoriteUrls = library.map { it.anime.url }.toSet()
                val favoriteTitles = library.map { it.anime.title.lowercase().trim() }.toSet()
                val favoriteIds = library.map { it.anime.id }.toSet()

                for (tag in topTags.take(10)) {
                    val jobs = sources.take(4).map { source ->
                        async {
                            try {
                                val result = source.getSearchAnime(1, tag, eu.kanade.tachiyomi.animesource.model.AnimeFilterList())
                                val domainAnimes = result.animes.map { it.toDomainAnime(source.id) }
                                networkToLocalAnime.await(domainAnimes)
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

                if (suggestions.size < 10) {
                    val jobs = sources.take(4).map { source ->
                        async {
                            try {
                                val result = source.getPopularAnime(1)
                                val domainAnimes = result.animes.map { it.toDomainAnime(source.id) }
                                networkToLocalAnime.await(domainAnimes)
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

                val finalSpotlight = suggestions.distinctBy { it.id }.shuffled().take(15)
                if (finalSpotlight.isNotEmpty()) {
                    mutableState.update {
                        it.copy(
                            spotlightAnime = finalSpotlight.toImmutableList(),
                            spotlightTagName = null,
                        )
                    }
                    fetchMissingSpotlightDetails(finalSpotlight)
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load anime spotlight suggestions" }
            }
        }
    }

    private fun fetchMissingSpotlightDetails(spotlightList: List<Anime>) {
        screenModelScope.launchIO {
            val updated = spotlightList.map { anime ->
                if (anime.genre.isNullOrEmpty() || anime.author.isNullOrBlank()) {
                    try {
                        val source = sourceManager.get(anime.source) as? AnimeCatalogueSource
                        if (source != null) {
                            val networkAnime = source.getAnimeDetails(anime.toSAnime())
                            val updatedAnime = anime.copy(
                                author = networkAnime.author?.ifBlank { anime.author } ?: anime.author,
                                artist = networkAnime.artist?.ifBlank { anime.artist } ?: anime.artist,
                                genre = networkAnime.getGenres()?.ifEmpty { anime.genre } ?: anime.genre,
                                description = networkAnime.description?.ifBlank { anime.description } ?: anime.description,
                                status = networkAnime.status.toLong(),
                            )
                            updateAnime.await(
                                AnimeUpdate(
                                    id = anime.id,
                                    author = updatedAnime.author,
                                    artist = updatedAnime.artist,
                                    genre = updatedAnime.genre,
                                    description = updatedAnime.description,
                                    status = updatedAnime.status,
                                ),
                            )
                            updatedAnime
                        } else {
                            anime
                        }
                    } catch (_: Exception) {
                        anime
                    }
                } else {
                    anime
                }
            }
            mutableState.update { state ->
                state.copy(spotlightAnime = updated.toImmutableList())
            }
        }
    }

    fun loadTrackerRecommendations(force: Boolean = false) {
        screenModelScope.launchIO {
            try {
                val cached = getTrackerRecommendations.getCached(MediaType.ANIME)
                if (cached.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = cached.toImmutableList()) }
                }
                val fresh = getTrackerRecommendations.fetch(MediaType.ANIME, force = force)
                if (fresh.isNotEmpty()) {
                    mutableState.update { it.copy(trackerRecommendations = fresh.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load anime tracker recommendations" }
            }
        }
    }

    fun loadTrackerContinue(force: Boolean = false) {
        screenModelScope.launchIO {
            try {
                val cached = getTrackerContinueReading.getCached(MediaType.ANIME)
                if (cached.isNotEmpty()) {
                    mutableState.update { it.copy(trackerContinue = cached.toImmutableList()) }
                }
                val fresh = getTrackerContinueReading.fetch(MediaType.ANIME, force = force)
                if (fresh.isNotEmpty()) {
                    mutableState.update { it.copy(trackerContinue = fresh.toImmutableList()) }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load anime tracker continue items" }
            }
        }
    }

    private fun loadFeedCache() {
        screenModelScope.launchIO {
            if (cacheFile.exists()) {
                try {
                    val text = cacheFile.readText()
                    val list = json.decodeFromString<List<CachedFeedAnime>>(text)
                    mutableState.update { it.copy(feed = list.toImmutableList()) }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to parse anime feed cache" }
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
                sourceManager.isInitialized.first { it }
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

                val library = getLibraryAnime.await()
                val topTags = extractTopTags(library)
                val favoriteUrls = library.map { it.anime.url }.toSet()
                val favoriteTitles = library.map { it.anime.title.lowercase().trim() }.toSet()
                val favoriteIds = library.map { it.anime.id }.toSet()

                val fetchJobs = sourcesToQuery.map { source ->
                    async {
                        try {
                            val itemsFromTags = mutableListOf<Anime>()
                            if (topTags.isNotEmpty()) {
                                for (tag in topTags.take(2)) {
                                    try {
                                        val tagResult = source.getSearchAnime(1, tag, eu.kanade.tachiyomi.animesource.model.AnimeFilterList())
                                        val domainAnimes = tagResult.animes.map { it.toDomainAnime(source.id) }
                                        itemsFromTags.addAll(networkToLocalAnime.await(domainAnimes))
                                    } catch (_: Throwable) {}
                                }
                            }

                            val animePage = if (source.supportsLatest) {
                                source.getLatestUpdates(1)
                            } else {
                                source.getPopularAnime(1)
                            }
                            val latestDomain = animePage.animes.map { sanime ->
                                networkToLocalAnime.await(sanime.toDomainAnime(source.id))
                            }

                            val combined = (itemsFromTags + latestDomain)
                                .filterNot { 
                                    it.favorite || favoriteIds.contains(it.id) || favoriteUrls.contains(it.url) || favoriteTitles.contains(it.title.lowercase().trim()) 
                                }
                                .distinctBy { it.id }
                            combined.map { localAnime ->
                                CachedFeedAnime(
                                    id = localAnime.id,
                                    sourceId = source.id,
                                    title = localAnime.title,
                                    thumbnailUrl = localAnime.thumbnailUrl,
                                    favorite = localAnime.favorite,
                                    coverLastModified = localAnime.coverLastModified,
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
                val mixedList = mutableListOf<CachedFeedAnime>()
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
                logcat(LogPriority.ERROR, e) { "Failed to fetch anime feed" }
            } finally {
                mutableState.update { it.copy(isFeedRefreshing = false) }
            }
        }
    }

    fun toggleFavorite(animeId: Long, currentFavorite: Boolean) {
        screenModelScope.launchIO {
            val getAnime = Injekt.get<GetAnime>()
            val dbAnime = getAnime.await(animeId) ?: return@launchIO

            val isFavoriteNow = !currentFavorite
            if (isFavoriteNow) {
                val getCategories = Injekt.get<GetAnimeCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val libraryPreferences = Injekt.get<LibraryPreferences>()
                val defaultCategoryId = libraryPreferences.defaultAnimeCategory().get()
                val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

                when {
                    defaultCategoryId == -1 && categories.isNotEmpty() -> {
                        val initialSelection = categories.mapAsCheckboxState { false }.toImmutableList()
                        mutableState.update { state ->
                            state.copy(
                                dialog = State.Dialog.ChangeCategory(
                                    anime = dbAnime,
                                    initialSelection = initialSelection,
                                ),
                            )
                        }
                    }
                    defaultCategory != null -> {
                        updateAnime.await(
                            AnimeUpdate(
                                id = animeId,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        val setAnimeCategories = Injekt.get<SetAnimeCategories>()
                        setAnimeCategories.await(animeId, listOf(defaultCategory.id))
                        updateLocalFavoriteState(animeId, true)
                    }
                    else -> {
                        updateAnime.await(
                            AnimeUpdate(
                                id = animeId,
                                favorite = true,
                                dateAdded = System.currentTimeMillis(),
                            ),
                        )
                        updateLocalFavoriteState(animeId, true)
                    }
                }
            } else {
                val getCategories = Injekt.get<GetAnimeCategories>()
                val categories = getCategories.await().filterNot { it.isSystemCategory }
                val preselectedIds = getCategories.await(animeId).map { it.id }
                if (categories.isNotEmpty()) {
                    mutableState.update { state ->
                        state.copy(
                            dialog = State.Dialog.ChangeCategory(
                                anime = dbAnime,
                                initialSelection = categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                            ),
                        )
                    }
                } else {
                    updateAnime.await(
                        AnimeUpdate(
                            id = animeId,
                            favorite = false,
                            dateAdded = 0L,
                        ),
                    )
                    updateLocalFavoriteState(animeId, false)
                }
            }
        }
    }

    fun setAnimeCategories(anime: Anime, categories: List<Long>) {
        screenModelScope.launchIO {
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val setAnimeCategories = Injekt.get<SetAnimeCategories>()
            setAnimeCategories.await(anime.id, categories)
            updateLocalFavoriteState(anime.id, true)
        }
    }

    private fun updateLocalFavoriteState(animeId: Long, favorite: Boolean) {
        mutableState.update { state ->
            val updatedFeed = state.feed.map { item ->
                if (item.id == animeId) item.copy(favorite = favorite) else item
            }
            val updatedSpotlight = if (favorite) state.spotlightAnime else state.spotlightAnime.filterNot { it.id == animeId }
            state.copy(
                feed = updatedFeed.toImmutableList(),
                spotlightAnime = updatedSpotlight.toImmutableList(),
            )
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launchIO {
            val createCategory = Injekt.get<CreateAnimeCategoryWithName>()
            createCategory.await(name, parentId)
        }
    }

    fun dismissSpotlight(anime: Anime) {
        mutableState.update { state ->
            val updated = state.spotlightAnime.filterNot { it.id == anime.id }
            state.copy(spotlightAnime = updated.toImmutableList())
        }
    }

    @Immutable
    data class State(
        val spotlightAnime: ImmutableList<Anime> = persistentListOf(),
        val spotlightTagName: String? = null,
        val history: ImmutableList<AnimeHistoryWithRelations> = persistentListOf(),
        val updates: ImmutableList<AnimeUpdatesWithRelations> = persistentListOf(),
        val libraryRandom: ImmutableList<Anime> = persistentListOf(),
        val trackerRecommendations: ImmutableList<TrackerRecommendation> = persistentListOf(),
        val trackerContinue: ImmutableList<eu.kanade.domain.manga.interactor.TrackerContinueItem> = persistentListOf(),
        val feed: ImmutableList<CachedFeedAnime> = persistentListOf(),
        val isFeedRefreshing: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        sealed interface Dialog {
            data class ChangeCategory(
                val anime: Anime,
                val initialSelection: ImmutableList<CheckboxState<Category>>,
            ) : Dialog
        }
    }
}
