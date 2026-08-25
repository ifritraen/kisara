package eu.kanade.tachiyomi.ui.browse.novel.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// Same bit values as NovelMigrationFlags
private const val CHAPTERS = 0b001
private const val CATEGORIES = 0b010
private const val DELETE_DOWNLOADED = 0b100

@Composable
fun NovelMigrationConfigScreenSheet(
    preferences: SourcePreferences,
    onDismissRequest: () -> Unit,
    onStartMigration: (extraSearchQuery: String?) -> Unit,
) {
    val preferenceStore: PreferenceStore = remember { Injekt.get() }
    val migrationFlagsPref = remember { preferenceStore.getInt("migrate_flags_novel", Int.MAX_VALUE) }
    var extraSearchQuery by rememberSaveable { mutableStateOf("") }
    val migrationFlags by migrationFlagsPref.collectAsState()

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(MR.strings.action_migrate),
                    style = MaterialTheme.typography.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    MigrationFlagChip(
                        selected = migrationFlags and CHAPTERS != 0,
                        label = stringResource(MR.strings.chapters),
                        onClick = {
                            migrationFlagsPref.set(migrationFlags xor CHAPTERS)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and CATEGORIES != 0,
                        label = stringResource(MR.strings.categories),
                        onClick = {
                            migrationFlagsPref.set(migrationFlags xor CATEGORIES)
                        },
                    )
                    MigrationFlagChip(
                        selected = migrationFlags and DELETE_DOWNLOADED != 0,
                        label = stringResource(MR.strings.delete_downloaded),
                        onClick = {
                            migrationFlagsPref.set(migrationFlags xor DELETE_DOWNLOADED)
                        },
                    )
                }

                OutlinedTextField(
                    value = extraSearchQuery,
                    onValueChange = { extraSearchQuery = it },
                    label = { Text(text = "Extra search query") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                )

                MigrationSwitchItem(
                    title = "Hide not found",
                    subtitle = null,
                    preference = preferences.migrationHideUnmatched(),
                )
                MigrationSwitchItem(
                    title = "Only with new chapters",
                    subtitle = null,
                    preference = preferences.migrationHideWithoutUpdates(),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
                MigrationWarningItem(text = "Advanced options")
                MigrationSwitchItem(
                    title = "Deep search mode",
                    subtitle = null,
                    preference = preferences.migrationDeepSearchMode(),
                )

                Text(
                    text = stringResource(MR.strings.action_migrate),
                    style = MaterialTheme.typography.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                )
                val prioritizeByChapters by preferences.migrationPrioritizeByChapters().collectAsState()
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    MigrationFlagChip(
                        selected = !prioritizeByChapters,
                        label = "First found",
                        onClick = {
                            preferences.migrationPrioritizeByChapters().set(false)
                        },
                    )
                    MigrationFlagChip(
                        selected = prioritizeByChapters,
                        label = "Most chapters",
                        onClick = {
                            preferences.migrationPrioritizeByChapters().set(true)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            }
            HorizontalDivider()
            Button(
                onClick = {
                    val cleanedExtraSearchQuery = extraSearchQuery.trim().ifBlank { null }
                    onStartMigration(cleanedExtraSearchQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Text(text = stringResource(KMR.strings.action_continue))
            }
        }
    }
}

@Composable
private fun MigrationFlagChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
            }
        },
    )
}

@Composable
private fun MigrationSwitchItem(
    title: String,
    subtitle: String?,
    preference: Preference<Boolean>,
) {
    val checked by preference.collectAsState()
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = subtitle) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { preference.set(it) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .clickable { preference.set(!checked) },
    )
}

@Composable
private fun MigrationWarningItem(text: String) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.active,
            )
        },
        headlineContent = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
