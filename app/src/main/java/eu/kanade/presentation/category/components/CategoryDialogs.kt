package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CopyAll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
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

    val parents = remember(selection) {
        selection.filter { it.value.parentId == null }
    }
    val childrenByParent = remember(selection) {
        selection.filter { it.value.parentId != null }
            .groupBy { it.value.parentId }
    }

    val onChange: (CheckboxState<Category>) -> Unit = {
        val index = selection.indexOf(it)
        if (index != -1) {
            val mutableList = selection.toMutableList()
            mutableList[index] = it.next()
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
                .padding(28.dp)
                .wrapContentHeight(),
        ) {
            GlassSurface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                style = GlassDefaults.prominentStyle(),
                dialogSurface = true,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // KMK -->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.action_move_category),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (onDuplicateCheck != null) {
                            IconButton(onClick = onDuplicateCheck) {
                                Icon(
                                    imageVector = Icons.Outlined.CopyAll,
                                    contentDescription = "Duplicate Check",
                                )
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

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChange(parentEntry) },
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
                                                modifier = Modifier.clickable { onChange(subEntry) },
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Text(
                                                        text = sub.visualName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    androidx.compose.material3.IconButton(
                                                        onClick = { onDirectAdd(sub.id) },
                                                        modifier = Modifier.size(20.dp),
                                                    ) {
                                                        androidx.compose.material3.Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Direct Add",
                                                            modifier = Modifier.size(12.dp),
                                                            tint = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
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
