package eu.kanade.tachiyomi.source

import android.content.Context
import android.graphics.Canvas
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import android.graphics.Rect as AndroidRect
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect as KotatsuRect

class AndroidBitmapWrapper(val bitmap: android.graphics.Bitmap) : KotatsuBitmap {
    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: KotatsuRect, dst: KotatsuRect) {
        val srcAndroid = AndroidRect(src.left, src.top, src.right, src.bottom)
        val dstAndroid = AndroidRect(dst.left, dst.top, dst.right, dst.bottom)
        val canvas = Canvas(bitmap)
        val sourceAndroid = (sourceBitmap as AndroidBitmapWrapper).bitmap
        canvas.drawBitmap(sourceAndroid, srcAndroid, dstAndroid, null)
    }
}

class JarCatalogueSource(
    val originalSource: MangaSource,
    val repoName: String?,
    private val parserFactory: () -> MangaParser,
) : HttpSource() {

    override val name: String = originalSource.name.lowercase().replaceFirstChar { it.uppercase() }
    override val lang: String = originalSource.locale.ifBlank { "en" }
    override val supportsLatest: Boolean = true

    // Use a unique long ID generated deterministically from package name + name
    override val id: Long = generateId(originalSource.name + " (Kotatsu)", lang, 1)

    override val baseUrl: String by lazy {
        "https://${originalSource.name.lowercase().replace(Regex("[^a-z0-9]"), "")}.com"
    }

    private val parser: MangaParser by lazy { parserFactory() }

    init {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val app = Injekt.get<android.app.Application>()
                fetchAndCacheIcon(app)
            } catch (e: Exception) {
                android.util.Log.e("JarCatalogueSource", "Failed to resolve Application or fetch favicon: ${e.message}")
            }
        }
    }

    private suspend fun fetchAndCacheIcon(context: Context) {
        val cachedFile = File(File(context.filesDir, "source_icons"), "$id.png")
        if (cachedFile.exists() && cachedFile.length() > 0) return

        try {
            val favicons = parser.getFavicons()
            val bestIcon = favicons.find(48)
            if (bestIcon != null) {
                val url = bestIcon.url
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        favicons.referer?.let { header("Referer", it) }
                    }
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        cachedFile.parentFile?.mkdirs()
                        cachedFile.writeBytes(bytes)
                    }
                }
                response.close()
            }
        } catch (e: Exception) {
            android.util.Log.w("JarCatalogueSource", "Failed to fetch favicon for $name: ${e.message}")
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 20
        val list = parser.getList(offset, SortOrder.POPULARITY, MangaListFilter.EMPTY)
        return MangasPage(
            mangas = list.map { it.toSManga() },
            hasNextPage = list.isNotEmpty(),
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * 20
        val order = if (parser.availableSortOrders.contains(SortOrder.UPDATED)) {
            SortOrder.UPDATED
        } else {
            SortOrder.POPULARITY
        }
        val list = parser.getList(offset, order, MangaListFilter.EMPTY)
        return MangasPage(
            mangas = list.map { it.toSManga() },
            hasNextPage = list.isNotEmpty(),
        )
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * 20
        val searchFilter = MangaListFilter(query = query)
        val list = parser.getList(offset, SortOrder.RELEVANCE, searchFilter)
        return MangasPage(
            mangas = list.map { it.toSManga() },
            hasNextPage = list.isNotEmpty(),
        )
    }

    override fun getFilterList(): FilterList {
        return FilterList()
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val kManga = parser.getDetails(manga.toKotatsuManga())
        return kManga.toSManga()
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val kManga = parser.getDetails(manga.toKotatsuManga())
        return kManga.chapters?.map { it.toSChapter() }?.reversed() ?: emptyList()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val kChapter = chapter.toKotatsuChapter()
        val kPages = parser.getPages(kChapter)
        return kPages.mapIndexed { index, kPage -> kPage.toPage(index) }
    }

    override suspend fun getImageUrl(page: Page): String {
        val kPage = MangaPage(
            id = page.index.toLong(),
            url = page.url,
            preview = null,
            source = originalSource,
        )
        return parser.getPageUrl(kPage)
    }

    // Abstract methods required by HttpSource
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Helper converters
    private fun Manga.toSManga(): SManga = SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = this@toSManga.coverUrl ?: this@toSManga.largeCoverUrl
        val creators = this@toSManga.authors.joinToString(", ")
        author = creators
        artist = creators
        description = this@toSManga.description
        status = when (this@toSManga.state) {
            MangaState.ONGOING -> SManga.ONGOING
            MangaState.FINISHED -> SManga.COMPLETED
            MangaState.ABANDONED -> SManga.CANCELLED
            MangaState.RESTRICTED -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
        genre = this@toSManga.tags.joinToString(", ") { it.title }
    }

    private fun SManga.toKotatsuManga(): Manga = Manga(
        id = 0L,
        title = this.title,
        altTitles = emptySet(),
        url = this.url,
        publicUrl = this.url,
        rating = 0f,
        contentRating = null,
        coverUrl = this.thumbnail_url,
        tags = this.genre?.split(",")?.map { org.koitharu.kotatsu.parsers.model.MangaTag(title = it.trim(), key = it.trim(), source = originalSource) }?.toSet().orEmpty(),
        state = when (this.status) {
            SManga.ONGOING -> MangaState.ONGOING
            SManga.COMPLETED -> MangaState.FINISHED
            else -> null
        },
        authors = this.author?.split(",")?.map { it.trim() }?.toSet().orEmpty(),
        chapters = null,
        source = originalSource,
    )

    private fun SChapter.toKotatsuChapter(): MangaChapter {
        val rawScanlator = this.scanlator
        val branchName = if (rawScanlator != null && " - " in rawScanlator) {
            rawScanlator.substringBefore(" - ")
        } else if (rawScanlator != null && rawScanlator.endsWith(" (Branch)")) {
            rawScanlator.removeSuffix(" (Branch)")
        } else {
            null
        }
        val scanlatorName = if (rawScanlator != null && " - " in rawScanlator) {
            rawScanlator.substringAfter(" - ")
        } else if (rawScanlator != null && rawScanlator.endsWith(" (Branch)")) {
            null
        } else {
            rawScanlator
        }
        return MangaChapter(
            id = 0L,
            title = this.name,
            number = this.chapter_number,
            volume = 0,
            url = this.url,
            scanlator = scanlatorName,
            uploadDate = this.date_upload,
            branch = branchName,
            source = originalSource,
        )
    }

    private fun MangaChapter.toSChapter(): SChapter = SChapter.create().apply {
        url = this@toSChapter.url
        name = this@toSChapter.title?.takeIf { it.isNotBlank() } ?: buildString {
            if (this@toSChapter.volume > 0) append("Vol. ").append(this@toSChapter.volume).append(" ")
            append("Chapter ").append(this@toSChapter.number.toString().removeSuffix(".0"))
        }
        chapter_number = this@toSChapter.number
        scanlator = buildString {
            if (!this@toSChapter.branch.isNullOrBlank()) {
                append(this@toSChapter.branch)
                if (!this@toSChapter.scanlator.isNullOrBlank()) {
                    append(" - ").append(this@toSChapter.scanlator)
                } else {
                    append(" (Branch)")
                }
            } else {
                if (!this@toSChapter.scanlator.isNullOrBlank()) {
                    append(this@toSChapter.scanlator)
                }
            }
        }.takeIf { it.isNotBlank() }
        date_upload = this@toSChapter.uploadDate
    }

    private fun MangaPage.toPage(index: Int): Page = Page(
        index = index,
        url = this.url,
        imageUrl = null,
    )
}
