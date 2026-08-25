package eu.kanade.presentation.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asToggleableState
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.novel.interactor.AddNovelTracks
import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, Long?) -> Unit,
    categories: ImmutableList<String>,
    parentOptions: ImmutableList<Category> = persistentListOf(),
    initialParentId: Long? = null,
    // SY -->
    title: String = stringResource(MR.strings.action_add_category),
    extraMessage: String? = null,
    alreadyExistsError: StringResource = MR.strings.error_category_exists,
    // SY <--
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf(initialParentId) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }
    val colorScheme = MaterialTheme.colorScheme

    eu.kanade.presentation.components.KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = extraMessage,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                androidx.compose.material3.Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = name.isNotEmpty() && !nameAlreadyExists,
                    onClick = {
                        onCreate(name, parentId)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_add))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        alreadyExistsError
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
            ParentCategorySelector(
                parentOptions = parentOptions,
                selectedParentId = parentId,
                onSelectParent = { parentId = it },
            )
        }
    }

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String, Long?) -> Unit,
    categories: ImmutableList<String>,
    category: String,
    parentOptions: ImmutableList<Category> = persistentListOf(),
    initialParentId: Long? = null,
    categoryHasChildren: Boolean = false,
) {
    var name by remember { mutableStateOf(category) }
    var valueHasChanged by remember { mutableStateOf(false) }
    var parentId by remember { mutableStateOf(initialParentId) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) && name != category }
    val parentHasChanged = parentId != initialParentId
    val canChangeName = valueHasChanged && !nameAlreadyExists
    val canChangeParent = parentHasChanged && !(categoryHasChildren && parentId != initialParentId)
    val hasChanges = canChangeName || canChangeParent
    val colorScheme = MaterialTheme.colorScheme

    eu.kanade.presentation.components.KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(MR.strings.action_rename_category),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                androidx.compose.material3.Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = hasChanges,
                    onClick = {
                        onRename(name, parentId)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
            ParentCategorySelector(
                parentOptions = parentOptions,
                selectedParentId = parentId,
                onSelectParent = { parentId = it },
                categoryHasChildren = categoryHasChildren,
            )
        }
    }

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    // SY -->
    category: String = "",
    categoryHasChildren: Boolean = false,
    title: String = stringResource(MR.strings.delete_category),
    text: String = stringResource(MR.strings.delete_category_confirmation, category),
    // SY <--
) {
    val colorScheme = MaterialTheme.colorScheme

    eu.kanade.presentation.components.KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = title,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                androidx.compose.material3.Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                    onClick = {
                        onDelete()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ParentCategorySelector(
    parentOptions: ImmutableList<Category>,
    selectedParentId: Long?,
    onSelectParent: (Long?) -> Unit,
    categoryHasChildren: Boolean = false,
) {
    if (parentOptions.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(MR.strings.none)
    val selectedCategory = remember(selectedParentId, parentOptions) {
        parentOptions.firstOrNull { it.id == selectedParentId }
    }
    val selectedLabel = if (selectedCategory != null) selectedCategory.visualName else noneLabel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        TextButton(onClick = { if (!categoryHasChildren) expanded = true }, enabled = !categoryHasChildren) {
            Text(selectedLabel)
        }
        if (!categoryHasChildren) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.none)) },
                    onClick = {
                        onSelectParent(null)
                        expanded = false
                    },
                )
                parentOptions.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.visualName) },
                        onClick = {
                            onSelectParent(parent.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: ImmutableList<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
    // KMK -->
    onDuplicateCheck: (() -> Unit)? = null,
    onDeleteManga: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    manga: tachiyomi.domain.manga.model.Manga? = null,
    anime: tachiyomi.domain.entries.anime.model.Anime? = null,
    novel: tachiyomi.domain.entries.novel.model.Novel? = null,
    onOpenTrackerSearch: (() -> Unit)? = null,
    onCreateCategory: ((name: String, parentId: Long?) -> Unit)? = null,
    // KMK <--
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }

    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }

    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val trackPreferences = remember { Injekt.get<TrackPreferences>() }
    val getTracks = remember { Injekt.get<GetTracks>() }
    val insertTrack = remember { Injekt.get<InsertTrack>() }
    val addTracks = remember { Injekt.get<AddTracks>() }
    val getAnimeTracks = remember { Injekt.get<GetAnimeTracks>() }
    val insertAnimeTrack = remember { Injekt.get<InsertAnimeTrack>() }
    val addAnimeTracks = remember { Injekt.get<AddAnimeTracks>() }
    val getNovelTracks = remember { Injekt.get<GetNovelTracks>() }
    val insertNovelTrack = remember { Injekt.get<InsertNovelTrack>() }
    val addNovelTracks = remember { Injekt.get<AddNovelTracks>() }
    val primaryTracker: Tracker? = remember { trackPreferences.getPrimaryTracker(trackerManager) }
    val getMangaExternalMetadata = remember { Injekt.get<tachiyomi.domain.manga.interactor.GetMangaExternalMetadata>() }
    val fetchExternalMetadata = remember { Injekt.get<eu.kanade.domain.manga.interactor.FetchExternalMetadata>() }
    val createCategoryWithName = remember { Injekt.get<tachiyomi.domain.category.interactor.CreateCategoryWithName>() }

    val effectiveTracker: Tracker? = remember(manga, anime, novel) {
        when {
            anime != null -> trackerManager.loggedInTrackers().find { it.id == TrackerManager.ANILIST } ?: trackerManager.aniList
            novel != null -> trackerManager.loggedInTrackers().find { it.id == trackerManager.mangaUpdates.id } ?: trackerManager.mangaUpdates
            else -> primaryTracker ?: trackerManager.loggedInTrackers().firstOrNull() ?: trackerManager.mangaUpdates
        }
    }

    var externalMetadata by remember { mutableStateOf<tachiyomi.domain.manga.model.MangaExternalMetadata?>(null) }
    var isMetadataLoading by remember { mutableStateOf(false) }
    var isMetadataExpanded by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    var trackerTitle by remember { mutableStateOf<String?>(null) }
    var currentTrack by remember { mutableStateOf<tachiyomi.domain.track.model.Track?>(null) }
    var currentAnimeTrack by remember { mutableStateOf<tachiyomi.domain.track.anime.model.AnimeTrack?>(null) }
    var currentNovelTrack by remember { mutableStateOf<tachiyomi.domain.track.novel.model.NovelTrack?>(null) }
    var trackerStatus by remember { mutableStateOf<Long?>(null) }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(manga, anime, novel) {
        when {
            manga != null -> {
                val cached = getMangaExternalMetadata.await(manga.id)
                if (cached != null) {
                    externalMetadata = cached
                } else {
                    isMetadataLoading = true
                    externalMetadata = fetchExternalMetadata.await(manga)
                    isMetadataLoading = false
                }
            }
            anime != null -> {
                isMetadataLoading = true
                externalMetadata = fetchExternalMetadata.fetchAnime(anime)
                isMetadataLoading = false
            }
            novel != null -> {
                isMetadataLoading = true
                externalMetadata = fetchExternalMetadata.fetchNovel(novel)
                isMetadataLoading = false
            }
        }
    }

    LaunchedEffect(manga, anime, novel, effectiveTracker) {
        val tracker = effectiveTracker ?: return@LaunchedEffect
        when {
            manga != null -> {
                val tracks = try {
                    getTracks.await(manga.id)
                } catch (e: Exception) {
                    emptyList()
                }
                val existing = tracks.find { it.trackerId == tracker.id }
                if (existing != null) {
                    currentTrack = existing
                    trackerTitle = existing.title.ifBlank { manga.title }
                    trackerStatus = existing.status
                } else {
                    trackerTitle = manga.title
                    trackerStatus = tracker.getReadingStatus()
                    try {
                        val searchResults = tracker.search(manga.title)
                        val match = searchResults.firstOrNull { it.title.equals(manga.title, ignoreCase = true) }
                            ?: searchResults.firstOrNull()
                        if (match != null) {
                            trackerTitle = match.title
                        }
                    } catch (e: Exception) {
                        // Ignore search errors
                    }
                }
            }
            anime != null -> {
                val animeTracker = tracker as? AnimeTracker
                val tracks = try {
                    getAnimeTracks.await(anime.id)
                } catch (e: Exception) {
                    emptyList()
                }
                val existing = tracks.find { it.trackerId == tracker.id }
                if (existing != null) {
                    currentAnimeTrack = existing
                    trackerTitle = existing.title.ifBlank { anime.title }
                    trackerStatus = existing.status
                } else {
                    trackerTitle = anime.title
                    trackerStatus = animeTracker?.getWatchingStatus() ?: tracker.getReadingStatus()
                    try {
                        val searchResults = if (animeTracker != null) {
                            animeTracker.searchAnime(anime.title)
                        } else {
                            tracker.search(anime.title)
                        }
                        val matchTitle = searchResults.firstOrNull {
                            val t = when (it) {
                                is eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch -> it.title
                                is eu.kanade.tachiyomi.data.track.model.TrackSearch -> it.title
                                else -> null
                            }
                            t.equals(anime.title, ignoreCase = true)
                        } ?: searchResults.firstOrNull()
                        if (matchTitle != null) {
                            trackerTitle = when (matchTitle) {
                                is eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch -> matchTitle.title
                                is eu.kanade.tachiyomi.data.track.model.TrackSearch -> matchTitle.title
                                else -> trackerTitle
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore search errors
                    }
                }
            }
            novel != null -> {
                val tracks = try {
                    getNovelTracks.await(novel.id)
                } catch (e: Exception) {
                    emptyList()
                }
                val existing = tracks.find { it.trackerId == tracker.id }
                if (existing != null) {
                    currentNovelTrack = existing
                    trackerTitle = existing.title.ifBlank { novel.title }
                    trackerStatus = existing.status
                } else {
                    trackerTitle = novel.title
                    trackerStatus = tracker.getReadingStatus()
                    try {
                        val searchResults = tracker.search(novel.title)
                        val match = searchResults.firstOrNull { it.title.equals(novel.title, ignoreCase = true) }
                            ?: searchResults.firstOrNull()
                        if (match != null) {
                            trackerTitle = match.title
                        }
                    } catch (e: Exception) {
                        // Ignore search errors
                    }
                }
            }
        }
    }

    val parents = remember(selection) {
        selection.filter { it.value.parentId == null }
    }
    val childrenByParent = remember(selection) {
        selection.filter { it.value.parentId != null }
            .groupBy { it.value.parentId }
    }

    val onChange: (CheckboxState<Category>) -> Unit = { entry ->
        val index = selection.indexOfFirst { it.value.id == entry.value.id }
        if (index != -1) {
            val mutableList = selection.toMutableList()
            mutableList[index] = entry.next()
            selection = mutableList.toList().toImmutableList()
        }
    }

    val onUnselect: (CheckboxState<Category>) -> Unit = { entry ->
        val index = selection.indexOfFirst { it.value.id == entry.value.id }
        if (index != -1) {
            val mutableList = selection.toMutableList()
            mutableList[index] = when (entry) {
                is CheckboxState.State -> CheckboxState.State.None(entry.value)
                is CheckboxState.TriState -> CheckboxState.TriState.None(entry.value)
            }
            selection = mutableList.toList().toImmutableList()
        }
    }

    val onDirectToggle: (Long) -> Unit = { targetId ->
        val targetEntry = selection.find { it.value.id == targetId }
        val isTargetSelected = targetEntry is CheckboxState.State.Checked || targetEntry is CheckboxState.TriState.Include
        val newSelectedIds = if (isTargetSelected) {
            selection.filter { it.value.id != targetId && (it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include) }.map { it.value.id }
        } else {
            selection.filter { it.value.id == targetId || it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }.map { it.value.id }
        }
        val newUnselectedIds = selection.map { it.value.id }.filterNot { newSelectedIds.contains(it) }
        onConfirm(newSelectedIds, newUnselectedIds)
        onDismissRequest()
    }

    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var createSubcategoryParentId by remember { mutableStateOf<Long?>(null) }

    val handleCreateCategory: (String, Long?) -> Unit = { name, parentId ->
        tachiyomi.core.common.util.lang.launchIO {
            try {
                val newCategory: Category? = when {
                    anime != null -> {
                        val createAnimeCat = Injekt.get<CreateAnimeCategoryWithName>()
                        val result = createAnimeCat.await(name, parentId)
                        if (result is CreateAnimeCategoryWithName.Result.Success) {
                            result.category
                        } else null
                    }
                    novel != null -> {
                        val createNovelCat = Injekt.get<CreateNovelCategoryWithName>()
                        val newId = createNovelCat.await(name, parentId)
                        if (newId != null) {
                            Category(
                                id = newId,
                                name = name,
                                order = (selection.maxOfOrNull { it.value.order } ?: 0L) + 1,
                                flags = 0L,
                                parentId = parentId,
                                hidden = false,
                            )
                        } else null
                    }
                    else -> {
                        val createMangaCat = Injekt.get<CreateCategoryWithName>()
                        val result = createMangaCat.await(name, parentId)
                        if (result is CreateCategoryWithName.Result.Success) {
                            result.category
                        } else null
                    }
                }
                if (newCategory != null) {
                    val mutableList = selection.toMutableList()
                    mutableList.add(CheckboxState.State.Checked(newCategory))
                    selection = mutableList.toList().toImmutableList()
                }
                onCreateCategory?.invoke(name, parentId)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to create category/subcategory" }
            }
        }
    }

    if (showCreateCategoryDialog) {
        CategoryCreateDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            onCreate = { name, parentId ->
                handleCreateCategory(name, parentId)
            },
            categories = selection.map { it.value.name }.toImmutableList(),
            parentOptions = parents.map { it.value }.toImmutableList(),
            initialParentId = null,
        )
    }

    if (createSubcategoryParentId != null) {
        val parentId = createSubcategoryParentId
        CategoryCreateDialog(
            onDismissRequest = { createSubcategoryParentId = null },
            onCreate = { name, selectedParentId ->
                val finalParentId = selectedParentId ?: parentId
                handleCreateCategory(name, finalParentId)
            },
            categories = selection.map { it.value.name }.toImmutableList(),
            parentOptions = parents.map { it.value }.toImmutableList(),
            initialParentId = parentId,
        )
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // KMK -->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (effectiveTracker != null && trackerTitle != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Box 1: Title
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = trackerTitle.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }

                        // Box 2: Status
                        Box {
                            val statusList = remember(effectiveTracker, anime) {
                                if (anime != null && effectiveTracker is AnimeTracker) {
                                    effectiveTracker.getStatusListAnime()
                                } else {
                                    effectiveTracker.getStatusList()
                                }
                            }
                            val currentRes = trackerStatus?.let {
                                if (anime != null && effectiveTracker is AnimeTracker) {
                                    effectiveTracker.getStatusForAnime(it)
                                } else {
                                    effectiveTracker.getStatus(it)
                                }
                            }
                            val defaultStatusText = if (anime != null) "Plan to watch" else "Plan to read"
                            val statusText = if (currentRes != null) stringResource(currentRes) else defaultStatusText

                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { showStatusDropdown = true },
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = showStatusDropdown,
                                onDismissRequest = { showStatusDropdown = false },
                            ) {
                                statusList.forEach { statusId ->
                                    val stringRes = if (anime != null && effectiveTracker is AnimeTracker) {
                                        effectiveTracker.getStatusForAnime(statusId)
                                    } else {
                                        effectiveTracker.getStatus(statusId)
                                    } ?: return@forEach
                                    val label = stringResource(stringRes)
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showStatusDropdown = false
                                            trackerStatus = statusId
                                            when {
                                                manga != null -> {
                                                    val existing = currentTrack
                                                    val currentManga = manga
                                                    if (existing != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val dbTrack = existing.toDbTrack()
                                                                effectiveTracker.setRemoteStatus(dbTrack, statusId)
                                                                insertTrack.await(existing.copy(status = statusId))
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed status update" }
                                                            }
                                                        }
                                                    } else if (currentManga != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                var searchResults = try {
                                                                    effectiveTracker.search(currentManga.title)
                                                                } catch (_: Exception) {
                                                                    emptyList()
                                                                }
                                                                var targetTracker: eu.kanade.tachiyomi.data.track.Tracker = effectiveTracker
                                                                if (searchResults.isEmpty()) {
                                                                    // Fallback to AniList
                                                                    val anilist = trackerManager.trackers.find { it.id == TrackerManager.ANILIST }
                                                                    if (anilist != null && anilist.id != effectiveTracker.id) {
                                                                        val alResults = try {
                                                                            anilist.search(currentManga.title)
                                                                        } catch (_: Exception) {
                                                                            emptyList()
                                                                        }
                                                                        if (alResults.isNotEmpty()) {
                                                                            searchResults = alResults
                                                                            targetTracker = anilist
                                                                        }
                                                                    }
                                                                }
                                                                val match = searchResults.firstOrNull() ?: return@launchIO
                                                                match.manga_id = currentManga.id
                                                                addTracks.bind(targetTracker, match, currentManga.id)
                                                                val newTracks = getTracks.await(currentManga.id)
                                                                val newlyAdded = newTracks.find { it.trackerId == targetTracker.id }
                                                                if (newlyAdded != null) {
                                                                    targetTracker.setRemoteStatus(newlyAdded.toDbTrack(), statusId)
                                                                    insertTrack.await(newlyAdded.copy(status = statusId))
                                                                    currentTrack = newlyAdded
                                                                }
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed to bind and set status" }
                                                            }
                                                        }
                                                    }
                                                }
                                                anime != null -> {
                                                    val existing = currentAnimeTrack
                                                    val currentAnime = anime
                                                    val animeTracker = effectiveTracker as? AnimeTracker
                                                    if (existing != null && animeTracker != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val dbTrack = existing.toDbTrack()
                                                                animeTracker.setRemoteAnimeStatus(dbTrack, statusId)
                                                                insertAnimeTrack.await(existing.copy(status = statusId))
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed anime status update" }
                                                            }
                                                        }
                                                    } else if (currentAnime != null && animeTracker != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val searchResults = try {
                                                                    animeTracker.searchAnime(currentAnime.title)
                                                                } catch (_: Exception) {
                                                                    emptyList()
                                                                }
                                                                val match = searchResults.firstOrNull() ?: return@launchIO
                                                                match.anime_id = currentAnime.id
                                                                addAnimeTracks.bind(animeTracker, match, currentAnime.id)
                                                                val newTracks = getAnimeTracks.await(currentAnime.id)
                                                                val newlyAdded = newTracks.find { it.trackerId == effectiveTracker.id }
                                                                if (newlyAdded != null) {
                                                                    animeTracker.setRemoteAnimeStatus(newlyAdded.toDbTrack(), statusId)
                                                                    insertAnimeTrack.await(newlyAdded.copy(status = statusId))
                                                                    currentAnimeTrack = newlyAdded
                                                                }
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed to bind anime and set status" }
                                                            }
                                                        }
                                                    }
                                                }
                                                novel != null -> {
                                                    val existing = currentNovelTrack
                                                    val currentNovel = novel
                                                    if (existing != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val dbTrack = existing.toDbTrack()
                                                                effectiveTracker.setRemoteStatus(dbTrack, statusId)
                                                                insertNovelTrack.await(existing.copy(status = statusId))
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed novel status update" }
                                                            }
                                                        }
                                                    } else if (currentNovel != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                var searchResults = try {
                                                                    effectiveTracker.search(currentNovel.title)
                                                                } catch (_: Exception) {
                                                                    emptyList()
                                                                }
                                                                var targetTracker: eu.kanade.tachiyomi.data.track.Tracker = effectiveTracker
                                                                if (searchResults.isEmpty()) {
                                                                    val anilist = trackerManager.trackers.find { it.id == TrackerManager.ANILIST }
                                                                    if (anilist != null && anilist.id != effectiveTracker.id) {
                                                                        val alResults = try {
                                                                            anilist.search(currentNovel.title)
                                                                        } catch (_: Exception) {
                                                                            emptyList()
                                                                        }
                                                                        if (alResults.isNotEmpty()) {
                                                                            searchResults = alResults
                                                                            targetTracker = anilist
                                                                        }
                                                                    }
                                                                }
                                                                val match = searchResults.firstOrNull() ?: return@launchIO
                                                                match.manga_id = currentNovel.id
                                                                addNovelTracks.bind(targetTracker, match, currentNovel.id)
                                                                val newTracks = getNovelTracks.await(currentNovel.id)
                                                                val newlyAdded = newTracks.find { it.trackerId == targetTracker.id }
                                                                if (newlyAdded != null) {
                                                                    targetTracker.setRemoteStatus(newlyAdded.toDbTrack(), statusId)
                                                                    insertNovelTrack.await(newlyAdded.copy(status = statusId))
                                                                    currentNovelTrack = newlyAdded
                                                                }
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed to bind novel and set status" }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Edit / Search Button
                        if (onOpenTrackerSearch != null) {
                            IconButton(
                                onClick = onOpenTrackerSearch,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Edit,
                                    contentDescription = "Edit Tracker",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(MR.strings.action_move_category),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (onDuplicateCheck != null) {
                    IconButton(onClick = onDuplicateCheck) {
                        Icon(
                            imageVector = Icons.Outlined.CopyAll,
                            contentDescription = "Duplicate Check",
                        )
                    }
                }

                IconButton(onClick = { showCreateCategoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(MR.strings.action_add_category),
                    )
                }
            }

            if (isMetadataLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Fetching external details…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (externalMetadata != null) {
                val meta = externalMetadata!!
                val suggestedTags = remember(meta) {
                    (meta.genres + meta.tags + listOfNotNull(meta.demographic)).distinct().filter { it.isNotBlank() }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMetadataExpanded = !isMetadataExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        androidx.compose.foundation.layout.FlowRow(
                            verticalArrangement = Arrangement.Center,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            val score = meta.score
                            if (score != null && score > 0.0) {
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.2f", score)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (!meta.status.isNullOrBlank()) {
                                Text(
                                    text = "• ${meta.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            val totalChapters = meta.totalChapters
                            if (totalChapters != null && totalChapters > 0) {
                                Text(
                                    text = "• $totalChapters ch.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Text(
                                text = "(${meta.sourceName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        Icon(
                            imageVector = if (isMetadataExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val synopsis = meta.synopsis
                    if (!synopsis.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSynopsisExpanded = !isSynopsisExpanded },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = synopsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isSynopsisExpanded) "Show less ▲" else "Show more ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = isMetadataExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (suggestedTags.isNotEmpty()) {
                                Text(
                                    text = stringResource(tachiyomi.i18n.kmk.KMR.strings.external_metadata_suggested_categories),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    suggestedTags.take(12).forEach { tag ->
                                        val matchingCategory = selection.find { it.value.name.equals(tag, ignoreCase = true) }
                                        val isSelected = matchingCategory is CheckboxState.State.Checked || matchingCategory is CheckboxState.TriState.Include

                                        androidx.compose.material3.SuggestionChip(
                                            onClick = {
                                                scope.launch {
                                                    if (matchingCategory != null) {
                                                        val index = selection.indexOfFirst { it.value.id == matchingCategory.value.id }
                                                        if (index != -1) {
                                                            val mutableList = selection.toMutableList()
                                                            mutableList[index] = matchingCategory.next()
                                                            selection = mutableList.toList().toImmutableList()
                                                        }
                                                    } else {
                                                        val newCat: Category? = when {
                                                            anime != null -> {
                                                                val createAnimeCat = Injekt.get<CreateAnimeCategoryWithName>()
                                                                val result = createAnimeCat.await(tag)
                                                                if (result is CreateAnimeCategoryWithName.Result.Success) {
                                                                    result.category
                                                                } else null
                                                            }
                                                            novel != null -> {
                                                                val createNovelCat = Injekt.get<CreateNovelCategoryWithName>()
                                                                val newId = createNovelCat.await(tag)
                                                                if (newId != null) {
                                                                    Category(
                                                                        id = newId,
                                                                        name = tag,
                                                                        order = (selection.maxOfOrNull { it.value.order } ?: 0L) + 1,
                                                                        flags = 0L,
                                                                        parentId = null,
                                                                        hidden = false,
                                                                    )
                                                                } else null
                                                            }
                                                            else -> {
                                                                val result = createCategoryWithName.await(tag)
                                                                if (result is CreateCategoryWithName.Result.Success) {
                                                                    result.category
                                                                } else null
                                                            }
                                                        }
                                                        if (newCat != null) {
                                                            val mutableList = selection.toMutableList()
                                                            mutableList.add(CheckboxState.State.Checked(newCat))
                                                            selection = mutableList.toList().toImmutableList()
                                                        }
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = if (isSelected) "✓ $tag" else "+ $tag",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                                )
                                            },
                                            colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            ),
                                            border = null,
                                            modifier = Modifier.height(26.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // KMK <--

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parents.forEach { parentEntry ->
                    val parent = parentEntry.value
                    val subcategories = childrenByParent[parent.id].orEmpty()
                    val isParentChecked = when (parentEntry) {
                        is CheckboxState.TriState -> parentEntry is CheckboxState.TriState.Include
                        is CheckboxState.State -> parentEntry.isChecked
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDirectToggle(parent.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (parentEntry) {
                                is CheckboxState.TriState -> {
                                    TriStateCheckbox(
                                        state = parentEntry.asToggleableState(),
                                        onClick = { onChange(parentEntry) },
                                    )
                                }
                                is CheckboxState.State -> {
                                    Checkbox(
                                        checked = parentEntry.isChecked,
                                        onCheckedChange = { onChange(parentEntry) },
                                    )
                                }
                            }

                            Text(
                                text = parent.visualName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isParentChecked) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(
                                onClick = { createSubcategoryParentId = parent.id },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Subcategory",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (subcategories.isNotEmpty()) {
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                subcategories.forEach { subEntry ->
                                    val sub = subEntry.value
                                    val isChecked = when (subEntry) {
                                        is CheckboxState.TriState -> subEntry is CheckboxState.TriState.Include
                                        is CheckboxState.State -> subEntry.isChecked
                                    }

                                    androidx.compose.material3.Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { onDirectToggle(sub.id) },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            // Check button to toggle without closing
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onChange(subEntry) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                when (subEntry) {
                                                    is CheckboxState.TriState.Include -> {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                    }
                                                    is CheckboxState.TriState.Exclude -> {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.error,
                                                        )
                                                    }
                                                    is CheckboxState.State -> {
                                                        if (subEntry.isChecked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .border(
                                                                        width = 1.5.dp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                        shape = RoundedCornerShape(3.dp),
                                                                    ),
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .border(
                                                                    width = 1.5.dp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                    shape = RoundedCornerShape(3.dp),
                                                                ),
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = sub.visualName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isChecked) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tachiyomi.presentation.core.components.material.TextButton(onClick = {
                    onDismissRequest()
                    onEditCategories()
                }) {
                    Text(text = stringResource(MR.strings.action_edit))
                }
                val deleteAction = onDelete ?: onDeleteManga
                if (deleteAction != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    tachiyomi.presentation.core.components.material.TextButton(onClick = {
                        onDismissRequest()
                        deleteAction()
                    }) {
                        Text(
                            text = stringResource(MR.strings.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm(
                            selection
                                .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
                                .map { it.value.id },
                            selection
                                .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                .map { it.value.id },
                        )
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        }
    }
}
