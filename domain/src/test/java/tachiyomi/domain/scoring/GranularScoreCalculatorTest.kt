package tachiyomi.domain.scoring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.scoring.model.GranularScoreCalculator
import tachiyomi.domain.scoring.model.GranularScoreCriterion

class GranularScoreCalculatorTest {

    @Test
    fun `test exact calculation matching AniScore xlsx sample`() {
        // From s1The Daily Life of the Immorta sheet in AniScore.xlsx:
        val criteria = listOf(
            GranularScoreCriterion("char_design", "Char Design", score = 85.0, weight = 4.0),
            GranularScoreCriterion("char_writing", "Char Writing", score = 70.0, weight = 4.0),
            GranularScoreCriterion("char_involve", "Char Involve", score = 70.0, weight = 4.0),
            GranularScoreCriterion("char_dev", "Char Dev", score = 65.0, weight = 4.0),
            GranularScoreCriterion("flow", "Flow", score = 65.0, weight = 5.0),
            GranularScoreCriterion("resonance", "Resonance", score = 65.0, weight = 12.0),
            GranularScoreCriterion("depth", "Depth", score = 60.0, weight = 10.0),
            GranularScoreCriterion("animation", "Animation", score = 75.0, weight = 6.0),
            GranularScoreCriterion("music", "Music", score = 75.0, weight = 6.0),
            GranularScoreCriterion("story_writing", "Story writing", score = 65.0, weight = 10.0),
            GranularScoreCriterion("world_building", "World Building", score = 60.0, weight = 5.0),
            GranularScoreCriterion("closure", "Closure", score = 70.0, weight = 5.0),
            GranularScoreCriterion("cinematography", "Cinematography", score = 75.0, weight = 5.0),
            GranularScoreCriterion("dir_execution", "Dir execution", score = 80.0, weight = 8.0),
            GranularScoreCriterion("dir_creativity", "Dir creativity", score = 65.0, weight = 4.0),
            GranularScoreCriterion("theme_exec", "Theme Exec", score = 80.0, weight = 8.0),
        )

        val (total, scale10) = GranularScoreCalculator.calculate(criteria, ignoreUnrated = false)
        // Row 18: Total = 69.8, Scale10 = 7.0
        assertEquals(69.8, total, 0.05)
        assertEquals(7.0, scale10, 0.05)
    }

    @Test
    fun `test proportional unrated calculation`() {
        // Only 2 criteria scored out of 10-point equal weights
        val criteria = listOf(
            GranularScoreCriterion("story", "Story", score = 80.0, weight = 10.0),
            GranularScoreCriterion("character", "Character", score = 90.0, weight = 10.0),
            GranularScoreCriterion("art", "Art", score = 0.0, weight = 10.0),
        )

        // Proportional average of 80 and 90 -> 85.0%, Scale10 -> 8.5
        val (total, scale10) = GranularScoreCalculator.calculate(criteria, ignoreUnrated = true)
        assertEquals(85.0, total, 0.05)
        assertEquals(8.5, scale10, 0.05)
    }
}
