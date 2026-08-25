package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
// KMK -->
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
// KMK <--
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.animeSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AnimeSourcesScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    val sourceManager = remember { Injekt.get<AnimeSourceManager>() }

    LaunchedEffect(Unit) {
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesGlobalSearchEvent.receiveAsFlow().collectLatest {
                navigator.push(eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen())
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesNsfwToggleEvent.receiveAsFlow().collectLatest {
                screenModel.toggleNsfwOnly()
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesFilterEvent.receiveAsFlow().collectLatest {
                navigator.push(eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreen())
            }
        }
    }
    LaunchedEffect(state.nsfwOnly) {
        eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesNsfwOnly = state.nsfwOnly
    }

    return TabContent(
        titleRes = KMR.strings.label_anime_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { screenModel.toggleNsfwOnly() },
            ),
        ),
        content = { contentPadding, _ ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // KMK -->
                    navigator.push(
                        BrowseAnimeSourceScreen(
                            sourceId = source.id,
                            listingQuery = listing.query,
                        ),
                    )
                    // KMK <--
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                onClickReorderPin = { _, _, _ -> },
                onChangeSearchQuery = screenModel::search,
                onSelectTag = screenModel::setSelectedTag,
                onClickManageTags = { source -> screenModel.dialog = SourcesScreenModel.Dialog.SourceTags(source) },
            )

            when (val dialog = state.dialog) {
                is SourcesScreenModel.Dialog.SourceLongClick -> {
                    val source = dialog.source
                    eu.kanade.presentation.browse.SourceOptionsDialog(
                        source = source,
                        onClickPin = {
                            screenModel.togglePin(source)
                            screenModel.closeDialog()
                        },
                        onClickDisable = {
                            screenModel.toggleSource(source)
                            screenModel.closeDialog()
                        },
                        onClickSetCategories = null,
                        onClickToggleDataSaver = null,
                        onDismiss = screenModel::closeDialog,
                        onClickSettings = {
                            val pkg = Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                                .getExtensionPackage(source.id)
                            if (pkg != null) {
                                navigator.push(eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen(pkg))
                            } else {
                                navigator.push(eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen(source.id))
                            }
                            screenModel.closeDialog()
                        },
                        onClickManageTags = {
                            screenModel.dialog = SourcesScreenModel.Dialog.SourceTags(source)
                        },
                        onClickSelectMultiple = {
                            screenModel.toggleSourceSelection(source.id)
                            screenModel.closeDialog()
                        },
                        onClickUninstall = {
                            screenModel.uninstallExtension(source)
                            screenModel.closeDialog()
                        },
                    )
                }
                is SourcesScreenModel.Dialog.SourceTags -> {
                    val source = dialog.source
                    val prefix = "${source.id}:"
                    val currentTags = state.sourceTagMappings
                        .filter { it.startsWith(prefix) }
                        .map { it.removePrefix(prefix) }
                        .toSet()
                    eu.kanade.presentation.browse.SourceTagsDialog(
                        itemName = source.visualName,
                        allTags = state.allTags,
                        currentTags = currentTags,
                        onDismissRequest = screenModel::closeDialog,
                        onSaveTags = { selectedTags, newTag ->
                            screenModel.saveSourceTags(source.id, selectedTags, newTag)
                        },
                    )
                }
                is SourcesScreenModel.Dialog.BulkSourceTags -> {
                    val sources = dialog.sources
                    eu.kanade.presentation.browse.SourceTagsDialog(
                        itemName = "${sources.size} Sources",
                        allTags = state.allTags,
                        currentTags = emptySet(),
                        onDismissRequest = screenModel::closeDialog,
                        onSaveTags = { selectedTags, newTag ->
                            screenModel.saveBulkSourceTags(sources.map { it.id }, selectedTags, newTag)
                        },
                    )
                }
                else -> Unit
            }
        },
    )
}
