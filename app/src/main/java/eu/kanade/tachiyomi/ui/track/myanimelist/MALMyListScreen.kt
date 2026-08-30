package eu.kanade.tachiyomi.ui.track.myanimelist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMediaItem

// KMK -->
/**
 * MyAnimeList My List subtab with status filter chips and grid.
 */
@Composable
fun MALMyListScreen(
    isLoggedIn: Boolean,
    entries: List<MALMediaItem>,
    isLoading: Boolean,
    selectedStatus: String?,
    activeMediaType: MediaType,
    onStatusSelected: (String?) -> Unit,
    onLoginClick: () -> Unit,
    onItemClick: (MALMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoggedIn) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Login to MyAnimeList to view your list",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sync your reading and watching progress across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onLoginClick) {
                    Text(text = "Connect MyAnimeList Account")
                }
            }
        }
        return
    }

    val isAnime = activeMediaType == MediaType.ANIME
    val statusChips = if (isAnime) {
        listOf(
            null to "All",
            "watching" to "Watching",
            "completed" to "Completed",
            "on_hold" to "On Hold",
            "dropped" to "Dropped",
            "plan_to_watch" to "Plan to Watch",
        )
    } else {
        listOf(
            null to "All",
            "reading" to "Reading",
            "completed" to "Completed",
            "on_hold" to "On Hold",
            "dropped" to "Dropped",
            "plan_to_read" to "Plan to Read",
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Status Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            statusChips.forEach { (statusKey, label) ->
                val isSelected = selectedStatus == statusKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusSelected(statusKey) },
                    label = { Text(text = label) },
                )
            }
        }

        if (isLoading && entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No entries found in this status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(entries, key = { it.id }) { item ->
                MALMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    width = 110,
                )
            }
        }
    }
}
// KMK <--
