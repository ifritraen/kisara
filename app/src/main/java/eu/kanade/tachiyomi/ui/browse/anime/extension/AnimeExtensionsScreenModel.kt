package eu.kanade.tachiyomi.ui.browse.anime.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.toInstalledAnimeExtensionPkgName
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

class AnimeExtensionsScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val getExtensions: GetAnimeExtensionsByType = Injekt.get(),
    private val context: Application = Injekt.get(),
) : StateScreenModel<ExtensionsScreenModel.State>(ExtensionsScreenModel.State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        screenModelScope.launchIO {
            combine(
                preferences.customSourceTags().changes(),
                preferences.sourceTagMappings().changes(),
            ) { tags, mappings ->
                Pair(tags, mappings)
            }.onEach { (tags, mappings) ->
                mutableState.update {
                    it.copy(
                        allTags = tags.toImmutableSet(),
                        extensionTagMappings = mappings.toImmutableSet(),
                    )
                }
            }.launchIn(screenModelScope)

            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
                    .map { query ->
                        val trimmed = query?.trim().orEmpty()
                        val predicate: (AnimeExtension) -> Boolean = { ext ->
                            trimmed.isEmpty() || ext.name.contains(trimmed, ignoreCase = true) || ext.pkgName.contains(trimmed, ignoreCase = true)
                        }
                        predicate
                    },
                state.map { it.nsfwOnly }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                getExtensions.subscribe(),
            ) { predicate, nsfwOnly, downloads, extensions ->
                val mapper: (AnimeExtension) -> ExtensionUiModel.Item = { animeExt ->
                    ExtensionUiModel.Item(
                        extension = animeExt.toUnifiedExtension(),
                        installStep = downloads[animeExt.pkgName] ?: downloads[animeExt.pkgName.toInstalledAnimeExtensionPkgName()] ?: InstallStep.Idle,
                    )
                }

                val updatesList = extensions.updates
                val installedList = extensions.installed
                val availableList = extensions.available
                val untrustedList = extensions.untrusted

                val result = mutableMapOf<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>()

                val updates = updatesList.filter { predicate(it) }.map(mapper)
                    .filter { !nsfwOnly || it.extension.isNsfw }
                if (updates.isNotEmpty()) {
                    result[ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = installedList.filter { predicate(it) }.map(mapper)
                    .filter { !nsfwOnly || it.extension.isNsfw }
                val untrusted = untrustedList.filter { predicate(it) }.map(mapper)
                    .filter { !nsfwOnly || it.extension.isNsfw }

                val (sideloaded, standardInstalled) = installed.partition {
                    val ext = it.extension
                    ext is Extension.Installed && !ext.isShared
                }

                if (sideloaded.isNotEmpty()) {
                    result[ExtensionUiModel.Header.Resource(KMR.strings.ext_sideloaded)] = sideloaded
                }

                if (standardInstalled.isNotEmpty() || untrusted.isNotEmpty()) {
                    result[ExtensionUiModel.Header.Resource(MR.strings.ext_installed)] = standardInstalled + untrusted
                }

                val languagesWithExtensions = availableList
                    .filter { predicate(it) }
                    .filter { !nsfwOnly || it.isNsfw }
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, exts) ->
                        ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                            exts.map(mapper)
                    }
                if (languagesWithExtensions.isNotEmpty()) {
                    result.putAll(languagesWithExtensions)
                }

                if (availableList.isEmpty()) {
                    result[ExtensionUiModel.Header.Resource(KMR.strings.extensions_page_more)] = emptyList()
                }

                result
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

        preferences.animeExtensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun toggleNsfwOnly() {
        mutableState.update {
            it.copy(nsfwOnly = !it.nsfwOnly)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            extensionManager.installedExtensionsFlow.first()
                .filter { it.hasUpdate }
                .forEach { updateExtension(it) }
        }
    }

    private fun findMatchingAvailable(pkgName: String): AnimeExtension.Available? {
        val target = pkgName.toInstalledAnimeExtensionPkgName()
        return extensionManager.availableExtensionsFlow.value.find {
            it.pkgName == pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == target
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launchIO {
            val avail = findMatchingAvailable(extension.pkgName) ?: return@launchIO
            extensionManager.installExtension(avail).collectToInstallUpdate(avail.pkgName)
        }
    }

    fun uninstallExtension(extension: Extension) {
        screenModelScope.launchIO {
            val targetPkg = extension.pkgName.toInstalledAnimeExtensionPkgName()
            val installed = extensionManager.installedExtensionsFlow.first().find { 
                it.pkgName == extension.pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == targetPkg 
            }
            val untrusted = if (installed == null) extensionManager.untrustedExtensionsFlow.first().find { 
                it.pkgName == extension.pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == targetPkg 
            } else null
            val target = installed ?: untrusted
            if (target != null) {
                extensionManager.uninstallExtension(target)
            }
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launchIO {
            val availableExt = findMatchingAvailable(extension.pkgName)
            if (availableExt != null && !extension.isShared) {
                sideloadExtension(availableExt.toUnifiedExtension())
            } else {
                val installed = extensionManager.installedExtensionsFlow.first().find { 
                    it.pkgName == extension.pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == extension.pkgName.toInstalledAnimeExtensionPkgName() 
                }
                installed?.let {
                    updateExtension(it)
                }
            }
        }
    }

    fun updateExtension(extension: AnimeExtension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension.pkgName)
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launchIO {
            val targetPkg = extension.pkgName.toInstalledAnimeExtensionPkgName()
            extensionManager.untrustedExtensionsFlow.first().find { 
                it.pkgName == extension.pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == targetPkg 
            }?.let {
                extensionManager.trust(it)
            }
        }
    }

    private val _events = kotlinx.coroutines.channels.Channel<ExtensionsScreenModel.Event>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            try {
                extensionManager.findAvailableExtensions()
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun sideloadExtension(extension: Extension) {
        screenModelScope.launchIO {
            val availableExt = findMatchingAvailable(extension.pkgName)
            if (availableExt == null) {
                _events.trySend(ExtensionsScreenModel.Event.SideloadError(extension.name, Exception("Extension not found in repository")))
                return@launchIO
            }

            var success = false
            var isError = false
            try {
                extensionManager.sideloadExtension(availableExt)
                    .onEach { installStep ->
                        currentDownloads.update {
                            val mutable = it.toMutableMap()
                            when (installStep) {
                                InstallStep.Idle, InstallStep.Installed -> {
                                    mutable.remove(availableExt.pkgName)
                                    mutable.remove(extension.pkgName)
                                }
                                else -> {
                                    mutable[availableExt.pkgName] = installStep
                                    mutable[extension.pkgName] = installStep
                                }
                            }
                            mutable
                        }
                        if (installStep == InstallStep.Installed) {
                            success = true
                            extensionManager.registerSideloadedExtension(availableExt.pkgName)
                        } else if (installStep == InstallStep.Error) {
                            isError = true
                        }
                    }
                    .takeWhile { installStep -> installStep != InstallStep.Installed && installStep != InstallStep.Error }
                    .catch { e ->
                        currentDownloads.update { it - availableExt.pkgName - extension.pkgName }
                        _events.trySend(ExtensionsScreenModel.Event.SideloadError(availableExt.name, e))
                    }
                    .onCompletion {
                        currentDownloads.update { it - availableExt.pkgName - extension.pkgName }
                        if (success) {
                            _events.trySend(ExtensionsScreenModel.Event.SideloadSuccess(availableExt.name))
                        } else if (isError) {
                            val err = extensionManager.getAndClearSideloadError(availableExt.pkgName)
                                ?: Exception("Unknown error during sideloading")
                            _events.trySend(ExtensionsScreenModel.Event.SideloadError(availableExt.name, err))
                        }
                    }
                    .collect()
            } catch (e: Exception) {
                _events.trySend(ExtensionsScreenModel.Event.SideloadError(availableExt.name, e))
            }
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        val targetPkg = extension.pkgName.toInstalledAnimeExtensionPkgName()
        val ext = findMatchingAvailable(extension.pkgName)
            ?: extensionManager.installedExtensionsFlow.value
                .find { it.pkgName == extension.pkgName || it.pkgName.toInstalledAnimeExtensionPkgName() == targetPkg }
        if (ext != null) {
            extensionManager.cancelInstallUpdateExtension(ext)
        }
    }

    private fun Flow<InstallStep>.collectToInstallUpdate(pkgName: String) =
        screenModelScope.launch {
            this@collectToInstallUpdate.collect { step ->
                currentDownloads.update {
                    val mutable = it.toMutableMap()
                    when (step) {
                        InstallStep.Idle, InstallStep.Installed -> mutable.remove(pkgName)
                        else -> mutable[pkgName] = step
                    }
                    mutable
                }
            }
        }

    private fun AnimeExtension.toUnifiedExtension(): Extension {
        return when (this) {
            is AnimeExtension.Installed -> Extension.Installed(
                name = name,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                lang = lang,
                isNsfw = isNsfw,
                signatureHash = "",
                storeName = repoName,
                pkgFactory = pkgFactory,
                sources = emptyList(),
                icon = icon,
                hasUpdate = hasUpdate,
                isObsolete = isObsolete,
                isShared = isShared,
                store = null,
                isRedundant = false,
            )
            is AnimeExtension.Available -> Extension.Available(
                name = name,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                lang = lang,
                isNsfw = isNsfw,
                signatureHash = "",
                storeName = repoName,
                sources = emptyList(),
                apkUrl = apkName,
                iconUrl = iconUrl,
                store = mihon.domain.extension.model.ExtensionStore(
                    indexUrl = repoUrl,
                    name = repoName,
                    badgeLabel = repoName,
                    signingKey = "",
                    contact = mihon.domain.extension.model.ExtensionStore.Contact("", null),
                    isLegacy = false,
                    extensionListUrl = null,
                ),
            )
            is AnimeExtension.Untrusted -> Extension.Untrusted(
                name = name,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                signatureHash = signatureHash,
                storeName = null,
                lang = lang,
                isNsfw = isNsfw,
            )
        }
    }

    fun setDialog(dialog: ExtensionsScreenModel.Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun saveExtensionTags(pkgName: String, selectedTags: Set<String>, newTag: String?) {
        val currentAllTags = preferences.customSourceTags().get().toMutableSet()
        if (newTag != null) {
            currentAllTags.add(newTag)
            preferences.customSourceTags().set(currentAllTags)
        }

        val prefix = "ext_$pkgName:"
        val currentMappings = preferences.sourceTagMappings().get().filterNot { it.startsWith(prefix) }.toMutableSet()
        selectedTags.forEach { tag ->
            currentMappings.add("$prefix$tag")
        }
        preferences.sourceTagMappings().set(currentMappings)
    }
}

