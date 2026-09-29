package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.translation.ColorizerManager
import eu.kanade.translation.TranslationManager
import eu.kanade.translation.model.PageTranslation
import mihon.core.archive.archiveReader
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) : PageLoader() {

    private val context: Application by injectLazy()
    private val translationManager: TranslationManager by injectLazy()
    private val colorizerManager: ColorizerManager by injectLazy()
    private val superResolutionManager: eu.kanade.translation.SuperResolutionManager by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            // SY -->
            manga.ogTitle,
            // SY <--
            source,
        )
        val translations = translationManager.getChapterTranslation(
            dbChapter.name,
            dbChapter.scanlator,
            manga.ogTitle,
            source,
        )
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath, dbChapter, translations)
        } else {
            getPagesFromDirectory(translations)
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(
        file: UniFile,
        dbChapter: eu.kanade.tachiyomi.data.database.models.Chapter,
        translations: Map<String, PageTranslation>,
    ): List<ReaderPage> {
        val loader = ArchivePageLoader(
            reader = file.archiveReader(context),
            chapterName = dbChapter.name,
            scanlator = dbChapter.scanlator,
            mangaTitle = manga.ogTitle,
            source = source,
            translations = translations,
        ).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(translations: Map<String, PageTranslation>): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val pages = downloadManager.buildPageList(source, manga, dbChapter.toDomainChapter()!!)
        return pages.map { page ->
            val pageName = page.uri?.let { uri ->
                val decoded = try { Uri.decode(uri.toString()) } catch (_: Exception) { uri.toString() }
                decoded.substringAfterLast("/").substringAfterLast(":")
            } ?: page.imageUrl?.substringAfterLast("/")

            ReaderPage(page.index, page.url, page.imageUrl) {
                val superResFile = if (pageName != null) {
                    superResolutionManager.getSuperResolutionPageFile(
                        chapterName = dbChapter.name,
                        scanlator = dbChapter.scanlator,
                        mangaTitle = manga.ogTitle,
                        source = source,
                        pageName = pageName,
                    )
                } else {
                    null
                }
                val colorizedFile = if (pageName != null) {
                    colorizerManager.getColorizedPageFile(
                        chapterName = dbChapter.name,
                        scanlator = dbChapter.scanlator,
                        mangaTitle = manga.ogTitle,
                        source = source,
                        pageName = pageName,
                    )
                } else {
                    null
                }
                if (superResFile != null && superResFile.exists() && superResFile.length() > 0) {
                    superResFile.openInputStream()!!
                } else if (colorizedFile != null && colorizedFile.exists() && colorizedFile.length() > 0) {
                    colorizedFile.openInputStream()!!
                } else {
                    context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
                }
            }.apply {
                status = Page.State.Ready
                // KMK -->
                val matchedTranslation = if (translations.isNotEmpty()) {
                    translations[pageName]
                        ?: (if (pageName != null) translations.entries.firstOrNull { it.key.equals(pageName, ignoreCase = true) || it.key.substringBeforeLast(".") == pageName.substringBeforeLast(".") }?.value else null)
                        ?: translations.entries.firstOrNull { entry ->
                            val digits = entry.key.filter { it.isDigit() }.toIntOrNull()
                            digits != null && (digits == page.index + 1 || digits == page.index)
                        }?.value
                } else {
                    null
                }
                if (matchedTranslation != null) {
                    translation = matchedTranslation
                    translationKey = pageName ?: "${page.index + 1}.jpg"
                }
                // KMK <--
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
