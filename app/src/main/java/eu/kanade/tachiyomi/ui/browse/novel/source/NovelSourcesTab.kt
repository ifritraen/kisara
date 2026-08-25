package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelSourceOptionsDialog
import eu.kanade.presentation.browse.novel.NovelSourceUiModel
import eu.kanade.presentation.browse.novel.NovelSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourcePagerScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelSourcesScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.label_novel_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalNovelSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(NovelSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            val scope = rememberCoroutineScope()
            NovelSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    val sourceIds = if (state.pinnedItems.any { it.id == source.id } && !state.verticalPinnedLayout) {
                        state.pinnedItems.map { it.id }
                    } else {
                        var currentHeaderLang: String? = null
                        val groups = mutableMapOf<String, MutableList<Long>>()
                        state.items.forEach { uiModel ->
                            when (uiModel) {
                                is NovelSourceUiModel.Header -> {
                                    currentHeaderLang = uiModel.language
                                }
                                is NovelSourceUiModel.Item -> {
                                    val lang = currentHeaderLang ?: ""
                                    groups.getOrPut(lang) { mutableListOf() }.add(uiModel.source.id)
                                }
                            }
                        }
                        groups.values.firstOrNull { it.contains(source.id) } ?: listOf(source.id)
                    }
                    navigator.push(BrowseNovelSourcePagerScreen(source.id, sourceIds, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                onToggleLanguage = screenModel::toggleLanguage,
            )

            state.dialog?.let { dialog ->
                NovelSourceOptionsDialog(
                    source = dialog.source,
                    onClickPin = {
                        screenModel.togglePin(dialog.source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(dialog.source)
                        screenModel.closeDialog()
                    },
                    onClickSettings = {
                        val pluginId = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                            .getPluginId(dialog.source.id)
                        if (pluginId != null) {
                            navigator.push(eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreen(pluginId))
                        } else {
                            navigator.push(eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen(dialog.source.id))
                        }
                        screenModel.closeDialog()
                    },
                    onClickUninstall = {
                        val extensionManager = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>()
                        val pluginId = extensionManager.getPluginId(dialog.source.id)
                        scope.launch {
                            val ext = extensionManager.installedPluginsFlow.first().find { it.id == pluginId || it.pkgName == pluginId }
                            if (ext != null) {
                                extensionManager.uninstallPlugin(ext)
                            }
                        }
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                launch {
                    screenModel.events.collectLatest { event ->
                        when (event) {
                            NovelSourcesScreenModel.Event.FailedFetchingSources -> {
                                launch { snackbarHostState.showSnackbar(internalErrString) }
                            }
                        }
                    }
                }
                launch {
                    eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesGlobalSearchEvent.receiveAsFlow().collectLatest {
                        navigator.push(GlobalNovelSearchScreen())
                    }
                }
                launch {
                    eu.kanade.tachiyomi.ui.browse.BrowseTab.sourcesFilterEvent.receiveAsFlow().collectLatest {
                        navigator.push(NovelSourcesFilterScreen())
                    }
                }
            }
        },
    )
}
