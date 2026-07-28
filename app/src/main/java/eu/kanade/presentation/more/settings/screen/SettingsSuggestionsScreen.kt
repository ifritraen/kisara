package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suggestions.SuggestionsReport
import eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.suggestions.interactor.GetSuggestionArtists
import tachiyomi.domain.suggestions.interactor.GetSuggestionAuthors
import tachiyomi.domain.suggestions.interactor.GetSuggestionSources
import tachiyomi.domain.suggestions.interactor.GetSuggestionTags
import tachiyomi.domain.suggestions.interactor.ModifySuggestionArtist
import tachiyomi.domain.suggestions.interactor.ModifySuggestionAuthor
import tachiyomi.domain.suggestions.interactor.ModifySuggestionSource
import tachiyomi.domain.suggestions.interactor.ModifySuggestionTag
import tachiyomi.domain.suggestions.model.SuggestionArtist
import tachiyomi.domain.suggestions.model.SuggestionAuthor
import tachiyomi.domain.suggestions.model.SuggestionSource
import tachiyomi.domain.suggestions.model.SuggestionTag
import tachiyomi.domain.suggestions.service.SuggestionsPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsSuggestionsScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backPress = LocalBackPress.current
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()

        val getSuggestionTags = remember { Injekt.get<GetSuggestionTags>() }
        val getSuggestionSources = remember { Injekt.get<GetSuggestionSources>() }
        val getSuggestionAuthors = remember { Injekt.get<GetSuggestionAuthors>() }
        val getSuggestionArtists = remember { Injekt.get<GetSuggestionArtists>() }
        val modifySuggestionTag = remember { Injekt.get<ModifySuggestionTag>() }
        val modifySuggestionSource = remember { Injekt.get<ModifySuggestionSource>() }
        val modifySuggestionAuthor = remember { Injekt.get<ModifySuggestionAuthor>() }
        val modifySuggestionArtist = remember { Injekt.get<ModifySuggestionArtist>() }
        val suggestionsPreferences = remember { Injekt.get<SuggestionsPreferences>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }

        val tags by getSuggestionTags.subscribe().collectAsState(initial = emptyList())
        val sources by getSuggestionSources.subscribe().collectAsState(initial = emptyList())
        val authors by getSuggestionAuthors.subscribe().collectAsState(initial = emptyList())
        val artists by getSuggestionArtists.subscribe().collectAsState(initial = emptyList())

        var showAddTagDialog by remember { mutableStateOf(false) }
        var showAddSourceDialog by remember { mutableStateOf(false) }
        var showAddAuthorDialog by remember { mutableStateOf(false) }
        var showAddArtistDialog by remember { mutableStateOf(false) }

        var maxTagsToMatch by remember { mutableStateOf(suggestionsPreferences.maxTagsToMatch().get()) }
        var maxSourcesToFetch by remember { mutableStateOf(suggestionsPreferences.maxSourcesToFetch().get()) }
        var maxSuggestionsToDisplay by remember { mutableStateOf(suggestionsPreferences.maxSuggestionsToDisplay().get()) }
        var suggestionsLoggingEnabled by remember { mutableStateOf(suggestionsPreferences.suggestionsLoggingEnabled().get()) }

        var isTagCountExpanded by remember { mutableStateOf(false) }
        var isSourceCountExpanded by remember { mutableStateOf(false) }
        var isAuthorCountExpanded by remember { mutableStateOf(false) }
        var isArtistCountExpanded by remember { mutableStateOf(false) }
        var isTagOrderExpanded by remember { mutableStateOf(true) }
        var isSourceOrderExpanded by remember { mutableStateOf(true) }
        var isAuthorOrderExpanded by remember { mutableStateOf(true) }
        var isArtistOrderExpanded by remember { mutableStateOf(true) }

        val tagCountList = remember(tags) { tags.sortedByDescending { it.count } }
        val sourceCountList = remember(sources) { sources.sortedByDescending { it.count } }
        val authorCountList = remember(authors) { authors.sortedByDescending { it.count } }
        val artistCountList = remember(artists) { artists.sortedByDescending { it.count } }

        val activeTags = remember(tags) {
            val nonBlocked = tags.filter { !it.isBlocked }
            val top10Tags = nonBlocked.sortedByDescending { it.count }.take(10).map { it.tag }.toSet()
            nonBlocked.filter { top10Tags.contains(it.tag) || it.isUserAdded }
                .sortedBy { it.sortOrder }
                .toMutableStateList()
        }
        val activeSources = remember(sources) {
            val nonBlocked = sources.filter { !it.isBlocked }
            val top5Sources = nonBlocked.sortedByDescending { it.count }.take(5).map { it.sourceId }.toSet()
            nonBlocked.filter { top5Sources.contains(it.sourceId) || it.isUserAdded }
                .sortedBy { it.sortOrder }
                .toMutableStateList()
        }
        val activeAuthors = remember(authors) {
            val nonBlocked = authors.filter { !it.isBlocked }
            val top5Authors = nonBlocked.sortedByDescending { it.count }.take(5).map { it.author }.toSet()
            nonBlocked.filter { top5Authors.contains(it.author) || it.isUserAdded }
                .sortedBy { it.sortOrder }
                .toMutableStateList()
        }
        val activeArtists = remember(artists) {
            val nonBlocked = artists.filter { !it.isBlocked }
            val top5Artists = nonBlocked.sortedByDescending { it.count }.take(5).map { it.artist }.toSet()
            nonBlocked.filter { top5Artists.contains(it.artist) || it.isUserAdded }
                .sortedBy { it.sortOrder }
                .toMutableStateList()
        }

        val lazyListState = rememberLazyListState()

        val reorderableTagsState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            if (!fromKey.startsWith("tag-") || !toKey.startsWith("tag-")) return@rememberReorderableLazyListState
            val fromTag = fromKey.removePrefix("tag-")
            val toTag = toKey.removePrefix("tag-")
            val fromIndex = activeTags.indexOfFirst { it.tag == fromTag }
            val toIndex = activeTags.indexOfFirst { it.tag == toTag }
            if (fromIndex != -1 && toIndex != -1) {
                val item = activeTags[fromIndex]
                activeTags.removeAt(fromIndex)
                activeTags.add(toIndex, item)
                scope.launch {
                    modifySuggestionTag.reorder(activeTags)
                }
            }
        }

        val reorderableSourcesState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            if (!fromKey.startsWith("source-") || !toKey.startsWith("source-")) return@rememberReorderableLazyListState
            val fromId = fromKey.removePrefix("source-").toLongOrNull() ?: return@rememberReorderableLazyListState
            val toId = toKey.removePrefix("source-").toLongOrNull() ?: return@rememberReorderableLazyListState
            val fromIndex = activeSources.indexOfFirst { it.sourceId == fromId }
            val toIndex = activeSources.indexOfFirst { it.sourceId == toId }
            if (fromIndex != -1 && toIndex != -1) {
                val item = activeSources[fromIndex]
                activeSources.removeAt(fromIndex)
                activeSources.add(toIndex, item)
                scope.launch {
                    modifySuggestionSource.reorder(activeSources)
                }
            }
        }

        val reorderableAuthorsState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            if (!fromKey.startsWith("author-") || !toKey.startsWith("author-")) return@rememberReorderableLazyListState
            val fromAuthor = fromKey.removePrefix("author-")
            val toAuthor = toKey.removePrefix("author-")
            val fromIndex = activeAuthors.indexOfFirst { it.author == fromAuthor }
            val toIndex = activeAuthors.indexOfFirst { it.author == toAuthor }
            if (fromIndex != -1 && toIndex != -1) {
                val item = activeAuthors[fromIndex]
                activeAuthors.removeAt(fromIndex)
                activeAuthors.add(toIndex, item)
                scope.launch {
                    modifySuggestionAuthor.reorder(activeAuthors)
                }
            }
        }

        val reorderableArtistsState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            if (!fromKey.startsWith("artist-") || !toKey.startsWith("artist-")) return@rememberReorderableLazyListState
            val fromArtist = fromKey.removePrefix("artist-")
            val toArtist = toKey.removePrefix("artist-")
            val fromIndex = activeArtists.indexOfFirst { it.artist == fromArtist }
            val toIndex = activeArtists.indexOfFirst { it.artist == toArtist }
            if (fromIndex != -1 && toIndex != -1) {
                val item = activeArtists[fromIndex]
                activeArtists.removeAt(fromIndex)
                activeArtists.add(toIndex, item)
                scope.launch {
                    modifySuggestionArtist.reorder(activeArtists)
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.pref_suggestions_title),
                    navigateUp = {
                        if (backPress != null) {
                            backPress.invoke()
                        } else {
                            navigator?.pop()
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    modifySuggestionTag.clear()
                                    modifySuggestionSource.clear()
                                    modifySuggestionAuthor.clear()
                                    modifySuggestionArtist.clear()
                                    // Trigger background recalculation worker
                                    val request = androidx.work.OneTimeWorkRequestBuilder<SuggestionsWorker>()
                                        .setInputData(androidx.work.workDataOf("is_manual" to true))
                                        .build()
                                    androidx.work.WorkManager.getInstance(context).enqueue(request)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Restore,
                                contentDescription = "Reset to Calculated",
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            content = { contentPadding ->
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Match Config Group
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Calculation Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Max Tags to Match: $maxTagsToMatch",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    value = maxTagsToMatch.toFloat(),
                                    onValueChange = {
                                        maxTagsToMatch = it.toInt()
                                        suggestionsPreferences.maxTagsToMatch().set(it.toInt())
                                    },
                                    valueRange = 5f..30f,
                                    steps = 25,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Max Extensions to Fetch: $maxSourcesToFetch",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    value = maxSourcesToFetch.toFloat(),
                                    onValueChange = {
                                        maxSourcesToFetch = it.toInt()
                                        suggestionsPreferences.maxSourcesToFetch().set(it.toInt())
                                    },
                                    valueRange = 2f..15f,
                                    steps = 13,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Max Suggestions in Screen: $maxSuggestionsToDisplay",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    value = maxSuggestionsToDisplay.toFloat(),
                                    onValueChange = {
                                        maxSuggestionsToDisplay = it.toInt()
                                        suggestionsPreferences.maxSuggestionsToDisplay().set(it.toInt())
                                    },
                                    valueRange = 50f..1000f,
                                    steps = 19, // (1000-50)/50 = 19 steps
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enable Suggestions Logging",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "Record logs for the suggestion generation process.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = suggestionsLoggingEnabled,
                                        onCheckedChange = {
                                            suggestionsLoggingEnabled = it
                                            suggestionsPreferences.suggestionsLoggingEnabled().set(it)
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                SuggestionsReportPreference()
                            }
                        }
                    }

                    // 1. Tag Count List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTagCountExpanded = !isTagCountExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tag Count List (${tagCountList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (isTagCountExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isTagCountExpanded) {
                        itemsIndexed(
                            items = tagCountList,
                            key = { _, item -> "count-tag-${item.tag}" },
                        ) { _, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.tag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isBlocked) FontWeight.Normal else FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = "${item.count} favs",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    val isTagInOrderList = remember(activeTags, item.tag) {
                                        activeTags.any { it.tag == item.tag }
                                    }
                                    if (!item.isBlocked && !isTagInOrderList) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    modifySuggestionTag.addTag(item.tag)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = "Add to order list",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                modifySuggestionTag.toggleBlock(item.tag)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (item.isBlocked) Icons.Outlined.Check else Icons.Outlined.Block,
                                            contentDescription = if (item.isBlocked) "Unblock" else "Block",
                                            tint = if (item.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Extension Count List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSourceCountExpanded = !isSourceCountExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Extension Count List (${sourceCountList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (isSourceCountExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isSourceCountExpanded) {
                        itemsIndexed(
                            items = sourceCountList,
                            key = { _, item -> "count-source-${item.sourceId}" },
                        ) { _, item ->
                            val source = remember(item.sourceId) { sourceManager.get(item.sourceId) }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (source != null) {
                                        val domainSource = tachiyomi.domain.source.model.Source(
                                            id = source.id,
                                            lang = source.lang,
                                            name = source.name,
                                            supportsLatest = false,
                                            isStub = false,
                                        )
                                        SourceIcon(source = domainSource, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = source.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (item.isBlocked) FontWeight.Normal else FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Text(
                                            text = "Unknown Source (${item.sourceId})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = "${item.count} favs",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    val isSourceInOrderList = remember(activeSources, item.sourceId) {
                                        activeSources.any { it.sourceId == item.sourceId }
                                    }
                                    if (!item.isBlocked && !isSourceInOrderList) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    modifySuggestionSource.addSource(item.sourceId)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = "Add to order list",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                modifySuggestionSource.toggleBlock(item.sourceId)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (item.isBlocked) Icons.Outlined.Check else Icons.Outlined.Block,
                                            contentDescription = if (item.isBlocked) "Unblock" else "Block",
                                            tint = if (item.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2a. Author Count List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAuthorCountExpanded = !isAuthorCountExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Author Count List (${authorCountList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (isAuthorCountExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isAuthorCountExpanded) {
                        itemsIndexed(
                            items = authorCountList,
                            key = { _, item -> "count-author-${item.author}" },
                        ) { _, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isBlocked) FontWeight.Normal else FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = "${item.count} favs",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    val isAuthorInOrderList = remember(activeAuthors, item.author) {
                                        activeAuthors.any { it.author == item.author }
                                    }
                                    if (!item.isBlocked && !isAuthorInOrderList) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    modifySuggestionAuthor.addAuthor(item.author)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = "Add to order list",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                modifySuggestionAuthor.toggleBlock(item.author)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (item.isBlocked) Icons.Outlined.Check else Icons.Outlined.Block,
                                            contentDescription = if (item.isBlocked) "Unblock" else "Block",
                                            tint = if (item.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2b. Artist Count List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isArtistCountExpanded = !isArtistCountExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Artist Count List (${artistCountList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (isArtistCountExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isArtistCountExpanded) {
                        itemsIndexed(
                            items = artistCountList,
                            key = { _, item -> "count-artist-${item.artist}" },
                        ) { _, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isBlocked) FontWeight.Normal else FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = "${item.count} favs",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    val isArtistInOrderList = remember(activeArtists, item.artist) {
                                        activeArtists.any { it.artist == item.artist }
                                    }
                                    if (!item.isBlocked && !isArtistInOrderList) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    modifySuggestionArtist.addArtist(item.artist)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = "Add to order list",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                modifySuggestionArtist.toggleBlock(item.artist)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (item.isBlocked) Icons.Outlined.Check else Icons.Outlined.Block,
                                            contentDescription = if (item.isBlocked) "Unblock" else "Block",
                                            tint = if (item.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

                    // 3. Tag Order List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTagOrderExpanded = !isTagOrderExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tag Order List (Drag and Drop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showAddTagDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add Tag")
                            }
                            Icon(
                                imageVector = if (isTagOrderExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isTagOrderExpanded) {
                        itemsIndexed(
                            items = activeTags,
                            key = { _, item -> "tag-${item.tag}" },
                        ) { _, item ->
                            ReorderableItem(
                                state = reorderableTagsState,
                                key = "tag-${item.tag}",
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            modifier = Modifier.draggableHandle(),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.tag,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.isUserAdded) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        modifySuggestionTag.delete(item.tag)
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Tag")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Extension Order List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSourceOrderExpanded = !isSourceOrderExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Extension Order List (Drag and Drop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showAddSourceDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add Extension")
                            }
                            Icon(
                                imageVector = if (isSourceOrderExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isSourceOrderExpanded) {
                        itemsIndexed(
                            items = activeSources,
                            key = { _, item -> "source-${item.sourceId}" },
                        ) { _, item ->
                            val source = remember(item.sourceId) { sourceManager.get(item.sourceId) }
                            ReorderableItem(
                                state = reorderableSourcesState,
                                key = "source-${item.sourceId}",
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            modifier = Modifier.draggableHandle(),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (source != null) {
                                            val domainSource = tachiyomi.domain.source.model.Source(
                                                id = source.id,
                                                lang = source.lang,
                                                name = source.name,
                                                supportsLatest = false,
                                                isStub = false,
                                            )
                                            SourceIcon(source = domainSource, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = source.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f),
                                            )
                                        } else {
                                            Text(
                                                text = "Unknown Source (${item.sourceId})",
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (item.isUserAdded) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        modifySuggestionSource.delete(item.sourceId)
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Extension")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Author Order List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAuthorOrderExpanded = !isAuthorOrderExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Author Order List (Drag and Drop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showAddAuthorDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add Author")
                            }
                            Icon(
                                imageVector = if (isAuthorOrderExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isAuthorOrderExpanded) {
                        itemsIndexed(
                            items = activeAuthors,
                            key = { _, item -> "author-${item.author}" },
                        ) { _, item ->
                            ReorderableItem(
                                state = reorderableAuthorsState,
                                key = "author-${item.author}",
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            modifier = Modifier.draggableHandle(),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.author,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.isUserAdded) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        modifySuggestionAuthor.delete(item.author)
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Author")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Artist Order List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isArtistOrderExpanded = !isArtistOrderExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Artist Order List (Drag and Drop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showAddArtistDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add Artist")
                            }
                            Icon(
                                imageVector = if (isArtistOrderExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }

                    if (isArtistOrderExpanded) {
                        itemsIndexed(
                            items = activeArtists,
                            key = { _, item -> "artist-${item.artist}" },
                        ) { _, item ->
                            ReorderableItem(
                                state = reorderableArtistsState,
                                key = "artist-${item.artist}",
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            modifier = Modifier.draggableHandle(),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.artist,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.isUserAdded) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        modifySuggestionArtist.delete(item.artist)
                                                    }
                                                },
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Artist")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )

        // Add Tag Dialog
        if (showAddTagDialog) {
            var newTagText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddTagDialog = false },
                title = { Text(text = "Add Custom Tag") },
                text = {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("Tag Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTagText.isNotBlank()) {
                                scope.launch {
                                    modifySuggestionTag.addTag(newTagText)
                                }
                                showAddTagDialog = false
                            }
                        },
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTagDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Add Source Dialog
        if (showAddSourceDialog) {
            val allOnlineSources = remember { sourceManager.getOnlineSources() }
            val existingSourceIds = remember(sources) { sources.map { it.sourceId }.toSet() }
            val availableSources = remember(allOnlineSources, existingSourceIds) {
                allOnlineSources.filter { !existingSourceIds.contains(it.id) }
            }

            AlertDialog(
                onDismissRequest = { showAddSourceDialog = false },
                title = { Text(text = "Add Extension") },
                text = {
                    if (availableSources.isEmpty()) {
                        Text("No more extensions available to add.")
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            itemsIndexed(availableSources) { _, source ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                modifySuggestionSource.addSource(source.id)
                                            }
                                            showAddSourceDialog = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val domainSource = tachiyomi.domain.source.model.Source(
                                        id = source.id,
                                        lang = source.lang,
                                        name = source.name,
                                        supportsLatest = false,
                                        isStub = false,
                                    )
                                    SourceIcon(source = domainSource, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = source.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddSourceDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Add Author Dialog
        if (showAddAuthorDialog) {
            var newAuthorText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddAuthorDialog = false },
                title = { Text(text = "Add Custom Author") },
                text = {
                    OutlinedTextField(
                        value = newAuthorText,
                        onValueChange = { newAuthorText = it },
                        label = { Text("Author Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAuthorText.isNotBlank()) {
                                scope.launch {
                                    modifySuggestionAuthor.addAuthor(newAuthorText)
                                }
                                showAddAuthorDialog = false
                            }
                        },
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAuthorDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Add Artist Dialog
        if (showAddArtistDialog) {
            var newArtistText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddArtistDialog = false },
                title = { Text(text = "Add Custom Artist") },
                text = {
                    OutlinedTextField(
                        value = newArtistText,
                        onValueChange = { newArtistText = it },
                        label = { Text("Artist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newArtistText.isNotBlank()) {
                                scope.launch {
                                    modifySuggestionArtist.addArtist(newArtistText)
                                }
                                showAddArtistDialog = false
                            }
                        },
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddArtistDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun SuggestionsReportPreference() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs by SuggestionsReport.logs.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "View Suggestions Logs",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (logs.isEmpty()) "No logs recorded yet" else "${logs.size} log entries recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.IconButton(onClick = { showDialog = true }) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "View Logs",
                )
            }
        }
    }

    if (showDialog) {
        val logText = remember(logs) {
            if (logs.isEmpty()) {
                "No logs available. Run suggestion updates first."
            } else {
                logs.joinToString("\n") { entry ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                    val ex = if (entry.exceptionTrace != null) "\n${entry.exceptionTrace}" else ""
                    "[$time] [${entry.level}] ${entry.message}$ex"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Suggestions Generation Report") },
            text = {
                Column {
                    Text(
                        text = "Logs detailing tag matching, searched extensions, candidate scores, and selection ranks.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(scrollState)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.copyToClipboard("Suggestions Logs", logText)
                    context.toast("Logs copied to clipboard")
                }) {
                    Text("Copy Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}
