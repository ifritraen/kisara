package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoresScreenModel(
    private val mangaRepo: ExtensionStoreRepository = Injekt.get(),
    private val animeRepo: AnimeExtensionStoreRepository = Injekt.get(),
    private val novelRepo: NovelExtensionStoreRepository = Injekt.get(),
    private val updateExtensionStores: UpdateExtensionStores = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<ExtensionStoreScreenState>(ExtensionStoreScreenState.Loading) {

    private inline fun updateSuccessState(
        func: (ExtensionStoreScreenState.Success) -> ExtensionStoreScreenState.Success,
    ) {
        mutableState.update {
            when (it) {
                ExtensionStoreScreenState.Loading -> it
                is ExtensionStoreScreenState.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            combine(
                mangaRepo.getAllAsFlow(),
                animeRepo.getAllAsFlow(),
                novelRepo.getAllAsFlow(),
            ) { mangaStores, animeStores, novelStores ->
                val mappedAnime = animeStores.map { it.toUnified() }
                val mappedNovel = novelStores.map { it.toUnified() }
                mutableState.update { current ->
                    when (current) {
                        ExtensionStoreScreenState.Loading -> ExtensionStoreScreenState.Success(
                            mangaStores = mangaStores,
                            animeStores = mappedAnime,
                            novelStores = mappedNovel,
                            disabledRepos = sourcePreferences.disabledRepos().get(),
                        )
                        is ExtensionStoreScreenState.Success -> current.copy(
                            mangaStores = mangaStores,
                            animeStores = mappedAnime,
                            novelStores = mappedNovel,
                        )
                    }
                }
            }.launchIn(screenModelScope)
        }

        sourcePreferences.disabledRepos().changes()
            .onEach { disabledRepos ->
                mutableState.update {
                    when (it) {
                        is ExtensionStoreScreenState.Success -> it.copy(disabledRepos = disabledRepos)
                        else -> it
                    }
                }
            }
            .launchIn(screenModelScope)
    }

    fun selectMediaIndex(index: Int) {
        updateSuccessState { it.copy(selectedMediaIndex = index) }
    }

    fun createRepo(indexUrl: String) {
        val activeIndex = (state.value as? ExtensionStoreScreenState.Success)?.selectedMediaIndex ?: 0
        screenModelScope.launchIO {
            updateSuccessState {
                it.copy(
                    dialog = when (it.dialog) {
                        is ExtensionStoreDialog.Create -> it.dialog.copy(processing = true)
                        is ExtensionStoreDialog.Confirm -> it.dialog.copy(processing = true)
                        else -> it.dialog
                    },
                )
            }
            val result = when (activeIndex) {
                1 -> animeRepo.insert(indexUrl)
                2 -> novelRepo.insert(indexUrl)
                else -> mangaRepo.insert(indexUrl)
            }
            result
                .onSuccess {
                    when (activeIndex) {
                        1 -> runCatching { animeExtensionManager.findAvailableExtensions() }
                        2 -> runCatching { novelExtensionManager.refreshAvailablePlugins() }
                        else -> extensionManager.findAvailableExtensions()
                    }
                    dismissDialog()
                }
                .onFailure { throwable ->
                    updateSuccessState {
                        it.copy(
                            dialog = when (it.dialog) {
                                is ExtensionStoreDialog.Create -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )
                                is ExtensionStoreDialog.Confirm -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )
                                else -> it.dialog
                            },
                        )
                    }
                }
        }
    }

    fun refreshRepos() {
        val status = state.value as? ExtensionStoreScreenState.Success ?: return
        screenModelScope.launchIO {
            when (status.selectedMediaIndex) {
                1 -> {
                    animeRepo.refreshAll()
                    runCatching { animeExtensionManager.findAvailableExtensions() }
                }
                2 -> {
                    novelRepo.refreshAll()
                    runCatching { novelExtensionManager.refreshAvailablePlugins() }
                }
                else -> {
                    updateExtensionStores()
                    extensionManager.findAvailableExtensions()
                }
            }
        }
    }

    fun deleteRepo(indexUrl: String) {
        val activeIndex = (state.value as? ExtensionStoreScreenState.Success)?.selectedMediaIndex ?: 0
        enableStore(indexUrl)
        screenModelScope.launchIO {
            when (activeIndex) {
                1 -> {
                    animeRepo.remove(indexUrl)
                    runCatching { animeExtensionManager.findAvailableExtensions() }
                }
                2 -> {
                    novelRepo.remove(indexUrl)
                    runCatching { novelExtensionManager.refreshAvailablePlugins() }
                }
                else -> {
                    mangaRepo.remove(indexUrl)
                    extensionManager.findAvailableExtensions()
                }
            }
        }
    }

    fun enableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (indexUrl in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos.filterNot { it == indexUrl }.toSet(),
            )
        }
    }

    fun disableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos().get()
        if (indexUrl !in disabledRepos) {
            sourcePreferences.disabledRepos().set(
                disabledRepos + indexUrl,
            )
        }
    }

    fun refreshExtensionList() {
        screenModelScope.launchIO {
            extensionManager.findAvailableExtensions()
            runCatching { animeExtensionManager.findAvailableExtensions() }
            runCatching { novelExtensionManager.refreshAvailablePlugins() }
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        updateSuccessState { state ->
            state.copy(
                dialog = ExtensionStoreDialog.Confirm(
                    url = storeIndexUrl,
                    alreadyExists = state.stores.any { it.indexUrl == storeIndexUrl },
                ),
            )
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        updateSuccessState { state ->
            state.copy(dialog = dialog)
        }
    }

    fun dismissDialog() {
        updateSuccessState {
            it.copy(dialog = null)
        }
    }

    private fun mihon.domain.extensionstore.model.ExtensionStore.toUnified(): ExtensionStore {
        return ExtensionStore(
            indexUrl = indexUrl,
            name = displayName,
            badgeLabel = badgeLabel,
            signingKey = signingKey,
            contact = ExtensionStore.Contact(contact.website, contact.discord),
            isLegacy = isLegacy,
            extensionListUrl = extensionListUrl,
        )
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val mangaStores: List<ExtensionStore> = emptyList(),
        val animeStores: List<ExtensionStore> = emptyList(),
        val novelStores: List<ExtensionStore> = emptyList(),
        val selectedMediaIndex: Int = 0,
        val dialog: ExtensionStoreDialog? = null,
        val disabledRepos: Set<String> = emptySet(),
    ) : ExtensionStoreScreenState() {

        val stores: List<ExtensionStore>
            get() = when (selectedMediaIndex) {
                1 -> animeStores
                2 -> novelStores
                else -> mangaStores
            }

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
