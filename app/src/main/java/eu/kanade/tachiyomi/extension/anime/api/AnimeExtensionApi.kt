package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.selectAnimeRegularUpdate
import eu.kanade.tachiyomi.extension.anime.model.selectAnimeReinstallCandidates
import eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.days

internal class AnimeExtensionApi(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val storeRepository: AnimeExtensionStoreRepository = Injekt.get(),
    private val extensionStoreService: ExtensionStoreService = Injekt.get(),
    private val updateExtensionRepo: UpdateAnimeExtensionRepo = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_anime_ext_check"), 0)
    }

    suspend fun checkForUpdatesIfDue(context: Context): List<AnimeExtension.Installed>? {
        return checkForUpdates(context, fromAvailableExtensionList = false)
    }

    /**
     * The result of fetching the available extensions.
     *
     * @param extensions The extensions of the stores that responded successfully.
     * @param failedRepoUrls The repo urls of the stores that failed to respond. When this is not
     * empty the fetch is partial and consumers should not assume missing extensions were removed.
     */
    data class FetchedExtensions(
        val extensions: List<AnimeExtension.Available>,
        val failedRepoUrls: Set<String>,
    ) {
        val isComplete: Boolean get() = failedRepoUrls.isEmpty()
    }

    suspend fun findExtensions(): FetchedExtensions {
        return withIOContext {
            val stores = storeRepository.getAll()
            val availableExtensions = mutableListOf<AnimeExtension.Available>()
            val failedRepoUrls = mutableSetOf<String>()

            for (store in stores) {
                val legacyStore = mihon.domain.extension.model.ExtensionStore(
                    indexUrl = store.indexUrl,
                    name = store.name,
                    badgeLabel = store.badgeLabel,
                    signingKey = store.signingKey,
                    contact = mihon.domain.extension.model.ExtensionStore.Contact(
                        website = store.contact.website,
                        discord = store.contact.discord,
                    ),
                    isLegacy = store.isLegacy,
                    extensionListUrl = store.extensionListUrl,
                )

                val result = extensionStoreService.getExtensions(legacyStore)
                result.onSuccess { exts ->
                    availableExtensions += exts.map { ext ->
                        AnimeExtension.Available(
                            name = ext.name,
                            pkgName = ext.pkgName,
                            versionName = ext.versionName,
                            versionCode = ext.versionCode,
                            libVersion = ext.libVersion,
                            lang = ext.lang,
                            isNsfw = ext.isNsfw,
                            isTorrent = false,
                            sources = ext.sources.map { s ->
                                AnimeExtension.Available.AnimeSource(
                                    id = s.id,
                                    lang = s.lang,
                                    name = s.name,
                                    baseUrl = s.baseUrl,
                                )
                            },
                            apkName = ext.apkUrl,
                            iconUrl = ext.iconUrl,
                            repoUrl = store.indexUrl,
                            repoName = store.name,
                        )
                    }
                }.onFailure {
                    failedRepoUrls += store.indexUrl
                }
            }

            FetchedExtensions(
                extensions = availableExtensions,
                failedRepoUrls = failedRepoUrls,
            )
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<AnimeExtension.Installed>? {
        val nowMs = timeProvider()
        if (!fromAvailableExtensionList &&
            nowMs < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            animeExtensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().extensions.also { lastExtCheck.set(nowMs) }
        }

        val extensionVariantsByPkgName = extensions.groupBy { it.pkgName }

        val installedExtensions = animeExtensionManager.installedExtensionsFlow.value

        val extensionsWithUpdate = mutableListOf<AnimeExtension.Installed>()
        for (installedExt in installedExtensions) {
            val variants = extensionVariantsByPkgName[installedExt.pkgName].orEmpty()
            if (variants.isEmpty()) continue
            val hasUpdate = selectAnimeRegularUpdate(installedExt, variants) != null ||
                selectAnimeReinstallCandidates(installedExt, variants).isNotEmpty()
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(
                names = extensionsWithUpdate.map { it.name },
                anime = true,
            )
        }

        return extensionsWithUpdate
    }

    fun getApkUrl(extension: AnimeExtension.Available): String {
        return if (extension.apkName.startsWith("http://") || extension.apkName.startsWith("https://")) {
            extension.apkName
        } else {
            val base = extension.repoUrl
                .removeSuffix("/repo.json")
                .removeSuffix("/index.min.json")
                .removeSuffix("/")
            "$base/apk/${extension.apkName}"
        }
    }
}
