package eu.kanade.tachiyomi.ui.storage.anime

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.storage.CommonStorageScreenModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import kotlinx.coroutines.flow.map
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeStorageScreenModel(
    downloadCache: AnimeDownloadCache = Injekt.get(),
    private val getLibraries: GetLibraryAnime = Injekt.get(),
    getCategories: GetAnimeCategories = Injekt.get(),
    getVisibleCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : CommonStorageScreenModel<LibraryAnime>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    categories = { hideHiddenCategories ->
        if (hideHiddenCategories) {
            getVisibleCategories.subscribe().map { list ->
                list.map { Category(id = it.id, name = it.name, order = it.order, flags = it.flags, hidden = it.hidden) }
            }
        } else {
            getCategories.subscribe().map { list ->
                list.map { Category(id = it.id, name = it.name, order = it.order, flags = it.flags, hidden = it.hidden) }
            }
        }
    },
    getDownloadSize = { downloadManager.getDownloadSize(anime) },
    getDownloadCount = { downloadManager.getDownloadCount(anime) },
    getId = { id },
    getCategoryIds = { listOf(category) },
    getTitle = { anime.title },
    getThumbnail = { anime.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val anime = getLibraries.await().find {
                it.id == id
            }?.anime ?: return@launchNonCancellable
            val source = sourceManager.get(anime.source) ?: return@launchNonCancellable
            downloadManager.deleteAnime(anime, source)
        }
    }
}
