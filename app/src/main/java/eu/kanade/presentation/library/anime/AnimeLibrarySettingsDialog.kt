package eu.kanade.presentation.library.anime

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AuroraAccordionItem
import eu.kanade.presentation.components.AuroraBaseSortItem
import eu.kanade.presentation.components.AuroraCheckboxItem
import eu.kanade.presentation.components.AuroraDisplayModeTiles
import eu.kanade.presentation.components.AuroraHeadingItem
import eu.kanade.presentation.components.AuroraSortItem
import eu.kanade.presentation.components.AuroraTriStateItem
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AnimeLibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: AnimeLibraryScreenModel,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.72f).dp

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.heightIn(max = maxSheetHeight),
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(screenModel, libraryPreferences)
                1 -> SortPage(screenModel)
                2 -> DisplayPage(libraryPreferences)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: AnimeLibraryScreenModel,
    libraryPreferences: LibraryPreferences,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = state.unseenFilter,
        onClick = screenModel::setUnseenFilter,
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.label_started),
        state = state.startedFilter,
        onClick = screenModel::setStartedFilter,
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = state.bookmarkedFilter,
        onClick = screenModel::setBookmarkedFilter,
    )
    AuroraTriStateItem(
        label = stringResource(MR.strings.completed),
        state = state.completedFilter,
        onClick = screenModel::setCompletedFilter,
    )

    if (state.libraryLanguages.isNotEmpty()) {
        var languageExpanded by remember { mutableStateOf(false) }
        AuroraAccordionItem(
            label = stringResource(MR.strings.action_display_language_badge),
            expanded = languageExpanded,
            onToggle = { languageExpanded = !languageExpanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                TextButton(onClick = { screenModel.setAllLanguages(state.libraryLanguages.toSet()) }) {
                    Text(text = stringResource(MR.strings.action_select_all))
                }
                TextButton(onClick = screenModel::clearLanguageFilter) {
                    Text(text = stringResource(MR.strings.action_reset))
                }
            }
            state.libraryLanguages.forEach { language ->
                AuroraCheckboxItem(
                    label = if (language == "all") {
                        stringResource(MR.strings.label_local)
                    } else {
                        LocaleHelper.getLocalizedDisplayName(language)
                    },
                    checked = language in state.languageFilter,
                    onClick = { screenModel.toggleLanguage(language) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    screenModel: AnimeLibraryScreenModel,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()
    val sortingMode = state.sort.type
    val sortDescending = !state.sort.isAscending

    val options = animeLibrarySortOptions()

    options.forEach { (titleRes, mode) ->
        if (mode == AnimeLibrarySort.Type.Random) {
            AuroraBaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh.takeIf { sortingMode == AnimeLibrarySort.Type.Random },
                onClick = {
                    screenModel.setSort(mode, AnimeLibrarySort.Direction.Ascending)
                },
            )
            return@forEach
        }

        AuroraSortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        AnimeLibrarySort.Direction.Ascending
                    } else {
                        AnimeLibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        AnimeLibrarySort.Direction.Descending
                    } else {
                        AnimeLibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(mode, direction)
            },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    libraryPreferences: LibraryPreferences,
) {
    val displayMode by libraryPreferences.animeDisplayMode().collectAsStateWithLifecycle()
    AuroraHeadingItem(MR.strings.action_display_mode)
    AuroraDisplayModeTiles(
        options = animeLibraryDisplayModes(),
        selected = displayMode,
        onSelect = { libraryPreferences.animeDisplayMode().set(it) },
    )

    val configuration = LocalConfiguration.current
    val columnPreference = remember(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            libraryPreferences.animeLandscapeColumns()
        } else {
            libraryPreferences.animePortraitColumns()
        }
    }

    AuroraHeadingItem(MR.strings.pref_library_columns)
    val columns by columnPreference.collectAsStateWithLifecycle()
    tachiyomi.presentation.core.components.SliderItem(
        label = stringResource(MR.strings.pref_library_columns),
        value = columns,
        valueRange = 0..10,
        onChange = { columnPreference.set(it) },
    )

    AuroraHeadingItem(MR.strings.overlay_header)
    AuroraCheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = libraryPreferences.downloadBadge(),
    )
    AuroraCheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = libraryPreferences.unreadBadge(),
    )
    AuroraCheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = libraryPreferences.languageBadge(),
    )
}

internal fun animeLibrarySortOptions(): List<Pair<StringResource, AnimeLibrarySort.Type>> {
    return listOf(
        MR.strings.action_sort_alpha to AnimeLibrarySort.Type.Alphabetical,
        MR.strings.action_sort_total to AnimeLibrarySort.Type.TotalEpisodes,
        MR.strings.action_sort_last_read to AnimeLibrarySort.Type.LastSeen,
        KMR.strings.action_sort_last_manga_update to AnimeLibrarySort.Type.LastUpdate,
        MR.strings.action_sort_unread_count to AnimeLibrarySort.Type.UnseenCount,
        MR.strings.action_sort_latest_chapter to AnimeLibrarySort.Type.LatestEpisode,
        MR.strings.action_sort_chapter_fetch_date to AnimeLibrarySort.Type.EpisodeFetchDate,
        MR.strings.action_sort_date_added to AnimeLibrarySort.Type.DateAdded,
        MR.strings.action_sort_random to AnimeLibrarySort.Type.Random,
    )
}

internal fun animeLibraryDisplayModes(): List<Pair<StringResource, LibraryDisplayMode>> {
    return listOf(
        MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
        MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
        MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
        MR.strings.action_display_list to LibraryDisplayMode.List,
    )
}
