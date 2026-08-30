package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupFileValidator(
    private val context: Context,

    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: tachiyomi.domain.source.anime.service.AnimeSourceManager = Injekt.get(),
    private val novelSourceManager: tachiyomi.domain.source.novel.service.NovelSourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    fun validate(uri: Uri): Results {
        val backup = try {
            BackupDecoder(context).decode(uri)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val missingMangaSources = backup.backupSources
            .filter { sourceManager.get(it.sourceId) == null }
            .map { it.name.ifEmpty { sourceManager.getOrStub(it.sourceId).toString() } }

        val missingAnimeSources = backup.backupAnimeSources
            .filter { animeSourceManager.get(it.sourceId) == null }
            .map { it.name.ifEmpty { animeSourceManager.getOrStub(it.sourceId).toString() } }

        val missingNovelSources = backup.backupNovelSources
            .filter { novelSourceManager.get(it.sourceId) == null }
            .map { it.name.ifEmpty { novelSourceManager.getOrStub(it.sourceId).toString() } }

        val missingSources = (missingMangaSources + missingAnimeSources + missingNovelSources)
            .distinct()
            .sorted()

        val allTrackers = (backup.backupManga.flatMap { it.tracking } +
            backup.backupAnime.flatMap { it.tracking } +
            backup.backupNovel.flatMap { it.tracking })
            .map { it.syncId }
            .distinct()
        val missingTrackers = allTrackers
            .mapNotNull { trackerManager.get(it.toLong()) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(
            missingSources = missingSources,
            missingTrackers = missingTrackers,
            mangaCount = backup.backupManga.size,
            animeCount = backup.backupAnime.size,
            novelCount = backup.backupNovel.size,
            mangaCategoryCount = backup.backupCategories.size,
            animeCategoryCount = backup.backupAnimeCategories.size,
            novelCategoryCount = backup.backupNovelCategories.size,
        )
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
        val mangaCount: Int = 0,
        val animeCount: Int = 0,
        val novelCount: Int = 0,
        val mangaCategoryCount: Int = 0,
        val animeCategoryCount: Int = 0,
        val novelCategoryCount: Int = 0,
    )
}
