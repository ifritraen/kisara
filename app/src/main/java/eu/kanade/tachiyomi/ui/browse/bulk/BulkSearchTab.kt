package eu.kanade.tachiyomi.ui.browse.bulk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Screen.bulkSearchTab(): TabContent {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { SourcesScreenModel(smartSearchConfig = null) }
    val state by screenModel.state.collectAsState()

    // Selection state
    val selectedSourceIds = remember { mutableStateListOf<Long>() }

    // Dialog trigger states
    var queryInputDialogSources by remember { mutableStateOf<List<Source>?>(null) }
    var saveTemplateDialogSources by remember { mutableStateOf<List<Source>?>(null) }
    var templateToDelete by remember { mutableStateOf<String?>(null) }
    var renameTemplateDialogTarget by remember { mutableStateOf<BulkSearchTemplate?>(null) }
    var renameTemplateText by remember { mutableStateOf("") }

    // Templates state
    var templates by remember { mutableStateOf(emptyList<BulkSearchTemplate>()) }
    LaunchedEffect(Unit) {
        templates = BulkSearchTemplates.getTemplates(context)
    }

    return TabContent(
        titleRes = KMR.strings.bulk_search,
        content = { contentPadding, snackbarHostState ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding()),
            ) {
                if (state.isLoading) {
                    LoadingScreen()
                } else if (state.isEmpty) {
                    EmptyScreen(
                        message = stringResource(MR.strings.source_empty_screen),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. Templates Header & Horizontal Scroll List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Search Templates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            if (templates.isEmpty()) {
                                Text(
                                    text = "No saved templates. Long press sources to create one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    templates.forEach { template ->
                                        ElevatedAssistChip(
                                            onClick = {
                                                val matchedSources = state.items
                                                    .filterIsInstance<SourceUiModel.Item>()
                                                    .map { it.source }
                                                    .filter { template.sourceIds.contains(it.id) }
                                                if (matchedSources.isNotEmpty()) {
                                                    queryInputDialogSources = matchedSources
                                                }
                                            },
                                            label = {
                                                Box(
                                                    modifier = Modifier.combinedClickable(
                                                        onClick = {
                                                            val matchedSources = state.items
                                                                .filterIsInstance<SourceUiModel.Item>()
                                                                .map { it.source }
                                                                .filter { template.sourceIds.contains(it.id) }
                                                            if (matchedSources.isNotEmpty()) {
                                                                queryInputDialogSources = matchedSources
                                                            }
                                                        },
                                                        onLongClick = {
                                                            renameTemplateDialogTarget = template
                                                            renameTemplateText = template.name
                                                        },
                                                    ),
                                                ) {
                                                    Text(template.name)
                                                }
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = { templateToDelete = template.name },
                                                    modifier = Modifier.size(16.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Delete Template",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Sources list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = if (selectedSourceIds.isNotEmpty()) {
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 140.dp
                                } else {
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                                },
                            ),
                        ) {
                            items(
                                items = state.items,
                                key = { item ->
                                    when (item) {
                                        is SourceUiModel.Header -> "header-${item.language}-${item.hashCode()}"
                                        is SourceUiModel.Item -> "source-${item.source.key()}"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is SourceUiModel.Header -> {
                                        Text(
                                            text = LocaleHelper.getSourceDisplayName(item.language, context),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                    is SourceUiModel.Item -> {
                                        val source = item.source
                                        val isSelected = selectedSourceIds.contains(source.id)
                                        val isMultiSelectActive = selectedSourceIds.isNotEmpty()

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isMultiSelectActive) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked == true) {
                                                            selectedSourceIds.add(source.id)
                                                        } else {
                                                            selectedSourceIds.remove(source.id)
                                                        }
                                                    },
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            BaseSourceItem(
                                                source = source,
                                                showLanguageInContent = false,
                                                modifier = Modifier.weight(1f),
                                                onClickItem = {
                                                    if (isMultiSelectActive) {
                                                        if (isSelected) {
                                                            selectedSourceIds.remove(source.id)
                                                        } else {
                                                            selectedSourceIds.add(source.id)
                                                        }
                                                    } else {
                                                        queryInputDialogSources = listOf(source)
                                                    }
                                                },
                                                onLongClickItem = {
                                                    if (isSelected) {
                                                        selectedSourceIds.remove(source.id)
                                                    } else {
                                                        selectedSourceIds.add(source.id)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Multi-Select Bottom Bar
                        if (selectedSourceIds.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 16.dp,
                                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${selectedSourceIds.size} sources selected",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        TextButton(
                                            onClick = { selectedSourceIds.clear() },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                val matchedSources = state.items
                                                    .filterIsInstance<SourceUiModel.Item>()
                                                    .map { it.source }
                                                    .filter { selectedSourceIds.contains(it.id) }
                                                saveTemplateDialogSources = matchedSources
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            ),
                                        ) {
                                            Icon(Icons.Outlined.Save, contentDescription = "Save Template")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save")
                                        }
                                        Button(
                                            onClick = {
                                                val matchedSources = state.items
                                                    .filterIsInstance<SourceUiModel.Item>()
                                                    .map { it.source }
                                                    .filter { selectedSourceIds.contains(it.id) }
                                                queryInputDialogSources = matchedSources
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Outlined.Search, contentDescription = "Start Bulk Search")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Search")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Dialogs ---

                // Query Input Dialog
                if (queryInputDialogSources != null) {
                    val sources = queryInputDialogSources!!
                    BulkQueryInputDialog(
                        sources = sources,
                        onDismissRequest = { queryInputDialogSources = null },
                        onConfirm = { parsedQueries ->
                            queryInputDialogSources = null
                            selectedSourceIds.clear()
                            navigator.push(
                                BulkSearchScreen(
                                    sourceIds = sources.map { it.id },
                                    queries = parsedQueries,
                                ),
                            )
                        },
                    )
                }

                // Save Template Dialog
                if (saveTemplateDialogSources != null) {
                    val sources = saveTemplateDialogSources!!
                    var templateName by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { saveTemplateDialogSources = null },
                        title = { Text("Save Group Template") },
                        text = {
                            Column {
                                Text("Save these ${sources.size} sources as a template:")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = templateName,
                                    onValueChange = { templateName = it },
                                    label = { Text("Template Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (templateName.isNotBlank()) {
                                        BulkSearchTemplates.saveTemplate(
                                            context,
                                            BulkSearchTemplate(
                                                name = templateName.trim(),
                                                sourceIds = sources.map { it.id },
                                            ),
                                        )
                                        templates = BulkSearchTemplates.getTemplates(context)
                                        saveTemplateDialogSources = null
                                    }
                                },
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { saveTemplateDialogSources = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                // Delete Template Confirm Dialog
                if (templateToDelete != null) {
                    val name = templateToDelete!!
                    AlertDialog(
                        onDismissRequest = { templateToDelete = null },
                        title = { Text("Delete Template") },
                        text = { Text("Are you sure you want to delete template '$name'?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    BulkSearchTemplates.deleteTemplate(context, name)
                                    templates = BulkSearchTemplates.getTemplates(context)
                                    templateToDelete = null
                                },
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { templateToDelete = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                // Rename Template Dialog
                if (renameTemplateDialogTarget != null) {
                    val target = renameTemplateDialogTarget!!
                    AlertDialog(
                        onDismissRequest = { renameTemplateDialogTarget = null },
                        title = { Text("Rename Template") },
                        text = {
                            Column {
                                Text("Enter a new name for template '${target.name}':")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = renameTemplateText,
                                    onValueChange = { renameTemplateText = it },
                                    label = { Text("New Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (renameTemplateText.isNotBlank()) {
                                        BulkSearchTemplates.renameTemplate(context, target.name, renameTemplateText.trim())
                                        templates = BulkSearchTemplates.getTemplates(context)
                                        renameTemplateDialogTarget = null
                                    }
                                },
                            ) {
                                Text("Rename")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { renameTemplateDialogTarget = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        },
    )
}
