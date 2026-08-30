package eu.kanade.tachiyomi.ui.schedule

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.anischedule.AniScheduleApi
import eu.kanade.tachiyomi.data.anischedule.AniScheduleFeedItem
import eu.kanade.tachiyomi.data.anischedule.AniScheduleMedia
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AnimeFeedType(val label: String) {
    SCHEDULE("Airing Schedule"),
    SUB_FEED("Sub Feed"),
    DUB_FEED("Dub Feed"),
    HENTAI_FEED("18+ Feed"),
}

@Immutable
data class AiringScheduleCardData(
    val media: AniScheduleMedia,
    val nextEpisode: Int?,
    val airingEpochSeconds: Long?,
    val dayOfWeek: DayOfWeek,
    val countdownText: String,
)

class AnimeScheduleScreenModel(
    private val api: AniScheduleApi = Injekt.get(),
    private val uiPreferences: eu.kanade.domain.ui.UiPreferences = Injekt.get(),
) : StateScreenModel<AnimeScheduleScreenModel.State>(
    State(
        selectedDay = LocalDate.now().dayOfWeek,
    ),
) {

    init {
        val isAdult = !uiPreferences.trackTabHideAdult().get()
        mutableState.update { it.copy(isAdultEnabled = isAdult) }

        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val schedule = api.getSubSchedule(forceRefresh)
                val subFeed = api.getSubEpisodeFeed(forceRefresh)
                val dubFeed = api.getDubEpisodeFeed(forceRefresh)
                val hentaiFeed = if (state.value.isAdultEnabled) {
                    api.getHentaiEpisodeFeed(forceRefresh)
                } else {
                    emptyList()
                }

                val mediaMap = schedule.associateBy { it.id }

                mutableState.update { current ->
                    current.copy(
                        schedule = schedule.toImmutableList(),
                        subFeed = subFeed.toImmutableList(),
                        dubFeed = dubFeed.toImmutableList(),
                        hentaiFeed = hentaiFeed.toImmutableList(),
                        mediaMap = mediaMap,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFeedType(type: AnimeFeedType) {
        mutableState.update { it.copy(selectedFeedType = type) }
    }

    fun setSelectedDay(day: DayOfWeek) {
        mutableState.update { it.copy(selectedDay = day) }
    }

    fun setSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    data class State(
        val selectedFeedType: AnimeFeedType = AnimeFeedType.SCHEDULE,
        val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
        val searchQuery: String = "",
        val schedule: ImmutableList<AniScheduleMedia> = emptyList<AniScheduleMedia>().toImmutableList(),
        val subFeed: ImmutableList<AniScheduleFeedItem> = emptyList<AniScheduleFeedItem>().toImmutableList(),
        val dubFeed: ImmutableList<AniScheduleFeedItem> = emptyList<AniScheduleFeedItem>().toImmutableList(),
        val hentaiFeed: ImmutableList<AniScheduleFeedItem> = emptyList<AniScheduleFeedItem>().toImmutableList(),
        val mediaMap: Map<Long, AniScheduleMedia> = emptyMap(),
        val isLoading: Boolean = false,
        val isAdultEnabled: Boolean = false,
    ) {
        fun getScheduleForSelectedDay(): List<AiringScheduleCardData> {
            val nowSeconds = System.currentTimeMillis() / 1000L
            val zoneId = ZoneId.systemDefault()

            return schedule.mapNotNull { media ->
                if (!isAdultEnabled && media.isAdult) return@mapNotNull null

                val nextNode = media.airingSchedule?.nodes
                    ?.filter { it.airingAt >= nowSeconds - 86400 } // Keep recent within 24h
                    ?.minByOrNull { it.airingAt }

                val epoch = nextNode?.airingAt ?: return@mapNotNull null
                val instant = Instant.ofEpochSecond(epoch)
                val zonedDateTime = instant.atZone(zoneId)
                val day = zonedDateTime.dayOfWeek

                val diff = epoch - nowSeconds
                val countdown = if (diff <= 0) {
                    "Aired Today"
                } else {
                    val days = diff / 86400
                    val hours = (diff % 86400) / 3600
                    val mins = (diff % 3600) / 60
                    if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                }

                AiringScheduleCardData(
                    media = media,
                    nextEpisode = nextNode.episode,
                    airingEpochSeconds = epoch,
                    dayOfWeek = day,
                    countdownText = countdown,
                )
            }.filter {
                it.dayOfWeek == selectedDay &&
                    (searchQuery.isBlank() || it.media.title?.getBestTitle()?.contains(searchQuery, ignoreCase = true) == true)
            }.sortedBy { it.airingEpochSeconds }
        }
    }
}
