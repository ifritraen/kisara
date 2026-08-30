package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import tachiyomi.domain.scoring.interactor.DeleteGranularTemplate
import tachiyomi.domain.scoring.interactor.GetGranularTemplates
import tachiyomi.domain.scoring.interactor.SaveGranularTemplate
import tachiyomi.domain.scoring.model.GranularScoreTemplate
import tachiyomi.domain.scoring.model.GranularTemplateCriterion
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class SettingsGranularScoringScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val getGranularTemplates: GetGranularTemplates = remember { Injekt.get() }
        val saveGranularTemplate: SaveGranularTemplate = remember { Injekt.get() }
        val deleteGranularTemplate: DeleteGranularTemplate = remember { Injekt.get() }

        val templates by getGranularTemplates.subscribe().collectAsState(initial = emptyList())

        var editingTemplate by remember { mutableStateOf<GranularScoreTemplate?>(null) }
        var isCreatingNew by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Granular Scoring Templates") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        isCreatingNew = true
                        editingTemplate = GranularScoreTemplate(
                            id = 0L,
                            name = "",
                            mediaType = "ALL",
                            criteria = listOf(
                                GranularTemplateCriterion("story", "Story", 20.0),
                                GranularTemplateCriterion("characters", "Characters", 20.0),
                                GranularTemplateCriterion("art", "Art & Visuals", 20.0),
                                GranularTemplateCriterion("pacing", "Pacing & Flow", 20.0),
                                GranularTemplateCriterion("enjoyment", "Personal Enjoyment", 20.0),
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Template")
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Manage scoring criteria and templates. These templates define the default categories and weight distribution available when rating manga, anime, and novels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(templates, key = { "${it.id}_${it.name}" }) { template ->
                    val isBuiltin = template.id < 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (isBuiltin) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = "Built-in",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            isCreatingNew = false
                                            editingTemplate = template
                                        },
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit / View")
                                    }
                                    if (!isBuiltin) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    deleteGranularTemplate.await(template.id)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${template.criteria.size} criteria (Total weight: ${template.criteria.sumOf { it.weight }.toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Criteria chip summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = template.criteria.take(6).joinToString(", ") { "${it.name} (${it.weight.toInt()}%)" } +
                                        if (template.criteria.size > 6) " +${template.criteria.size - 6} more" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Edit / Create Template Dialog
        editingTemplate?.let { currentTmpl ->
            val isBuiltin = currentTmpl.id < 0
            var name by remember { mutableStateOf(currentTmpl.name) }
            val criteriaList = remember { mutableStateListOf(*currentTmpl.criteria.toTypedArray()) }

            AlertDialog(
                onDismissRequest = { editingTemplate = null },
                title = {
                    Text(if (isBuiltin) "View Template: ${currentTmpl.name}" else if (isCreatingNew) "New Template" else "Edit Template")
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!isBuiltin) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Template Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Text(
                            text = "Criteria (Total: ${criteriaList.sumOf { it.weight }.toInt()}%):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(criteriaList.size) { idx ->
                                val crit = criteriaList[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = crit.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${crit.weight.toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    if (!isBuiltin) {
                                        IconButton(
                                            onClick = { criteriaList.removeAt(idx) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isBuiltin) {
                        Button(
                            onClick = {
                                val finalTemplate = currentTmpl.copy(
                                    name = name.trim(),
                                    criteria = criteriaList.toList(),
                                )
                                scope.launch {
                                    saveGranularTemplate.await(finalTemplate)
                                }
                                editingTemplate = null
                            },
                            enabled = name.isNotBlank() && criteriaList.isNotEmpty(),
                        ) {
                            Text("Save")
                        }
                    } else {
                        Button(onClick = { editingTemplate = null }) {
                            Text("Close")
                        }
                    }
                },
                dismissButton = {
                    if (!isBuiltin) {
                        TextButton(onClick = { editingTemplate = null }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }
    }
}
// KMK <--
