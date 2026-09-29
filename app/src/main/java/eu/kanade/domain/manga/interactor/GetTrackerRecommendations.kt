package eu.kanade.domain.manga.interactor

import android.app.Application
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

@Serializable
data class TrackerRecommendation(
    val id: Long = 0,
    val title: String,
    val coverUrl: String? = null,
    val score: Double? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val sourceName: String = "AniList",
    val synopsis: String? = null,
)

class GetTrackerRecommendations(
    private val app: Application = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) {
    private val client: OkHttpClient by lazy { networkHelper.client }
    private val json = Json { ignoreUnknownKeys = true }

    private fun getCacheFile(mediaType: MediaType): File {
        val suffix = when (mediaType) {
            MediaType.MANGA -> "manga"
            MediaType.ANIME -> "anime"
            MediaType.NOVEL -> "novel"
        }
        return File(app.cacheDir, "tracker_recommendations_${suffix}_cache.json")
    }

    fun getCached(mediaType: MediaType = MediaType.MANGA): List<TrackerRecommendation> {
        return try {
            val file = getCacheFile(mediaType)
            if (file.exists()) {
                json.decodeFromString<List<TrackerRecommendation>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetch(mediaType: MediaType = MediaType.MANGA, force: Boolean = false): List<TrackerRecommendation> = withIOContext {
        if (!force) {
            val cached = getCached(mediaType)
            if (cached.isNotEmpty()) return@withIOContext cached
        }

        val anilistResults = tryFetchAniListRecommendations(mediaType)
        if (anilistResults.isNotEmpty()) {
            saveCache(mediaType, anilistResults)
            return@withIOContext anilistResults
        }

        if (mediaType == MediaType.MANGA) {
            val mangaUpdatesResults = tryFetchMangaUpdatesRecommendations()
            if (mangaUpdatesResults.isNotEmpty()) {
                saveCache(mediaType, mangaUpdatesResults)
                return@withIOContext mangaUpdatesResults
            }
        }

        getCached(mediaType)
    }

    private fun saveCache(mediaType: MediaType, items: List<TrackerRecommendation>) {
        try {
            getCacheFile(mediaType).writeText(json.encodeToString(items))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to save tracker recommendations cache for $mediaType" }
        }
    }

    private suspend fun tryFetchAniListRecommendations(mediaType: MediaType): List<TrackerRecommendation> {
        return try {
            val trackedMediaIds = mutableListOf<Long>()

            when (mediaType) {
                MediaType.MANGA -> {
                    val libraryMangas = getLibraryManga.await()
                    for (manga in libraryMangas.take(20)) {
                        val tracks = try { getTracks.await(manga.id) } catch (_: Exception) { emptyList() }
                        val alTrack = tracks.find { it.trackerId == TrackerManager.ANILIST && it.remoteId > 0 }
                        if (alTrack != null) trackedMediaIds.add(alTrack.remoteId)
                    }
                }
                MediaType.ANIME -> {
                    try {
                        val getLibraryAnime = Injekt.get<tachiyomi.domain.entries.anime.interactor.GetLibraryAnime>()
                        val getAnimeTracks = Injekt.get<tachiyomi.domain.track.anime.interactor.GetAnimeTracks>()
                        val libraryAnime = getLibraryAnime.await()
                        for (anime in libraryAnime.take(20)) {
                            val tracks = try { getAnimeTracks.await(anime.id) } catch (_: Exception) { emptyList() }
                            val alTrack = tracks.find { it.trackerId == TrackerManager.ANILIST && it.remoteId > 0 }
                            if (alTrack != null) trackedMediaIds.add(alTrack.remoteId)
                        }
                    } catch (_: Exception) {}
                }
                MediaType.NOVEL -> {
                    // Novels are tracked as MANGA format NOVEL on AniList
                }
            }

            val query = when (mediaType) {
                MediaType.ANIME -> {
                    if (trackedMediaIds.isNotEmpty()) {
                        val randomTargetId = trackedMediaIds.shuffled().first()
                        """
                        query {
                          Media(id: $randomTargetId, type: ANIME) {
                            recommendations(sort: RATING_DESC, perPage: 15) {
                              nodes {
                                mediaRecommendation {
                                  id
                                  type
                                  format
                                  title {
                                    userPreferred
                                    romaji
                                    english
                                  }
                                  coverImage {
                                    large
                                  }
                                  averageScore
                                  status
                                  genres
                                  description(asHtml: false)
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        query {
                          Page(page: 1, perPage: 15) {
                            media(type: ANIME, sort: [TRENDING_DESC, SCORE_DESC], isAdult: false) {
                              id
                              type
                              format
                              title {
                                userPreferred
                                romaji
                                english
                              }
                              coverImage {
                                large
                              }
                              averageScore
                              status
                              genres
                              description(asHtml: false)
                            }
                          }
                        }
                        """.trimIndent()
                    }
                }
                MediaType.NOVEL -> {
                    """
                    query {
                      Page(page: 1, perPage: 15) {
                        media(type: MANGA, format: NOVEL, sort: [TRENDING_DESC, SCORE_DESC], isAdult: false) {
                          id
                          type
                          format
                          title {
                            userPreferred
                            romaji
                            english
                          }
                          coverImage {
                            large
                          }
                          averageScore
                          status
                          genres
                          description(asHtml: false)
                        }
                      }
                    }
                    """.trimIndent()
                }
                MediaType.MANGA -> {
                    if (trackedMediaIds.isNotEmpty()) {
                        val randomTargetId = trackedMediaIds.shuffled().first()
                        """
                        query {
                          Media(id: $randomTargetId, type: MANGA) {
                            recommendations(sort: RATING_DESC, perPage: 15) {
                              nodes {
                                mediaRecommendation {
                                  id
                                  type
                                  format
                                  title {
                                    userPreferred
                                    romaji
                                    english
                                  }
                                  coverImage {
                                    large
                                  }
                                  averageScore
                                  status
                                  genres
                                  description(asHtml: false)
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        query {
                          Page(page: 1, perPage: 15) {
                            media(type: MANGA, sort: [TRENDING_DESC, SCORE_DESC], isAdult: false) {
                              id
                              type
                              format
                              title {
                                userPreferred
                                romaji
                                english
                              }
                              coverImage {
                                large
                              }
                              averageScore
                              status
                              genres
                              description(asHtml: false)
                            }
                          }
                        }
                        """.trimIndent()
                    }
                }
            }

            val payload = buildJsonObject {
                put("query", query)
            }
            val body = payload.toString().toRequestBody(jsonMime)
            val request = POST("https://graphql.anilist.co", body = body)
            val response = client.newCall(request).awaitSuccess()
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()

            val list = mutableListOf<TrackerRecommendation>()

            fun isValidRec(recMedia: kotlinx.serialization.json.JsonObject): Boolean {
                val recType = recMedia["type"]?.jsonPrimitive?.content
                val recFormat = recMedia["format"]?.jsonPrimitive?.content
                val isAnimeFormat = recFormat in listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
                return when (mediaType) {
                    MediaType.ANIME -> (recType == null || recType.equals("ANIME", ignoreCase = true)) &&
                        (recType?.equals("ANIME", ignoreCase = true) == true || isAnimeFormat)
                    MediaType.NOVEL -> (recType == null || recType.equals("MANGA", ignoreCase = true)) &&
                        recFormat?.equals("NOVEL", ignoreCase = true) == true
                    MediaType.MANGA -> (recType == null || recType.equals("MANGA", ignoreCase = true)) &&
                        !isAnimeFormat && recFormat?.equals("NOVEL", ignoreCase = true) != true
                }
            }

            if (data.containsKey("Media")) {
                val media = data["Media"]?.jsonObject
                val recs = media?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray
                recs?.forEach { node ->
                    val recMedia = node.jsonObject["mediaRecommendation"]?.jsonObject ?: return@forEach
                    if (!isValidRec(recMedia)) return@forEach

                    val titleObj = recMedia["title"]?.jsonObject
                    val title = titleObj?.get("userPreferred")?.jsonPrimitive?.content
                        ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                        ?: titleObj?.get("english")?.jsonPrimitive?.content
                        ?: return@forEach
                    val cover = recMedia["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                    val score = recMedia["averageScore"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { it / 10.0 }
                    val status = recMedia["status"]?.jsonPrimitive?.content
                    val genres = recMedia["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    val desc = recMedia["description"]?.jsonPrimitive?.content
                    val id = recMedia["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                    list.add(
                        TrackerRecommendation(
                            id = id,
                            title = title,
                            coverUrl = cover,
                            score = score,
                            status = status,
                            genres = genres,
                            sourceName = "AniList",
                            synopsis = desc,
                        ),
                    )
                }
            } else if (data.containsKey("Page")) {
                val page = data["Page"]?.jsonObject
                val mediaList = page?.get("media")?.jsonArray
                mediaList?.forEach { node ->
                    val recMedia = node.jsonObject
                    if (!isValidRec(recMedia)) return@forEach

                    val titleObj = recMedia["title"]?.jsonObject
                    val title = titleObj?.get("userPreferred")?.jsonPrimitive?.content
                        ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                        ?: titleObj?.get("english")?.jsonPrimitive?.content
                        ?: return@forEach
                    val cover = recMedia["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                    val score = recMedia["averageScore"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { it / 10.0 }
                    val status = recMedia["status"]?.jsonPrimitive?.content
                    val genres = recMedia["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    val desc = recMedia["description"]?.jsonPrimitive?.content
                    val id = recMedia["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                    list.add(
                        TrackerRecommendation(
                            id = id,
                            title = title,
                            coverUrl = cover,
                            score = score,
                            status = status,
                            genres = genres,
                            sourceName = "AniList",
                            synopsis = desc,
                        ),
                    )
                }
            }

            list
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to query AniList recommendations for $mediaType" }
            emptyList()
        }
    }

    private suspend fun tryFetchMangaUpdatesRecommendations(): List<TrackerRecommendation> {
        return try {
            val request = GET("https://api.mangaupdates.com/v1/series/top?limit=15")
            val response = client.newCall(request).awaitSuccess()
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            val list = mutableListOf<TrackerRecommendation>()
            results.forEach { item ->
                val record = item.jsonObject["record"]?.jsonObject ?: return@forEach
                val id = record["series_id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val title = record["title"]?.jsonPrimitive?.content ?: return@forEach
                val image = record["image"]?.jsonObject?.get("url")?.jsonObject?.get("original")?.jsonPrimitive?.content
                val score = record["bayesian_rating"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val status = record["status"]?.jsonPrimitive?.content
                val genres = record["genres"]?.jsonArray?.mapNotNull { it.jsonObject["genre"]?.jsonPrimitive?.content } ?: emptyList()
                val desc = record["description"]?.jsonPrimitive?.content

                list.add(
                    TrackerRecommendation(
                        id = id,
                        title = title,
                        coverUrl = image,
                        score = score,
                        status = status,
                        genres = genres,
                        sourceName = "MangaUpdates",
                        synopsis = desc,
                    ),
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to query MangaUpdates recommendations" }
            emptyList()
        }
    }
}
