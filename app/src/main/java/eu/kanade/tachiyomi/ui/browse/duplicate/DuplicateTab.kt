package eu.kanade.tachiyomi.ui.browse.duplicate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class DuplicateGroupUI(
    val id: String,
    val main: LibraryManga,
    val duplicates: List<LibraryManga>,
    val isSkipped: Boolean = false,
    val isResolved: Boolean = false,
)

data class DuplicateScreenState(
    val isLoading: Boolean = true,
    val groups: List<DuplicateGroupUI> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val activeCategoryManga: Manga? = null,
    val categories: List<Category> = emptyList(),
)

class DuplicateScreenModel(
    val targetMangaId: Long? = null,
    val targetMangaIds: List<Long>? = null,
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<DuplicateScreenState>(DuplicateScreenState()) {

    init {
        loadDuplicates()
    }

    fun loadDuplicates() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, groups = emptyList()) }
            val allLibrary = getLibraryManga.await()
            val categories = getCategories.await()
            mutableState.update { it.copy(categories = categories) }

            val tracksMap = try {
                Injekt.get<GetTracks>().await().groupBy { it.mangaId }
            } catch (e: Exception) {
                emptyMap()
            }

            val searchIds = targetMangaIds ?: targetMangaId?.let { listOf(it) }
            val isManualTargetCheck = !searchIds.isNullOrEmpty()

            val history = uiPreferences.duplicateHistory().get()
            val maxScan = uiPreferences.duplicateMaxScanCount().get()
            val limit = if (maxScan > 0) maxScan else Int.MAX_VALUE

            // Filter out items in history ONLY during global scans, NOT when manually checking specific target manga
            val filteredLibrary = if (isManualTargetCheck) {
                allLibrary
            } else {
                allLibrary.filter { it.manga.id.toString() !in history }
            }

            // Union-Find implementation
            val parent = mutableMapOf<Long, Long>()
            fun find(i: Long): Long {
                var root = i
                while (root != parent[root]) {
                    root = parent[root] ?: root
                }
                var curr = i
                while (curr != root) {
                    val nxt = parent[curr] ?: curr
                    parent[curr] = root
                    curr = nxt
                }
                return root
            }
            fun union(i: Long, j: Long) {
                val rootI = find(i)
                val rootJ = find(j)
                if (rootI != rootJ) {
                    parent[rootI] = rootJ
                }
            }

            // Initialize Union-Find parent pointers
            filteredLibrary.forEach { parent[it.manga.id] = it.manga.id }

            // Group 1: By cleaned title
            val byTitle = filteredLibrary.groupBy { cleanTitle(it.manga.title) }
            byTitle.forEach { (title, list) ->
                if (title.isNotEmpty() && list.size > 1) {
                    val firstId = list.first().manga.id
                    list.drop(1).forEach { union(firstId, it.manga.id) }
                }
            }

            // Group 2: By trackers
            val byTracker = mutableMapOf<Pair<Long, Long>, MutableList<Long>>()
            filteredLibrary.forEach { manga ->
                val tracks = tracksMap[manga.manga.id] ?: emptyList()
                tracks.forEach { track ->
                    val key = Pair(track.trackerId, track.remoteId)
                    byTracker.getOrPut(key) { mutableListOf() }.add(manga.manga.id)
                }
            }
            byTracker.forEach { (_, ids) ->
                if (ids.size > 1) {
                    val firstId = ids.first()
                    ids.drop(1).forEach { union(firstId, it) }
                }
            }

            // Collect grouped duplicates
            val groupsMap = filteredLibrary.groupBy { find(it.manga.id) }
            val groupsList = groupsMap.values
                .filter { it.size > 1 }
                .map { group ->
                    DuplicateGroupUI(
                        id = group.first().manga.id.toString(),
                        main = group.first(),
                        duplicates = group.drop(1),
                    )
                }
                .filter { group ->
                    searchIds.isNullOrEmpty() ||
                        searchIds.contains(group.main.manga.id) ||
                        group.duplicates.any { searchIds.contains(it.manga.id) }
                }
                .take(limit)

            mutableState.update { it.copy(isLoading = false, groups = groupsList) }
        }
    }

    fun refreshDuplicates() {
        uiPreferences.duplicateHistory().set(emptySet())
        loadDuplicates()
    }

    fun toggleSelection(mangaId: Long) {
        mutableState.update { state ->
            val selected = state.selectedIds
            if (mangaId in selected) {
                state.copy(selectedIds = selected - mangaId)
            } else {
                state.copy(selectedIds = selected + mangaId)
            }
        }
    }

    fun skipGroup(groupId: String) {
        val group = state.value.groups.firstOrNull { it.id == groupId }
        if (group != null) {
            val idsToSkip = (listOf(group.main) + group.duplicates).map { it.manga.id.toString() }
            val currentHistory = uiPreferences.duplicateHistory().get()
            uiPreferences.duplicateHistory().set(currentHistory + idsToSkip)
        }

        mutableState.update { state ->
            val newGroups = state.groups.map {
                if (it.id == groupId) it.copy(isSkipped = true) else it
            }
            state.copy(groups = newGroups)
        }
    }

    fun showChangeCategory(manga: Manga) {
        mutableState.update { it.copy(activeCategoryManga = manga) }
    }

    fun closeChangeCategory() {
        mutableState.update { it.copy(activeCategoryManga = null) }
    }

    fun changeMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
            mutableState.update { state ->
                state.copy(
                    selectedIds = state.selectedIds + mangaId,
                    activeCategoryManga = null,
                )
            }
            val allLibrary = getLibraryManga.await()
            mutableState.update { state ->
                val updatedGroups = state.groups.map { group ->
                    val updatedMain = allLibrary.firstOrNull { it.manga.id == group.main.manga.id } ?: group.main
                    val updatedDuplicates = group.duplicates.map { dup ->
                        allLibrary.firstOrNull { it.manga.id == dup.manga.id } ?: dup
                    }
                    group.copy(main = updatedMain, duplicates = updatedDuplicates)
                }
                state.copy(groups = updatedGroups)
            }
        }
    }

    fun processResolvedGroups() {
        screenModelScope.launchIO {
            val state = state.value
            val toRemoveFromLibrary = mutableListOf<MangaUpdate>()
            val resolvedGroupIds = mutableSetOf<String>()
            val resolvedGroupMangaIds = mutableListOf<String>()

            state.groups.forEach { group ->
                if (group.isSkipped || group.isResolved) return@forEach

                val groupItems = listOf(group.main) + group.duplicates
                val selectedInGroup = groupItems.filter { it.manga.id in state.selectedIds }

                if (selectedInGroup.isNotEmpty()) {
                    val unselectedInGroup = groupItems.filterNot { it.manga.id in state.selectedIds }
                    unselectedInGroup.forEach {
                        toRemoveFromLibrary.add(MangaUpdate(id = it.manga.id, favorite = false))
                    }
                    resolvedGroupIds.add(group.id)
                    resolvedGroupMangaIds.addAll(groupItems.map { it.manga.id.toString() })
                }
            }

            if (toRemoveFromLibrary.isNotEmpty()) {
                updateManga.awaitAll(toRemoveFromLibrary)
            }

            if (resolvedGroupMangaIds.isNotEmpty()) {
                val currentHistory = uiPreferences.duplicateHistory().get()
                uiPreferences.duplicateHistory().set(currentHistory + resolvedGroupMangaIds)
            }

            mutableState.update { state ->
                val newGroups = state.groups.map {
                    if (it.id in resolvedGroupIds) it.copy(isResolved = true) else it
                }
                state.copy(groups = newGroups)
            }
        }
    }
}

