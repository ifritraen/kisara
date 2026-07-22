package eu.kanade.tachiyomi.ui.home.favorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.data.favorite.FavoriteManager
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun favoritesTab(): TabContent {
    return TabContent(
        titleRes = tachiyomi.i18n.kmk.KMR.strings.action_favorites,
        content = { contentPadding, _ ->
            FavoritesTabContent(modifier = Modifier.padding(contentPadding))
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesTabContent(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.currentOrThrow
    val favoriteManager = remember { Injekt.get<FavoriteManager>() }

    val authorsList = remember { mutableStateListOf(*favoriteManager.getAuthors().toTypedArray()) }
    val artistsList = remember { mutableStateListOf(*favoriteManager.getArtists().toTypedArray()) }
    val tagsList = remember { mutableStateListOf(*favoriteManager.getTags().toTypedArray()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Section 1: Favorite Authors & Artists
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Favorite Authors & Artists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            val combined = (authorsList + artistsList).distinct()
            if (combined.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No favorite authors or artists added yet.\nLong-press an author or artist on any manga info page to add them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    combined.forEach { name ->
                        InputChip(
                            selected = false,
                            onClick = {
                                navigator.push(GlobalSearchScreen(searchQuery = name))
                            },
                            label = { Text(name) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            if (authorsList.contains(name)) {
                                                favoriteManager.toggleFavoriteAuthor(name)
                                                authorsList.remove(name)
                                            }
                                            if (artistsList.contains(name)) {
                                                favoriteManager.toggleFavoriteArtist(name)
                                                artistsList.remove(name)
                                            }
                                        },
                                )
                            },
                        )
                    }
                }
            }
        }

        // Section 2: Favorite Tags
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Favorite Tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (tagsList.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No favorite tags added yet.\nLong-press any tag chip on a manga info page to add it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tagsList.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {
                                navigator.push(GlobalSearchScreen(searchQuery = tag))
                            },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            favoriteManager.toggleFavoriteTag(tag)
                                            tagsList.remove(tag)
                                        },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
