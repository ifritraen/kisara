package eu.kanade.tachiyomi.ui.browse.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.JarCatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.filter
import kotlin.time.Duration.Companion.seconds

class ExtensionsScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getExtensions: GetExtensionsByType = Injekt.get(),
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    private val _events = kotlinx.coroutines.channels.Channel<Event>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    sealed interface Event {
        data class SideloadSuccess(val extensionName: String) : Event
        data class SideloadError(val extensionName: String, val error: Throwable) : Event
    }

    private val availableJars = MutableStateFlow<List<Extension.AvailableJar>>(emptyList())

    data class ExtensionsCombo(
        val predicate: (Extension) -> Boolean,
        val nsfwOnly: Boolean,
        val downloads: Map<String, InstallStep>,
        val extensions: eu.kanade.domain.extension.model.Extensions,
        val jarSources: List<JarCatalogueSource>,
    )

    data class ExtensionsData(
        val extensions: eu.kanade.domain.extension.model.Extensions,
        val jarAvailables: List<Extension.AvailableJar>,
        val jarSources: List<JarCatalogueSource>,
    )

    init {
        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel.Item) = { map ->
            {
                ExtensionUiModel.Item(
                    it,
                    map[
                        it.pkgName +
                            // KMK -->
                            "_${it.signatureHash}",
                        // KMK <--
                    ] ?: map[it.pkgName] ?: InstallStep.Idle,
                )
            }
        }

        val extensionsFlow: kotlinx.coroutines.flow.Flow<ExtensionsData> = combine(
            getExtensions.subscribe(),
            availableJars,
            eu.kanade.tachiyomi.extension.JarExtensionManager.sources,
        ) { extensions, jarAvailables, jarSources ->
            ExtensionsData(extensions, jarAvailables, jarSources)
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { searchQueryPredicate(it ?: "") },
                state.map { it.nsfwOnly }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                extensionsFlow,
            ) { predicate: (Extension) -> Boolean, nsfwOnly: Boolean, downloads: Map<String, InstallStep>, extensionsData: ExtensionsData ->
                val extensions: eu.kanade.domain.extension.model.Extensions = extensionsData.extensions
                val jarAvailables: List<Extension.AvailableJar> = extensionsData.jarAvailables
                val jarSources: List<JarCatalogueSource> = extensionsData.jarSources

                val updatesList: List<Extension.Installed> = extensions.updates
                val installedList: List<Extension.Installed> = extensions.installed
                val availableList: List<Extension.Available> = extensions.available
                val untrustedList: List<Extension.Untrusted> = extensions.untrusted
                val enabledLanguages = preferences.enabledLanguages().get()
                buildMap<ExtensionUiModel.Header, List<ExtensionUiModel.Item>> {
                    val updates = updatesList.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--
                    if (updates.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
                    }

                    val installed = installedList.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--
                    val untrusted = untrustedList.filter(predicate).map(extensionMapper(downloads))
                        // KMK -->
                        .filter { !nsfwOnly || it.extension.isNsfw }
                    // KMK <--

                    val (sideloaded, standardInstalled) = installed.partition {
                        val ext = it.extension
                        ext is Extension.Installed && !ext.isShared
                    }

                    if (sideloaded.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(KMR.strings.ext_sideloaded), sideloaded)
                    }

                    val jarPlugins = eu.kanade.tachiyomi.extension.JarExtensionManager.getInstalledJars()
                    if (jarPlugins.isNotEmpty()) {
                        val jarItems = jarPlugins.mapNotNull { plugin ->
                            val pluginSources = jarSources.filter { it.originalSource in plugin.sources && it.lang in enabledLanguages }
                            if (pluginSources.isEmpty()) return@mapNotNull null
                            val repoName = eu.kanade.tachiyomi.extension.JarExtensionManager.getRepoNameForJar(
                                uy.kohesive.injekt.Injekt.get<android.app.Application>(),
                                plugin.jarName,
                            )
                            val pkgName = plugin.jarName.removeSuffix(".jar")
                            val matchingAvail = jarAvailables.find { it.pkgName == pkgName }
                            val hasUpdate = matchingAvail != null && matchingAvail.versionCode > 1L
                            val extension = Extension.Jar(
                                name = plugin.jarName,
                                pkgName = "jar:${plugin.jarName}",
                                versionName = "1.0",
                                versionCode = 1L,
                                libVersion = 1.0,
                                lang = "all",
                                isNsfw = false,
                                filename = plugin.jarName,
                                repoName = repoName,
                                sources = pluginSources,
                                hasUpdate = hasUpdate,
                            )
                            ExtensionUiModel.Item(
                                extension = extension,
                                installStep = downloads[pkgName] ?: InstallStep.Idle,
                            )
                        }.filter { predicate(it.extension) }
                            .sortedBy { it.extension.name }
                        if (jarItems.isNotEmpty()) {
                            put(ExtensionUiModel.Header.Resource(KMR.strings.ext_jar_extensions), jarItems)
                        }
                    }

                    val installedJarPkgs = jarPlugins.map { it.jarName.removeSuffix(".jar") }.toSet()
                    val availableJarsToDisplay = jarAvailables.filter { it.pkgName !in installedJarPkgs }
                        .filter(predicate)

                    val availableJarsByRepo = availableJarsToDisplay.groupBy { it.repoName ?: "Unknown JAR Repo" }
                    for ((repoName, avails) in availableJarsByRepo) {
                        val repoItems = avails.map { avail ->
                            ExtensionUiModel.Item(
                                extension = avail,
                                installStep = downloads[avail.pkgName] ?: InstallStep.Idle,
                            )
                        }.sortedBy { it.extension.name }
                        if (repoItems.isNotEmpty()) {
                            put(ExtensionUiModel.Header.Text("$repoName (JAR)"), repoItems)
                        }
                    }

                    if (standardInstalled.isNotEmpty() || untrusted.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_installed), standardInstalled + untrusted)
                    }

                    val languagesWithExtensions = availableList
                        .filter(predicate)
                        // KMK -->
                        .filter { !nsfwOnly || it.isNsfw }
                        // KMK <--
                        .groupBy { it.lang }
                        .toSortedMap(LocaleHelper.comparator)
                        .map { (lang, exts) ->
                            ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                                exts.map(extensionMapper(downloads))
                        }
                    if (languagesWithExtensions.isNotEmpty()) {
                        putAll(languagesWithExtensions)
                    }

                    // KMK -->
                    // Show "More..." header if no available extensions
                    if (availableList.isEmpty()) {
                        put(ExtensionUiModel.Header.Resource(KMR.strings.extensions_page_more), emptyList())
                    }
                    // KMK <--
                }
            }
                .collectLatest { items ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                        )
                    }
                }
        }

        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.extensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun searchQueryPredicate(query: String): (Extension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        return { extension ->
            subqueries.any { subquery ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true

                when (extension) {
                    is Extension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.baseUrl?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    is Extension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subquery.toLongOrNull()
                    }

                    is Extension.Jar -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.baseUrl?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    else -> false
                }
            }
        }
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<Extension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun sideloadExtension(extension: Extension) {
        screenModelScope.launchIO {
            val availableExt = when (extension) {
                is Extension.Available -> extension
                is Extension.Installed -> {
                    extensionManager.availableExtensionsFlow.value
                        .find { it.pkgName == extension.pkgName }
                }
                else -> null
            }
            if (availableExt == null) {
                _events.trySend(Event.SideloadError(extension.name, Exception("Extension update not found")))
                return@launchIO
            }

            var success = false
            var isError = false
            try {
                extensionManager.sideloadExtension(availableExt)
                    .onEach { installStep ->
                        addDownloadState(extension, installStep)
                        addDownloadState(availableExt, installStep)
                        if (installStep == InstallStep.Installed) {
                            success = true
                            extensionManager.registerSideloadedExtension(availableExt.pkgName)
                        } else if (installStep == InstallStep.Error) {
                            isError = true
                        }
                    }
                    .takeWhile { installStep -> installStep != InstallStep.Installed && installStep != InstallStep.Error }
                    .onCompletion {
                        removeDownloadState(extension)
                        removeDownloadState(availableExt)
                        if (success) {
                            _events.trySend(Event.SideloadSuccess(availableExt.name))
                        } else if (isError) {
                            val err = extensionManager.getAndClearSideloadError(availableExt.pkgName) ?: Exception("Unknown error during sideloading")
                            _events.trySend(Event.SideloadError(availableExt.name, err))
                        }
                    }
                    .collect()
            } catch (e: Exception) {
                _events.trySend(Event.SideloadError(availableExt.name, e))
            }
        }
    }

    fun installJarExtension(uri: android.net.Uri) {
        screenModelScope.launchIO {
            val context = uy.kohesive.injekt.Injekt.get<android.app.Application>()
            val success = eu.kanade.tachiyomi.extension.JarExtensionManager.installJar(context, uri)
            if (success) {
                _events.trySend(Event.SideloadSuccess("JAR Extension"))
            } else {
                _events.trySend(Event.SideloadError("JAR Extension", Exception("Failed to install JAR file")))
            }
        }
    }

    fun toggleJarSource(sourceId: Long) {
        val disabled = preferences.disabledSources().get().toMutableSet()
        val idStr = sourceId.toString()
        if (disabled.contains(idStr)) {
            disabled.remove(idStr)
        } else {
            disabled.add(idStr)
        }
        preferences.disabledSources().set(disabled)
    }

    fun uninstallJarExtension(extension: Extension.Jar) {
        screenModelScope.launchIO {
            val context = uy.kohesive.injekt.Injekt.get<android.app.Application>()
            val success = eu.kanade.tachiyomi.extension.JarExtensionManager.uninstallJar(context, extension.filename)
            if (success) {
                _events.trySend(Event.SideloadSuccess("JAR Extension"))
            } else {
                _events.trySend(Event.SideloadError("JAR Extension", Exception("Failed to uninstall JAR extension")))
            }
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launchIO {
            val availableExt = extensionManager.availableExtensionsFlow.value
                .find { it.pkgName == extension.pkgName }
            if (availableExt != null && (!extension.isShared || availableExt.signatureHash != extension.signatureHash)) {
                sideloadExtension(availableExt)
            } else {
                extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
            }
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        currentDownloads.update {
            it + Pair(
                extension.pkgName +
                    // KMK -->
                    "_${extension.signatureHash}",
                // KMK <--
                installStep,
            ) + Pair(extension.pkgName, installStep)
        }
    }

    private fun removeDownloadState(extension: Extension) {
        currentDownloads.update {
            it - (
                extension.pkgName +
                    // KMK -->
                    "_${extension.signatureHash}"
                // KMK <--
                ) - extension.pkgName
        }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .takeWhile { installStep -> installStep != InstallStep.Installed }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: Extension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()
            fetchAvailableJars()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun fetchAvailableJars() {
        screenModelScope.launchIO {
            val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
            val repos = uiPreferences.jarExtensionRepos().get()
            val list = mutableListOf<Extension.AvailableJar>()
            for (repoString in repos) {
                val parts = repoString.split("|", limit = 2)
                if (parts.size < 2) continue
                val repoName = parts[0]
                val repoUrl = parts[1]
                try {
                    val jarInfos = eu.kanade.tachiyomi.extension.JarExtensionManager.fetchRepositoryIndex(repoUrl)
                    for (info in jarInfos) {
                        list.add(
                            Extension.AvailableJar(
                                name = info.name,
                                pkgName = info.pkg,
                                versionName = info.version,
                                versionCode = info.versionCode.toLong(),
                                libVersion = 1.0,
                                lang = null,
                                isNsfw = false,
                                repoName = repoName,
                                url = info.url,
                                iconUrl = info.iconUrl,
                                repoUrl = repoUrl,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExtensionsScreenModel", "Failed to fetch JAR repo index for $repoName ($repoUrl): ${e.message}", e)
                }
            }
            availableJars.update { list }
        }
    }

    fun installAvailableJar(extension: Extension.AvailableJar) {
        screenModelScope.launchIO {
            addDownloadState(extension, InstallStep.Installing)
            val context = Injekt.get<Application>()
            val filename = "${extension.pkgName}.jar"
            val success = eu.kanade.tachiyomi.extension.JarExtensionManager.downloadAndInstallJar(
                context = context,
                url = extension.url,
                filename = filename,
                repoName = extension.repoName,
            )
            removeDownloadState(extension)
            if (success) {
                _events.trySend(Event.SideloadSuccess(extension.name))
                findAvailableExtensions()
            } else {
                _events.trySend(Event.SideloadError(extension.name, Exception("Failed to download and install JAR file")))
            }
        }
    }

    fun browseAvailableExtension(extension: Extension.Available, onBrowse: (Long) -> Unit) {
        screenModelScope.launchIO {
            var success = false
            var isError = false
            try {
                val isInstalled = extensionManager.installedExtensionsFlow.value.any { it.pkgName == extension.pkgName }
                if (!isInstalled) {
                    temporarilySideloadedPkgs.add(extension.pkgName)
                }

                extensionManager.sideloadExtension(extension)
                    .onEach { installStep ->
                        addDownloadState(extension, installStep)
                        if (installStep == InstallStep.Installed) {
                            success = true
                            extensionManager.registerSideloadedExtension(extension.pkgName)
                        } else if (installStep == InstallStep.Error) {
                            isError = true
                        }
                    }
                    .takeWhile { installStep -> installStep != InstallStep.Installed && installStep != InstallStep.Error }
                    .onCompletion {
                        removeDownloadState(extension)
                        if (success) {
                            val installed = withTimeoutOrNull<Extension.Installed>(2000) {
                                extensionManager.installedExtensionsFlow
                                    .mapNotNull { list -> list.find { it.pkgName == extension.pkgName } }
                                    .filter { it.sources.isNotEmpty() }
                                    .first()
                            }
                            val targetSourceId = installed?.sources?.firstOrNull()?.id
                            if (targetSourceId != null) {
                                onBrowse(targetSourceId)
                            } else {
                                temporarilySideloadedPkgs.remove(extension.pkgName)
                                val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == extension.pkgName }
                                if (installedExt != null) {
                                    extensionManager.uninstallExtension(installedExt)
                                }
                                _events.trySend(Event.SideloadError(extension.name, Exception("No sources found in extension")))
                            }
                        } else if (isError) {
                            temporarilySideloadedPkgs.remove(extension.pkgName)
                            val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == extension.pkgName }
                            if (installedExt != null) {
                                extensionManager.uninstallExtension(installedExt)
                            }
                            val err = extensionManager.getAndClearSideloadError(extension.pkgName) ?: Exception("Unknown error during sideloading")
                            _events.trySend(Event.SideloadError(extension.name, err))
                        }
                    }
                    .collect()
            } catch (e: Exception) {
                temporarilySideloadedPkgs.remove(extension.pkgName)
                val installedExt = extensionManager.installedExtensionsFlow.value.find { it.pkgName == extension.pkgName }
                if (installedExt != null) {
                    extensionManager.uninstallExtension(installedExt)
                }
                _events.trySend(Event.SideloadError(extension.name, e))
            }
        }
    }

    fun browseAvailableJar(context: Context, extension: Extension.AvailableJar, onBrowse: (Long) -> Unit) {
        screenModelScope.launchIO {
            var success = false
            try {
                val filename = "${extension.pkgName}.jar"
                val isInstalled = eu.kanade.tachiyomi.extension.JarExtensionManager.getInstalledJars().any { it.jarName == filename }
                if (!isInstalled) {
                    temporarilySideloadedPkgs.add("jar:$filename")
                }

                addDownloadState(extension, InstallStep.Installing)
                val ok = eu.kanade.tachiyomi.extension.JarExtensionManager.downloadAndInstallJar(
                    context = context,
                    url = extension.url,
                    filename = filename,
                    repoName = extension.repoName,
                )
                removeDownloadState(extension)
                if (ok) {
                    val sourceId = kotlinx.coroutines.withTimeoutOrNull<Long>(3000) {
                        eu.kanade.tachiyomi.extension.JarExtensionManager.sources
                            .map { sourcesList ->
                                val jarSourcesList = eu.kanade.tachiyomi.extension.JarExtensionManager.getSourcesForJar(filename)
                                jarSourcesList.firstOrNull()
                            }
                            .filterNotNull()
                            .first()
                    }
                    if (sourceId != null) {
                        success = true
                        onBrowse(sourceId)
                    } else {
                        temporarilySideloadedPkgs.remove("jar:$filename")
                        eu.kanade.tachiyomi.extension.JarExtensionManager.uninstallJar(context, filename)
                        _events.trySend(Event.SideloadError(extension.name, Exception("No sources found in JAR extension")))
                    }
                } else {
                    temporarilySideloadedPkgs.remove("jar:$filename")
                    _events.trySend(Event.SideloadError(extension.name, Exception("Failed to download JAR file")))
                }
            } catch (e: Exception) {
                val filename = "${extension.pkgName}.jar"
                temporarilySideloadedPkgs.remove("jar:$filename")
                eu.kanade.tachiyomi.extension.JarExtensionManager.uninstallJar(context, filename)
                _events.trySend(Event.SideloadError(extension.name, e))
            }
        }
    }

    fun updateJarExtension(extension: Extension.Jar) {
        screenModelScope.launchIO {
            val cleanPkg = extension.pkgName.removePrefix("jar:").removeSuffix(".jar")
            val matchingAvail = availableJars.value.find { it.pkgName == cleanPkg }
            if (matchingAvail != null) {
                installAvailableJar(matchingAvail)
            } else {
                _events.trySend(Event.SideloadError(extension.name, Exception("Update source URL not found")))
            }
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    // KMK -->
    fun toggleNsfwOnly() {
        mutableState.update {
            it.copy(nsfwOnly = !it.nsfwOnly)
        }
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        // KMK -->
        val nsfwOnly: Boolean = false,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }

    fun cleanupTemporaryExtensions() {
        screenModelScope.launchIO {
            val temps = temporarilySideloadedPkgs.toList()
            temporarilySideloadedPkgs.clear()
            val context = Injekt.get<Application>()
            temps.forEach { pkgName ->
                if (pkgName.startsWith("jar:")) {
                    val filename = pkgName.removePrefix("jar:")
                    eu.kanade.tachiyomi.extension.JarExtensionManager.uninstallJar(context, filename)
                } else {
                    extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }?.let {
                        extensionManager.uninstallExtension(it)
                    }
                }
            }
        }
    }

    companion object {
        val temporarilySideloadedPkgs = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }
}

typealias ItemGroups = Map<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>

object ExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    )
}
