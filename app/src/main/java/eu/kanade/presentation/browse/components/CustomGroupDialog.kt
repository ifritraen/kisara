// KMK -->
package eu.kanade.presentation.browse.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.source.model.CustomSearchGroup
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

@Composable
fun CustomGroupManagerDialog(
    groups: List<CustomSearchGroup>,
    activeGroupId: String,
    onSelectGroup: (String) -> Unit,
    onSaveGroup: (CustomSearchGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var editingGroup by remember { mutableStateOf<CustomSearchGroup?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (editingGroup != null || isCreatingNew) {
        CustomGroupEditDialog(
            group = editingGroup ?: CustomSearchGroup(
                id = UUID.randomUUID().toString(),
                name = "",
                sourceIds = emptySet(),
            ),
            onSave = { group ->
                onSaveGroup(group)
                editingGroup = null
                isCreatingNew = false
            },
            onDismissRequest = {
                editingGroup = null
                isCreatingNew = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(KMR.strings.custom_groups))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.custom_group_no_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    ) {
                        items(groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectGroup(group.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = group.id == activeGroupId,
                                    onClick = { onSelectGroup(group.id) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${group.sourceIds.size} sources",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editingGroup = group }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(KMR.strings.action_edit_custom_group),
                                    )
                                }
                                IconButton(onClick = { onDeleteGroup(group.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { isCreatingNew = true },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(KMR.strings.action_new_custom_group))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(tachiyomi.i18n.MR.strings.action_ok))
            }
        },
    )
}

@Composable
fun CustomGroupEditDialog(
    group: CustomSearchGroup,
    onSave: (CustomSearchGroup) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var selectedSourceIds by remember { mutableStateOf(group.sourceIds) }
    var searchQuery by remember { mutableStateOf("") }

    val extensionManager = remember { Injekt.get<ExtensionManager>() }
    val installedExtensions = remember {
        extensionManager.installedExtensionsFlow.value.filterIsInstance<Extension.Installed>()
    }

    val filteredExtensions = remember(searchQuery, installedExtensions) {
        if (searchQuery.isBlank()) {
            installedExtensions
        } else {
            installedExtensions.filter { ext ->
                ext.name.contains(searchQuery, ignoreCase = true) ||
                    ext.sources.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = if (group.name.isEmpty()) {
                    stringResource(KMR.strings.action_new_custom_group)
                } else {
                    stringResource(KMR.strings.action_edit_custom_group)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(KMR.strings.custom_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(text = stringResource(KMR.strings.action_search_for_source)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(KMR.strings.custom_group_select_sources),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    items(filteredExtensions, key = { it.pkgName }) { extension ->
                        ExtensionSourceItem(
                            extension = extension,
                            selectedSourceIds = selectedSourceIds,
                            onToggleExtension = { ext, isChecked ->
                                val extSourceIds = ext.sources.filterIsInstance<CatalogueSource>().map { it.id }.toSet()
                                selectedSourceIds = if (isChecked) {
                                    selectedSourceIds + extSourceIds
                                } else {
                                    selectedSourceIds - extSourceIds
                                }
                            },
                            onToggleSource = { sourceId, isChecked ->
                                selectedSourceIds = if (isChecked) {
                                    selectedSourceIds + sourceId
                                } else {
                                    selectedSourceIds - sourceId
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(group.copy(name = name.trim(), sourceIds = selectedSourceIds))
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(tachiyomi.i18n.MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(tachiyomi.i18n.MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun ExtensionSourceItem(
    extension: Extension.Installed,
    selectedSourceIds: Set<Long>,
    onToggleExtension: (Extension.Installed, Boolean) -> Unit,
    onToggleSource: (Long, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val catalogueSources = remember(extension) {
        extension.sources.filterIsInstance<CatalogueSource>()
    }
    val sourceIds = remember(catalogueSources) {
        catalogueSources.map { it.id }.toSet()
    }
    val isAllSelected = sourceIds.isNotEmpty() && sourceIds.all { it in selectedSourceIds }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isAllSelected,
                onCheckedChange = { checked -> onToggleExtension(extension, checked) },
            )
            Spacer(modifier = Modifier.width(4.dp))
            ExtensionIcon(
                extension = extension,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val extLangText = remember(extension.lang) {
                val display = eu.kanade.tachiyomi.util.system.LocaleHelper.getSourceDisplayName(extension.lang, context)
                val emoji = tachiyomi.presentation.core.icons.FlagEmoji.getEmojiLangFlag(extension.lang)
                if (emoji.isNotBlank()) "$display $emoji" else display
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (catalogueSources.size > 1) "${catalogueSources.size} sources • $extLangText" else extLangText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (catalogueSources.size > 1) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded && catalogueSources.size > 1) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(modifier = Modifier.padding(start = 32.dp)) {
                catalogueSources.forEach { source ->
                    val isChecked = source.id in selectedSourceIds
                    val sourceLangText = remember(source.lang) {
                        val display = eu.kanade.tachiyomi.util.system.LocaleHelper.getSourceDisplayName(source.lang, context)
                        val emoji = tachiyomi.presentation.core.icons.FlagEmoji.getEmojiLangFlag(source.lang)
                        if (emoji.isNotBlank()) "$display $emoji" else display
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSource(source.id, !isChecked) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked -> onToggleSource(source.id, checked) },
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        SourceIcon(
                            source = tachiyomi.domain.source.model.Source(
                                id = source.id,
                                lang = source.lang,
                                name = source.name,
                                supportsLatest = source.supportsLatest,
                                isStub = false,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = sourceLangText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
