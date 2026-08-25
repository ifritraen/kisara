package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Composable
fun animeExtensionsTab(
    extensionsScreenModel: AnimeExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by extensionsScreenModel.state.collectAsState()
    val context = LocalContext.current
    var extensionToUninstall by remember { mutableStateOf<Extension?>(null) }

    var sideloadError by remember { mutableStateOf<Throwable?>(null) }
    var sideloadErrorExtName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsSearchEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.search("")
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsNsfwToggleEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.toggleNsfwOnly()
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsWebViewRefreshEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.findAvailableExtensions()
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsReposEvent.receiveAsFlow().collectLatest {
                navigator.push(ExtensionStoresScreen())
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsFilterEvent.receiveAsFlow().collectLatest {
                navigator.push(eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen())
            }
        }
        launch {
            extensionsScreenModel.events.collectLatest { event ->
                when (event) {
                    is eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel.Event.SideloadSuccess -> {
                        context.toast(context.getString(tachiyomi.i18n.kmk.KMR.strings.ext_sideload_success.resourceId, event.extensionName))
                    }
                    is eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel.Event.SideloadError -> {
                        sideloadError = event.error
                        sideloadErrorExtName = event.extensionName
                    }
                }
            }
        }
    }

    return TabContent(
        titleRes = KMR.strings.label_anime_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    extensionsScreenModel.setDialog(ExtensionsScreenModel.Dialog.ExtensionTags(extension))
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onOpenWebView = { },
                onInstallExtension = extensionsScreenModel::installExtension,
                onSideloadExtension = extensionsScreenModel::sideloadExtension,
                onUninstallExtension = { extensionToUninstall = it },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onTrustExtension = extensionsScreenModel::trustExtension,
                onOpenExtension = { extension ->
                    val sourceId = extension.sources.firstOrNull()?.id
                        ?: Injekt.get<AnimeExtensionManager>().installedExtensionsFlow.value
                            .find { it.pkgName == extension.pkgName }?.sources?.firstOrNull()?.id
                    if (sourceId != null) {
                        navigator.push(BrowseAnimeSourceScreen(sourceId))
                    } else {
                        navigator.push(AnimeExtensionDetailsScreen(extension.pkgName))
                    }
                },
                onOpenExtensionDetails = { navigator.push(AnimeExtensionDetailsScreen(it.pkgName)) },
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onRefresh = extensionsScreenModel::findAvailableExtensions,
            )

            when (val dialog = state.dialog) {
                is ExtensionsScreenModel.Dialog.ExtensionTags -> {
                    val extension = dialog.extension
                    val prefix = "ext_${extension.pkgName}:"
                    val currentTags = state.extensionTagMappings
                        .filter { it.startsWith(prefix) }
                        .map { it.removePrefix(prefix) }
                        .toSet()
                    eu.kanade.presentation.browse.SourceTagsDialog(
                        itemName = extension.name,
                        allTags = state.allTags,
                        currentTags = currentTags,
                        onDismissRequest = { extensionsScreenModel.setDialog(null) },
                        onSaveTags = { selectedTags, newTag ->
                            extensionsScreenModel.saveExtensionTags(extension.pkgName, selectedTags, newTag)
                        },
                        onUninstall = if (extension is Extension.Installed) {
                            { extensionsScreenModel.uninstallExtension(extension) }
                        } else null,
                    )
                }
                else -> Unit
            }

            extensionToUninstall?.let { extension ->
                val isPrivate = extension is Extension.Installed && !extension.isShared
                val message = if (isPrivate) {
                    stringResource(MR.strings.remove_private_extension_message, extension.name)
                } else {
                    stringResource(MR.strings.ext_confirm_remove)
                }
                AlertDialog(
                    title = {
                        Text(text = stringResource(MR.strings.ext_confirm_remove))
                    },
                    text = {
                        Text(text = message)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                extensionsScreenModel.uninstallExtension(extension)
                                extensionToUninstall = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { extensionToUninstall = null }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { extensionToUninstall = null },
                )
            }

            sideloadError?.let { err ->
                AlertDialog(
                    title = {
                        Text(text = stringResource(KMR.strings.ext_sideload_failed, sideloadErrorExtName ?: ""))
                    },
                    text = {
                        Text(text = err.localizedMessage ?: err.message ?: "Unknown error")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                sideloadError = null
                                sideloadErrorExtName = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                    onDismissRequest = {
                        sideloadError = null
                        sideloadErrorExtName = null
                    },
                )
            }
        },
    )
}
