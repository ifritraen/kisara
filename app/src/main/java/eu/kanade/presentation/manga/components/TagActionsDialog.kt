package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.favorite.FavoriteManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TropeDictionary {
    private val definitions = mapOf(
        "isekai" to "Transported, summoned, or reincarnated into an alternate fantasy or game-like world.",
        "regression" to "Protagonist dies or fails, then awakens back in the past with their future memories intact.",
        "reincarnation" to "Reborn into a new body or different world after death, retaining past memories.",
        "villainess" to "Reincarnated or transmuted as the designated villain/antagonist of an otome game, novel, or story.",
        "dungeon" to "Setting with labyrinthine dungeons, monster raids, and hunter awakening systems.",
        "system" to "Features game-like leveling, status windows, quests, and rewards visible to the protagonist.",
        "tower" to "Ascending a mythical tower with distinct floor challenges, tests, and floor masters.",
        "cultivation" to "Eastern martial/spiritual development through Qi gathering, realm breakthroughs, and Dao mastery.",
        "martial arts" to "Murim / Wuxia combat traditions, internal energy, sect rivalries, and chivalric honor.",
        "otome" to "Female-targeted romance setting typically featuring noble academy intrigues and multiple suitors.",
        "time loop" to "Trapped repeating a specific span of time until solving the central crisis.",
        "slow romance" to "Deliberately paced, emotionally deep relationship development over extended storylines.",
        "enemies to lovers" to "Adversaries or rivals whose conflict evolves gradually into deep mutual affection.",
        "harem" to "Single protagonist pursued romantically by multiple potential partners.",
        "reverse harem" to "Single female protagonist surrounded by multiple handsome suitors.",
        "monster girls" to "Demi-human or mythical female companions with monster traits.",
        "academy" to "Educational institution setting specializing in magic, combat, or supernatural arts.",
        "post-apocalyptic" to "Civilization has fallen to catastrophe; survival against ruined environments and hostile threats.",
        "cyberpunk" to "High-tech, low-life future dominated by mega-corporations, cybernetic augments, and neon decay.",
    )

    fun getDefinition(tag: String): String? {
        val clean = tag.trim().lowercase()
        return definitions[clean] ?: definitions.entries.firstOrNull { clean.contains(it.key) }?.value
    }
}

@Composable
fun TagActionDialog(
    tag: String,
    onDismissRequest: () -> Unit,
    onSearchInSource: (String) -> Unit,
    onGlobalSearch: (String) -> Unit,
    onLibrarySearch: (String) -> Unit,
    onStartMultiSelect: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val favoriteManager = remember { Injekt.get<FavoriteManager>() }
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }

    val isFavorite = remember(tag) { favoriteManager.getFavoriteTags().contains(tag) }
    val isBlocked = remember(tag) { sourcePreferences.blockedTags().get().contains(tag) }
    val tropeDefinition = remember(tag) { TropeDictionary.getDefinition(tag) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = tag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tropeDefinition != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Trope / Tag Definition",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tropeDefinition,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TagActionItem(
                    icon = Icons.Outlined.Public,
                    label = stringResource(MR.strings.action_global_search),
                    description = "Search all extensions for \"$tag\"",
                    onClick = {
                        onDismissRequest()
                        onGlobalSearch(tag)
                    },
                )

                TagActionItem(
                    icon = Icons.Outlined.Search,
                    label = stringResource(MR.strings.action_search),
                    description = "Search inside this source",
                    onClick = {
                        onDismissRequest()
                        onSearchInSource(tag)
                    },
                )

                TagActionItem(
                    icon = Icons.Outlined.CollectionsBookmark,
                    label = "Find in Library",
                    description = "Filter saved manga with this tag",
                    onClick = {
                        onDismissRequest()
                        onLibrarySearch(tag)
                    },
                )

                TagActionItem(
                    icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    label = if (isFavorite) "Remove from Favorite Tags" else "Add to Favorite Tags",
                    description = "Used for smart suggestions & home feed",
                    iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else null,
                    onClick = {
                        val added = favoriteManager.toggleFavoriteTag(tag)
                        val msg = if (added) {
                            context.getString(KMR.strings.added_to_favorite_tags.resourceId)
                        } else {
                            context.getString(KMR.strings.removed_from_favorite_tags.resourceId)
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                )

                TagActionItem(
                    icon = Icons.Outlined.Block,
                    label = if (isBlocked) "Unblock Tag" else "Block Tag (Blacklist)",
                    description = if (isBlocked) "Remove from blocked list" else "Hide titles with this tag",
                    iconTint = if (isBlocked) MaterialTheme.colorScheme.error else null,
                    onClick = {
                        val currentBlocked = sourcePreferences.blockedTags().get().toMutableSet()
                        if (isBlocked) {
                            currentBlocked.remove(tag)
                            android.widget.Toast.makeText(context, "Unblocked: $tag", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            currentBlocked.add(tag)
                            android.widget.Toast.makeText(context, "Blocked: $tag", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        sourcePreferences.blockedTags().set(currentBlocked)
                        onDismissRequest()
                    },
                )

                TagActionItem(
                    icon = Icons.Outlined.Checklist,
                    label = "Select Multiple Tags...",
                    description = "Combine tags for multi-search & actions",
                    onClick = {
                        onDismissRequest()
                        onStartMultiSelect()
                    },
                )

                TagActionItem(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(MR.strings.action_copy_to_clipboard),
                    description = "Copy tag text",
                    onClick = {
                        clipboardManager.setText(AnnotatedString(tag))
                        android.widget.Toast.makeText(context, "Copied: $tag", android.widget.Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun TagActionItem(
    icon: ImageVector,
    label: String,
    description: String? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun TagMultiSelectBottomBar(
    selectedTags: Set<String>,
    onGlobalSearch: (List<String>) -> Unit,
    onSourceSearch: (List<String>) -> Unit,
    onFavoriteAll: (List<String>) -> Unit,
    onBlockAll: (List<String>) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedTags.size} tags selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = selectedTags.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { onGlobalSearch(selectedTags.toList()) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedTags.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Global",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                FilledTonalButton(
                    onClick = { onSourceSearch(selectedTags.toList()) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedTags.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                OutlinedButton(
                    onClick = { onFavoriteAll(selectedTags.toList()) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedTags.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Favorite",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
