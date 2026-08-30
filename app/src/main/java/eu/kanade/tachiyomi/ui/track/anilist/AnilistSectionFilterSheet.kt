package eu.kanade.tachiyomi.ui.track.anilist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// KMK -->
data class AnilistSectionConfig(
    val key: String,
    val title: String,
)

object AnilistLandingSections {
    val ALL_SECTIONS = listOf(
        AnilistSectionConfig("continue", "Continue (According to tracker)"),
        AnilistSectionConfig("recent", "Recent"),
        AnilistSectionConfig("trending", "Trending"),
        AnilistSectionConfig("top_rated", "Top (High scored)"),
        AnilistSectionConfig("popular", "Popular"),
        AnilistSectionConfig("recently_completed", "Recently completed"),
        AnilistSectionConfig("upcoming", "Upcoming"),
        AnilistSectionConfig("prev_top_rated", "Top (High scored) in previous seasons"),
        AnilistSectionConfig("prev_popular", "Most Popular in previous seasons"),
        AnilistSectionConfig("recommended", "Recommended for you"),
        AnilistSectionConfig("community_recommendation", "Community recommendation"),
    )
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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
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
                Text(
                    text = "Landing Sections (${if (enabledSections.isEmpty()) AnilistLandingSections.ALL_SECTIONS.size else enabledSections.size}/${AnilistLandingSections.ALL_SECTIONS.size})",
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
                items(AnilistLandingSections.ALL_SECTIONS, key = { it.key }) { config ->
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
// KMK <--
