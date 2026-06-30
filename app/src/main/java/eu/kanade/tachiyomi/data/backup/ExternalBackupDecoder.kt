package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.max

class ExternalBackupDecoder(
    private val context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Attempts to decode an external zip backup (Kotatsu/Kototoro or Venera).
     * Returns null if the backup is not recognized as a supported external format.
     */
    fun decodeExternal(uri: Uri): Backup? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val isZip = checkIfZip(uri)
        if (!isZip) {
            inputStream.close()
            return null
        }

        // We will scan the zip entries to detect format
        var hasKotatsuFiles = false
        var hasVeneraFiles = false

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (name == "favourites.json" || name == "history.json" || name == "categories.json") {
                    hasKotatsuFiles = true
                } else if (name == "local_favorite.db" || name == "history.db") {
                    hasVeneraFiles = true
                }
                entry = zip.nextEntry
            }
        }

        return when {
            hasKotatsuFiles -> decodeKotatsu(uri)
            hasVeneraFiles -> decodeVenera(uri)
            else -> null
        }
    }

    private fun checkIfZip(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                read == 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // --- Kotatsu / Kototoro Decoding ---

    @Serializable
    private data class KotatsuTag(val name: String)

    @Serializable
    private data class KotatsuManga(
        val title: String,
        val url: String,
        val cover_url: String? = null,
        val author: String? = null,
        val source: String,
        val tags: List<KotatsuTag> = emptyList(),
    )

    @Serializable
    private data class KotatsuFavourite(
        val manga_id: Long,
        val category_id: Long,
        val manga: KotatsuManga,
    )

    @Serializable
    private data class KotatsuHistory(
        val manga_id: Long,
        val created_at: Long,
        val updated_at: Long,
        val chapter_id: Long,
        val page: Int = 0,
        val manga: KotatsuManga,
    )

    @Serializable
    private data class KotatsuCategory(
        val category_id: Long,
        val title: String,
        val sort_key: Int = 0,
    )

    private fun decodeKotatsu(uri: Uri): Backup {
        var favourites = emptyList<KotatsuFavourite>()
        var history = emptyList<KotatsuHistory>()
        var categories = emptyList<KotatsuCategory>()

        context.contentResolver.openInputStream(uri)?.let { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    when (name) {
                        "favourites.json" -> {
                            val text = zip.reader().readText()
                            favourites = jsonParser.decodeFromString(text)
                        }
                        "history.json" -> {
                            val text = zip.reader().readText()
                            history = jsonParser.decodeFromString(text)
                        }
                        "categories.json" -> {
                            val text = zip.reader().readText()
                            categories = jsonParser.decodeFromString(text)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }

        // Map categories
        val categoryMap = categories.associate { it.category_id to it.title }
        val backupCategories = categories.map {
            BackupCategory(name = it.title, order = it.category_id, flags = 0)
        }

        // Group by URL to merge favorites and history for the same manga
        val mangaGroup = LinkedHashMap<String, ExternalRecord>()
        favourites.forEach { fav ->
            val key = fav.manga.url
            val existing = mangaGroup[key]
            val catList = existing?.categories.orEmpty() + fav.category_id
            mangaGroup[key] = ExternalRecord(
                title = fav.manga.title,
                url = fav.manga.url,
                coverUrl = fav.manga.cover_url,
                author = fav.manga.author,
                source = fav.manga.source,
                tags = fav.manga.tags.map { it.name },
                isFavorite = true,
                categories = catList,
                history = existing?.history.orEmpty(),
            )
        }

        history.forEach { hist ->
            val key = hist.manga.url
            val existing = mangaGroup[key]
            val histList = existing?.history.orEmpty() + hist
            mangaGroup[key] = ExternalRecord(
                title = existing?.title ?: hist.manga.title,
                url = hist.manga.url,
                coverUrl = existing?.coverUrl ?: hist.manga.cover_url,
                author = existing?.author ?: hist.manga.author,
                source = existing?.source ?: hist.manga.source,
                tags = existing?.tags ?: hist.manga.tags.map { it.name },
                isFavorite = existing?.isFavorite ?: false,
                categories = existing?.categories.orEmpty(),
                history = histList,
            )
        }

        val backupMangas = mangaGroup.values.map { rec ->
            val sourceId = resolveSourceId(rec.source)
            val recHistory = rec.history.map {
                val chapterUrl = "kotatsu:chapter:${it.chapter_id}"
                BackupHistory(url = chapterUrl, lastRead = it.updated_at)
            }
            val recChapters = rec.history.map {
                val chapterUrl = "kotatsu:chapter:${it.chapter_id}"
                BackupChapter(
                    url = chapterUrl,
                    name = "Chapter #${it.chapter_id}",
                    read = true,
                    lastPageRead = it.page.toLong(),
                    dateFetch = it.created_at,
                    dateUpload = it.created_at,
                    lastModifiedAt = it.updated_at,
                )
            }
            BackupManga(
                source = sourceId,
                url = rec.url,
                title = rec.title,
                author = rec.author,
                artist = null,
                genre = rec.tags,
                thumbnailUrl = rec.coverUrl,
                favorite = rec.isFavorite,
                categories = rec.categories,
                history = recHistory,
                chapters = recChapters,
            )
        }

        val backupSources = backupMangas.map { it.source }.distinct().map {
            BackupSource(name = sourceManager.getOrStub(it).name, sourceId = it)
        }

        return Backup(
            backupManga = backupMangas,
            backupCategories = backupCategories,
            backupSources = backupSources,
        )
    }

    // --- Venera Decoding ---

    private fun decodeVenera(uri: Uri): Backup {
        val backupFile = File.createTempFile("venera_backup", ".zip", context.cacheDir)
        val databaseFiles = LinkedHashMap<String, File>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            }
            databaseFiles += extractVeneraDatabases(backupFile)
            val favorites = databaseFiles["local_favorite.db"]?.let(::readVeneraFavorites).orEmpty()
            val history = databaseFiles["history.db"]?.let(::readVeneraHistory).orEmpty()
            return compileVeneraBackup(favorites, history)
        } finally {
            backupFile.delete()
            databaseFiles.values.forEach { it.delete() }
        }
    }

    private fun extractVeneraDatabases(file: File): Map<String, File> {
        val result = LinkedHashMap<String, File>()
        java.util.zip.ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && (name == "local_favorite.db" || name == "history.db")) {
                    val temp = File.createTempFile("venera_$name", ".db", context.cacheDir)
                    zip.getInputStream(entry).use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    result[name] = temp
                }
            }
        }
        return result
    }

    private data class VeneraFavRecord(
        val categoryName: String,
        val title: String,
        val url: String,
        val coverUrl: String?,
        val author: String?,
        val tags: List<String>,
        val source: String,
        val timestamp: Long,
    )

    private data class VeneraHistRecord(
        val title: String,
        val url: String,
        val coverUrl: String?,
        val author: String?,
        val source: String,
        val chapterIndex: Int,
        val maxPage: Int,
        val timestamp: Long,
    )

    private fun readVeneraFavorites(file: File): List<VeneraFavRecord> {
        val records = ArrayList<VeneraFavRecord>()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = queryUserTables(db)
                .filterNot { it in setOf("folder_order", "folder_sync", "sqlite_sequence") || it.startsWith("sqlite_") }
            for (table in tables) {
                db.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.stringValue("name", "title")
                        if (title.isBlank()) continue
                        val url = cursor.stringValue("id", "url", "comic_id").ifBlank { title }
                        val source = cursor.stringValue("source_key", "sourceKey", "source").ifBlank { table }
                        records.add(
                            VeneraFavRecord(
                                categoryName = table,
                                title = title,
                                url = url,
                                coverUrl = cursor.stringValue("cover_path", "cover", "cover_url", "image").ifBlank { null },
                                author = cursor.stringValue("author", "subtitle").ifBlank { null },
                                tags = cursor.stringValue("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                source = source,
                                timestamp = normalizeTimestamp(cursor.longValue("time", "created_at", "updated_at")),
                            ),
                        )
                    }
                }
            }
        }
        return records
    }

    private fun readVeneraHistory(file: File): List<VeneraHistRecord> {
        val records = ArrayList<VeneraHistRecord>()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            if (!hasTable(db, "history")) return emptyList()
            db.rawQuery("SELECT * FROM history", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.stringValue("title", "name")
                    if (title.isBlank()) continue
                    val url = cursor.stringValue("id", "url", "comic_id").ifBlank { title }
                    val source = cursor.stringValue("source_key", "sourceKey", "source").ifBlank { "" }
                    records.add(
                        VeneraHistRecord(
                            title = title,
                            url = url,
                            coverUrl = cursor.stringValue("cover", "cover_url", "image").ifBlank { null },
                            author = cursor.stringValue("subtitle", "author").ifBlank { null },
                            source = source,
                            chapterIndex = cursor.intValue("ep", "episode", "chapter", "page"),
                            maxPage = cursor.intValue("max_page", "maxPage", "chapters"),
                            timestamp = normalizeTimestamp(cursor.longValue("time", "updated_at", "last_read")),
                        ),
                    )
                }
            }
        }
        return records
    }

    private fun compileVeneraBackup(favorites: List<VeneraFavRecord>, history: List<VeneraHistRecord>): Backup {
        val categories = favorites.map { it.categoryName }.distinct().sorted()
        val backupCategories = categories.mapIndexed { idx, name ->
            BackupCategory(name = name, order = idx.toLong(), flags = 0)
        }
        val categoryToIndex = categories.withIndex().associate { it.value to it.index.toLong() }

        val recGroup = LinkedHashMap<String, ExternalRecordVenera>()
        favorites.forEach { fav ->
            val key = fav.url
            val existing = recGroup[key]
            val catIdx = categoryToIndex[fav.categoryName]
            val catList = existing?.categories.orEmpty() + listOfNotNull(catIdx)
            recGroup[key] = ExternalRecordVenera(
                title = fav.title,
                url = fav.url,
                coverUrl = fav.coverUrl,
                author = fav.author,
                source = fav.source,
                tags = fav.tags,
                isFavorite = true,
                categories = catList,
                history = existing?.history.orEmpty(),
            )
        }

        history.forEach { hist ->
            val key = hist.url
            val existing = recGroup[key]
            val histList = existing?.history.orEmpty() + hist
            recGroup[key] = ExternalRecordVenera(
                title = existing?.title ?: hist.title,
                url = hist.url,
                coverUrl = existing?.coverUrl ?: hist.coverUrl,
                author = existing?.author ?: hist.author,
                source = existing?.source ?: hist.source,
                tags = existing?.tags.orEmpty(),
                isFavorite = existing?.isFavorite ?: false,
                categories = existing?.categories.orEmpty(),
                history = histList,
            )
        }

        val backupMangas = recGroup.values.map { rec ->
            val sourceId = resolveSourceId(rec.source)
            val recHistory = rec.history.map {
                val chapterUrl = "venera:chapter:${it.chapterIndex}"
                BackupHistory(url = chapterUrl, lastRead = it.timestamp)
            }
            val recChapters = rec.history.map {
                val chapterUrl = "venera:chapter:${it.chapterIndex}"
                BackupChapter(
                    url = chapterUrl,
                    name = "Chapter #${it.chapterIndex}",
                    read = true,
                    lastPageRead = it.maxPage.toLong(),
                    dateFetch = it.timestamp,
                    dateUpload = it.timestamp,
                    lastModifiedAt = it.timestamp,
                )
            }
            BackupManga(
                source = sourceId,
                url = rec.url,
                title = rec.title,
                author = rec.author,
                artist = null,
                genre = rec.tags,
                thumbnailUrl = rec.coverUrl,
                favorite = rec.isFavorite,
                categories = rec.categories,
                history = recHistory,
                chapters = recChapters,
            )
        }

        val backupSources = backupMangas.map { it.source }.distinct().map {
            BackupSource(name = sourceManager.getOrStub(it).name, sourceId = it)
        }

        return Backup(
            backupManga = backupMangas,
            backupCategories = backupCategories,
            backupSources = backupSources,
        )
    }

    private fun queryUserTables(db: SQLiteDatabase): List<String> {
        return db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean {
        return db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            cursor.moveToFirst()
        }
    }

    // --- Shared Helper Utilities ---

    private data class ExternalRecord(
        val title: String,
        val url: String,
        val coverUrl: String?,
        val author: String?,
        val source: String,
        val tags: List<String>,
        val isFavorite: Boolean,
        val categories: List<Long>,
        val history: List<KotatsuHistory>,
    )

    private data class ExternalRecordVenera(
        val title: String,
        val url: String,
        val coverUrl: String?,
        val author: String?,
        val source: String,
        val tags: List<String>,
        val isFavorite: Boolean,
        val categories: List<Long>,
        val history: List<VeneraHistRecord>,
    )

    private fun resolveSourceId(sourceKey: String): Long {
        val normalizedKey = sourceKey.lowercase().replace(Regex("[^a-z0-9]"), "")

        // 1. Predefined mapping
        val predefined = PREDEFINED_SOURCE_MAP[normalizedKey]
        if (predefined != null) return predefined

        // 2. Fuzzy match from installed catalogue sources
        val sources = sourceManager.getCatalogueSources()
        val matchByName = sources.find { source ->
            val normName = source.name.lowercase().replace(Regex("[^a-z0-9]"), "")
            normName == normalizedKey || normalizedKey.contains(normName) || normName.contains(normalizedKey)
        }
        if (matchByName != null) return matchByName.id

        // 3. Fallback deterministic hash
        return sourceKey.hashCode().toLong()
    }

    private fun normalizeTimestamp(ts: Long): Long {
        if (ts <= 0L) return System.currentTimeMillis()
        return if (ts < 946684800000L) ts * 1000L else ts
    }

    private fun android.database.Cursor.stringValue(vararg names: String): String {
        for (name in names) {
            val index = getColumnIndex(name)
            if (index >= 0 && !isNull(index)) {
                return getString(index).orEmpty()
            }
        }
        return ""
    }

    private fun android.database.Cursor.longValue(vararg names: String): Long {
        for (name in names) {
            val index = getColumnIndex(name)
            if (index >= 0 && !isNull(index)) {
                return runCatching { getLong(index) }.getOrElse { getString(index).toLongOrNull() ?: 0L }
            }
        }
        return 0L
    }

    private fun android.database.Cursor.intValue(vararg names: String): Int {
        return longValue(*names).toInt()
    }

    private companion object {
        private val PREDEFINED_SOURCE_MAP = mapOf(
            "mangadex" to 8071852085799981602L,
            "nhentai" to 6696312508930833206L,
            "ehentai" to 8330761271167448896L,
            "picacg" to 553570794L,
            "jm" to 769844263L,
            "wnacg" to 823512256L,
            "hitomi" to 258019538L,
            "komiic" to 637999886L,
            "baozi" to 233488852L,
            "copymanga" to 6696312508930833206L,
        )

        private fun veneraLegacySourceKey(type: Int): String? {
            return when (type) {
                0 -> "picacg"
                1 -> "ehentai"
                2 -> "jm"
                3 -> "hitomi"
                4 -> "wnacg"
                5, 6 -> "nhentai"
                233488852 -> "baozi"
                29663848 -> "hot_manga"
                42816288 -> "manwaba"
                11995058 -> "lanraragi"
                150465061 -> "zaimanhua"
                236897507 -> "hcomic"
                258019538 -> "hitomi"
                264196719 -> "nhentai"
                331263271 -> "shonen_jump_plus"
                385625716 -> "ehentai"
                553570794 -> "picacg"
                550146035 -> "goda"
                557997769 -> "copy_manga"
                577341847 -> "mh1234"
                577718694 -> "manga_dex"
                631413104 -> "manhuaren"
                635587041 -> "komga"
                637999886 -> "Komiic"
                716010982 -> "ikmmh"
                740690276 -> "jcomic"
                771282371 -> "mxs"
                778108598 -> "mh18"
                798816513 -> "ykmh"
                807338462 -> "ccc"
                823512256 -> "wnacg"
                875043938 -> "kavita"
                893043064 -> "comic_walker"
                964788560 -> "comick"
                977805693 -> "happy"
                981441865 -> "ManHuaGui"
                769844263 -> "jm"
                else -> null
            }
        }
    }
}
