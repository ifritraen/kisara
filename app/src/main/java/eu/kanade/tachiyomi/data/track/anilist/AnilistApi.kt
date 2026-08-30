package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAddMangaResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnime
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCurrentUserResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALError
import eu.kanade.tachiyomi.data.track.anilist.dto.ALGenreCollectionResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALIdSearchResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaMetadata
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFilterMetadataResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALHomeSection
import eu.kanade.tachiyomi.data.track.anilist.dto.ALPaginatedSearchResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioSearchResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserAnime
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListMangaQueryResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserStatsResult
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes
import tachiyomi.domain.track.model.Track as DomainTrack

class AnilistApi(val client: OkHttpClient, interceptor: AnilistInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 85, period = 1.minutes)
        .build()

    // KMK -->
    private fun Response.parseALError() {
        val bodyString = peekBody(1024 * 1024).string()
        val errorObj = try {
            json.decodeFromString<ALError>(bodyString)
        } catch (_: Exception) {
            null
        }

        errorObj?.errors?.firstOrNull()?.let {
            val msg = it.message
            if (msg.contains("Invalid token") || it.status == 401) {
                throw Exception("AniList token expired, please login again")
            }
            throw Exception(msg)
        }
    }
    // KMK <--

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val query = $$"""
            |mutation AddManga($mangaId: Int, $progress: Int, $status: MediaListStatus, $private: Boolean) {
                |SaveMediaListEntry (mediaId: $mangaId, progress: $progress, status: $status, private: $private) {
                |   id
                |   status
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mangaId", track.remote_id)
                    put("progress", track.last_chapter_read.toInt())
                    put("status", track.toApiStatus())
                    put("private", track.private)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALAddMangaResult>()
                    .let {
                        track.library_id = it.data.entry.id
                        track
                    }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val query = $$"""
            |mutation UpdateManga(
                |$listId: Int, $progress: Int, $status: MediaListStatus, $private: Boolean,
                |$score: Int, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput
            |) {
                |SaveMediaListEntry(
                    |id: $listId, progress: $progress, status: $status, private: $private,
                    |scoreRaw: $score, startedAt: $startedAt, completedAt: $completedAt
                |) {
                    |id
                    |status
                    |progress
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", track.library_id)
                    put("progress", track.last_chapter_read.toInt())
                    put("status", track.toApiStatus())
                    put("score", track.score.toInt())
                    put("startedAt", createDate(track.started_reading_date))
                    put("completedAt", createDate(track.finished_reading_date))
                    put("private", track.private)
                }
            }
            authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                // KMK -->
                .use { it.parseALError() }
            // KMK <--
            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            val query = $$"""
            |mutation DeleteManga($listId: Int) {
                |DeleteMediaListEntry(id: $listId) {
                    |deleted
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", track.libraryId)
                }
            }
            authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                // KMK -->
                .use { it.parseALError() }
            // KMK <--
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val query = $$"""
            |query Search($query: String) {
                |Page (perPage: 50) {
                    |media(search: $query, type: MANGA, format_not_in: [NOVEL]) {
                        |id
                        |staff {
                            |edges {
                                |role
                                |id
                                |node {
                                    |name {
                                        |full
                                        |userPreferred
                                        |native
                                    |}
                                |}
                            |}
                        |}
                        |title {
                            |userPreferred
                        |}
                        |coverImage {
                            |large
                        |}
                        |format
                        |status
                        |chapters
                        |description
                        |startDate {
                            |year
                            |month
                            |day
                        |}
                        |averageScore
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALSearchResult>()
                    .data.page.media
                    .map { it.toALManga().toTrack() }
            }
        }
    }

    suspend fun findLibManga(track: Track, userid: Int): Track? {
        return withIOContext {
            val query = $$"""
            |query ($id: Int!, $manga_id: Int!) {
                |Page {
                    |mediaList(userId: $id, type: MANGA, mediaId: $manga_id) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |private
                        |startedAt {
                            |year
                            |month
                            |day
                        |}
                        |completedAt {
                            |year
                            |month
                            |day
                        |}
                        |media {
                            |id
                            |title {
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |format
                            |status
                            |chapters
                            |description
                            |startDate {
                                |year
                                |month
                                |day
                            |}
                            |staff {
                                |edges {
                                    |role
                                    |id
                                    |node {
                                        |name {
                                            |full
                                            |userPreferred
                                            |native
                                        |}
                                    |}
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", userid)
                    put("manga_id", track.remote_id)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALUserListMangaQueryResult>()
                    .data.page.mediaList
                    .map { it.toALUserManga() }
                    .firstOrNull()
                    ?.toTrack()
            }
        }
    }

    suspend fun getLibManga(track: Track, userId: Int): Track {
        return findLibManga(track, userId) ?: throw Exception("Could not find manga")
    }

    fun createOAuth(token: String): ALOAuth {
        return ALOAuth(token, "Bearer", System.currentTimeMillis() + 31536000000, 31536000000)
    }

    suspend fun getCurrentUser(): Pair<Int, String> {
        return withIOContext {
            val query = """
            |query User {
                |Viewer {
                    |id
                    |mediaListOptions {
                        |scoreFormat
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALCurrentUserResult>()
                    .let {
                        val viewer = it.data.viewer
                        Pair(viewer.id, viewer.mediaListOptions.scoreFormat)
                    }
            }
        }
    }

    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            val query = $$"""
            |query ($mangaId: Int!) {
                |Media (id: $mangaId) {
                    |id
                    |title {
                        |userPreferred
                    |}
                    |coverImage {
                        |large
                    |}
                    |description
                    |staff {
                        |edges {
                            |role
                            |id
                            |node {
                                |name {
                                    |full
                                    |userPreferred
                                    |native
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mangaId", track.remoteId)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALMangaMetadata>()
                    .let { metadata ->
                        val media = metadata.data.media
                        TrackMangaMetadata(
                            remoteId = media.id,
                            title = media.title.userPreferred,
                            thumbnailUrl = media.coverImage.large,
                            description = media.description?.htmlDecode()?.ifEmpty { null },
                            authors = media.staff.edges
                                .filter { "Story" in it.role }
                                .mapNotNull { it.node.name() }
                                .joinToString(", ")
                                .ifEmpty { null },
                            artists = media.staff.edges
                                .filter { "Art" in it.role }
                                .mapNotNull { it.node.name() }
                                .joinToString(", ")
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    // SY -->
    suspend fun searchById(id: String): TrackSearch {
        return withIOContext {
            val query = $$"""
            |query ($mangaId: Int!) {
                |Media (id: $mangaId) {
                    |id
                    |title {
                        |userPreferred
                    |}
                    |coverImage {
                        |large
                    |}
                    |format
                    |status
                    |chapters
                    |description
                    |startDate {
                        |year
                        |month
                        |day
                    |}
                    |averageScore
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mangaId", id)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    // KMK -->
                    .also { it.parseALError() }
                    // KMK <--
                    .parseAs<ALIdSearchResult>()
                    .data.media
                    .toALManga()
                    .toTrack()
            }
        }
    }
    // SY <--

    // Anime Tracking & Feeds -->
    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val query = $$"""
            |mutation AddAnime($animeId: Int, $progress: Int, $status: MediaListStatus, $private: Boolean) {
                |SaveMediaListEntry (mediaId: $animeId, progress: $progress, status: $status, private: $private) {
                |   id
                |   status
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("animeId", track.remote_id)
                    put("progress", track.last_episode_seen.toInt())
                    put("status", track.toApiStatus())
                    put("private", track.private)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALAddMangaResult>()
                    .let {
                        track.library_id = it.data.entry.id
                        track
                    }
            }
        }
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val query = $$"""
            |mutation UpdateAnime(
                |$listId: Int, $progress: Int, $status: MediaListStatus, $private: Boolean,
                |$score: Int, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput
            |) {
                |SaveMediaListEntry(
                    |id: $listId, progress: $progress, status: $status, private: $private,
                    |scoreRaw: $score, startedAt: $startedAt, completedAt: $completedAt
                |) {
                    |id
                    |status
                    |progress
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", track.library_id)
                    put("progress", track.last_episode_seen.toInt())
                    put("status", track.toApiStatus())
                    put("score", track.score.toInt())
                    put("startedAt", createDate(track.started_watching_date))
                    put("completedAt", createDate(track.finished_watching_date))
                    put("private", track.private)
                }
            }
            authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .use { it.parseALError() }
            track
        }
    }

    suspend fun deleteLibAnime(track: AnimeTrack) {
        withIOContext {
            val query = $$"""
            |mutation DeleteAnime($listId: Int) {
                |DeleteMediaListEntry(id: $listId) {
                    |deleted
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", track.library_id)
                }
            }
            authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .use { it.parseALError() }
        }
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return withIOContext {
            val query = $$"""
            |query SearchAnime($query: String) {
                |Page (perPage: 50) {
                    |media(search: $query, type: ANIME) {
                        |id
                        |title {
                            |userPreferred
                        |}
                        |coverImage {
                            |large
                        |}
                        |format
                        |status
                        |episodes
                        |description
                        |startDate {
                            |year
                            |month
                            |day
                        |}
                        |averageScore
                        |genres
                        |studios {
                            |edges {
                                |isMain
                                |node {
                                    |name
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALSearchResult>()
                    .data.page.media
                    .map { it.toALAnime().toTrack() }
            }
        }
    }

    suspend fun findLibAnime(track: AnimeTrack, userid: Int): ALUserAnime? {
        return withIOContext {
            val query = $$"""
            |query ($id: Int!, $anime_id: Int!) {
                |Page {
                    |mediaList(userId: $id, type: ANIME, mediaId: $anime_id) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |private
                        |startedAt {
                            |year
                            |month
                            |day
                        |}
                        |completedAt {
                            |year
                            |month
                            |day
                        |}
                        |media {
                            |id
                            |title {
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |format
                            |status
                            |episodes
                            |description
                            |startDate {
                                |year
                                |month
                                |day
                            |}
                            |genres
                            |studios {
                                |edges {
                                    |isMain
                                    |node {
                                        |name
                                    |}
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", userid)
                    put("anime_id", track.remote_id)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALUserListMangaQueryResult>()
                    .data.page.mediaList
                    .map { it.toALUserAnime() }
                    .firstOrNull()
            }
        }
    }

    suspend fun getLibAnime(track: AnimeTrack, userId: Int): ALUserAnime {
        return findLibAnime(track, userId) ?: throw Exception("Could not find anime in library")
    }

    suspend fun getTrendingAnime(page: Int = 1, perPage: Int = 25): List<ALAnime> {
        return withIOContext {
            val query = $$"""
            |query ($page: Int, $perPage: Int) {
                |Page (page: $page, perPage: $perPage) {
                    |media(type: ANIME, sort: TRENDING_DESC) {
                        |id
                        |title { userPreferred }
                        |coverImage { large }
                        |format
                        |status
                        |episodes
                        |description
                        |averageScore
                        |genres
                        |startDate { year month day }
                        |studios {
                            |edges {
                                |isMain
                                |node { name }
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", page)
                    put("perPage", perPage)
                }
            }
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALSearchResult>()
                    .data.page.media
                    .map { it.toALAnime() }
            }
        }
    }

    suspend fun getSeasonalAnime(season: String, seasonYear: Int, page: Int = 1, perPage: Int = 25): List<ALAnime> {
        return withIOContext {
            val query = $$"""
            |query ($season: MediaSeason, $seasonYear: Int, $page: Int, $perPage: Int) {
                |Page (page: $page, perPage: $perPage) {
                    |media(type: ANIME, season: $season, seasonYear: $seasonYear, sort: POPULARITY_DESC) {
                        |id
                        |title { userPreferred }
                        |coverImage { large }
                        |format
                        |status
                        |episodes
                        |description
                        |averageScore
                        |genres
                        |startDate { year month day }
                        |studios {
                            |edges {
                                |isMain
                                |node { name }
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("season", season)
                    put("seasonYear", seasonYear)
                    put("page", page)
                    put("perPage", perPage)
                }
            }
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALSearchResult>()
                    .data.page.media
                    .map { it.toALAnime() }
            }
        }
    }

    suspend fun getTopRatedAnime(page: Int = 1, perPage: Int = 25): List<ALAnime> {
        return withIOContext {
            val query = $$"""
            |query ($page: Int, $perPage: Int) {
                |Page (page: $page, perPage: $perPage) {
                    |media(type: ANIME, sort: SCORE_DESC) {
                        |id
                        |title { userPreferred }
                        |coverImage { large }
                        |format
                        |status
                        |episodes
                        |description
                        |averageScore
                        |genres
                        |startDate { year month day }
                        |studios {
                            |edges {
                                |isMain
                                |node { name }
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", page)
                    put("perPage", perPage)
                }
            }
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALSearchResult>()
                    .data.page.media
                    .map { it.toALAnime() }
            }
        }
    }

    suspend fun getUserAnimeList(userId: Int, status: String? = null): List<ALUserAnime> {
        return withIOContext {
            val query = $$"""
            |query ($id: Int!, $status: MediaListStatus) {
                |Page (perPage: 50) {
                    |mediaList(userId: $id, type: ANIME, status: $status) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |private
                        |startedAt { year month day }
                        |completedAt { year month day }
                        |media {
                            |id
                            |title { userPreferred }
                            |coverImage { large }
                            |format
                            |status
                            |episodes
                            |description
                            |startDate { year month day }
                            |genres
                            |studios {
                                |edges {
                                    |isMain
                                    |node { name }
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", userId)
                    if (status != null) put("status", status)
                }
            }
            with(json) {
                authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALUserListMangaQueryResult>()
                    .data.page.mediaList
                    .map { it.toALUserAnime() }
            }
        }
    }

    suspend fun searchStudios(search: String): List<ALStudioNode> {
        return withIOContext {
            val query = $$"""
            |query ($query: String) {
                |Page (perPage: 25) {
                    |studios(search: $query) {
                        |id
                        |name
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALStudioSearchResult>()
                    .data.page.studios
            }
        }
    }

    suspend fun getAnimeGenres(): List<String> {
        return withIOContext {
            val query = """
            |query {
                |GenreCollection
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALGenreCollectionResult>()
                    .data.genres
            }
        }
    }
    // Anime Tracking & Feeds <--

    suspend fun getHomePage(
        type: String,
        format: String?,
        isAdult: Boolean?,
        season: String,
        seasonYear: Int,
        prevSeasons: List<Pair<String, Int>>,
        enabledSections: Set<String> = emptySet(),
    ): List<ALHomeSection> {
        return withIOContext {
            val mediaFragment = """
                id title { userPreferred } coverImage { large } format status episodes chapters description averageScore genres startDate { year month day } studios { edges { isMain node { name } } } isAdult
            """.trimIndent()

            val typeFilter = "type: $type"
            val formatFilter = format?.let { ", format: $it" } ?: ""
            val adultFilter = isAdult?.let { ", isAdult: $it" } ?: ""
            val baseFilter = "$typeFilter$formatFilter$adultFilter"

            val isAll = enabledSections.isEmpty()

            val sb = StringBuilder("query {\n")

            fun addSection(alias: String, filters: String) {
                sb.append("""
                    $alias: Page(page: 1, perPage: 15) {
                        media($filters) {
                            $mediaFragment
                        }
                    }
                """.trimIndent()).append("\n")
            }

            fun addRecSection(alias: String, sort: String) {
                sb.append("""
                    $alias: Page(page: 1, perPage: 15) {
                        recommendations(sort: $sort) {
                            mediaRecommendation {
                                $mediaFragment
                            }
                        }
                    }
                """.trimIndent()).append("\n")
            }

            if (isAll || "trending" in enabledSections) addSection("trendingMedia", "$baseFilter, sort: TRENDING_DESC")
            if (isAll || "popular" in enabledSections) addSection("popularMedia", "$baseFilter, sort: POPULARITY_DESC")
            if (isAll || "recent" in enabledSections) addSection("recentMedia", "$baseFilter, sort: ID_DESC")
            if (isAll || "top_rated" in enabledSections) addSection("topRatedMedia", "$baseFilter, sort: SCORE_DESC")
            if (isAll || "recently_completed" in enabledSections) addSection("recentlyCompletedMedia", "$baseFilter, status: FINISHED, sort: END_DATE_DESC")
            if (isAll || "upcoming" in enabledSections) addSection("upcomingMedia", "$baseFilter, status: NOT_YET_RELEASED, sort: POPULARITY_DESC")
            if (isAll || "popular_season" in enabledSections) addSection("popularThisSeason", "$baseFilter, season: $season, seasonYear: $seasonYear, sort: POPULARITY_DESC")
            if (isAll || "top_rated_season" in enabledSections) addSection("topRatedThisSeason", "$baseFilter, season: $season, seasonYear: $seasonYear, sort: SCORE_DESC")

            if (isAll || "prev_popular" in enabledSections) {
                prevSeasons.forEachIndexed { i, (s, y) ->
                    addSection("prev${i}Popular", "$baseFilter, season: $s, seasonYear: $y, sort: POPULARITY_DESC")
                }
            }
            if (isAll || "prev_top_rated" in enabledSections) {
                prevSeasons.forEachIndexed { i, (s, y) ->
                    addSection("prev${i}TopRated", "$baseFilter, season: $s, seasonYear: $y, sort: SCORE_DESC")
                }
            }

            if (isAll || "community_recommendation" in enabledSections) addRecSection("communityRecs", "RATING_DESC")
            if (isAll || "recommended" in enabledSections) addRecSection("recommendedMedia", "ID_DESC")

            sb.append("}")

            val payload = buildJsonObject {
                put("query", sb.toString())
            }

            val responseText = client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .also { it.parseALError() }
                .body.string()

            val data = json.parseToJsonElement(responseText).jsonObject["data"]?.jsonObject ?: return@withIOContext emptyList()

            val sections = mutableListOf<ALHomeSection>()

            fun extract(key: String, title: String) {
                val mediaList = data[key]?.jsonObject?.get("media")?.jsonArray
                if (mediaList != null && mediaList.isNotEmpty()) {
                    val items = mediaList.mapNotNull {
                        try {
                            json.decodeFromJsonElement<ALSearchItem>(it)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (items.isNotEmpty()) {
                        sections.add(ALHomeSection(key, title, items))
                    }
                }
            }

            fun extractRec(key: String, title: String) {
                val recList = data[key]?.jsonObject?.get("recommendations")?.jsonArray
                if (recList != null && recList.isNotEmpty()) {
                    val items = recList.mapNotNull { recElem ->
                        val mediaRec = recElem.jsonObject["mediaRecommendation"] ?: return@mapNotNull null
                        try {
                            json.decodeFromJsonElement<ALSearchItem>(mediaRec)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (items.isNotEmpty()) {
                        sections.add(ALHomeSection(key, title, items))
                    }
                }
            }

            extract("recentMedia", "Recent")
            extract("trendingMedia", "Trending")
            extract("topRatedMedia", "Top (High scored)")
            extract("popularMedia", "Popular")
            extract("recentlyCompletedMedia", "Recently completed")
            extract("upcomingMedia", "Upcoming")

            val seasonTitle = "${season.lowercase().replaceFirstChar { it.uppercase() }} $seasonYear"
            extract("popularThisSeason", "Popular $seasonTitle")
            extract("topRatedThisSeason", "Highest Rated $seasonTitle")

            prevSeasons.forEachIndexed { i, (s, y) ->
                val prevTitle = "${s.lowercase().replaceFirstChar { it.uppercase() }} $y"
                extract("prev${i}TopRated", "Top (High scored) in $prevTitle")
                extract("prev${i}Popular", "Most Popular in $prevTitle")
            }

            extractRec("recommendedMedia", "Recommended for you")
            extractRec("communityRecs", "Community recommendation")

            sections
        }
    }

    suspend fun getFilterMetadata(): ALFilterMetadataResult {
        return withIOContext {
            val query = """
                query {
                    GenreCollection
                    MediaTagCollection {
                        name
                        description
                        category
                        isAdult
                    }
                }
            """.trimIndent()
            
            val payload = buildJsonObject { put("query", query) }
            
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALFilterMetadataResult>()
            }
        }
    }

    suspend fun getUserStats(): ALUserStatsResult {
        return withIOContext {
            val query = """
                query {
                  Viewer {
                    id
                    name
                    avatar { large medium }
                    bannerImage
                    options { displayAdultContent }
                    mediaListOptions {
                      scoreFormat
                      animeList { sectionOrder splitCompletedSectionByFormat }
                      mangaList { sectionOrder splitCompletedSectionByFormat }
                    }
                    statistics {
                      anime { count episodesWatched meanScore minutesWatched scores { score count } genres { genre count meanScore minutesWatched } tags { tag { name isAdult } count meanScore } formats { format count } }
                      manga { count chaptersRead volumesRead meanScore scores { score count } genres { genre count meanScore chaptersRead } tags { tag { name isAdult } count meanScore } formats { format count } }
                    }
                    favourites {
                      anime { nodes { id title { userPreferred } coverImage { large } } }
                      manga { nodes { id title { userPreferred } coverImage { large } } }
                      characters { nodes { id name { userPreferred } image { large } } }
                      staff { nodes { id name { userPreferred } image { large } } }
                    }
                  }
                }
            """.trimIndent()
            
            val payload = buildJsonObject { put("query", query) }
            
            with(json) {
                authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALUserStatsResult>()
            }
        }
    }

    suspend fun searchMediaPaginated(
        type: String,
        format: String?,
        search: String?,
        genres: List<String>?,
        tags: List<String>?,
        status: String?,
        sort: String?,
        year: Int?,
        isAdult: Boolean?,
        page: Int,
        perPage: Int
    ): ALPaginatedSearchResult {
        return withIOContext {
            val query = ""${'"'}
            |query(
                |${'$'}page: Int, ${'$'}perPage: Int, ${'$'}type: MediaType, ${'$'}format: MediaFormat,
                |${'$'}search: String, ${'$'}genres: [String], ${'$'}tags: [String], ${'$'}status: MediaStatus,
                |${'$'}sort: [MediaSort], ${'$'}year: Int, ${'$'}isAdult: Boolean
            |) {
                |Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo {
                        |total
                        |perPage
                        |currentPage
                        |lastPage
                        |hasNextPage
                    |}
                    |media(
                        |type: ${'$'}type, format: ${'$'}format, search: ${'$'}search, genre_in: ${'$'}genres,
                        |tag_in: ${'$'}tags, status: ${'$'}status, sort: ${'$'}sort, seasonYear: ${'$'}year, isAdult: ${'$'}isAdult
                    |) {
                        |id title { userPreferred } coverImage { large } format status episodes chapters description averageScore genres startDate { year month day } studios { edges { isMain node { name } } } isAdult
                    |}
                |}
            |}
            ""${'"'}.trimMargin()
            
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", page)
                    put("perPage", perPage)
                    put("type", type)
                    if (format != null) put("format", format)
                    if (search != null) put("search", search)
                    if (genres != null && genres.isNotEmpty()) {
                        putJsonArray("genres") { genres.forEach { add(it) } }
                    }
                    if (tags != null && tags.isNotEmpty()) {
                        putJsonArray("tags") { tags.forEach { add(it) } }
                    }
                    if (status != null) put("status", status)
                    if (sort != null) {
                        putJsonArray("sort") { add(sort) }
                    }
                    if (year != null) put("year", year)
                    if (isAdult != null) put("isAdult", isAdult)
                }
            }
            
            with(json) {
                client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALPaginatedSearchResult>()
            }
        }
    }

    suspend fun getUserMediaList(userId: Int, type: String): ALUserListResult {
        return withIOContext {
            val query = ""${'"'}
            |query (${'$'}id: Int, ${'$'}type: MediaType) {
                |MediaListCollection(userId: ${'$'}id, type: ${'$'}type) {
                    |lists {
                        |name
                        |isCustomList
                        |isSplitCompletedList
                        |status
                        |entries {
                            |id
                            |status
                            |score
                            |progress
                            |progressVolumes
                            |repeat
                            |priority
                            |private
                            |hiddenFromStatusLists
                            |customLists
                            |advancedScores
                            |notes
                            |updatedAt
                            |startedAt { year month day }
                            |completedAt { year month day }
                            |media {
                                |id title { userPreferred } coverImage { large } format status episodes chapters description averageScore genres startDate { year month day } studios { edges { isMain node { name } } } isAdult
                            |}
                        |}
                    |}
                |}
            |}
            ""${'"'}.trimMargin()
            
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", userId)
                    put("type", type)
                }
            }
            
            with(json) {
                authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .also { it.parseALError() }
                    .parseAs<ALUserListResult>()
            }
        }
    }

    private fun createDate(dateValue: Long): JsonObject {
        if (dateValue == 0L) {
            return buildJsonObject {
                put("year", JsonNull)
                put("month", JsonNull)
                put("day", JsonNull)
            }
        }

        val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateValue), ZoneId.systemDefault())
        return buildJsonObject {
            put("year", dateTime.year)
            put("month", dateTime.monthValue)
            put("day", dateTime.dayOfMonth)
        }
    }

    companion object {
        // Registered under KMK's MAL account
        private const val CLIENT_ID = "16801"
        private const val API_URL = "https://graphql.anilist.co/"
        private const val BASE_URL = "https://anilist.co/api/v2/"
        private const val BASE_MANGA_URL = "https://anilist.co/manga/"
        private const val BASE_ANIME_URL = "https://anilist.co/anime/"

        fun mangaUrl(mediaId: Long): String {
            return BASE_MANGA_URL + mediaId
        }

        fun animeUrl(mediaId: Long): String {
            return BASE_ANIME_URL + mediaId
        }

        fun authUrl(): Uri = "${BASE_URL}oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
    }
}
