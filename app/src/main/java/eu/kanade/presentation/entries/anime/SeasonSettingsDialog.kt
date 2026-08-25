package eu.kanade.presentation.entries.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import aniyomi.domain.anime.SeasonDisplayMode
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.anime.model.effectiveSeasonDownloadedFilter
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
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SeasonSettingsDialog(
    onDismissRequest: () -> Unit,
    anime: Anime? = null,
    downloadedOnly: Boolean,

    // Filter page
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnseenFilterChanged: (TriState) -> Unit,
    onStartedFilterChanged: (TriState) -> Unit,
    onCompletedFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onFillermarkedFilterChanged: (TriState) -> Unit,

    // Sort page
    onSortModeChanged: (Long) -> Unit,
    onSortDescendingChanged: (Boolean) -> Unit = {},

    // Display page
    onDisplayGridModeChanged: (SeasonDisplayMode) -> Unit,
    onDisplayGridSizeChanged: (Int) -> Unit,
    onOverlayDownloadedChanged: (Boolean) -> Unit,
    onOverlayUnseenChanged: (Boolean) -> Unit,
    onOverlayLocalChanged: (Boolean) -> Unit,
    onOverlayLangChanged: (Boolean) -> Unit,
    onOverlayContinueChanged: (Boolean) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,

    // Overflow action
    onSetAsDefault: (applyToExistingAnime: Boolean) -> Unit,
) {
    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            isEpisode = false,
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
                    SeasonFilterPage(
                        downloadFilter = anime?.effectiveSeasonDownloadedFilter(downloadedOnly) ?: TriState.DISABLED,
                        downloadFilterChange = onDownloadFilterChanged,
                        unseenFilter = anime?.seasonUnseenFilter ?: TriState.DISABLED,
                        unseenFilterChange = onUnseenFilterChanged,
                        startedFilter = anime?.seasonStartedFilter ?: TriState.DISABLED,
                        startedFilterChange = onStartedFilterChanged,
                        completedFilter = anime?.seasonCompletedFilter ?: TriState.DISABLED,
                        completedFilterChange = onCompletedFilterChanged,
                        bookmarkedFilter = anime?.seasonBookmarkedFilter ?: TriState.DISABLED,
                        bookmarkedFilterChange = onBookmarkedFilterChanged,
                        fillermarkedFilter = anime?.seasonFillermarkedFilter ?: TriState.DISABLED,
                        fillermarkedFilterChange = onFillermarkedFilterChanged,
                    )
                }
                1 -> {
                    SeasonSortPage(
                        sortingMode = anime?.seasonSorting ?: 0,
                        sortDescending = anime?.seasonSortDescending() ?: false,
                        onItemSelected = onSortModeChanged,
                    )
                }
                2 -> {
                    SeasonDisplayPage(
                        displayGridMode = anime?.seasonDisplayGridMode ?: SeasonDisplayMode.CompactGrid,
                        displayGridModeChange = onDisplayGridModeChanged,
                        displayGridModeSize = anime?.seasonDisplayGridSize ?: 0,
                        displayGridModeSizeChange = onDisplayGridSizeChanged,
                        overlayDownloaded = anime?.seasonDownloadedOverlay ?: false,
                        overlayDownloadedChange = onOverlayDownloadedChanged,
                        overlayUnseen = anime?.seasonUnseenOverlay ?: false,
                        overlayUnseenChange = onOverlayUnseenChanged,
                        overlayLocal = anime?.seasonLocalOverlay ?: false,
                        overlayLocalChange = onOverlayLocalChanged,
                        overlayLang = anime?.seasonLangOverlay ?: false,
                        overlayLangChange = onOverlayLangChanged,
                        overlayContinue = anime?.seasonContinueOverlay ?: false,
                        overlayContinueChange = onOverlayContinueChanged,
                        displayMode = anime?.seasonDisplayMode ?: Anime.SEASON_DISPLAY_MODE_SOURCE,
                        displayModeChange = onDisplayModeChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SeasonFilterPage(
    downloadFilter: TriState,
    downloadFilterChange: (TriState) -> Unit,
    unseenFilter: TriState,
    unseenFilterChange: (TriState) -> Unit,
    startedFilter: TriState,
    startedFilterChange: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    bookmarkedFilterChange: (TriState) -> Unit,
    completedFilter: TriState,
    completedFilterChange: (TriState) -> Unit,
    fillermarkedFilter: TriState,
    fillermarkedFilterChange: (TriState) -> Unit,
) {
    AuroraHeadingItem(MR.strings.action_filter)
    AuroraTriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadFilter,
        onClick = { downloadFilterChange(downloadFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = unseenFilter,
        onClick = { unseenFilterChange(unseenFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.label_started),
        state = startedFilter,
        onClick = { startedFilterChange(startedFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = { bookmarkedFilterChange(bookmarkedFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.completed),
        state = completedFilter,
        onClick = { completedFilterChange(completedFilter.next()) },
    )
    AuroraTriStateItem(
        label = stringResource(KMR.strings.filler),
        state = fillermarkedFilter,
        onClick = { fillermarkedFilterChange(fillermarkedFilter.next()) },
    )
}

@Composable
private fun ColumnScope.SeasonSortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    val sortOptions: List<Pair<StringResource, Long>> = listOf(
        MR.strings.sort_by_source to Anime.SEASON_SORT_SOURCE,
        KMR.strings.sort_by_season_number to Anime.SEASON_SORT_SEASON,
        MR.strings.sort_by_upload_date to Anime.SEASON_SORT_UPLOAD,
        MR.strings.action_sort_alpha to Anime.SEASON_SORT_ALPHABET,
        KMR.strings.action_sort_unseen_count to Anime.SEASON_SORT_COUNT,
        KMR.strings.action_sort_last_seen to Anime.SEASON_SORT_LAST_SEEN,
        KMR.strings.action_sort_episode_fetch_date to Anime.SEASON_SORT_FETCHED,
    )
    sortOptions.forEach { (titleRes, mode) ->
        AuroraSortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}

private val displayModes: List<Pair<StringResource, SeasonDisplayMode>> = listOf(
    MR.strings.action_display_grid to SeasonDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to SeasonDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to SeasonDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to SeasonDisplayMode.List,
)

@Composable
private fun ColumnScope.SeasonDisplayPage(
    displayGridMode: SeasonDisplayMode,
    displayGridModeChange: (SeasonDisplayMode) -> Unit,
    displayGridModeSize: Int,
    displayGridModeSizeChange: (Int) -> Unit,
    overlayDownloaded: Boolean,
    overlayDownloadedChange: (Boolean) -> Unit,
    overlayUnseen: Boolean,
    overlayUnseenChange: (Boolean) -> Unit,
    overlayLocal: Boolean,
    overlayLocalChange: (Boolean) -> Unit,
    overlayLang: Boolean,
    overlayLangChange: (Boolean) -> Unit,
    overlayContinue: Boolean,
    overlayContinueChange: (Boolean) -> Unit,
    displayMode: Long,
    displayModeChange: (Long) -> Unit,
) {
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.forEach { (titleRes, mode) ->
            FilterChip(
                selected = displayGridMode == mode,
                onClick = { displayGridModeChange(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayGridMode == SeasonDisplayMode.List) {
        SliderItem(
            value = displayGridModeSize,
            valueRange = 0..10,
            label = stringResource(KMR.strings.pref_library_rows),
            valueString = if (displayGridModeSize > 0) {
                displayGridModeSize.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = { displayGridModeSizeChange(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = displayGridModeSize,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueString = if (displayGridModeSize > 0) {
                displayGridModeSize.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = { displayGridModeSizeChange(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    AuroraHeadingItem(MR.strings.overlay_header)
    AuroraCheckboxItem(
        label = stringResource(KMR.strings.action_display_download_badge_anime),
        checked = overlayDownloaded,
        onClick = { overlayDownloadedChange(!overlayDownloaded) },
    )
    AuroraCheckboxItem(
        label = stringResource(KMR.strings.action_display_unseen_badge),
        checked = overlayUnseen,
        onClick = { overlayUnseenChange(!overlayUnseen) },
    )
    AuroraCheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        checked = overlayLocal,
        onClick = { overlayLocalChange(!overlayLocal) },
    )
    AuroraCheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        checked = overlayLang,
        onClick = { overlayLangChange(!overlayLang) },
    )
    AuroraCheckboxItem(
        label = stringResource(KMR.strings.action_display_show_continue_watching_button),
        checked = overlayContinue,
        onClick = { overlayContinueChange(!overlayContinue) },
    )

    AuroraHeadingItem(KMR.strings.action_display_grid_mode)
    val radioOptions: List<Pair<StringResource, Long>> = listOf(
        MR.strings.show_title to Anime.SEASON_DISPLAY_MODE_SOURCE,
        KMR.strings.show_season_number to Anime.SEASON_DISPLAY_MODE_NUMBER,
    )
    radioOptions.forEach { (titleRes, mode) ->
        AuroraRadioItem(
            label = stringResource(titleRes),
            selected = displayMode == mode,
            onClick = { displayModeChange(mode) },
        )
    }
}
