package eu.kanade.tachiyomi.data.anischedule

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniScheduleApi(
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    private val client = network.client
    private val mutex = Mutex()

    private var cachedSchedule: List<AniScheduleMedia>? = null
    private var lastScheduleFetch: Long = 0L

    private var cachedSubFeed: List<AniScheduleFeedItem>? = null
    private var lastSubFeedFetch: Long = 0L

    private var cachedDubFeed: List<AniScheduleFeedItem>? = null
    private var lastDubFeedFetch: Long = 0L

    private var cachedHentaiFeed: List<AniScheduleFeedItem>? = null
    private var lastHentaiFeedFetch: Long = 0L

    suspend fun getSubSchedule(forceRefresh: Boolean = false): List<AniScheduleMedia> = withIOContext {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedSchedule != null && now - lastScheduleFetch < CACHE_TTL) {
                return@withIOContext cachedSchedule!!
            }
            try {
                val response = client.newCall(GET(SUB_SCHEDULE_URL)).awaitSuccess()
                val list = response.parseAs<List<AniScheduleMedia>>(json)
                cachedSchedule = list
                lastScheduleFetch = now
                list
            } catch (e: Exception) {
                cachedSchedule ?: emptyList()
            }
        }
    }

    suspend fun getSubEpisodeFeed(forceRefresh: Boolean = false): List<AniScheduleFeedItem> = withIOContext {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedSubFeed != null && now - lastSubFeedFetch < CACHE_TTL) {
                return@withIOContext cachedSubFeed!!
            }
            try {
                val response = client.newCall(GET(SUB_EPISODE_FEED_URL)).awaitSuccess()
                val list = response.parseAs<List<AniScheduleFeedItem>>(json)
                cachedSubFeed = list
                lastSubFeedFetch = now
                list
            } catch (e: Exception) {
                cachedSubFeed ?: emptyList()
            }
        }
    }

    suspend fun getDubEpisodeFeed(forceRefresh: Boolean = false): List<AniScheduleFeedItem> = withIOContext {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedDubFeed != null && now - lastDubFeedFetch < CACHE_TTL) {
                return@withIOContext cachedDubFeed!!
            }
            try {
                val response = client.newCall(GET(DUB_EPISODE_FEED_URL)).awaitSuccess()
                val list = response.parseAs<List<AniScheduleFeedItem>>(json)
                cachedDubFeed = list
                lastDubFeedFetch = now
                list
            } catch (e: Exception) {
                cachedDubFeed ?: emptyList()
            }
        }
    }

    suspend fun getHentaiEpisodeFeed(forceRefresh: Boolean = false): List<AniScheduleFeedItem> = withIOContext {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedHentaiFeed != null && now - lastHentaiFeedFetch < CACHE_TTL) {
                return@withIOContext cachedHentaiFeed!!
            }
            try {
                val response = client.newCall(GET(HENTAI_EPISODE_FEED_URL)).awaitSuccess()
                val list = response.parseAs<List<AniScheduleFeedItem>>(json)
                cachedHentaiFeed = list
                lastHentaiFeedFetch = now
                list
            } catch (e: Exception) {
                cachedHentaiFeed ?: emptyList()
            }
        }
    }

    companion object {
        private const val BASE_RAW_URL = "https://raw.githubusercontent.com/RockinChaos/AniSchedule/master/readable"
        private const val SUB_SCHEDULE_URL = "$BASE_RAW_URL/sub-schedule-readable.json"
        private const val SUB_EPISODE_FEED_URL = "$BASE_RAW_URL/sub-episode-feed-readable.json"
        private const val DUB_EPISODE_FEED_URL = "$BASE_RAW_URL/dub-episode-feed-readable.json"
        private const val HENTAI_EPISODE_FEED_URL = "$BASE_RAW_URL/hentai-episode-feed-readable.json"

        private const val CACHE_TTL = 5 * 60 * 1000L // 5 minutes
    }
}
