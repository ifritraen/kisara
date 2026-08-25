package eu.kanade.domain.entries.interactor

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
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
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class GetEntrySimilarTitles(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
) {
    private val client: OkHttpClient by lazy { networkHelper.client }
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, List<SuggestionItem>>()

    suspend fun fetchAnimeSuggestions(
        anime: Anime,
        remoteAniListId: Long? = null,
    ): List<SuggestionItem> = withIOContext {
        val cacheKey = "anime_${anime.id}"
        cache[cacheKey]?.let { return@withIOContext it }

        val cleanTitle = cleanTitleForSearch(anime.title)

        // 1. Try AniList Anime Recommendations & Search
        var results = tryFetchAniListAnime(remoteAniListId, cleanTitle)

        // 2. If empty, try Source Tag / Keyword Search Fallback
        if (results.isEmpty()) {
            results = tryFetchAnimeSourceFallback(anime, cleanTitle)
        }

        if (results.isNotEmpty()) {
            cache[cacheKey] = results
        }
        results
    }

    suspend fun fetchNovelSuggestions(
        novel: Novel,
        remoteAniListId: Long? = null,
    ): List<SuggestionItem> = withIOContext {
        val cacheKey = "novel_${novel.id}"
        cache[cacheKey]?.let { return@withIOContext it }

        val cleanTitle = cleanTitleForSearch(novel.title)

        // 1. Try AniList Novel/Manga Recommendations & Search
        var results = tryFetchAniListNovel(remoteAniListId, cleanTitle)

        // 2. Try MangaUpdates Novel Search & Related Series
        if (results.isEmpty() && cleanTitle.isNotBlank()) {
            results = tryFetchMangaUpdatesNovel(cleanTitle)
        }

        // 3. If still empty, try Source Tag / Keyword Search Fallback
        if (results.isEmpty()) {
            results = tryFetchNovelSourceFallback(novel, cleanTitle)
        }

        if (results.isNotEmpty()) {
            cache[cacheKey] = results
        }
        results
    }

    fun clearCache() {
        cache.clear()
    }

    private suspend fun tryFetchAniListAnime(
        remoteId: Long?,
        cleanTitle: String,
    ): List<SuggestionItem> {
        if (remoteId == null && cleanTitle.isBlank()) return emptyList()

        return try {
            val query = if (remoteId != null && remoteId > 0) {
                """
                query {
                  Media(id: $remoteId, type: ANIME) {
                    recommendations(sort: RATING_DESC, perPage: 15) {
                      nodes {
                        mediaRecommendation {
                          id
                          title {
                            userPreferred
                            romaji
                            english
                          }
                          coverImage {
                            large
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            } else {
                val escaped = cleanTitle.replace("\"", "\\\"")
                """
                query {
                  Media(search: "$escaped", type: ANIME) {
                    id
                    recommendations(sort: RATING_DESC, perPage: 15) {
                      nodes {
                        mediaRecommendation {
                          id
                          title {
                            userPreferred
                            romaji
                            english
                          }
                          coverImage {
                            large
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            }

            var list = parseAniListRecommendations(query)
            if (list.isEmpty() && cleanTitle.isNotBlank()) {
                // Secondary AniList Page search fallback
                val searchEscaped = cleanTitle.take(30).replace("\"", "\\\"")
                val pageQuery = """
                query {
                  Page(perPage: 12) {
                    media(search: "$searchEscaped", type: ANIME, sort: POPULARITY_DESC) {
                      id
                      title {
                        userPreferred
                        romaji
                        english
                      }
                      coverImage {
                        large
                      }
                    }
                  }
                }
                """.trimIndent()
                list = parseAniListPageResponse(pageQuery)
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to fetch AniList anime recommendations for '$cleanTitle'" }
            emptyList()
        }
    }

    private suspend fun tryFetchAniListNovel(
        remoteId: Long?,
        cleanTitle: String,
    ): List<SuggestionItem> {
        if (remoteId == null && cleanTitle.isBlank()) return emptyList()

        return try {
            val query = if (remoteId != null && remoteId > 0) {
                """
                query {
                  Media(id: $remoteId, type: MANGA) {
                    recommendations(sort: RATING_DESC, perPage: 15) {
                      nodes {
                        mediaRecommendation {
                          id
                          title {
                            userPreferred
                            romaji
                            english
                          }
                          coverImage {
                            large
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            } else {
                val escaped = cleanTitle.replace("\"", "\\\"")
                """
                query {
                  Media(search: "$escaped", type: MANGA) {
                    id
                    recommendations(sort: RATING_DESC, perPage: 15) {
                      nodes {
                        mediaRecommendation {
                          id
                          title {
                            userPreferred
                            romaji
                            english
                          }
                          coverImage {
                            large
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            }

            var list = parseAniListRecommendations(query)
            if (list.isEmpty() && cleanTitle.isNotBlank()) {
                // Secondary AniList Page search fallback
                val searchEscaped = cleanTitle.take(30).replace("\"", "\\\"")
                val pageQuery = """
                query {
                  Page(perPage: 12) {
                    media(search: "$searchEscaped", type: MANGA, sort: POPULARITY_DESC) {
                      id
                      title {
                        userPreferred
                        romaji
                        english
                      }
                      coverImage {
                        large
                      }
                    }
                  }
                }
                """.trimIndent()
                list = parseAniListPageResponse(pageQuery)
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to fetch AniList novel recommendations for '$cleanTitle'" }
            emptyList()
        }
    }

    private suspend fun parseAniListRecommendations(graphqlQuery: String): List<SuggestionItem> {
        return try {
            val payload = buildJsonObject {
                put("query", graphqlQuery)
            }
            val body = payload.toString().toRequestBody(jsonMime)
            val request = POST("https://graphql.anilist.co", body = body)
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }
            val responseBody = response.body.string()
            val root = json.parseToJsonElement(responseBody).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()
            val media = data["Media"]?.jsonObject ?: return emptyList()
            val nodes = media["recommendations"]?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()

            val list = mutableListOf<SuggestionItem>()
            nodes.forEach { node ->
                val rec = node.jsonObject["mediaRecommendation"]?.jsonObject ?: return@forEach
                val titleObj = rec["title"]?.jsonObject
                val title = titleObj?.get("userPreferred")?.jsonPrimitive?.content
                    ?: titleObj?.get("english")?.jsonPrimitive?.content
                    ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                    ?: return@forEach
                val cover = rec["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                val id = rec["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                list.add(
                    SuggestionItem(
                        id = id,
                        title = title,
                        thumbnailUrl = cover,
                        providerId = "anilist_$id",
                    ),
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AniList recommendations parse skipped" }
            emptyList()
        }
    }

    private suspend fun parseAniListPageResponse(graphqlQuery: String): List<SuggestionItem> {
        return try {
            val payload = buildJsonObject {
                put("query", graphqlQuery)
            }
            val body = payload.toString().toRequestBody(jsonMime)
            val request = POST("https://graphql.anilist.co", body = body)
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }
            val responseBody = response.body.string()
            val root = json.parseToJsonElement(responseBody).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()
            val page = data["Page"]?.jsonObject ?: return emptyList()
            val mediaList = page["media"]?.jsonArray ?: return emptyList()

            val list = mutableListOf<SuggestionItem>()
            mediaList.forEach { node ->
                val mediaObj = node.jsonObject
                val titleObj = mediaObj["title"]?.jsonObject
                val title = titleObj?.get("userPreferred")?.jsonPrimitive?.content
                    ?: titleObj?.get("english")?.jsonPrimitive?.content
                    ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                    ?: return@forEach
                val cover = mediaObj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                val id = mediaObj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                list.add(
                    SuggestionItem(
                        id = id,
                        title = title,
                        thumbnailUrl = cover,
                        providerId = "anilist_$id",
                    ),
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AniList page parse skipped" }
            emptyList()
        }
    }

    private suspend fun tryFetchMangaUpdatesNovel(cleanTitle: String): List<SuggestionItem> {
        return try {
            val payload = buildJsonObject {
                put("search", cleanTitle)
                put("stype", "title")
            }
            val body = payload.toString().toRequestBody(jsonMime)
            val searchReq = POST("https://api.mangaupdates.com/v1/series/search", body = body)
            val searchRes = client.newCall(searchReq).execute()
            if (!searchRes.isSuccessful) {
                searchRes.close()
                return emptyList()
            }
            val root = json.parseToJsonElement(searchRes.body.string()).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            val list = mutableListOf<SuggestionItem>()
            results.take(12).forEach { item ->
                val record = item.jsonObject["record"]?.jsonObject ?: return@forEach
                val title = record["title"]?.jsonPrimitive?.content ?: return@forEach
                val seriesId = record["series_id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val cover = record["image"]?.jsonObject?.get("url")?.jsonObject?.get("original")?.jsonPrimitive?.content

                list.add(
                    SuggestionItem(
                        id = seriesId,
                        title = title,
                        thumbnailUrl = cover,
                        providerId = "mu_$seriesId",
                    ),
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to fetch MangaUpdates novel recommendations for '$cleanTitle'" }
            emptyList()
        }
    }

    private suspend fun tryFetchAnimeSourceFallback(anime: Anime, cleanTitle: String): List<SuggestionItem> {
        return try {
            val source = animeSourceManager.get(anime.source) as? AnimeCatalogueSource ?: return emptyList()
            val query = anime.genre?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: cleanTitle.split(" ").take(2).joinToString(" ")

            if (query.isBlank()) return emptyList()

            val searchResult = source.getSearchAnime(1, query, AnimeFilterList())
            searchResult.animes
                .filterNot { it.title.equals(anime.title, ignoreCase = true) }
                .take(10)
                .map { sAnime ->
                    SuggestionItem(
                        id = sAnime.url.hashCode().toLong(),
                        title = sAnime.title,
                        thumbnailUrl = sAnime.thumbnail_url,
                        providerId = "source_${anime.source}",
                    )
                }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Anime source fallback failed" }
            emptyList()
        }
    }

    private suspend fun tryFetchNovelSourceFallback(novel: Novel, cleanTitle: String): List<SuggestionItem> {
        return try {
            val source = novelSourceManager.get(novel.source) as? NovelCatalogueSource ?: return emptyList()
            val query = novel.genre?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: cleanTitle.split(" ").take(2).joinToString(" ")

            if (query.isBlank()) return emptyList()

            val searchResult = source.getSearchNovels(1, query, NovelFilterList())
            searchResult.novels
                .filterNot { it.title.equals(novel.title, ignoreCase = true) }
                .take(10)
                .map { sNovel ->
                    SuggestionItem(
                        id = sNovel.url.hashCode().toLong(),
                        title = sNovel.title,
                        thumbnailUrl = sNovel.thumbnail_url,
                        providerId = "source_${novel.source}",
                    )
                }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Novel source fallback failed" }
            emptyList()
        }
    }

    private fun cleanTitleForSearch(title: String): String {
        return title
            .replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), "") // Remove bracketed tags like [Novel], (TV), {LN}
            .replace(Regex("(?i)\\b(season|part|cour|vol\\.?|volume|ch\\.?|chapter|novel|light novel|web novel)\\b.*"), "")
            .replace(Regex("[:\\-–—_/~|#]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

