package eu.kanade.tachiyomi.ui.track.anilist

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem

// KMK -->
/**
 * AniList Search screen with dynamic filter builder (Anymex style) and paginated results.
 */
@Composable
fun AnilistSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<ALSearchItem>,
    isSearching: Boolean,
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    selectedSort: String,
    onSortSelected: (String) -> Unit,
    activeMediaType: MediaType,
    onItemClick: (ALSearchItem) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: ((ALSearchItem) -> Unit)? = null,
) {
    val sortOptions = listOf(
        "TRENDING_DESC" to "Trending",
        "POPULARITY_DESC" to "Popular",
        "SCORE_DESC" to "Score",
        "START_DATE_DESC" to "Newest",
        "FAVOURITES_DESC" to "Favourites",
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(text = "Search AniList...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        onSearch()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear",
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        // Sort Options Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sortOptions.forEach { (sortKey, label) ->
                val isSelected = selectedSort == sortKey
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSortSelected(sortKey)
                        onSearch()
                    },
                    label = { Text(label) },
                )
            }
        }

        // Genre Filter Chips Row
        if (genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ElevatedAssistChip(
                    onClick = {
                        onGenreSelected(null)
                        onSearch()
                    },
                    label = { Text("All Genres") },
                    leadingIcon = if (selectedGenre == null) {
                        { Icon(Icons.Outlined.FilterList, contentDescription = null) }
                    } else null,
                )

                genres.forEach { genre ->
                    val isSelected = selectedGenre == genre
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onGenreSelected(if (isSelected) null else genre)
                            onSearch()
                        },
                        label = { Text(genre) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isSearching) {
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

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No results found. Try a different search.",
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
            items(
                count = results.size,
                key = { "anilist-search-${results[it].id}-$it" },
            ) { index ->
                val item = results[index]
                AnilistMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = onItemLongClick?.let { { it(item) } },
                    width = 110,
                )
            }
        }
    }
}
// KMK <--
