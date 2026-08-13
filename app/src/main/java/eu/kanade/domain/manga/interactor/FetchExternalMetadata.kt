package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetMangaExternalMetadata
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaExternalMetadata
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

class FetchExternalMetadata(
    private val getMangaExternalMetadata: GetMangaExternalMetadata = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val client: OkHttpClient = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    suspend fun await(manga: Manga, forceRefresh: Boolean = false): MangaExternalMetadata? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val existing = getMangaExternalMetadata.await(manga.id)
            if (existing != null) return@withContext existing
        }

        val cleanTitle = cleanMangaTitle(manga.title)

        // 1. Primary: MangaUpdates
        var metadata = fetchFromMangaUpdates(cleanTitle, manga.id)

        // 2. Fallback 1: MangaBaka
        if (metadata == null) {
            metadata = fetchFromMangaBaka(cleanTitle, manga.id)
        }

        // 3. Fallback 2: AniList
        if (metadata == null) {
            metadata = fetchFromAniList(cleanTitle, manga.id)
        }

        if (metadata != null) {
            getMangaExternalMetadata.upsert(metadata)
        }

        metadata
    }

    private suspend fun fetchFromMangaUpdates(title: String, mangaId: Long): MangaExternalMetadata? {
        return try {
            val mangaUpdates = trackerManager.mangaUpdates
            val searchResults = mangaUpdates.api.search(title)
            val bestMatch = searchResults.firstOrNull() ?: return null
            val seriesId = bestMatch.seriesId ?: return null
            val series = mangaUpdates.api.getSeries(seriesId)

            val score = series.bayesianRating ?: bestMatch.bayesianRating
            val status = series.status?.htmlDecode()
            val startDate = series.year ?: bestMatch.year
            val totalChapters = series.latestChapter ?: bestMatch.latestChapter
            val genres = series.genres?.mapNotNull { it.genre?.htmlDecode() }?.filter { it.isNotBlank() }.orEmpty()
            val tags = series.categories?.mapNotNull { it.category?.htmlDecode() }?.filter { it.isNotBlank() }.orEmpty()
            val licensor = series.englishPublisher?.htmlDecode()
            val demographic = series.type?.htmlDecode()
            val synopsis = series.description?.htmlDecode() ?: bestMatch.description?.htmlDecode()

            MangaExternalMetadata(
                mangaId = mangaId,
                score = score,
                status = status,
                startDate = startDate,
                totalChapters = totalChapters,
                genres = genres,
                tags = tags,
                licensor = licensor,
                demographic = demographic,
                synopsis = synopsis,
                sourceName = "MangaUpdates",
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "MangaUpdates metadata fetch failed for: $title" }
            null
        }
    }

    private suspend fun fetchFromMangaBaka(title: String, mangaId: Long): MangaExternalMetadata? {
        return try {
            val encodedQuery = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.mangabaka.dev/v1/series?q=$encodedQuery"
            val response = client.newCall(GET(url)).awaitSuccess()
            val searchResult = with(json) { response.parseAs<MBSearchResponse>() }
            val series = searchResult.data?.firstOrNull() ?: return null

            val score = series.score
            val status = series.status
            val startDate = series.year?.toString()
            val totalChapters = series.chapters
            val genres = series.genres.orEmpty()
            val tags = series.tags.orEmpty()
            val synopsis = series.description

            MangaExternalMetadata(
                mangaId = mangaId,
                score = score,
                status = status,
                startDate = startDate,
                totalChapters = totalChapters,
                genres = genres,
                tags = tags,
                licensor = null,
                demographic = null,
                synopsis = synopsis,
                sourceName = "MangaBaka",
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "MangaBaka metadata fetch failed for: $title" }
            null
        }
    }

    private suspend fun fetchFromAniList(title: String, mangaId: Long): MangaExternalMetadata? {
        return try {
            val query = """
                query (${'$'}search: String) {
                    Media(search: ${'$'}search, type: MANGA) {
                        id
                        averageScore
                        status
                        chapters
                        startDate {
                            year
                            month
                            day
                        }
                        genres
                        tags {
                            name
                        }
                        description(asHtml: false)
                    }
                }
            """.trimIndent()

            val body = buildJsonObject {
                put("query", query)
                put(
                    "variables",
                    buildJsonObject {
                        put("search", title)
                    },
                )
            }

            val request = POST(
                url = "https://graphql.anilist.co/",
                body = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            val response = client.newCall(request).awaitSuccess()
            val alResult = with(json) { response.parseAs<ALMetadataResponse>() }
            val media = alResult.data?.media ?: return null

            val score = media.averageScore?.let { it / 10.0 }
            val status = media.status
            val startDate = media.startDate?.year?.toString()
            val totalChapters = media.chapters
            val genres = media.genres.orEmpty()
            val tags = media.tags?.mapNotNull { it.name }.orEmpty()
            val synopsis = media.description

            MangaExternalMetadata(
                mangaId = mangaId,
                score = score,
                status = status,
                startDate = startDate,
                totalChapters = totalChapters,
                genres = genres,
                tags = tags,
                licensor = null,
                demographic = null,
                synopsis = synopsis,
                sourceName = "AniList",
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "AniList metadata fetch failed for: $title" }
            null
        }
    }

    private fun cleanMangaTitle(title: String): String {
        return title
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""\s*(Season|Chapter|Volume|Vol\.|Ch\.)\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { title }
    }
}

@Serializable
private data class MBSearchResponse(
    val data: List<MBSeriesItem>? = null,
)

@Serializable
private data class MBSeriesItem(
    val id: Long? = null,
    val title: String? = null,
    val score: Double? = null,
    val status: String? = null,
    val year: Int? = null,
    val chapters: Int? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val description: String? = null,
)

@Serializable
private data class ALMetadataResponse(
    val data: ALMediaData? = null,
)

@Serializable
private data class ALMediaData(
    @SerialName("Media")
    val media: ALMediaItem? = null,
)

@Serializable
private data class ALMediaItem(
    val id: Long? = null,
    val averageScore: Int? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val startDate: ALFuzzyDate? = null,
    val genres: List<String>? = null,
    val tags: List<ALTagItem>? = null,
    val description: String? = null,
)

@Serializable
private data class ALFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
private data class ALTagItem(
    val name: String? = null,
)
