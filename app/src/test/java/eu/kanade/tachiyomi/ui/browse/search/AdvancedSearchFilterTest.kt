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
            isIncludeAndMode = true,
            tagStates = mapOf(
                "Action" to TagSelectionState.INCLUDED,
                "Supernatural" to TagSelectionState.INCLUDED,
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
    fun `test compileQuery with OR mode`() {
        val state = AdvancedSearchState(
            query = "Solo",
            isIncludeAndMode = false,
            tagStates = mapOf(
                "Action" to TagSelectionState.INCLUDED,
                "Fantasy" to TagSelectionState.INCLUDED,
            ),
        )

        val compiled = state.compileQuery()
        assertEquals("Solo tag:\"Action\" OR tag:\"Fantasy\"", compiled)
    }
}