private fun cleanTitle(title: String): String {
    val parsedClean = eu.kanade.tachiyomi.util.MangaTitleParser.parse(title).cleanTitle
    var t = parsedClean.lowercase()
    val wordsToRemove = listOf(
        "official", "colored", "digital", "edition", "remastered",
        "uncensored", "scanlation", "webtoon", "manga", "novel",
        "ver", "version", "raw",
    )
    wordsToRemove.forEach { word ->
        t = t.replace(word, "")
    }
    return t.filter { it.isLetterOrDigit() }.trim()
}

private fun isDuplicate(
    m1: Manga,
    m2: Manga,
    tracksMap: Map<Long, List<Track>>,
): Boolean {
    val t1 = tracksMap[m1.id]
    val t2 = tracksMap[m2.id]
    if (t1 != null && t2 != null) {
        val sharedTracker = t1.any { track1 ->
            t2.any { track2 ->
                track1.trackerId == track2.trackerId && track1.remoteId == track2.remoteId
            }
        }
        if (sharedTracker) return true
    }

    val clean1 = cleanTitle(m1.title)
    val clean2 = cleanTitle(m2.title)
    if (clean1 == clean2 && clean1.isNotEmpty()) return true

    return false
}

data object DuplicateTab : Tab {
    private fun readResolve(): Any = DuplicateTab

