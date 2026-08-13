package eu.kanade.presentation.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
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
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Column {
                extraMessage?.let { Text(it) }
                // SY <--

                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    supportingText = {
                        val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                            // SY -->
                            alreadyExistsError
                            // SY <--
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
                // SY -->
            }
            // SY <--
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = hasChanges,
                onClick = {
                    onRename(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
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
    title: String = stringResource(MR.strings.delete_category),
    text: String = stringResource(MR.strings.delete_category_confirmation, category),
    // SY <--
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Text(text = text)
            // SY <--
        },
    )
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
    manga: tachiyomi.domain.manga.model.Manga? = null,
    onOpenTrackerSearch: (() -> Unit)? = null,
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

    var selection by remember { mutableStateOf(initialSelection) }

    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val trackPreferences = remember { Injekt.get<TrackPreferences>() }
    val getTracks = remember { Injekt.get<GetTracks>() }
    val insertTrack = remember { Injekt.get<InsertTrack>() }
    val addTracks = remember { Injekt.get<AddTracks>() }
    val primaryTracker: Tracker? = remember { trackPreferences.getPrimaryTracker(trackerManager) }
    val getMangaExternalMetadata = remember { Injekt.get<tachiyomi.domain.manga.interactor.GetMangaExternalMetadata>() }
    val fetchExternalMetadata = remember { Injekt.get<eu.kanade.domain.manga.interactor.FetchExternalMetadata>() }
    val createCategoryWithName = remember { Injekt.get<tachiyomi.domain.category.interactor.CreateCategoryWithName>() }

    var externalMetadata by remember { mutableStateOf<tachiyomi.domain.manga.model.MangaExternalMetadata?>(null) }
    var isMetadataExpanded by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    var trackerTitle by remember { mutableStateOf<String?>(null) }
    var currentTrack by remember { mutableStateOf<tachiyomi.domain.track.model.Track?>(null) }
    var trackerStatus by remember { mutableStateOf<Long?>(null) }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(manga) {
        val currentManga = manga ?: return@LaunchedEffect
        val cached = getMangaExternalMetadata.await(currentManga.id)
        if (cached != null) {
            externalMetadata = cached
        } else {
            val fetched = fetchExternalMetadata.await(currentManga)
            externalMetadata = fetched
        }
    }

    LaunchedEffect(manga, primaryTracker) {
        val pTracker = primaryTracker
        val currentManga = manga
        if (pTracker != null && pTracker.isLoggedIn && currentManga != null) {
            val tracks = try {
                getTracks.await(currentManga.id)
            } catch (e: Exception) {
                emptyList()
            }
            val existing = tracks.find { it.trackerId == pTracker.id }
            if (existing != null) {
                currentTrack = existing
                trackerTitle = existing.title.ifBlank { currentManga.title }
                trackerStatus = existing.status
            } else {
                try {
                    val searchResults = pTracker.search(currentManga.title)
                    val match = searchResults.firstOrNull { it.title.equals(currentManga.title, ignoreCase = true) }
                        ?: searchResults.firstOrNull()
                    if (match != null) {
                        trackerTitle = match.title
                        trackerStatus = pTracker.getReadingStatus()
                    }
                } catch (e: Exception) {
                    // Ignore search errors
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

    val onDirectAdd: (Long) -> Unit = { targetId ->
        onConfirm(
            selection.filter { (it.value.id == targetId) || it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }.map { it.value.id },
            selection.filter { (it.value.id != targetId) && (it is CheckboxState.State.None || it is CheckboxState.TriState.None) }.map { it.value.id },
        )
        onDismissRequest()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window?.let {
                    it.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    it.attributes.blurBehindRadius = 60
                    it.setDimAmount(0.15f)
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .wrapContentHeight(),
        ) {
            GlassSurface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                style = GlassDefaults.prominentStyle(),
                dialogSurface = true,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Drag Handle (Anymex style)
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally),
                    )

                    // KMK -->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (primaryTracker != null && primaryTracker.isLoggedIn && trackerTitle != null) {
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
                                    val statusList = remember(primaryTracker) { primaryTracker.getStatusList() }
                                    val currentRes = trackerStatus?.let { primaryTracker.getStatus(it) }
                                    val statusText = if (currentRes != null) stringResource(currentRes) else "Plan to read"

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
                                            val stringRes = primaryTracker.getStatus(statusId) ?: return@forEach
                                            val label = stringResource(stringRes)
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    showStatusDropdown = false
                                                    trackerStatus = statusId
                                                    val existing = currentTrack
                                                    val currentManga = manga
                                                    if (existing != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val dbTrack = existing.toDbTrack()
                                                                primaryTracker.setRemoteStatus(dbTrack, statusId)
                                                                insertTrack.await(existing.copy(status = statusId))
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed status update" }
                                                            }
                                                        }
                                                    } else if (currentManga != null) {
                                                        tachiyomi.core.common.util.lang.launchIO {
                                                            try {
                                                                val searchResults = primaryTracker.search(currentManga.title)
                                                                val match = searchResults.firstOrNull() ?: return@launchIO
                                                                match.manga_id = currentManga.id
                                                                addTracks.bind(primaryTracker, match, currentManga.id)
                                                                val newTracks = getTracks.await(currentManga.id)
                                                                val newlyAdded = newTracks.find { it.trackerId == primaryTracker.id }
                                                                if (newlyAdded != null) {
                                                                    primaryTracker.setRemoteStatus(newlyAdded.toDbTrack(), statusId)
                                                                    insertTrack.await(newlyAdded.copy(status = statusId))
                                                                    currentTrack = newlyAdded
                                                                }
                                                            } catch (e: Exception) {
                                                                logcat(LogPriority.WARN, e) { "Failed to bind and set status" }
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
                    }

                    if (externalMetadata != null) {
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
                                                                val result = createCategoryWithName.await(tag)
                                                                if (result is tachiyomi.domain.category.interactor.CreateCategoryWithName.Result.Success) {
                                                                    val newCat = result.category
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        parents.forEach { parentEntry ->
                            val parent = parentEntry.value
                            val subcategories = childrenByParent[parent.id].orEmpty()
                            val isParentChecked = when (parentEntry) {
                                is CheckboxState.TriState -> parentEntry is CheckboxState.TriState.Include
                                is CheckboxState.State -> parentEntry.isChecked
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isParentChecked) {
                                                onUnselect(parentEntry)
                                            } else {
                                                onChange(parentEntry)
                                            }
                                        },
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

                                    androidx.compose.material3.IconButton(
                                        onClick = { onDirectAdd(parent.id) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Direct Add",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
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
                                                modifier = Modifier.clickable {
                                                    if (isChecked) {
                                                        onUnselect(subEntry)
                                                    } else {
                                                        onChange(subEntry)
                                                    }
                                                },
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    // Check button
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
                        if (onDeleteManga != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            tachiyomi.presentation.core.components.material.TextButton(onClick = {
                                onDismissRequest()
                                onDeleteManga()
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
    }
}
