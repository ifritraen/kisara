package eu.kanade.presentation.entries.anime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.anime.model.effectiveDownloadedFilter
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.EpisodeListDensity
import eu.kanade.presentation.components.AuroraCheckboxItem
import eu.kanade.presentation.components.AuroraHeadingItem
import eu.kanade.presentation.components.AuroraRadioItem
import eu.kanade.presentation.components.AuroraSortItem
import eu.kanade.presentation.components.AuroraTriStateItem
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun EpisodeSettingsDialog(
    onDismissRequest: () -> Unit,
    anime: Anime? = null,
    downloadedOnly: Boolean,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnseenFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onFillermarkedFilterChanged: (TriState) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onShowPreviewsEnabled: (Long) -> Unit,
    onShowSummariesEnabled: (Long) -> Unit,
    onSetAsDefault: (applyToExistingAnime: Boolean) -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val episodeListDensity by uiPreferences.episodeListDensity().collectAsState()

    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
        tabOverflowMenuContent = { closeMenu ->
            AuroraEntryDropdownMenuItem(
                text = stringResource(MR.strings.set_chapter_settings_as_default),
                leadingIcon = Icons.Outlined.Save,
                onClick = {
                    showSetAsDefaultDialog = true
                    closeMenu()
                },
            )
        },
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    FilterPage(
                        downloadFilter = anime?.effectiveDownloadedFilter(downloadedOnly) ?: TriState.DISABLED,
                        onDownloadFilterChanged = onDownloadFilterChanged
                            .takeUnless { downloadedOnly },
                        unseenFilter = anime?.unseenFilter ?: TriState.DISABLED,
                        onUnseenFilterChanged = onUnseenFilterChanged,
                        bookmarkedFilter = anime?.bookmarkedFilter ?: TriState.DISABLED,
                        onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                        fillermarkedFilter = anime?.fillermarkedFilter ?: TriState.DISABLED,
                        onFillermarkedFilterChanged = onFillermarkedFilterChanged,
                    )
                }
                1 -> {
                    SortPage(
                        sortingMode = anime?.sorting ?: 0,
                        sortDescending = anime?.sortDescending() ?: false,
                        onItemSelected = onSortModeChanged,
                    )
                }
                2 -> {
                    DisplayPage(
                        displayMode = anime?.displayMode ?: 0,
                        onDisplayModeChanged = onDisplayModeChanged,
                        showPreviews = anime?.showPreviews() ?: false,
                        onShowPreviewsEnabled = onShowPreviewsEnabled,
                        showSummaries = anime?.showSummaries() ?: false,
                        onShowSummariesEnabled = onShowSummariesEnabled,
                        episodeListDensity = episodeListDensity,
                        onEpisodeListDensityChanged = { uiPreferences.episodeListDensity().set(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    downloadFilter: TriState,
    onDownloadFilterChanged: ((TriState) -> Unit)?,
    unseenFilter: TriState,
    onUnseenFilterChanged: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    fillermarkedFilter: TriState,
    onFillermarkedFilterChanged: (TriState) -> Unit,
) {
    AuroraHeadingItem(MR.strings.action_filter)
    AuroraTriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadFilter,
        enabled = onDownloadFilterChanged != null,
        onClick = { onDownloadFilterChanged?.invoke(downloadFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = unseenFilter,
        onClick = { onUnseenFilterChanged(unseenFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = { onBookmarkedFilterChanged(bookmarkedFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(KMR.strings.filler),
        state = fillermarkedFilter,
        onClick = { onFillermarkedFilterChanged(fillermarkedFilter.next()) },
    )
}

@Composable
private fun ColumnScope.SortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.sort_by_source to Anime.EPISODE_SORTING_SOURCE,
        MR.strings.sort_by_number to Anime.EPISODE_SORTING_NUMBER,
        MR.strings.sort_by_upload_date to Anime.EPISODE_SORTING_UPLOAD_DATE,
        MR.strings.action_sort_alpha to Anime.EPISODE_SORTING_ALPHABET,
    ).forEach { (titleRes, mode) ->
        AuroraSortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    displayMode: Long,
    onDisplayModeChanged: (Long) -> Unit,
    showPreviews: Boolean,
    onShowPreviewsEnabled: (Long) -> Unit,
    showSummaries: Boolean,
    onShowSummariesEnabled: (Long) -> Unit,
    episodeListDensity: EpisodeListDensity,
    onEpisodeListDensityChanged: (EpisodeListDensity) -> Unit,
) {
    val displayOptions: List<Pair<StringResource, Long>> = listOf(
        MR.strings.show_title to Anime.EPISODE_DISPLAY_NAME,
        KMR.strings.show_episode_number to Anime.EPISODE_DISPLAY_NUMBER,
    )
    displayOptions.forEach { (titleRes, mode) ->
        AuroraRadioItem(
            label = stringResource(titleRes),
            selected = displayMode == mode,
            onClick = { onDisplayModeChanged(mode) },
        )
    }
    val showPreviewsFlag = if (showPreviews) Anime.EPISODE_SHOW_NOT_PREVIEWS else Anime.EPISODE_SHOW_PREVIEWS
    AuroraCheckboxItem(
        label = stringResource(KMR.strings.show_episode_previews),
        checked = showPreviews,
        onClick = { onShowPreviewsEnabled(showPreviewsFlag) },
    )
    val showSummariesFlag = if (showSummaries) Anime.EPISODE_SHOW_NOT_SUMMARIES else Anime.EPISODE_SHOW_SUMMARIES
    AuroraCheckboxItem(
        label = stringResource(KMR.strings.show_episode_summaries),
        checked = showSummaries,
        onClick = { onShowSummariesEnabled(showSummariesFlag) },
    )

    AuroraHeadingItem(KMR.strings.pref_episode_list_density)
    EpisodeListDensity.entries.forEach { density ->
        val densityName = when (density) {
            EpisodeListDensity.Comfortable -> "Comfortable"
            EpisodeListDensity.Compact -> "Compact"
            EpisodeListDensity.Dense -> "Dense"
        }
        AuroraRadioItem(
            label = densityName,
            selected = episodeListDensity == density,
            onClick = { onEpisodeListDensityChanged(density) },
        )
    }
}

@Composable
internal fun SetAsDefaultDialog(
    onDismissRequest: () -> Unit,
    isEpisode: Boolean = true,
    onConfirmed: (optionalChecked: Boolean) -> Unit,
) {
    var optionalChecked by rememberSaveable { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val title = if (isEpisode) {
        stringResource(KMR.strings.episode_settings)
    } else {
        stringResource(KMR.strings.season_settings)
    }

    eu.kanade.presentation.components.KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = title,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
            Button(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    onConfirmed(optionalChecked)
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
        Text(
            text = stringResource(MR.strings.confirm_set_chapter_settings),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    LabeledCheckbox(
                        label = stringResource(KMR.strings.also_set_episode_settings_for_library),
                        checked = optionalChecked,
                        onCheckedChange = { optionalChecked = it },
                    )
                }
            }
        }
    }
}
