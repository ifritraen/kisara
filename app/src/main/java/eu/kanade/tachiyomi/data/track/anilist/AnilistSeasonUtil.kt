package eu.kanade.tachiyomi.data.track.anilist

import java.time.LocalDate

// KMK -->
/**
 * Utility for calculating AniList media seasons.
 * ponytail: reusable season math for batched queries.
 */
object AnilistSeasonUtil {

    enum class Season(val anilistName: String) {
        WINTER("WINTER"),
        SPRING("SPRING"),
        SUMMER("SUMMER"),
        FALL("FALL"),
    }

    data class SeasonYear(val season: Season, val year: Int) {
        val displayName: String get() = "${season.anilistName.lowercase().replaceFirstChar { it.uppercase() }} $year"
    }

    fun getCurrentSeason(): SeasonYear {
        val now = LocalDate.now()
        val season = when (now.monthValue) {
            in 1..3 -> Season.WINTER
            in 4..6 -> Season.SPRING
            in 7..9 -> Season.SUMMER
            else -> Season.FALL
        }
        return SeasonYear(season, now.year)
    }

    fun getPreviousSeasons(count: Int): List<SeasonYear> {
        val current = getCurrentSeason()
        val seasons = Season.entries
        var seasonIdx = seasons.indexOf(current.season)
        var year = current.year

        return buildList {
            repeat(count) {
                seasonIdx--
                if (seasonIdx < 0) {
                    seasonIdx = seasons.lastIndex
                    year--
                }
                add(SeasonYear(seasons[seasonIdx], year))
            }
        }
    }
}
// KMK <--