    val resolveDuplicatesEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 4u,
                title = stringResource(KMR.strings.label_duplicate),
                icon = null,
            )
        }

    @Composable
    override fun Content() {
        Content(contentPadding = PaddingValues())
    }

    @Composable
    fun Content(contentPadding: PaddingValues) {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DuplicateScreenModel() }
        val state by screenModel.state.collectAsState()
        var showWarningDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            resolveDuplicatesEvent.receiveAsFlow().collectLatest {
                showWarningDialog = true
            }
        }

        if (showWarningDialog) {
            val groups = state.groups
            val selectedIds = state.selectedIds

            var keepCount = 0
            var deleteCount = 0
            var skipCount = 0

            groups.forEach { group ->
                if (group.isResolved) return@forEach
                if (group.isSkipped) {
                    skipCount += 1 + group.duplicates.size
                    return@forEach
                }
                val groupItems = listOf(group.main) + group.duplicates
                val selectedInGroup = groupItems.filter { it.manga.id in selectedIds }
                if (selectedInGroup.isNotEmpty()) {
                    keepCount += selectedInGroup.size
                    deleteCount += (groupItems.size - selectedInGroup.size)
                } else {
                    skipCount += groupItems.size
                }
            }

            AlertDialog(
                onDismissRequest = { showWarningDialog = false },
                title = { Text(text = "Confirm Resolution") },
                text = {
                    Text(
                        text = "Are you sure you want to resolve duplicates?\n\n" +
                            "Keeping: $keepCount manga(s)\n" +
                            "Deleting: $deleteCount manga(s)\n" +
                            "Skipping: $skipCount manga(s)",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showWarningDialog = false
                            screenModel.processResolvedGroups()
                        },
                    ) {
                        Text(text = "Yes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showWarningDialog = false },
                    ) {
                        Text(text = "No")
                    }
                },
            )
        }

        DuplicateScreen(
            state = state,
            onEntryClick = screenModel::toggleSelection,
            onEntryLongClick = { navigator.push(MangaScreen(it)) },
            onCategoryClick = screenModel::showChangeCategory,
            onSkipGroup = screenModel::skipGroup,
            onConfirmCategory = screenModel::changeMangaCategories,
            onDismissCategory = screenModel::closeChangeCategory,
            onRefresh = screenModel::refreshDuplicates,
            contentPadding = contentPadding,
        )
    }
}

@Composable
fun Screen.duplicateSourceTab(): TabContent {
    return TabContent(
        titleRes = KMR.strings.label_duplicate,
        actions = persistentListOf(
            AppBar.Action(
                title = "Done",
                icon = Icons.Default.Check,
                onClick = { DuplicateTab.resolveDuplicatesEvent.trySend(Unit) },
            ),
        ),
        content = { contentPadding, _ ->
            val topPadding = contentPadding.calculateTopPadding()
            val startPadding = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val endPadding = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, start = startPadding, end = endPadding),
            ) {
                DuplicateTab.Content(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        },
    )
}

@Composable
fun DuplicateScreen(
    state: DuplicateScreenState,
    onEntryClick: (Long) -> Unit,
    onEntryLongClick: (Long) -> Unit,
    onCategoryClick: (Manga) -> Unit,
    onSkipGroup: (String) -> Unit,
    onConfirmCategory: (Long, List<Long>) -> Unit,
    onDismissCategory: () -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullRefresh(
            refreshing = state.isLoading,
            enabled = true,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.groups.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.groups.all { it.isSkipped || it.isResolved } -> {
                    EmptyScreen(
                        message = "No duplicates found.",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    val visibleGroups = remember(state.groups) {
                        state.groups.filterNot { it.isSkipped || it.isResolved }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding + PaddingValues(bottom = 72.dp),
                    ) {
                        items(
                            items = visibleGroups,
                            key = { it.id },
                        ) { group ->
                            DuplicateGroupItem(
                                group = group,
                                categories = state.categories,
                                selectedIds = state.selectedIds,
                                onEntryClick = onEntryClick,
                                onEntryLongClick = onEntryLongClick,
                                onCategoryClick = onCategoryClick,
                                onSkipClick = { onSkipGroup(group.id) },
                            )
                        }
                    }
                }
            }
        }

        // Category selection popup
        if (state.activeCategoryManga != null) {
            var currentMangaCategoryIds by remember(state.activeCategoryManga) {
                mutableStateOf<List<Long>?>(null)
            }
            LaunchedEffect(state.activeCategoryManga) {
                currentMangaCategoryIds = Injekt.get<GetCategories>().await(state.activeCategoryManga.id).map { it.id }
            }

            if (currentMangaCategoryIds != null) {
                val initialSelection = remember(state.categories, currentMangaCategoryIds) {
                    state.categories.mapAsCheckboxState { it.id in currentMangaCategoryIds!! }.toImmutableList()
                }

                ChangeCategoryDialog(
                    initialSelection = initialSelection,
                    onDismissRequest = onDismissCategory,
                    onEditCategories = {},
                    onConfirm = { include, _ ->
                        onConfirmCategory(state.activeCategoryManga.id, include)
                    },
                )
            }
        }
    }
}

