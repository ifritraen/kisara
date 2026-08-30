package tachiyomi.domain.scoring.model

import kotlinx.serialization.Serializable
import kotlin.math.round

// KMK -->
@Serializable
data class GranularScoreCriterion(
    val id: String,
    val name: String,
    val score: Double = 0.0,
    val weight: Double = 10.0,
    val isExcluded: Boolean = false,
)

@Serializable
data class GranularTemplateCriterion(
    val id: String,
    val name: String,
    val weight: Double = 10.0,
)

@Serializable
data class GranularScoreTemplate(
    val id: Long = 0L,
    val name: String,
    val mediaType: String = "ALL",
    val criteria: List<GranularTemplateCriterion>,
    val isDefault: Boolean = false,
)

data class GranularScoreEntry(
    val mangaId: Long,
    val templateName: String,
    val criteria: List<GranularScoreCriterion>,
    val totalScore: Double,
    val scale10Score: Double,
    val ignoreUnrated: Boolean = true,
    val autoSyncTracker: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

object GranularScoreCalculator {
    /**
     * Calculates totalScore (0..100) and scale10Score (0..10.0) based on criteria weights and scores.
     * Follows the exact formula from AniScore.xlsx.
     */
    fun calculate(
        criteria: List<GranularScoreCriterion>,
        ignoreUnrated: Boolean,
    ): Pair<Double, Double> {
        val activeCriteria = criteria.filter { !it.isExcluded }
        if (activeCriteria.isEmpty()) return 0.0 to 0.0

        val scoredCriteria = if (ignoreUnrated) {
            activeCriteria.filter { it.score > 0.0 }
        } else {
            activeCriteria
        }

        if (scoredCriteria.isEmpty()) return 0.0 to 0.0

        val totalEarned = scoredCriteria.sumOf { (it.score / 100.0) * it.weight }
        val totalWeight = scoredCriteria.sumOf { it.weight }

        if (totalWeight <= 0.0) return 0.0 to 0.0

        val normalizedTotalScore = (totalEarned / totalWeight) * 100.0
        val clampedTotal = normalizedTotalScore.coerceIn(0.0, 100.0)
        val roundedTotal = round(clampedTotal * 10.0) / 10.0
        val scale10 = round((roundedTotal / 10.0) * 10.0) / 10.0

        return roundedTotal to scale10.coerceIn(0.0, 10.0)
    }
}

object GranularScorePresets {
    val ADVANCED = GranularScoreTemplate(
        id = -1L,
        name = "Advanced",
        mediaType = "ALL",
        criteria = listOf(
            GranularTemplateCriterion("char_design", "Char Design", 4.0),
            GranularTemplateCriterion("char_writing", "Char Writing", 4.0),
            GranularTemplateCriterion("char_involve", "Char Involve", 4.0),
            GranularTemplateCriterion("char_dev", "Char Dev", 4.0),
            GranularTemplateCriterion("flow", "Flow", 5.0),
            GranularTemplateCriterion("resonance", "Resonance", 12.0),
            GranularTemplateCriterion("depth", "Depth", 10.0),
            GranularTemplateCriterion("animation", "Animation", 6.0),
            GranularTemplateCriterion("music", "Music", 6.0),
            GranularTemplateCriterion("story_writing", "Story writing", 10.0),
            GranularTemplateCriterion("world_building", "World Building", 5.0),
            GranularTemplateCriterion("closure", "Closure", 5.0),
            GranularTemplateCriterion("cinematography", "Cinematography", 5.0),
            GranularTemplateCriterion("dir_execution", "Dir execution", 8.0),
            GranularTemplateCriterion("dir_creativity", "Dir creativity", 4.0),
            GranularTemplateCriterion("theme_exec", "Theme Exec", 8.0),
        ),
        isDefault = true,
    )

    val EQUAL_ADV = GranularScoreTemplate(
        id = -2L,
        name = "EqualAdv",
        mediaType = "ALL",
        criteria = listOf(
            GranularTemplateCriterion("story", "Story", 10.0),
            GranularTemplateCriterion("character", "Character", 10.0),
            GranularTemplateCriterion("depth", "Depth", 10.0),
            GranularTemplateCriterion("theme_exec", "Theme Exec", 10.0),
            GranularTemplateCriterion("direction", "Direction", 10.0),
            GranularTemplateCriterion("cinemato_design", "Cinemato + design", 10.0),
            GranularTemplateCriterion("animation_music", "Animation + Music", 10.0),
            GranularTemplateCriterion("flow_ending", "Flow & Ending", 10.0),
            GranularTemplateCriterion("resonance", "Resonance", 10.0),
            GranularTemplateCriterion("extra_personal", "Extra + Personal", 10.0),
        ),
        isDefault = false,
    )

    val SIMPLE = GranularScoreTemplate(
        id = -3L,
        name = "Simple",
        mediaType = "ALL",
        criteria = listOf(
            GranularTemplateCriterion("character", "Character", 10.0),
            GranularTemplateCriterion("story", "Story", 20.0),
            GranularTemplateCriterion("flow", "Flow", 10.0),
            GranularTemplateCriterion("pleasing", "Pleasing", 20.0),
            GranularTemplateCriterion("direction", "Direction", 10.0),
            GranularTemplateCriterion("philosophy", "Philosophy", 10.0),
            GranularTemplateCriterion("animation", "Animation", 10.0),
            GranularTemplateCriterion("scene_music", "Scene & Music", 10.0),
            GranularTemplateCriterion("favorite", "Favorite", 10.0),
        ),
        isDefault = false,
    )

    val ALL_PRESETS = listOf(ADVANCED, EQUAL_ADV, SIMPLE)
}
// KMK <--
