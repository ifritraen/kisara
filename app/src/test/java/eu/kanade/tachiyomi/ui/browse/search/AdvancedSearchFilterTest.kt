package eu.kanade.tachiyomi.ui.browse.search

import eu.kanade.tachiyomi.ui.browse.search.model.AdvancedSearchState
import eu.kanade.tachiyomi.ui.browse.search.model.TagSelectionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdvancedSearchFilterTest {

    @Test
    fun `test compileQuery with AND mode and tag inclusions exclusions`() {
        val state = AdvancedSearchState(
            query = "Demon Slayer",
            author = "Gotouge",
            artist = "Koyoharu",
            tagStates = mapOf(
                "Action" to TagSelectionState.MUST_HAVE,
                "Supernatural" to TagSelectionState.MUST_HAVE,
                "Ecchi" to TagSelectionState.EXCLUDED,
            ),
            demographic = "Shounen",
            status = "Completed",
            origin = "Manga",
        )

        val compiled = state.compileQuery()

        assertTrue(compiled.contains("Demon Slayer"))
        assertTrue(compiled.contains("author:\"Gotouge\""))
        assertTrue(compiled.contains("artist:\"Koyoharu\""))
        assertTrue(compiled.contains("tag:\"Action\""))
        assertTrue(compiled.contains("tag:\"Supernatural\""))
        assertTrue(compiled.contains("-tag:\"Ecchi\""))
        assertTrue(compiled.contains("tag:\"Shounen\""))
        assertTrue(compiled.contains("status:\"Completed\""))
        assertTrue(compiled.contains("origin:\"Manga\""))
    }

    @Test
    fun `test compileQuery with optional OR tags`() {
        val state = AdvancedSearchState(
            query = "Solo",
            tagStates = mapOf(
                "Action" to TagSelectionState.OPTIONAL,
                "Fantasy" to TagSelectionState.OPTIONAL,
            ),
        )

        val compiled = state.compileQuery()
        assertEquals("Solo (tag:\"Action\" OR tag:\"Fantasy\")", compiled)
    }

    @Test
    fun `test compileQuery with combined must-have AND and optional OR tags`() {
        val state = AdvancedSearchState(
            query = "Leveling",
            tagStates = mapOf(
                "Action" to TagSelectionState.MUST_HAVE,
                "Fantasy" to TagSelectionState.MUST_HAVE,
                "Isekai" to TagSelectionState.OPTIONAL,
                "Magic" to TagSelectionState.OPTIONAL,
                "Horror" to TagSelectionState.EXCLUDED,
            ),
        )

        val compiled = state.compileQuery()
        assertTrue(compiled.contains("Leveling"))
        assertTrue(compiled.contains("tag:\"Action\""))
        assertTrue(compiled.contains("tag:\"Fantasy\""))
        assertTrue(compiled.contains("(tag:\"Isekai\" OR tag:\"Magic\")"))
        assertTrue(compiled.contains("-tag:\"Horror\""))
    }

    @Test
    fun `test SearchQueryTagMatcher extracts optional tags from query`() {
        val query = "Leveling tag:\"Action\" (tag:\"Isekai\" OR tag:\"Magic\") -tag:\"Horror\""
        val optional = eu.kanade.tachiyomi.ui.browse.search.model.SearchQueryTagMatcher.extractOptionalTags(query)
        assertEquals(listOf("Isekai", "Magic"), optional)
    }
}