@Composable
fun DuplicateGroupItem(
    group: DuplicateGroupUI,
    categories: List<Category>,
    selectedIds: Set<Long>,
    onEntryClick: (Long) -> Unit,
    onEntryLongClick: (Long) -> Unit,
    onCategoryClick: (Manga) -> Unit,
    onSkipClick: () -> Unit,
) {
    if (group.isSkipped || group.isResolved) return

    val items = remember(group) {
        listOf(group.main) + group.duplicates
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.main.manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onSkipClick,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(text = "Skip", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val gridHeight = if (items.size <= 3) 220.dp else 420.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .height(gridHeight)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = items.size > 6,
        ) {
            items(items) { item ->
                val isSelected = item.manga.id in selectedIds
                val isMain = item.manga.id == group.main.manga.id
                val borderCol = when {
                    isSelected -> Color.Green
                    isMain -> Color.Blue
                    else -> Color.Red
                }

                val categoryName = item.categories.map { catId ->
                    val cat = categories.find { it.id == catId }
                    if (cat != null) {
                        if (cat.isSystemCategory) stringResource(MR.strings.label_default) else cat.name
                    } else {
                        null
                    }
                }.filterNotNull().joinToString(", ").takeIf { it.isNotEmpty() } ?: "Default"

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onCategoryClick(item.manga) }
                            .padding(bottom = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, borderCol, RoundedCornerShape(8.dp))
                            .padding(2.dp)
                            .combinedClickable(
                                onClick = { onEntryClick(item.manga.id) },
                                onLongClick = { onEntryLongClick(item.manga.id) },
                            ),
                    ) {
                        MangaCover.Book(
                            data = item.manga.asMangaCover(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.manga.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

class DuplicateMangaScreen(val mangaIds: List<Long>) : Screen {
    constructor(mangaId: Long) : this(listOf(mangaId))

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DuplicateScreenModel(targetMangaIds = mangaIds) }
        val state by screenModel.state.collectAsState()
        var showWarningDialog by remember { mutableStateOf(false) }

        if (showWarningDialog) {
            val groups = state.groups
            val selectedIds = state.selectedIds

            var keepCount = 0
            var deleteCount = 0
            var skipCount = 0

            groups.forEach { group ->
                if (group.isResolved) return@forEach
                if (group.isSkipped) {
                    skipCount += 1 + group.duplicates.size
                    return@forEach
                }
                val groupItems = listOf(group.main) + group.duplicates
                val selectedInGroup = groupItems.filter { it.manga.id in selectedIds }
                if (selectedInGroup.isNotEmpty()) {
                    keepCount += selectedInGroup.size
                    deleteCount += (groupItems.size - selectedInGroup.size)
                } else {
                    skipCount += groupItems.size
                }
            }

            AlertDialog(
                onDismissRequest = { showWarningDialog = false },
                title = { Text(text = "Confirm Resolution") },
                text = {
                    Text(
                        text = "Are you sure you want to resolve duplicates?\n\n" +
                            "Keeping: $keepCount manga(s)\n" +
                            "Deleting: $deleteCount manga(s)\n" +
                            "Skipping: $skipCount manga(s)",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showWarningDialog = false
                            screenModel.processResolvedGroups()
                        },
                    ) {
                        Text(text = "Yes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showWarningDialog = false },
                    ) {
                        Text(text = "No")
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.label_duplicate),
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(onClick = { showWarningDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            DuplicateScreen(
                state = state,
                onEntryClick = screenModel::toggleSelection,
                onEntryLongClick = { navigator.push(MangaScreen(it)) },
                onCategoryClick = screenModel::showChangeCategory,
                onSkipGroup = screenModel::skipGroup,
                onConfirmCategory = screenModel::changeMangaCategories,
                onDismissCategory = screenModel::closeChangeCategory,
                onRefresh = screenModel::refreshDuplicates,
                contentPadding = contentPadding,
            )
        }
    }
}
