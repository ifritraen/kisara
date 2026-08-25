package eu.kanade.tachiyomi.ui.browse.novel.duplicate

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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.service.NovelSourceManager
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

data object NovelDuplicateTab : Tab {
    private fun readResolve(): Any = NovelDuplicateTab

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
        val screenModel = rememberScreenModel { NovelDuplicateScreenModel() }
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
                val selectedInGroup = groupItems.filter { it.novel.id in selectedIds }
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
                            "Keeping: $keepCount novel(s)\n" +
                            "Deleting: $deleteCount novel(s)\n" +
                            "Skipping: $skipCount novel(s)",
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

        NovelDuplicateScreen(
            state = state,
            onEntryClick = screenModel::toggleSelection,
            onEntryLongClick = { navigator.push(NovelScreen(it)) },
            onCategoryClick = screenModel::showChangeCategory,
            onSkipGroup = screenModel::skipGroup,
            onConfirmCategory = { novelId, catIds ->
                val novel = state.activeCategoryNovel
                if (novel != null) screenModel.changeNovelCategories(novel, catIds)
            },
            onDismissCategory = screenModel::closeChangeCategory,
            onRefresh = screenModel::refreshDuplicates,
            contentPadding = contentPadding,
        )
    }
}

@Composable
fun Screen.novelDuplicateTab(): TabContent {
    return TabContent(
        titleRes = KMR.strings.label_duplicate,
        actions = persistentListOf(
            AppBar.Action(
                title = "Done",
                icon = Icons.Default.Check,
                onClick = { NovelDuplicateTab.resolveDuplicatesEvent.trySend(Unit) },
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
                NovelDuplicateTab.Content(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        },
    )
}

@Composable
fun NovelDuplicateScreen(
    state: NovelDuplicateScreenState,
    onEntryClick: (Long) -> Unit,
    onEntryLongClick: (Long) -> Unit,
    onCategoryClick: (Novel) -> Unit,
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
                            NovelDuplicateGroupItem(
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
        if (state.activeCategoryNovel != null) {
            var currentNovelCategoryIds by remember(state.activeCategoryNovel) {
                mutableStateOf<List<Long>?>(null)
            }
            LaunchedEffect(state.activeCategoryNovel) {
                currentNovelCategoryIds = Injekt.get<GetNovelCategories>().await(state.activeCategoryNovel.id).map { it.id }
            }

            if (currentNovelCategoryIds != null) {
                val initialSelection = remember(state.categories, currentNovelCategoryIds) {
                    state.categories.mapAsCheckboxState { it.id in currentNovelCategoryIds!! }.toImmutableList()
                }

                ChangeCategoryDialog(
                    initialSelection = initialSelection,
                    onDismissRequest = onDismissCategory,
                    onEditCategories = {},
                    onConfirm = { include, _ ->
                        onConfirmCategory(state.activeCategoryNovel.id, include)
                    },
                    novel = state.activeCategoryNovel,
                )
            }
        }
    }
}

@Composable
fun NovelDuplicateGroupItem(
    group: NovelDuplicateGroupUI,
    categories: List<Category>,
    selectedIds: Set<Long>,
    onEntryClick: (Long) -> Unit,
    onEntryLongClick: (Long) -> Unit,
    onCategoryClick: (Novel) -> Unit,
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
                    text = group.main.novel.title,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = items.size > 3,
        ) {
            items(items, key = { it.novel.id }) { item ->
                val isSelected = item.novel.id in selectedIds
                val novelCategories = remember(categories, item.category) {
                    categories.filter { it.id == item.category }
                }

                NovelDuplicateItemCard(
                    item = item,
                    categories = novelCategories,
                    isSelected = isSelected,
                    onClick = { onEntryClick(item.novel.id) },
                    onLongClick = { onEntryLongClick(item.novel.id) },
                    onCategoryClick = { onCategoryClick(item.novel) },
                )
            }
        }
    }
}

@Composable
fun NovelDuplicateItemCard(
    item: LibraryNovel,
    categories: List<Category>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCategoryClick: () -> Unit,
) {
    val context = LocalContext.current
    val novel = item.novel
    val sourceManager = remember { Injekt.get<NovelSourceManager>() }
    val sourceName = remember(novel.source) { sourceManager.getOrStub(novel.source).name }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            MangaCover.Book(
                data = sourceAwareNovelCoverModel(novel),
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Source: $sourceName",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "Chapters: ${item.totalChapters}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable { onCategoryClick() },
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ) {
            val catText = if (categories.isEmpty()) "Default" else categories.joinToString { it.visualName(context) }
            Text(
                text = catText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

class DuplicateNovelScreen(val novelIds: List<Long>) : Screen {
    constructor(novelId: Long) : this(listOf(novelId))

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelDuplicateScreenModel(targetNovelIds = novelIds) }
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
                val selectedInGroup = groupItems.filter { it.novel.id in selectedIds }
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
                            "Keeping: $keepCount novel(s)\n" +
                            "Deleting: $deleteCount novel(s)\n" +
                            "Skipping: $skipCount novel(s)",
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
            NovelDuplicateScreen(
                state = state,
                onEntryClick = screenModel::toggleSelection,
                onEntryLongClick = { navigator.push(NovelScreen(it)) },
                onCategoryClick = screenModel::showChangeCategory,
                onSkipGroup = screenModel::skipGroup,
                onConfirmCategory = { novelId, catIds ->
                    val novel = state.activeCategoryNovel
                    if (novel != null) screenModel.changeNovelCategories(novel, catIds)
                },
                onDismissCategory = screenModel::closeChangeCategory,
                onRefresh = screenModel::refreshDuplicates,
                contentPadding = contentPadding,
            )
        }
    }
}
