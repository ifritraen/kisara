package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.domain.scoring.model.GranularScoreCalculator
import tachiyomi.domain.scoring.model.GranularScoreCriterion
import tachiyomi.domain.scoring.model.GranularScoreEntry
import tachiyomi.domain.scoring.model.GranularScoreTemplate
import tachiyomi.domain.scoring.model.GranularTemplateCriterion
import kotlin.math.roundToInt

// KMK -->
/**
 * Granular Scoring Card (AniScore engine) on the Manga/Anime details screen.
 * Expands on tap to show detailed multi-criteria sliders and custom template controls.
 */
@Composable
fun GranularScoreCard(
    entry: GranularScoreEntry?,
    availableTemplates: List<GranularScoreTemplate>,
    onSaveScore: (GranularScoreEntry) -> Unit,
    onSaveNewTemplate: (GranularScoreTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var quickEditCriterion by remember { mutableStateOf<GranularScoreCriterion?>(null) }

    // Fallback template is Advanced if not yet scored
    val currentTemplateName = entry?.templateName ?: "Advanced"
    val criteria = remember(entry) {
        if (entry != null && entry.criteria.isNotEmpty()) {
            entry.criteria
        } else {
            val template = availableTemplates.firstOrNull { it.name.equals(currentTemplateName, ignoreCase = true) }
                ?: availableTemplates.firstOrNull()
            template?.criteria?.map {
                GranularScoreCriterion(
                    id = it.id,
                    name = it.name,
                    score = 0.0,
                    weight = it.weight,
                )
            } ?: emptyList()
        }
    }

    val ignoreUnrated = entry?.ignoreUnrated ?: true
    val autoSyncTracker = entry?.autoSyncTracker ?: true

    val (totalScore, scale10) = remember(criteria, ignoreUnrated) {
        GranularScoreCalculator.calculate(criteria, ignoreUnrated)
    }

    val scoredCount = criteria.count { it.score > 0.0 && !it.isExcluded }
    val totalActiveCount = criteria.count { !it.isExcluded }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(tween(250)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Always visible, tap expands/collapses)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (scale10 > 0.0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (scale10 > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (scale10 > 0.0) "★ $scale10 / 10" else "Granular Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (scale10 > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (totalScore > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "$totalScore%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "$currentTemplateName • $scoredCount/$totalActiveCount rated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expanded Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toolbar / Control Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Template Switcher Button
                        Box {
                            FilledTonalButton(onClick = { showTemplateMenu = true }) {
                                Text(text = "Template: $currentTemplateName", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = showTemplateMenu,
                                onDismissRequest = { showTemplateMenu = false },
                            ) {
                                availableTemplates.forEach { tmpl ->
                                    DropdownMenuItem(
                                        text = { Text(tmpl.name) },
                                        onClick = {
                                            showTemplateMenu = false
                                            val newCriteria = tmpl.criteria.map {
                                                GranularScoreCriterion(
                                                    id = it.id,
                                                    name = it.name,
                                                    score = 0.0,
                                                    weight = it.weight,
                                                )
                                            }
                                            val (newTotal, newScale10) = GranularScoreCalculator.calculate(newCriteria, ignoreUnrated)
                                            onSaveScore(
                                                GranularScoreEntry(
                                                    mangaId = entry?.mangaId ?: 0L,
                                                    templateName = tmpl.name,
                                                    criteria = newCriteria,
                                                    totalScore = newTotal,
                                                    scale10Score = newScale10,
                                                    ignoreUnrated = ignoreUnrated,
                                                    autoSyncTracker = autoSyncTracker,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add Criterion", tint = MaterialTheme.colorScheme.primary)
                            }

                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Options")
                                }

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Save as New Template") },
                                        onClick = {
                                            showOptionsMenu = false
                                            showSaveTemplateDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reset All Scores") },
                                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                                        onClick = {
                                            showOptionsMenu = false
                                            val resetCriteria = criteria.map { it.copy(score = 0.0) }
                                            onSaveScore(
                                                GranularScoreEntry(
                                                    mangaId = entry?.mangaId ?: 0L,
                                                    templateName = currentTemplateName,
                                                    criteria = resetCriteria,
                                                    totalScore = 0.0,
                                                    scale10Score = 0.0,
                                                    ignoreUnrated = ignoreUnrated,
                                                    autoSyncTracker = autoSyncTracker,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Toggles Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Proportional unrated average",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ignoreUnrated,
                            onCheckedChange = { checked ->
                                val (newTotal, newScale10) = GranularScoreCalculator.calculate(criteria, checked)
                                onSaveScore(
                                    GranularScoreEntry(
                                        mangaId = entry?.mangaId ?: 0L,
                                        templateName = currentTemplateName,
                                        criteria = criteria,
                                        totalScore = newTotal,
                                        scale10Score = newScale10,
                                        ignoreUnrated = checked,
                                        autoSyncTracker = autoSyncTracker,
                                    ),
                                )
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Criteria Slider Rows
                    criteria.forEachIndexed { index, criterion ->
                        CriterionSliderRow(
                            criterion = criterion,
                            onScoreChange = { newScore ->
                                val updated = criteria.toMutableList()
                                updated[index] = criterion.copy(score = newScore)
                                val (newTotal, newScale10) = GranularScoreCalculator.calculate(updated, ignoreUnrated)
                                onSaveScore(
                                    GranularScoreEntry(
                                        mangaId = entry?.mangaId ?: 0L,
                                        templateName = currentTemplateName,
                                        criteria = updated,
                                        totalScore = newTotal,
                                        scale10Score = newScale10,
                                        ignoreUnrated = ignoreUnrated,
                                        autoSyncTracker = autoSyncTracker,
                                    ),
                                )
                            },
                            onToggleExcluded = {
                                val updated = criteria.toMutableList()
                                updated[index] = criterion.copy(isExcluded = !criterion.isExcluded)
                                val (newTotal, newScale10) = GranularScoreCalculator.calculate(updated, ignoreUnrated)
                                onSaveScore(
                                    GranularScoreEntry(
                                        mangaId = entry?.mangaId ?: 0L,
                                        templateName = currentTemplateName,
                                        criteria = updated,
                                        totalScore = newTotal,
                                        scale10Score = newScale10,
                                        ignoreUnrated = ignoreUnrated,
                                        autoSyncTracker = autoSyncTracker,
                                    ),
                                )
                            },
                            onDelete = {
                                val updated = criteria.toMutableList()
                                updated.removeAt(index)
                                val (newTotal, newScale10) = GranularScoreCalculator.calculate(updated, ignoreUnrated)
                                onSaveScore(
                                    GranularScoreEntry(
                                        mangaId = entry?.mangaId ?: 0L,
                                        templateName = currentTemplateName,
                                        criteria = updated,
                                        totalScore = newTotal,
                                        scale10Score = newScale10,
                                        ignoreUnrated = ignoreUnrated,
                                        autoSyncTracker = autoSyncTracker,
                                    ),
                                )
                            },
                            onQuickEdit = { quickEditCriterion = criterion },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // Quick Numeric Edit Dialog
    quickEditCriterion?.let { criterion ->
        var tempScoreStr by remember { mutableStateOf(criterion.score.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { quickEditCriterion = null },
            title = { Text(criterion.name) },
            text = {
                OutlinedTextField(
                    value = tempScoreStr,
                    onValueChange = { tempScoreStr = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Score (0 - 100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempScoreStr.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
                        val updated = criteria.map { if (it.id == criterion.id) it.copy(score = parsed) else it }
                        val (newTotal, newScale10) = GranularScoreCalculator.calculate(updated, ignoreUnrated)
                        onSaveScore(
                            GranularScoreEntry(
                                mangaId = entry?.mangaId ?: 0L,
                                templateName = currentTemplateName,
                                criteria = updated,
                                totalScore = newTotal,
                                scale10Score = newScale10,
                                ignoreUnrated = ignoreUnrated,
                                autoSyncTracker = autoSyncTracker,
                            ),
                        )
                        quickEditCriterion = null
                    },
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickEditCriterion = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Add Criterion Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var weightStr by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Scoring Criterion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Criterion Name (e.g. World Building)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                        label = { Text("Weight (e.g. 5, 10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val weight = weightStr.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 10.0
                        val newCrit = GranularScoreCriterion(
                            id = name.lowercase().replace(" ", "_"),
                            name = name.trim(),
                            score = 0.0,
                            weight = weight,
                        )
                        val updated = criteria + newCrit
                        val (newTotal, newScale10) = GranularScoreCalculator.calculate(updated, ignoreUnrated)
                        onSaveScore(
                            GranularScoreEntry(
                                mangaId = entry?.mangaId ?: 0L,
                                templateName = currentTemplateName,
                                criteria = updated,
                                totalScore = newTotal,
                                scale10Score = newScale10,
                                ignoreUnrated = ignoreUnrated,
                                autoSyncTracker = autoSyncTracker,
                            ),
                        )
                        showAddDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Save as New Template Dialog
    if (showSaveTemplateDialog) {
        var templateName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("Save as New Template") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Template Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val templateCriteria = criteria.map {
                            GranularTemplateCriterion(id = it.id, name = it.name, weight = it.weight)
                        }
                        onSaveNewTemplate(
                            GranularScoreTemplate(
                                name = templateName.trim(),
                                mediaType = "ALL",
                                criteria = templateCriteria,
                            ),
                        )
                        showSaveTemplateDialog = false
                    },
                    enabled = templateName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CriterionSliderRow(
    criterion: GranularScoreCriterion,
    onScoreChange: (Double) -> Unit,
    onToggleExcluded: () -> Unit,
    onDelete: () -> Unit,
    onQuickEdit: () -> Unit,
) {
    val contribution = (criterion.score / 100.0) * criterion.weight
    val isExcluded = criterion.isExcluded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isExcluded) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = criterion.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "w:${criterion.weight.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isExcluded && criterion.score > 0.0) {
                    Text(
                        text = "+${String.format("%.1f", contribution)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isExcluded) { onQuickEdit() },
                    color = if (criterion.score > 0.0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = if (isExcluded) "Excl" else "${criterion.score.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (criterion.score > 0.0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        if (!isExcluded) {
            Slider(
                value = criterion.score.toFloat(),
                onValueChange = { onScoreChange(it.roundToInt().toDouble()) },
                valueRange = 0f..100f,
                steps = 19, // 5% increments on step
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            )
        }
    }
}
// KMK <--
