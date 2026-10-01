package eu.kanade.tachiyomi.ui.browse.local

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class LocalMangaItem(
    val manga: Manga,
    val categoryBadges: List<String> = emptyList(),
    val downloadCount: Int = 0,
    val unreadCount: Long = 0,
)

data class LocalScreenState(
    val isLoading: Boolean = true,
    val isLocalLoading: Boolean = true,
    val isDownloadedLoading: Boolean = true,
    val localManga: List<LocalMangaItem> = emptyList(),
    val downloadedManga: List<LocalMangaItem> = emptyList(),
    val searchQuery: String = "",
) {
    val filteredLocalManga: List<LocalMangaItem>
        get() = if (searchQuery.isBlank()) {
            localManga
        } else {
            localManga.filter {
                it.manga.title.contains(searchQuery, ignoreCase = true) ||
                    it.categoryBadges.any { badge -> badge.contains(searchQuery, ignoreCase = true) }
            }
        }

    val filteredDownloadedManga: List<LocalMangaItem>
        get() = if (searchQuery.isBlank()) {
            downloadedManga
        } else {
            downloadedManga.filter {
                it.manga.title.contains(searchQuery, ignoreCase = true) ||
                    it.categoryBadges.any { badge -> badge.contains(searchQuery, ignoreCase = true) }
            }
        }
}

class LocalScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateScreenModel<LocalScreenState>(LocalScreenState()) {

    init {
        combine(
            getLibraryManga.subscribe(),
            getCategories.subscribe(),
            downloadCache.changes,
        ) { libraryMangaList, categoryList, _ ->
            updateDownloadedManga(libraryMangaList, categoryList)
        }
            .flowOn(Dispatchers.IO)
            .launchIn(screenModelScope)

        refreshLocalSource()
    }

    private fun getCategoryBadges(
        categoryIds: List<Long>,
        categoryMap: Map<Long, Category>,
    ): List<String> {
        return categoryIds.mapNotNull { catId ->
            val cat = categoryMap[catId]
            val catName = if (cat?.isSystemCategory == true || catId == 0L) "Default" else cat?.name
            if (catName.isNullOrBlank()) return@mapNotNull null
            val parent = cat?.parentId?.let { categoryMap[it] }
            if (parent != null) "${parent.name} > $catName" else catName
        }.distinct()
    }

    private fun updateDownloadedManga(
        libraryMangaList: List<LibraryManga>,
        categoryList: List<Category>,
    ) {
        val categoryMap = categoryList.associateBy { it.id }
        val downloaded = libraryMangaList.filter {
            downloadManager.getDownloadCount(it.manga) > 0
        }.map { libManga ->
            LocalMangaItem(
                manga = libManga.manga,
                categoryBadges = getCategoryBadges(libManga.categories, categoryMap),
                downloadCount = downloadManager.getDownloadCount(libManga.manga),
                unreadCount = libManga.unreadCount,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.manga.title })

        mutableState.update {
            it.copy(
                isDownloadedLoading = false,
                downloadedManga = downloaded,
                isLoading = it.isLocalLoading,
            )
        }
    }

    fun refresh() {
        refreshLocalSource()
    }

    fun refreshLocalSource() {
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(isLocalLoading = true) }
            val localSource = sourceManager.get(LocalSource.ID) as? CatalogueSource
            val diskMangas = try {
                localSource?.getPopularManga(1)?.mangas?.map { sManga ->
                    networkToLocalManga(sManga.toDomainManga(LocalSource.ID))
                }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            val libraryList = getLibraryManga.await()
            val categoryList = getCategories.await()
            val categoryMap = categoryList.associateBy { it.id }
            val libraryLocalMap = libraryList.filter { it.manga.source == LocalSource.ID }
                .associateBy { it.manga.url }

            val allLocalItems = mutableListOf<LocalMangaItem>()
            val seenUrls = mutableSetOf<String>()

            diskMangas.forEach { manga ->
                seenUrls.add(manga.url)
                val libManga = libraryLocalMap[manga.url]
                allLocalItems.add(
                    LocalMangaItem(
                        manga = libManga?.manga ?: manga,
                        categoryBadges = libManga?.let { getCategoryBadges(it.categories, categoryMap) }.orEmpty(),
                        downloadCount = libManga?.let { downloadManager.getDownloadCount(it.manga) } ?: 0,
                        unreadCount = libManga?.unreadCount ?: 0L,
                    ),
                )
            }

            libraryLocalMap.values.forEach { libManga ->
                if (!seenUrls.contains(libManga.manga.url)) {
                    allLocalItems.add(
                        LocalMangaItem(
                            manga = libManga.manga,
                            categoryBadges = getCategoryBadges(libManga.categories, categoryMap),
                            downloadCount = downloadManager.getDownloadCount(libManga.manga),
                            unreadCount = libManga.unreadCount,
                        ),
                    )
                }
            }

            val sorted = allLocalItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.manga.title })

            mutableState.update {
                it.copy(
                    isLocalLoading = false,
                    localManga = sorted,
                    isLoading = it.isDownloadedLoading,
                )
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }
}
