package eu.kanade.tachiyomi.ui.browse.extension

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

@Composable
fun extensionsTab(
    extensionsScreenModel: ExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val state by extensionsScreenModel.state.collectAsState()
    val chooseJar = rememberLauncherForActivityResult(
        object : androidx.activity.result.contract.ActivityResultContracts.GetContent() {
            override fun createIntent(context: android.content.Context, input: String): android.content.Intent {
                val intent = super.createIntent(context, input)
                return android.content.Intent.createChooser(intent, "Select Kotatsu JAR Extension")
            }
        },
    ) { uri ->
        if (uri != null) {
            extensionsScreenModel.installJarExtension(uri)
        }
    }
    var privateExtensionToUninstall by remember { mutableStateOf<Extension?>(null) }
    var sideloadError by remember { mutableStateOf<Throwable?>(null) }
    var sideloadErrorExtName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        extensionsScreenModel.cleanupTemporaryExtensions()
        launch {
            BrowseTab.extensionsSearchEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.search("")
            }
        }
        launch {
            BrowseTab.extensionsNsfwToggleEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.toggleNsfwOnly()
            }
        }
        launch {
            BrowseTab.extensionsWebViewRefreshEvent.receiveAsFlow().collectLatest {
                extensionsScreenModel.findAvailableExtensions()
            }
        }
        launch {
            BrowseTab.extensionsFilterEvent.receiveAsFlow().collectLatest {
                navigator.push(ExtensionFilterScreen())
            }
        }
        launch {
            BrowseTab.extensionsReposEvent.receiveAsFlow().collectLatest {
                navigator.push(ExtensionStoresScreen())
            }
        }
        launch {
            BrowseTab.extensionsInstallJarEvent.receiveAsFlow().collectLatest {
                chooseJar.launch("*/*")
            }
        }
        launch {
            extensionsScreenModel.events.collectLatest { event ->
                when (event) {
                    is ExtensionsScreenModel.Event.SideloadSuccess -> {
                        context.toast(context.contextStringResource(KMR.strings.ext_sideload_success, event.extensionName))
                    }
                    is ExtensionsScreenModel.Event.SideloadError -> {
                        sideloadError = event.error
                        sideloadErrorExtName = event.extensionName
                    }
                }
            }
        }
    }
    LaunchedEffect(state.nsfwOnly) {
        BrowseTab.extensionsNsfwOnly = state.nsfwOnly
    }

    return TabContent(
        titleRes = MR.strings.label_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            // KMK -->
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { extensionsScreenModel.toggleNsfwOnly() },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_webview_refresh),
                onClick = extensionsScreenModel::findAvailableExtensions,
            ),
            // KMK <--
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(ExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
            AppBar.OverflowAction(
                title = "Install Kotatsu JAR Extension",
                onClick = { chooseJar.launch("*/*") },
            ),
        ),
        content = { contentPadding, _ ->
            BackHandler(enabled = state.searchQuery != null) {
                extensionsScreenModel.search(null)
            }
            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    extensionsScreenModel.setDialog(ExtensionsScreenModel.Dialog.ExtensionTags(extension))
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsScreenModel::installExtension,
                onSideloadExtension = extensionsScreenModel::sideloadExtension,
                onOpenExtension = { extension ->
                    val source = extension.sources.firstOrNull()
                    if (source != null) {
                        val uiPreferences = Injekt.get<UiPreferences>()
                        val useNewSourceNavigation = uiPreferences.useNewSourceNavigation().get()
                        val screen = if (useNewSourceNavigation) {
                            SourceFeedScreen(source.id)
                        } else {
                            BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR)
                        }
                        navigator.push(screen)
                    } else {
                        navigator.push(ExtensionDetailsScreen(extension.pkgName))
                    }
                },
                onOpenExtensionDetails = { navigator.push(ExtensionDetailsScreen(it.pkgName)) },
                onTrustExtension = { extensionsScreenModel.trustExtension(it) },
                onUninstallExtension = { extensionsScreenModel.uninstallExtension(it) },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onRefresh = extensionsScreenModel::findAvailableExtensions,
                onToggleJarSource = extensionsScreenModel::toggleJarSource,
                onUninstallJar = extensionsScreenModel::uninstallJarExtension,
                onInstallAvailableJar = extensionsScreenModel::installAvailableJar,
                onUpdateJar = extensionsScreenModel::updateJarExtension,
                onBrowseAvailableJar = { extension ->
                    extensionsScreenModel.browseAvailableJar(context, extension) { sourceId ->
                        val uiPreferences = Injekt.get<UiPreferences>()
                        val useNewSourceNavigation = uiPreferences.useNewSourceNavigation().get()
                        val screen = if (useNewSourceNavigation) {
                            SourceFeedScreen(sourceId)
                        } else {
                            BrowseSourceScreen(sourceId, GetRemoteManga.QUERY_POPULAR)
                        }
                        navigator.push(screen)
                    }
                },
                onBrowseAvailableExtension = { extension ->
                    extensionsScreenModel.browseAvailableExtension(extension) { sourceId ->
                        val uiPreferences = Injekt.get<UiPreferences>()
                        val useNewSourceNavigation = uiPreferences.useNewSourceNavigation().get()
                        val screen = if (useNewSourceNavigation) {
                            SourceFeedScreen(sourceId)
                        } else {
                            BrowseSourceScreen(sourceId, GetRemoteManga.QUERY_POPULAR)
                        }
                        navigator.push(screen)
                    }
                },
                onSelectTag = extensionsScreenModel::setSelectedTag,
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
                    )
                }
                null -> Unit
            }

            privateExtensionToUninstall?.let { extension ->
                ExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }

            sideloadError?.let { error ->
                val extName = sideloadErrorExtName.orEmpty()
                AlertDialog(
                    onDismissRequest = {
                        sideloadError = null
                        sideloadErrorExtName = null
                    },
                    title = {
                        Text(text = context.contextStringResource(KMR.strings.ext_sideload_failed, extName))
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            SelectionContainer {
                                Text(
                                    text = error.stackTraceToString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                context.copyToClipboard("Sideload Error", error.stackTraceToString())
                                sideloadError = null
                                sideloadErrorExtName = null
                            },
                        ) {
                            Text(text = context.contextStringResource(KMR.strings.ext_sideload_copy_error))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                sideloadError = null
                                sideloadErrorExtName = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun ExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_confirm_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
