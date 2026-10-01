package eu.kanade.tachiyomi.ui.browse.extension

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tachiyomi.presentation.core.util.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.ai.NsfwTagClassifier
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Screen displaying installed Manga, Anime, and Novel extensions with their NSFW (18+)
 * status and providing manual overrides (SFW vs 18+).
 */
class ExtensionNsfwScreen(
    private val initialTab: Int = 0,
) : Screen() {

    private enum class ExtensionFilterMode(val label: String) {
        ALL("All"),
        NSFW_ONLY("18+ Only"),
        SFW_ONLY("SFW Only"),
        OVERRIDDEN("Overridden"),
    }

    private data class ExtensionNsfwItem(
        val id: String,
        val name: String,
        val lang: String,
        val versionName: String,
        val nativeIsNsfw: Boolean,
        val iconDrawable: Drawable? = null,
        val iconUrl: String? = null,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val extensionManager = remember { Injekt.get<ExtensionManager>() }
        val animeExtensionManager = remember { Injekt.get<AnimeExtensionManager>() }
        val novelExtensionManager = remember { Injekt.get<NovelExtensionManager>() }

        val mediaTypes = remember { listOf(MediaType.MANGA, MediaType.ANIME, MediaType.NOVEL) }
        var selectedMediaIndex by rememberSaveable {
            mutableStateOf(initialTab.coerceIn(0, mediaTypes.size - 1))
        }
        val currentMediaType = mediaTypes[selectedMediaIndex]

        var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
        var filterMode by rememberSaveable { mutableStateOf(ExtensionFilterMode.ALL) }

        // Live preferences and flows
        val overrideSfw by sourcePreferences.nsfwOverrideSfwExtensions().collectAsState()
        val overrideNsfw by sourcePreferences.nsfwOverrideNsfwExtensions().collectAsState()

        val mangaExtensions by extensionManager.installedExtensionsFlow.collectAsState()
        val animeExtensions by animeExtensionManager.installedExtensionsFlow.collectAsState()
        val novelPlugins by novelExtensionManager.installedPluginsFlow.collectAsState(initial = emptyList())

        val allItems = remember(currentMediaType, mangaExtensions, animeExtensions, novelPlugins) {
            when (currentMediaType) {
                MediaType.MANGA -> mangaExtensions.map { ext ->
                    ExtensionNsfwItem(
                        id = ext.pkgName,
                        name = ext.name,
                        lang = ext.lang,
                        versionName = ext.versionName,
                        nativeIsNsfw = ext.isNsfw,
                        iconDrawable = ext.icon,
                    )
                }
                MediaType.ANIME -> animeExtensions.map { ext ->
                    ExtensionNsfwItem(
                        id = ext.pkgName,
                        name = ext.name,
                        lang = ext.lang,
                        versionName = ext.versionName,
                        nativeIsNsfw = ext.isNsfw,
                        iconDrawable = ext.icon,
                    )
                }
                MediaType.NOVEL -> novelPlugins.map { plugin ->
                    ExtensionNsfwItem(
                        id = plugin.pkgName ?: plugin.id,
                        name = plugin.name,
                        lang = plugin.lang,
                        versionName = plugin.versionName,
                        nativeIsNsfw = plugin.isNsfw,
                        iconUrl = plugin.iconUrl,
                    )
                }
            }
        }

        val filteredItems = remember(allItems, searchQuery, filterMode, overrideSfw, overrideNsfw) {
            val query = searchQuery?.trim()?.lowercase().orEmpty()
            allItems.filter { item ->
                val matchesQuery = query.isEmpty() ||
                    item.name.lowercase().contains(query) ||
                    item.lang.lowercase().contains(query) ||
                    item.id.lowercase().contains(query)

                if (!matchesQuery) return@filter false

                val isSfwOverridden = overrideSfw.contains(item.id)
                val isNsfwOverridden = overrideNsfw.contains(item.id)
                val isNsfwActive = when {
                    isSfwOverridden -> false
                    isNsfwOverridden -> true
                    else -> item.nativeIsNsfw
                }
                val isOverridden = isSfwOverridden || isNsfwOverridden

                when (filterMode) {
                    ExtensionFilterMode.ALL -> true
                    ExtensionFilterMode.NSFW_ONLY -> isNsfwActive
                    ExtensionFilterMode.SFW_ONLY -> !isNsfwActive
                    ExtensionFilterMode.OVERRIDDEN -> isOverridden
                }
            }.sortedWith(
                compareByDescending<ExtensionNsfwItem> { item ->
                    val isSfwOverridden = overrideSfw.contains(item.id)
                    val isNsfwOverridden = overrideNsfw.contains(item.id)
                    when {
                        isSfwOverridden -> false
                        isNsfwOverridden -> true
                        else -> item.nativeIsNsfw
                    }
                }.thenBy { it.name.lowercase() },
            )
        }

        Scaffold(
            topBar = {
                SearchToolbar(
                    titleContent = {
                        AppBarTitle(
                            title = stringResource(KMR.strings.extension_nsfw_title),
                            subtitle = stringResource(KMR.strings.extension_nsfw_subtitle),
                        )
                    },
                    navigateUp = { navigator.pop() },
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it },
                    placeholderText = stringResource(KMR.strings.extension_nsfw_search_hint),
                    actions = {
                        val hasAnyOverrides = overrideSfw.isNotEmpty() || overrideNsfw.isNotEmpty()
                        if (hasAnyOverrides) {
                            AppBarActions(
                                actions = persistentListOf(
                                    AppBar.Action(
                                        title = "Reset All Overrides",
                                        icon = Icons.Outlined.RestartAlt,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sourcePreferences.nsfwOverrideSfwExtensions().set(emptySet())
                                            sourcePreferences.nsfwOverrideNsfwExtensions().set(emptySet())
                                            scope.launch {
                                                snackbarHostState.showSnackbar("All extension 18+ overrides reset to defaults")
                                            }
                                        },
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Media Type Tabs (Manga / Anime / Novel)
                PrimaryTabRow(
                    selectedTabIndex = selectedMediaIndex,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    mediaTypes.forEachIndexed { index, mediaType ->
                        Tab(
                            selected = selectedMediaIndex == index,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedMediaIndex = index
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = mediaType.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(mediaType.titleRes),
                                        fontWeight = if (selectedMediaIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            },
                        )
                    }
                }

                // Filter chips row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(ExtensionFilterMode.values()) { mode ->
                        val count = when (mode) {
                            ExtensionFilterMode.ALL -> allItems.size
                            ExtensionFilterMode.NSFW_ONLY -> allItems.count {
                                val sfw = overrideSfw.contains(it.id)
                                val nsfw = overrideNsfw.contains(it.id)
                                if (sfw) false else if (nsfw) true else it.nativeIsNsfw
                            }
                            ExtensionFilterMode.SFW_ONLY -> allItems.count {
                                val sfw = overrideSfw.contains(it.id)
                                val nsfw = overrideNsfw.contains(it.id)
                                if (sfw) true else if (nsfw) false else !it.nativeIsNsfw
                            }
                            ExtensionFilterMode.OVERRIDDEN -> allItems.count {
                                overrideSfw.contains(it.id) || overrideNsfw.contains(it.id)
                            }
                        }
                        val isSelected = filterMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                filterMode = mode
                            },
                            label = { Text("${mode.label} ($count)") },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                text = stringResource(KMR.strings.extension_nsfw_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = filteredItems,
                            key = { "${currentMediaType.name}-${it.id}" },
                        ) { item ->
                            val isSfwOverridden = overrideSfw.contains(item.id)
                            val isNsfwOverridden = overrideNsfw.contains(item.id)
                            val isNsfwActive = when {
                                isSfwOverridden -> false
                                isNsfwOverridden -> true
                                else -> item.nativeIsNsfw
                            }

                            ExtensionNsfwRow(
                                item = item,
                                isNsfwActive = isNsfwActive,
                                isSfwOverridden = isSfwOverridden,
                                isNsfwOverridden = isNsfwOverridden,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    NsfwTagClassifier.toggleExtensionNsfw(item.id, item.nativeIsNsfw)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ExtensionNsfwRow(
        item: ExtensionNsfwItem,
        isNsfwActive: Boolean,
        isSfwOverridden: Boolean,
        isNsfwOverridden: Boolean,
        onToggle: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Extension icon
                val iconBitmap = remember(item.iconDrawable) {
                    item.iconDrawable?.toBitmap()?.asImageBitmap()
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        iconBitmap != null -> {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        item.iconUrl != null -> {
                            AsyncImage(
                                model = item.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Filled.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                // Extension details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        // Lang badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = LocaleHelper.getSourceDisplayName(item.lang, LocalContext.current),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Status and override badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Native status badge
                        if (item.nativeIsNsfw) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = "18+",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = "Standard",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                        }

                        // Override status indicator
                        if (isSfwOverridden) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.extension_nsfw_overridden_sfw),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        } else if (isNsfwOverridden) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.extension_nsfw_overridden_nsfw),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        Text(
                            text = "v${item.versionName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }

                // Switch column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Switch(
                        checked = isNsfwActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    )
                    Text(
                        text = if (isNsfwActive) "18+ Blocked" else "SFW Allowed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isNsfwActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}
// KMK <--
