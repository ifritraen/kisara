package eu.kanade.tachiyomi.ui.track

import android.app.Application
import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistMediaCache
import eu.kanade.tachiyomi.data.track.anilist.AnilistSeasonUtil
import eu.kanade.tachiyomi.data.track.anilist.dto.ALHomeSection
import eu.kanade.tachiyomi.data.track.anilist.dto.ALItemTitle
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ItemCover
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListEntry
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserStatsViewer
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUAuthorRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGenreItem
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUGroupRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUPublisherRecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MURecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MUReviewRecord
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnime
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALHomeSection
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALManga
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMediaItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUser
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.track.matcher.TrackerSourceMatcher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.SetNovelCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.manga.model.Manga
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class TrackScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val context: Application = Injekt.get(),
) : ScreenModel {

    private val anilistHomeCache = mutableMapOf<MediaType, List<ALHomeSection>>()
    private val anilistUserListCache = mutableMapOf<MediaType, List<ALUserListEntry>>()
    private val malHomeCache = mutableMapOf<MediaType, List<MALHomeSection>>()
    private val malUserListCache = mutableMapOf<MediaType, List<MALMediaItem>>()

    private val mediaCache by lazy { AnilistMediaCache(context) }

    private val _state = MutableStateFlow(TrackState())
    val state = _state.asStateFlow()

    init {
        loadFilterMetadata()
    }

    // ==========================================
    // ANILIST OPERATIONS
    // ==========================================

    fun isAniListLoggedIn(): Boolean = trackerManager.aniList.isLoggedIn

    fun loginAniList(token: String, onDone: () -> Unit = {}) {
        screenModelScope.launch {
            try {
                trackerManager.aniList.login(token)
                loadAnilistStats()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log in to AniList" }
            }
            onDone()
        }
    }

    fun logoutAniList(onDone: () -> Unit = {}) {
        screenModelScope.launch {
            try {
                trackerManager.aniList.logout()
                _state.update { it.copy(userStats = null, userList = emptyList()) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log out of AniList" }
            }
            onDone()
        }
    }

    fun loadAnilistHome(mediaType: MediaType, forceRefresh: Boolean = false) {
        if (!forceRefresh && _state.value.homeSections.isNotEmpty() && _state.value.currentHomeMediaType == mediaType) {
            return
        }

        if (!forceRefresh) {
            val cached = anilistHomeCache[mediaType]
            if (cached != null) {
                _state.update {
                    it.copy(
                        homeSections = cached,
                        isLoadingHome = false,
                        currentHomeMediaType = mediaType,
                    )
                }
                return
            }
        }

        screenModelScope.launch {
            _state.update {
                it.copy(
                    homeSections = emptyList(),
                    isLoadingHome = true,
                    currentHomeMediaType = mediaType,
                )
            }
            try {
                val currentSeason = AnilistSeasonUtil.getCurrentSeason()
                val prevSeasonCount = uiPreferences.trackTabPreviousSeasons().get().coerceIn(1, 8)
                val prevSeasons = AnilistSeasonUtil.getPreviousSeasons(prevSeasonCount).map {
                    it.season.anilistName to it.year
                }

                val type = when (mediaType) {
                    MediaType.ANIME -> "ANIME"
                    MediaType.NOVEL, MediaType.MANGA -> "MANGA"
                }

                val format = when (mediaType) {
                    MediaType.NOVEL -> "NOVEL"
                    else -> null
                }

                val isAdult = if (uiPreferences.trackTabHideAdult().get()) false else null
                val enabledSections = uiPreferences.anilistHomeEnabledSections().get()

                val sections = trackerManager.aniList.api.getHomePage(
                    type = type,
                    format = format,
                    isAdult = isAdult,
                    season = currentSeason.season.anilistName,
                    seasonYear = currentSeason.year,
                    prevSeasons = prevSeasons,
                    enabledSections = enabledSections,
                ).toMutableList()

                // Continue section (According to tracker)
                if ((enabledSections.isEmpty() || "continue" in enabledSections) && isAniListLoggedIn()) {
                    try {
                        val (userId, _) = trackerManager.aniList.api.getCurrentUser()
                        val userListResult = trackerManager.aniList.api.getUserMediaList(userId, type)
                        val continueItems = userListResult.data.collection.lists
                            .filter { it.status == "CURRENT" }
                            .flatMap { it.entries }
                            .filter { entry ->
                                val fmt = entry.media?.format
                                if (mediaType == MediaType.NOVEL) fmt == "NOVEL"
                                else if (mediaType == MediaType.MANGA) fmt != "NOVEL"
                                else true
                            }
                            .mapNotNull { entry ->
                                val media = entry.media ?: return@mapNotNull null
                                ALSearchItem(
                                    id = media.id,
                                    title = ALItemTitle(userPreferred = media.title?.userPreferred ?: ""),
                                    coverImage = ItemCover(large = media.coverImage?.large ?: ""),
                                    description = media.description,
                                    format = media.format,
                                    status = media.status,
                                    episodes = media.episodes,
                                    chapters = media.chapters,
                                    averageScore = media.averageScore,
                                    genres = media.genres,
                                )
                            }
                        if (continueItems.isNotEmpty()) {
                            sections.add(0, ALHomeSection("continue", "Continue", continueItems))
                        }
                    } catch (_: Throwable) {}
                }

                // Deduplicate items per section and cache for quick lookups
                val deduplicatedSections = sections.map { section ->
                    section.copy(items = section.items.distinctBy { it.id })
                }
                deduplicatedSections.forEach { section ->
                    mediaCache.cacheMediaList(section.items)
                }

                anilistHomeCache[mediaType] = deduplicatedSections
                _state.update {
                    it.copy(
                        homeSections = deduplicatedSections,
                        isLoadingHome = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load AniList home page" }
                _state.update { it.copy(isLoadingHome = false) }
            }
        }
    }

    fun toggleAnilistHomeSection(key: String, isEnabled: Boolean) {
        val current = uiPreferences.anilistHomeEnabledSections().get().toMutableSet()
        if (current.isEmpty()) {
            current.addAll(eu.kanade.tachiyomi.ui.track.anilist.AnilistLandingSections.getSections().map { it.key })
        }
        if (isEnabled) {
            current.add(key)
        } else {
            current.remove(key)
        }
        uiPreferences.anilistHomeEnabledSections().set(current)
        _state.value.currentHomeMediaType?.let { loadAnilistHome(it, forceRefresh = true) }
    }

    fun selectAllAnilistHomeSections() {
        uiPreferences.anilistHomeEnabledSections().set(emptySet())
        _state.value.currentHomeMediaType?.let { loadAnilistHome(it, forceRefresh = true) }
    }

    fun deselectAllAnilistHomeSections() {
        uiPreferences.anilistHomeEnabledSections().set(setOf("__none__"))
        _state.value.currentHomeMediaType?.let { loadAnilistHome(it, forceRefresh = true) }
    }

    fun loadAnilistUserList(mediaType: MediaType, forceRefresh: Boolean = false) {
        if (!isAniListLoggedIn()) return
        if (!forceRefresh && _state.value.userList.isNotEmpty() && _state.value.currentUserListMediaType == mediaType) return

        if (!forceRefresh) {
            val cached = anilistUserListCache[mediaType]
            if (cached != null) {
                _state.update {
                    it.copy(
                        userList = cached,
                        isLoadingUserList = false,
                        currentUserListMediaType = mediaType,
                    )
                }
                return
            }
        }

        screenModelScope.launch {
            _state.update {
                it.copy(
                    userList = emptyList(),
                    isLoadingUserList = true,
                    currentUserListMediaType = mediaType,
                )
            }
            try {
                val (userId, _) = trackerManager.aniList.api.getCurrentUser()
                val type = when (mediaType) {
                    MediaType.ANIME -> "ANIME"
                    MediaType.NOVEL, MediaType.MANGA -> "MANGA"
                }

                val result = trackerManager.aniList.api.getUserMediaList(userId, type)
                val entries = result.data.collection.lists
                    .flatMap { it.entries }
                    .filter { entry ->
                        val fmt = entry.media?.format
                        if (mediaType == MediaType.NOVEL) fmt == "NOVEL"
                        else if (mediaType == MediaType.MANGA) fmt != "NOVEL"
                        else true
                    }

                anilistUserListCache[mediaType] = entries
                _state.update {
                    it.copy(
                        userList = entries,
                        isLoadingUserList = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load AniList user media list" }
                _state.update { it.copy(isLoadingUserList = false) }
            }
        }
    }

    fun setAnilistStatusFilter(status: String?) {
        _state.update { it.copy(userListStatus = status) }
    }

    private fun loadFilterMetadata() {
        screenModelScope.launch {
            try {
                val meta = trackerManager.aniList.api.getFilterMetadata()
                _state.update {
                    it.copy(
                        filterGenres = meta.data.genres,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to load AniList filter metadata" }
            }
        }
    }

    fun searchAnilist(query: String, mediaType: MediaType) {
        screenModelScope.launch {
            _state.update { it.copy(isSearching = true, searchQuery = query) }
            try {
                val type = when (mediaType) {
                    MediaType.ANIME -> "ANIME"
                    MediaType.NOVEL, MediaType.MANGA -> "MANGA"
                }
                val format = when (mediaType) {
                    MediaType.NOVEL -> "NOVEL"
                    else -> null
                }
                val isAdult = if (uiPreferences.trackTabHideAdult().get()) false else null
                val searchParam = query.trim().ifBlank { null }
                val genreList = _state.value.selectedGenre?.let { listOf(it) }

                val result = trackerManager.aniList.api.searchMediaPaginated(
                    type = type,
                    format = format,
                    search = searchParam,
                    genres = genreList,
                    tags = null,
                    status = null,
                    sort = _state.value.selectedSort,
                    year = null,
                    isAdult = isAdult,
                    page = 1,
                    perPage = 30,
                )

                val filteredMedia = result.data.page.media.filter { item ->
                    if (mediaType == MediaType.NOVEL) item.format == "NOVEL"
                    else if (mediaType == MediaType.MANGA) item.format != "NOVEL"
                    else true
                }

                _state.update {
                    it.copy(
                        searchResults = filteredMedia,
                        isSearching = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to search AniList media" }
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun setAnilistGenre(genre: String?) {
        _state.update { it.copy(selectedGenre = genre) }
    }

    fun setAnilistSort(sort: String) {
        _state.update { it.copy(selectedSort = sort) }
    }

    fun loadAnilistStats() {
        if (!isAniListLoggedIn()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingStats = true) }
            try {
                val stats = trackerManager.aniList.api.getUserStats()
                _state.update {
                    it.copy(
                        userStats = stats.data.viewer,
                        isLoadingStats = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load AniList stats" }
                _state.update { it.copy(isLoadingStats = false) }
            }
        }
    }

    // ==========================================
    // MYANIMELIST OPERATIONS
    // ==========================================

    fun isMALLoggedIn(): Boolean = trackerManager.myAnimeList.isLoggedIn

    fun logoutMAL() {
        screenModelScope.launch {
            try {
                trackerManager.myAnimeList.logout()
                _state.update { it.copy(malUserProfile = null, malUserList = emptyList()) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log out of MyAnimeList" }
            }
        }
    }

    fun loadMALHome(mediaType: MediaType, forceRefresh: Boolean = false) {
        if (!forceRefresh && _state.value.malHomeSections.isNotEmpty() && _state.value.currentMALHomeMediaType == mediaType) {
            return
        }

        if (!forceRefresh) {
            val cached = malHomeCache[mediaType]
            if (cached != null) {
                _state.update {
                    it.copy(
                        malHomeSections = cached,
                        isLoadingMALHome = false,
                        currentMALHomeMediaType = mediaType,
                    )
                }
                return
            }
        }

        screenModelScope.launch {
            _state.update {
                it.copy(
                    malHomeSections = emptyList(),
                    isLoadingMALHome = true,
                    currentMALHomeMediaType = mediaType,
                )
            }
            try {
                val isAnime = mediaType == MediaType.ANIME
                val sections = mutableListOf<MALHomeSection>()

                if (isAnime) {
                    val airing = trackerManager.myAnimeList.api.getAnimeRanking("airing").map { it.toMALMediaItem() }
                    if (airing.isNotEmpty()) sections.add(MALHomeSection("airing", "Top Airing", airing))

                    val popular = trackerManager.myAnimeList.api.getAnimeRanking("bypopularity").map { it.toMALMediaItem() }
                    if (popular.isNotEmpty()) sections.add(MALHomeSection("popular", "All Time Popular", popular))

                    val top = trackerManager.myAnimeList.api.getAnimeRanking("all").map { it.toMALMediaItem() }
                    if (top.isNotEmpty()) sections.add(MALHomeSection("top", "Top Ranked", top))

                    val upcoming = trackerManager.myAnimeList.api.getAnimeRanking("upcoming").map { it.toMALMediaItem() }
                    if (upcoming.isNotEmpty()) sections.add(MALHomeSection("upcoming", "Top Upcoming", upcoming))

                    val favorite = trackerManager.myAnimeList.api.getAnimeRanking("favorite").map { it.toMALMediaItem() }
                    if (favorite.isNotEmpty()) sections.add(MALHomeSection("favorite", "Most Favorited", favorite))
                } else if (mediaType == MediaType.NOVEL) {
                    val novels = trackerManager.myAnimeList.api.getMangaRanking("novels").map { it.toMALMediaItem() }
                    if (novels.isNotEmpty()) sections.add(MALHomeSection("novels", "Top Light Novels", novels))

                    val popular = trackerManager.myAnimeList.api.getMangaRanking("bypopularity").map { it.toMALMediaItem() }
                    if (popular.isNotEmpty()) sections.add(MALHomeSection("popular", "All Time Popular", popular))

                    val top = trackerManager.myAnimeList.api.getMangaRanking("all").map { it.toMALMediaItem() }
                    if (top.isNotEmpty()) sections.add(MALHomeSection("top", "Top Ranked", top))
                } else {
                    val popular = trackerManager.myAnimeList.api.getMangaRanking("bypopularity").map { it.toMALMediaItem() }
                    if (popular.isNotEmpty()) sections.add(MALHomeSection("popular", "All Time Popular", popular))

                    val top = trackerManager.myAnimeList.api.getMangaRanking("all").map { it.toMALMediaItem() }
                    if (top.isNotEmpty()) sections.add(MALHomeSection("top", "Top Ranked", top))

                    val manga = trackerManager.myAnimeList.api.getMangaRanking("manga").map { it.toMALMediaItem() }
                    if (manga.isNotEmpty()) sections.add(MALHomeSection("manga", "Top Manga", manga))

                    val novels = trackerManager.myAnimeList.api.getMangaRanking("novels").map { it.toMALMediaItem() }
                    if (novels.isNotEmpty()) sections.add(MALHomeSection("novels", "Top Light Novels", novels))

                    val favorite = trackerManager.myAnimeList.api.getMangaRanking("favorite").map { it.toMALMediaItem() }
                    if (favorite.isNotEmpty()) sections.add(MALHomeSection("favorite", "Most Favorited", favorite))
                }

                val deduplicatedSections = sections.map { section ->
                    section.copy(items = section.items.distinctBy { it.id })
                }

                malHomeCache[mediaType] = deduplicatedSections
                _state.update {
                    it.copy(
                        malHomeSections = deduplicatedSections,
                        isLoadingMALHome = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MAL home sections" }
                _state.update { it.copy(isLoadingMALHome = false) }
            }
        }
    }

    fun loadMALUserList(mediaType: MediaType, forceRefresh: Boolean = false) {
        if (!isMALLoggedIn()) return
        if (!forceRefresh && _state.value.malUserList.isNotEmpty() && _state.value.currentMALUserListMediaType == mediaType) return

        if (!forceRefresh) {
            val cached = malUserListCache[mediaType]
            if (cached != null) {
                _state.update {
                    it.copy(
                        malUserList = cached,
                        isLoadingMALUserList = false,
                        currentMALUserListMediaType = mediaType,
                    )
                }
                return
            }
        }

        screenModelScope.launch {
            _state.update {
                it.copy(
                    malUserList = emptyList(),
                    isLoadingMALUserList = true,
                    currentMALUserListMediaType = mediaType,
                )
            }
            try {
                val isAnime = mediaType == MediaType.ANIME
                val items = if (isAnime) {
                    trackerManager.myAnimeList.api.getUserAnimeList().map { it.node.toMALMediaItem() }
                } else {
                    trackerManager.myAnimeList.api.getUserMangaList().map { it.node.toMALMediaItem() }
                }

                malUserListCache[mediaType] = items
                _state.update {
                    it.copy(
                        malUserList = items,
                        isLoadingMALUserList = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MAL user list" }
                _state.update { it.copy(isLoadingMALUserList = false) }
            }
        }
    }

    fun setMALStatusFilter(status: String?) {
        _state.update { it.copy(malUserListStatus = status) }
    }

    fun searchMAL(query: String, mediaType: MediaType) {
        screenModelScope.launch {
            _state.update { it.copy(isSearchingMAL = true, malSearchQuery = query) }
            try {
                val isAnime = mediaType == MediaType.ANIME
                val results = if (isAnime) {
                    trackerManager.myAnimeList.api.searchAnime(query).map { it.toMALMediaItem() }
                } else {
                    trackerManager.myAnimeList.api.searchManga(query).map { it.toMALMediaItem() }
                }

                _state.update {
                    it.copy(
                        malSearchResults = results,
                        isSearchingMAL = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to search MAL" }
                _state.update { it.copy(isSearchingMAL = false) }
            }
        }
    }

    fun loadMALProfile() {
        if (!isMALLoggedIn()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingMALProfile = true) }
            try {
                val profile = trackerManager.myAnimeList.api.getUserProfile()
                _state.update {
                    it.copy(
                        malUserProfile = profile,
                        isLoadingMALProfile = false,
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MAL user profile" }
                _state.update { it.copy(isLoadingMALProfile = false) }
            }
        }
    }

    private fun MALAnime.toMALMediaItem(): MALMediaItem = MALMediaItem(
        id = id,
        title = title,
        coverUrl = covers?.large?.ifEmpty { null },
        score = if (mean > 0) mean else null,
        format = mediaType,
        status = status,
        numEpisodesOrChapters = numEpisodes,
        isAnime = true,
        synopsis = synopsis,
        startDate = startDate,
        genres = genres?.map { it.name } ?: emptyList(),
    )

    private fun MALManga.toMALMediaItem(): MALMediaItem = MALMediaItem(
        id = id,
        title = title,
        coverUrl = covers?.large?.ifEmpty { null } ?: covers?.medium,
        score = if (mean > 0) mean else null,
        format = mediaType,
        status = status,
        numEpisodesOrChapters = numChapters,
        isAnime = false,
        synopsis = synopsis,
        startDate = startDate,
        authors = authors.mapNotNull { it.node.getFullName() }.joinToString(", ").ifBlank { null },
    )

    fun selectSeries(item: TrackSeriesItem?) {
        _state.update { it.copy(selectedSeries = item) }
    }

    fun dismissDetails() {
        _state.update { it.copy(selectedSeries = null) }
    }

    // ==========================================
    // MANGAUPDATES OPERATIONS (Legacy/Alternative)
    // ==========================================

    fun isMangaUpdatesLoggedIn(): Boolean = trackerManager.mangaUpdates.isLoggedIn
    fun isLoggedIn(): Boolean = isMangaUpdatesLoggedIn()

    fun loginMangaUpdates(u: String, p: String, onDone: () -> Unit) {
        screenModelScope.launch {
            try {
                trackerManager.mangaUpdates.login(u, p)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log in to MangaUpdates" }
            }
            onDone()
        }
    }
    fun login(u: String, p: String, onDone: () -> Unit) = loginMangaUpdates(u, p, onDone)

    fun logoutMangaUpdates(onDone: () -> Unit = {}) {
        screenModelScope.launch {
            try {
                trackerManager.mangaUpdates.logout()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to log out of MangaUpdates" }
            }
            onDone()
        }
    }
    fun logout(onDone: () -> Unit = {}) = logoutMangaUpdates(onDone)

    fun loadNewReleases() {
        if (_state.value.newReleases.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingReleases = true) }
            try {
                val results = trackerManager.mangaUpdates.api.getRecentReleases()
                val mapped = results.map {
                    MUReleaseItem(
                        title = it.title,
                        chapter = it.chapter,
                        groups = it.groups?.mapNotNull { g -> g.name }?.joinToString(", "),
                        releaseDate = it.releaseDate,
                    )
                }
                _state.update { it.copy(newReleases = mapped, isLoadingReleases = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates new releases" }
                _state.update { it.copy(isLoadingReleases = false) }
            }
        }
    }

    fun loadRecommended() {
        if (_state.value.recommendedSeries.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingRecommended = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search("a")
                _state.update { it.copy(recommendedSeries = results, isLoadingRecommended = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates recommendations" }
                _state.update { it.copy(isLoadingRecommended = false) }
            }
        }
    }

    fun searchSeries(query: String) {
        val q = query.trim().ifBlank { "a" }
        screenModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search(q)
                _state.update { it.copy(muSearchResults = results, isSearching = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to search MangaUpdates series" }
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadGenres() {
        if (_state.value.genres.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingGenres = true) }
            try {
                val results = trackerManager.mangaUpdates.api.getGenres()
                _state.update { it.copy(genres = results, isLoadingGenres = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates genres" }
                _state.update { it.copy(isLoadingGenres = false) }
            }
        }
    }

    fun loadGroups(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingGroups = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchGroups(query)
                _state.update { it.copy(groups = results, isLoadingGroups = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates groups" }
                _state.update { it.copy(isLoadingGroups = false) }
            }
        }
    }

    fun loadNovelRecommended() {
        if (_state.value.novelRecommendedSeries.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingNovelRecommended = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search("a", type = "Novel")
                _state.update { it.copy(novelRecommendedSeries = results, isLoadingNovelRecommended = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates novel recommendations" }
                _state.update { it.copy(isLoadingNovelRecommended = false) }
            }
        }
    }

    fun searchNovels(query: String) {
        val q = query.trim().ifBlank { "a" }
        screenModelScope.launch {
            _state.update { it.copy(isSearchingNovels = true) }
            try {
                val results = trackerManager.mangaUpdates.api.search(q, type = "Novel")
                _state.update { it.copy(novelSearchResults = results, isSearchingNovels = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to search MangaUpdates novels" }
                _state.update { it.copy(isSearchingNovels = false) }
            }
        }
    }

    fun loadNovelReleases() {
        if (_state.value.novelReleases.isNotEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoadingNovelReleases = true) }
            try {
                val results = trackerManager.mangaUpdates.api.getRecentReleases()
                val mapped = results.map {
                    MUReleaseItem(
                        title = it.title,
                        chapter = it.chapter,
                        groups = it.groups?.mapNotNull { g -> g.name }?.joinToString(", "),
                        releaseDate = it.releaseDate,
                    )
                }
                _state.update { it.copy(novelReleases = mapped, isLoadingNovelReleases = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load novel releases" }
                _state.update { it.copy(isLoadingNovelReleases = false) }
            }
        }
    }

    fun loadAuthors(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingAuthors = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchAuthors(query)
                _state.update { it.copy(authors = results, isLoadingAuthors = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates authors" }
                _state.update { it.copy(isLoadingAuthors = false) }
            }
        }
    }

    fun loadPublishers(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingPublishers = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchPublishers(query)
                _state.update { it.copy(publishers = results, isLoadingPublishers = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates publishers" }
                _state.update { it.copy(isLoadingPublishers = false) }
            }
        }
    }

    fun loadReviews(query: String) {
        screenModelScope.launch {
            _state.update { it.copy(isLoadingReviews = true) }
            try {
                val results = trackerManager.mangaUpdates.api.searchReviews(query)
                _state.update { it.copy(reviews = results, isLoadingReviews = false) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load MangaUpdates reviews" }
                _state.update { it.copy(isLoadingReviews = false) }
            }
        }
    }

    // ==========================================
    // LOCAL CATEGORIES & TRACKER LIBRARY
    // ==========================================

    private val matcher = TrackerSourceMatcher()
    private val getCategories = Injekt.get<GetCategories>()
    private val getAnimeCategories = Injekt.get<GetAnimeCategories>()
    private val getNovelCategories = Injekt.get<GetNovelCategories>()
    private val setMangaCategories = Injekt.get<SetMangaCategories>()
    private val setAnimeCategories = Injekt.get<SetAnimeCategories>()
    private val setNovelCategories = Injekt.get<SetNovelCategories>()
    private val updateManga = Injekt.get<UpdateManga>()
    private val updateAnime = Injekt.get<UpdateAnime>()
    private val updateNovel = Injekt.get<UpdateNovel>()

    sealed interface Dialog {
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog

        data class ChangeAnimeCategory(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog

        data class ChangeNovelCategory(
            val novel: Novel,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog

        data class TrackerStatusPicker(
            val item: TrackSeriesItem,
            val isAniList: Boolean,
        ) : Dialog
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun showTrackerStatusPicker(item: TrackSeriesItem, isAniList: Boolean) {
        _state.update { it.copy(dialog = Dialog.TrackerStatusPicker(item, isAniList)) }
    }

    fun showSetCategoryDialog(series: TrackSeriesItem, mediaType: MediaType) {
        screenModelScope.launchIO {
            val storedIds = when (mediaType) {
                MediaType.ANIME -> uiPreferences.trackerPrioritizedAnimeSources().get()
                MediaType.NOVEL -> uiPreferences.trackerPrioritizedNovelSources().get()
                MediaType.MANGA -> uiPreferences.trackerPrioritizedMangaSources().get()
            }.split(",").mapNotNull { it.trim().toLongOrNull() }

            val priorityIds = if (storedIds.isNotEmpty()) storedIds else {
                when (mediaType) {
                    MediaType.ANIME -> Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>().getCatalogueSources().take(5).map { it.id }
                    MediaType.NOVEL -> Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>().getCatalogueSources().take(5).map { it.id }
                    MediaType.MANGA -> Injekt.get<tachiyomi.domain.source.service.SourceManager>().getOnlineSources().filterIsInstance<eu.kanade.tachiyomi.source.CatalogueSource>().take(5).map { it.id }
                }
            }

            val matchResult = matcher.matchAndFetch(series.title, priorityIds, mediaType)
            if (matchResult is TrackerSourceMatcher.MatchResult.Success) {
                val entryId = matchResult.entryId
                when (mediaType) {
                    MediaType.MANGA -> {
                        val manga = Injekt.get<tachiyomi.domain.manga.interactor.GetManga>().await(entryId) ?: return@launchIO
                        val categories = getCategories.await().filterNot { it.isSystemCategory }
                        val mangaCategories = getCategories.await(manga.id)
                        val initialSelection = categories.mapAsCheckboxState { cat ->
                            mangaCategories.any { it.id == cat.id }
                        }.toImmutableList()
                        _state.update { it.copy(dialog = Dialog.ChangeMangaCategory(manga, initialSelection)) }
                    }
                    MediaType.ANIME -> {
                        val anime = Injekt.get<tachiyomi.domain.entries.anime.interactor.GetAnime>().await(entryId) ?: return@launchIO
                        val categories = getAnimeCategories.await().filterNot { it.isSystemCategory }
                        val animeCategories = getAnimeCategories.await(anime.id)
                        val initialSelection = categories.mapAsCheckboxState { cat ->
                            animeCategories.any { it.id == cat.id }
                        }.toImmutableList()
                        _state.update { it.copy(dialog = Dialog.ChangeAnimeCategory(anime, initialSelection)) }
                    }
                    MediaType.NOVEL -> {
                        val novel = Injekt.get<tachiyomi.domain.entries.novel.interactor.GetNovel>().await(entryId) ?: return@launchIO
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
                        val novelCategories = getNovelCategories.await(novel.id)
                        val initialSelection = categories.mapAsCheckboxState { cat ->
                            novelCategories.any { it.id == cat.id }
                        }.toImmutableList()
                        _state.update { it.copy(dialog = Dialog.ChangeNovelCategory(novel, initialSelection)) }
                    }
                }
            }
        }
    }

    fun setMangaCategories(manga: Manga, categories: List<Long>) {
        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
            setMangaCategories.await(manga.id, categories)
            _state.update { it.copy(dialog = null) }
        }
    }

    fun setAnimeCategories(anime: Anime, categories: List<Long>) {
        screenModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
            setAnimeCategories.await(anime.id, categories)
            _state.update { it.copy(dialog = null) }
        }
    }

    fun setNovelCategories(novel: Novel, categories: List<Long>) {
        screenModelScope.launchIO {
            updateNovel.awaitUpdateFavorite(novel.id, true)
            setNovelCategories.await(novel.id, categories)
            _state.update { it.copy(dialog = null) }
        }
    }

    fun updateTrackerEntryStatus(
        item: TrackSeriesItem,
        status: String,
        isAniList: Boolean,
    ) {
        screenModelScope.launchIO {
            try {
                val trackerId = item.trackerId ?: return@launchIO
                if (isAniList) {
                    if (item.mediaType == MediaType.ANIME) {
                        val track = eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack.create(1L).apply {
                            remote_id = trackerId
                            this.status = when (status.uppercase()) {
                                "CURRENT", "WATCHING" -> 1L
                                "COMPLETED" -> 2L
                                "PAUSED", "ON_HOLD" -> 3L
                                "DROPPED" -> 4L
                                "PLANNING", "PLAN_TO_WATCH" -> 5L
                                "REPEATING" -> 6L
                                else -> 5L
                            }
                        }
                        trackerManager.aniList.api.addLibAnime(track)
                    } else {
                        val track = eu.kanade.tachiyomi.data.database.models.Track.create(1L).apply {
                            remote_id = trackerId
                            this.status = when (status.uppercase()) {
                                "CURRENT", "READING" -> 1L
                                "COMPLETED" -> 2L
                                "PAUSED", "ON_HOLD" -> 3L
                                "DROPPED" -> 4L
                                "PLANNING", "PLAN_TO_READ" -> 5L
                                "REPEATING" -> 6L
                                else -> 5L
                            }
                        }
                        trackerManager.aniList.api.addLibManga(track)
                    }
                } else {
                    if (item.mediaType != MediaType.ANIME) {
                        val track = eu.kanade.tachiyomi.data.database.models.Track.create(2L).apply {
                            remote_id = trackerId
                            this.status = when (status.uppercase()) {
                                "CURRENT", "READING" -> 1L
                                "COMPLETED" -> 2L
                                "PAUSED", "ON_HOLD" -> 3L
                                "DROPPED" -> 4L
                                "PLANNING", "PLAN_TO_READ" -> 5L
                                else -> 5L
                            }
                        }
                        trackerManager.myAnimeList.api.updateItem(track)
                    }
                }
                _state.update { it.copy(dialog = null) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to update tracker entry status" }
            }
        }
    }
}

data class TrackState(
    val dialog: TrackScreenModel.Dialog? = null,
    val selectedSeries: TrackSeriesItem? = null,
    // AniList State
    val homeSections: List<ALHomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val currentHomeMediaType: MediaType? = null,
    val userList: List<ALUserListEntry> = emptyList(),
    val isLoadingUserList: Boolean = false,
    val currentUserListMediaType: MediaType? = null,
    val userListStatus: String? = null,
    val searchQuery: String = "",
    val searchResults: List<ALSearchItem> = emptyList(),
    val isSearching: Boolean = false,
    val filterGenres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val selectedSort: String = "TRENDING_DESC",
    val userStats: ALUserStatsViewer? = null,
    val isLoadingStats: Boolean = false,
    // MyAnimeList State
    val malHomeSections: List<MALHomeSection> = emptyList(),
    val isLoadingMALHome: Boolean = false,
    val currentMALHomeMediaType: MediaType? = null,
    val malUserList: List<MALMediaItem> = emptyList(),
    val isLoadingMALUserList: Boolean = false,
    val currentMALUserListMediaType: MediaType? = null,
    val malUserListStatus: String? = null,
    val malSearchQuery: String = "",
    val malSearchResults: List<MALMediaItem> = emptyList(),
    val isSearchingMAL: Boolean = false,
    val malUserProfile: MALUser? = null,
    val isLoadingMALProfile: Boolean = false,
    // MangaUpdates State
    val isLoadingReleases: Boolean = false,
    val newReleases: List<MUReleaseItem> = emptyList(),
    val isLoadingRecommended: Boolean = false,
    val recommendedSeries: List<MURecord> = emptyList(),
    val isLoadingNovelRecommended: Boolean = false,
    val novelRecommendedSeries: List<MURecord> = emptyList(),
    val muSearchResults: List<MURecord> = emptyList(),
    val isSearchingNovels: Boolean = false,
    val novelSearchResults: List<MURecord> = emptyList(),
    val isLoadingNovelReleases: Boolean = false,
    val novelReleases: List<MUReleaseItem> = emptyList(),
    val isLoadingGenres: Boolean = false,
    val genres: List<MUGenreItem> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val groups: List<MUGroupRecord> = emptyList(),
    val isLoadingAuthors: Boolean = false,
    val authors: List<MUAuthorRecord> = emptyList(),
    val isLoadingPublishers: Boolean = false,
    val publishers: List<MUPublisherRecord> = emptyList(),
    val isLoadingReviews: Boolean = false,
    val reviews: List<MUReviewRecord> = emptyList(),
)

data class MUReleaseItem(
    val title: String?,
    val chapter: String?,
    val groups: String?,
    val releaseDate: String? = null,
)
// KMK <--
