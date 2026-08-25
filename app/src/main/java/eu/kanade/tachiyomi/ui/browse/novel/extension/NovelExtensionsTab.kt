package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelExtensionScreen
import eu.kanade.presentation.browse.novel.NovelRepoPickerDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourcePagerScreen

@Composable
fun novelExtensionsTab(
    extensionsScreenModel: NovelExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by extensionsScreenModel.state.collectAsStateWithLifecycle()
    var pluginToUninstall by remember { mutableStateOf<NovelPlugin.Installed?>(null) }
    var pluginToReinstall by remember { mutableStateOf<NovelPlugin.Installed?>(null) }
    var showInstallerDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsSearchEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.search("")
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsWebViewRefreshEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.refresh()
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsFilterEvent.receiveAsFlow().collectLatest {
                navigator.push(NovelExtensionFilterScreen())
            }
        }
        launch {
            eu.kanade.tachiyomi.ui.browse.BrowseTab.extensionsReposEvent.receiveAsFlow().collectLatest {
                navigator.push(ExtensionStoresScreen())
            }
        }
    }

    return TabContent(
        titleRes = KMR.strings.label_novel_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(NovelExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
            AppBar.OverflowAction(
                title = "Installer Diagnostics",
                onClick = { showInstallerDiagnostics = true },
            ),
        ),
        content = { contentPadding, _ ->
            NovelExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onInstallExtension = extensionsScreenModel::installExtension,
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onReinstallExtension = { pluginToReinstall = it },
                onOpenExtension = { plugin ->
                    val singleSource = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>()
                        .getCatalogueSources()
                        .firstOrNull { uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>().getPluginId(it.id) == plugin.id }
                    if (singleSource != null) {
                        navigator.push(BrowseNovelSourcePagerScreen(singleSource.id, listOf(singleSource.id)))
                    } else {
                        navigator.push(novelExtensionDetailsScreen(plugin.id))
                    }
                },
                onOpenExtensionSettings = { navigator.push(novelExtensionSettingsScreen(it)) },
                onUninstallExtension = { pluginToUninstall = it },
                onUninstallUntrustedExtension = extensionsScreenModel::uninstallExtension,
                onTrustExtension = extensionsScreenModel::trust,
                onUpdateAll = extensionsScreenModel::updateAllExtensions,
                onRefresh = extensionsScreenModel::refresh,
                onToggleSection = extensionsScreenModel::toggleSection,
                onCopyDiagnostic = { plugin ->
                    context.copyToClipboard(
                        label = "Novel extension diagnostic",
                        content = extensionsScreenModel.diagnosticFor(plugin),
                    )
                },
                onShareApk = extensionsScreenModel::shareApk,
                onReinstallAfterSignatureMismatch = extensionsScreenModel::reinstallAfterSignatureMismatch,
                onDismissSignatureMismatch = extensionsScreenModel::dismissSignatureMismatch,
                onLongClickItem = { plugin ->
                    extensionsScreenModel.setDialog(NovelExtensionsScreenModel.Dialog.ExtensionTags(plugin))
                },
            )

            when (val dialog = state.dialog) {
                is NovelExtensionsScreenModel.Dialog.ExtensionTags -> {
                    val plugin = dialog.plugin
                    val prefix = "ext_${plugin.id}:"
                    val currentTags = state.extensionTagMappings
                        .filter { it.startsWith(prefix) }
                        .map { it.removePrefix(prefix) }
                        .toSet()
                    eu.kanade.presentation.browse.SourceTagsDialog(
                        itemName = plugin.name,
                        allTags = state.allTags,
                        currentTags = currentTags,
                        onDismissRequest = { extensionsScreenModel.setDialog(null) },
                        onSaveTags = { selectedTags, newTag ->
                            extensionsScreenModel.saveExtensionTags(plugin.id, selectedTags, newTag)
                        },
                        onUninstall = if (plugin is tachiyomi.domain.extension.novel.model.NovelPlugin.Installed) {
                            { pluginToUninstall = plugin }
                        } else null,
                    )
                }
                null -> Unit
            }

            pluginToUninstall?.let { plugin ->
                AlertDialog(
                    title = { Text(text = stringResource(MR.strings.ext_confirm_remove)) },
                    text = { Text(text = stringResource(MR.strings.remove_private_extension_message, plugin.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                extensionsScreenModel.uninstallExtension(plugin)
                                pluginToUninstall = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pluginToUninstall = null }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { pluginToUninstall = null },
                )
            }

            pluginToReinstall?.let { plugin ->
                NovelRepoReinstallDialog(
                    plugin = plugin,
                    candidates = extensionsScreenModel.getReinstallCandidates(plugin),
                    onClickCandidate = { candidate ->
                        extensionsScreenModel.reinstallFromRepo(plugin, candidate)
                        pluginToReinstall = null
                    },
                    onDismissRequest = { pluginToReinstall = null },
                )
            }

            if (state.repoPickerOptions.isNotEmpty()) {
                NovelRepoPickerDialog(
                    pluginName = state.repoPickerOptions.first().name,
                    options = state.repoPickerOptions,
                    onSelectPlugin = extensionsScreenModel::installFromRepo,
                    onDismiss = extensionsScreenModel::dismissRepoPicker,
                )
            }

            if (showInstallerDiagnostics) {
                val diagnostic = extensionsScreenModel.installerCompatibilityDiagnostic()
                val diagnosticsTitle = "Installer Diagnostics"
                AlertDialog(
                    title = { Text(text = diagnosticsTitle) },
                    text = { Text(text = diagnostic) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                context.copyToClipboard(
                                    label = diagnosticsTitle,
                                    content = diagnostic,
                                )
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_copy_to_clipboard))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInstallerDiagnostics = false }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { showInstallerDiagnostics = false },
                )
            }
        },
    )
}

@Composable
private fun NovelRepoReinstallDialog(
    plugin: NovelPlugin.Installed,
    candidates: List<NovelPlugin.Available>,
    onClickCandidate: (NovelPlugin.Available) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = "Reinstall Extension") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Text(
                    text = "Update available from multiple repositories for ${plugin.name}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                if (candidates.isEmpty()) {
                    Text(
                        text = "No compatible update found in configured repositories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    candidates.forEach { candidate ->
                        val repoName = candidate.repoName
                            .ifBlank { candidate.repoUrl.substringAfter("://", candidate.repoUrl).substringBefore('/') }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onClickCandidate(candidate) },
                        ) {
                            Text(text = "Install from $repoName")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

internal fun novelExtensionDetailsScreen(pluginId: String): NovelExtensionDetailsScreen {
    return NovelExtensionDetailsScreen(pluginId)
}

internal fun novelExtensionSettingsScreen(sourceId: Long): NovelSourcePreferencesScreen {
    return NovelSourcePreferencesScreen(sourceId)
}
