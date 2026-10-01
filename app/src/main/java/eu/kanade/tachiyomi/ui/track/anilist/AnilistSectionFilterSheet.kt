package eu.kanade.tachiyomi.ui.track.anilist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import tachiyomi.presentation.core.util.collectAsState
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
data class AnilistSectionConfig(
    val key: String,
    val title: String,
)

object AnilistLandingSections {
    fun getSections(
        season: String = "",
        seasonYear: Int = 0,
        prevSeasonCount: Int = 3,
    ): List<AnilistSectionConfig> {
        val (calcSeason, calcYear) = if (season.isNotBlank() && seasonYear > 0) {
            season to seasonYear
        } else {
            getCurrentSeasonAndYear()
        }
        val seasonTitle = "${calcSeason.lowercase().replaceFirstChar { it.uppercase() }} $calcYear"

        return listOf(
            AnilistSectionConfig("continue", "Continue (According to tracker)"),
            AnilistSectionConfig("trending", "Trending"),
            AnilistSectionConfig("top_rated", "Highest Rated $seasonTitle"),
            AnilistSectionConfig("popular", "Popular (All time)"),
            AnilistSectionConfig("popular_season", "Popular $seasonTitle"),
            AnilistSectionConfig("prev_top_rated", "Top in last $prevSeasonCount seasons"),
            AnilistSectionConfig("prev_popular", "Popular in last $prevSeasonCount seasons"),
            AnilistSectionConfig("upcoming", "Upcoming"),
            AnilistSectionConfig("recently_completed", "Recently completed"),
            AnilistSectionConfig("recent", "Recent"),
            AnilistSectionConfig("recommended", "Recommended for you"),
            AnilistSectionConfig("community_recommendation", "Community recommendation"),
        )
    }

    private fun getCurrentSeasonAndYear(): Pair<String, Int> {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) // 0-11
        val season = when (month) {
            in 0..2 -> "WINTER"
            in 3..5 -> "SPRING"
            in 6..8 -> "SUMMER"
            else -> "FALL"
        }
        return season to year
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnilistSectionFilterSheet(
    enabledSections: Set<String>,
    onToggleSection: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val prevSeasonCount by uiPreferences.trackTabPreviousSeasonsCount().collectAsState()
    val allSections = remember(prevSeasonCount) {
        AnilistLandingSections.getSections(prevSeasonCount = prevSeasonCount)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.Transparent,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            style = GlassDefaults.prominentStyle(),
            dialogSurface = true,
            isStandardSurface = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeCount = if (enabledSections.isEmpty()) allSections.size else enabledSections.filter { key -> allSections.any { it.key == key } }.size
                Text(
                    text = "Landing Sections ($activeCount/${allSections.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Row {
                    TextButton(onClick = onSelectAll) {
                        Text("All")
                    }
                    TextButton(onClick = onDeselectAll) {
                        Text("None")
                    }
                }
            }

            Text(
                text = "Select which sections to query and display to reduce rate limits and load times.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .padding(vertical = 4.dp),
            ) {
                items(allSections, key = { it.key }) { config ->
                    val isChecked = enabledSections.isEmpty() || config.key in enabledSections
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSection(config.key, !isChecked) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = config.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleSection(config.key, it) },
                        )
                    }
                }
            }
        }
    }
}
}
}
// KMK <--
