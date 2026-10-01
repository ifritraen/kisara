package eu.kanade.tachiyomi.ui.browse.search.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.launchIO
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

// KMK -->
enum class TagNamespace(val prefix: String, val label: String) {
    AUTHOR("author", "Author / Writer"),
    ARTIST("artist", "Artist / Mangaka"),
    CIRCLE("circle", "Circle / Group"),
    PARODY("parody", "Parody / Series"),
    CHARACTER("character", "Character"),
    FEMALE("female", "Female Attribute"),
    MALE("male", "Male Attribute"),
    LANGUAGE("language", "Language"),
    CATEGORY("category", "Category"),
    OTHER("tag", "Tag / Genre");

    companion object {
        fun fromPrefix(prefix: String?): TagNamespace {
            if (prefix.isNullOrBlank()) return OTHER
            val clean = prefix.lowercase().trim()
            return entries.firstOrNull { it.prefix == clean || it.name.lowercase() == clean } ?: OTHER
        }
    }
}

@Serializable
data class CanonicalTagDto(
    val name: String,
    val namespace: String? = null,
    val aliases: List<String> = emptyList(),
    val count: Int = 0,
)

data class CanonicalTag(
    val name: String,
    val namespace: TagNamespace,
    val aliases: List<String> = emptyList(),
    val count: Int = 0,
) {
    val namespacedName: String
        get() = if (namespace == TagNamespace.OTHER) name else "${namespace.prefix}:$name"
}

object TagDictionary {
    private val allTags = mutableListOf<CanonicalTag>()
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun initialize(context: Context) {
        if (isInitialized.get() || isInitializing.getAndSet(true)) return

        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream = context.assets.open("taxonomy/canonical_tags.json")
                @OptIn(ExperimentalSerializationApi::class)
                val dtos: List<CanonicalTagDto> = json.decodeFromStream(inputStream)
                val parsed = dtos.map {
                    CanonicalTag(
                        name = it.name,
                        namespace = TagNamespace.fromPrefix(it.namespace),
                        aliases = it.aliases,
                        count = it.count,
                    )
                }.toMutableList()

                // Load custom runtime tags from disk if present
                val customFile = File(context.filesDir, "custom_canonical_tags.json")
                if (customFile.exists()) {
                    try {
                        val customDtos = json.decodeFromString<List<CanonicalTagDto>>(customFile.readText())
                        val customParsed = customDtos.map {
                            CanonicalTag(
                                name = it.name,
                                namespace = TagNamespace.fromPrefix(it.namespace),
                                aliases = it.aliases,
                                count = it.count,
                            )
                        }
                        parsed.addAll(customParsed)
                        logcat { "TagDictionary: loaded ${customParsed.size} custom persisted tags" }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN) { "Failed loading custom_canonical_tags.json: ${e.message}" }
                    }
                }

                synchronized(allTags) {
                    allTags.clear()
                    allTags.addAll(parsed)
                }
                isInitialized.set(true)
                logcat { "TagDictionary initialized with ${allTags.size} entries" }
            } catch (e: Throwable) {
                logcat { "Failed to load canonical_tags.json: ${e.message}" }
                loadDefaults()
                isInitialized.set(true)
            } finally {
                isInitializing.set(false)
            }
        }
    }

    /**
     * Registers tags fetched from external trackers (AniList, MangaUpdates, MangaBaka).
     * If a tag is not present in the 50k canonical database, it is dynamically added and persisted.
     */
    fun registerTags(
        context: Context,
        tags: List<String>,
        defaultNamespace: TagNamespace = TagNamespace.OTHER,
    ) {
        if (tags.isEmpty()) return
        val appContext = context.applicationContext
        launchIO {
            if (!isInitialized.get()) {
                initialize(appContext)
            }

            val newlyAdded = mutableListOf<CanonicalTagDto>()
            synchronized(allTags) {
                val existingNames = allTags.map { it.name.lowercase() }.toHashSet()
                val existingAliases = allTags.flatMap { it.aliases }.map { it.lowercase() }.toHashSet()

                for (rawTag in tags) {
                    val clean = rawTag.trim().lowercase()
                    if (clean.isBlank() || clean.length < 2) continue
                    if (existingNames.contains(clean) || existingAliases.contains(clean)) continue

                    val newTag = CanonicalTag(
                        name = clean,
                        namespace = defaultNamespace,
                        aliases = emptyList(),
                        count = 1,
                    )
                    allTags.add(newTag)
                    existingNames.add(clean)
                    newlyAdded.add(
                        CanonicalTagDto(
                            name = clean,
                            namespace = defaultNamespace.prefix,
                            aliases = emptyList(),
                            count = 1,
                        ),
                    )
                }
            }

            if (newlyAdded.isNotEmpty()) {
                saveCustomTags(appContext, newlyAdded)
                logcat { "TagDictionary: registered ${newlyAdded.size} new tracker tags into 50k DB" }
            }
        }
    }

    private fun saveCustomTags(context: Context, newDtos: List<CanonicalTagDto>) {
        try {
            val file = File(context.filesDir, "custom_canonical_tags.json")
            val existing: MutableList<CanonicalTagDto> = if (file.exists()) {
                try {
                    json.decodeFromString<List<CanonicalTagDto>>(file.readText()).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            val existingNames = existing.map { it.name.lowercase() }.toSet()
            val toAppend = newDtos.filterNot { existingNames.contains(it.name.lowercase()) }
            if (toAppend.isNotEmpty()) {
                existing.addAll(toAppend)
                file.writeText(json.encodeToString(existing))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN) { "Failed saving custom canonical tags: ${e.message}" }
        }
    }

    private fun loadDefaults() {
        val defaults = listOf(
            CanonicalTag("big breasts", TagNamespace.FEMALE, listOf("oppai", "large breasts"), 120000),
            CanonicalTag("maid", TagNamespace.FEMALE, listOf("meido"), 45000),
            CanonicalTag("stockings", TagNamespace.FEMALE, listOf("tights"), 68000),
            CanonicalTag("schoolgirl uniform", TagNamespace.FEMALE, listOf("jk", "sailor suit"), 89000),
            CanonicalTag("swimsuit", TagNamespace.FEMALE, listOf("bikini"), 52000),
            CanonicalTag("milf", TagNamespace.FEMALE, listOf("mother"), 65000),
            CanonicalTag("shota", TagNamespace.MALE, listOf("shotacon"), 34000),
            CanonicalTag("vanilla", TagNamespace.OTHER, listOf("wholesome"), 58000),
            CanonicalTag("yuri", TagNamespace.OTHER, listOf("lesbian"), 29000),
            CanonicalTag("english", TagNamespace.LANGUAGE, listOf("en", "eng"), 210000),
            CanonicalTag("japanese", TagNamespace.LANGUAGE, listOf("ja", "jp"), 320000),
            CanonicalTag("fate grand order", TagNamespace.PARODY, listOf("fgo", "fate"), 56000),
            CanonicalTag("genshin impact", TagNamespace.PARODY, listOf("genshin"), 42000),
            CanonicalTag("blue archive", TagNamespace.PARODY, listOf("ba", "schale"), 38000),
        )
        synchronized(allTags) {
            allTags.clear()
            allTags.addAll(defaults)
        }
    }

    fun autocomplete(
        query: String,
        namespace: TagNamespace? = null,
        limit: Int = 15,
    ): List<CanonicalTag> {
        if (query.isBlank()) return emptyList()
        val clean = query.lowercase().trim()

        var targetPrefix = namespace?.prefix
        var searchTerm = clean
        if (clean.contains(':')) {
            val parts = clean.split(':', limit = 2)
            targetPrefix = parts[0].trim()
            searchTerm = parts.getOrElse(1) { "" }.trim()
        }

        if (searchTerm.isEmpty()) return emptyList()

        val prefixMatches = mutableListOf<CanonicalTag>()
        val containsMatches = mutableListOf<CanonicalTag>()

        synchronized(allTags) {
            for (tag in allTags) {
                if (targetPrefix != null && tag.namespace.prefix != targetPrefix) {
                    continue
                }

                val normName = tag.name.lowercase()
                if (normName.startsWith(searchTerm)) {
                    prefixMatches.add(tag)
                    if (prefixMatches.size >= limit * 2) break
                } else if (normName.contains(searchTerm) || tag.aliases.any { it.lowercase().contains(searchTerm) }) {
                    if (containsMatches.size < limit) {
                        containsMatches.add(tag)
                    }
                }
            }
        }

        return (prefixMatches + containsMatches).distinctBy { it.namespacedName }.take(limit)
    }

    fun searchAuthors(query: String, limit: Int = 15): List<CanonicalTag> {
        if (query.isBlank()) return emptyList()
        val clean = query.lowercase().trim()
        val results = mutableListOf<CanonicalTag>()

        synchronized(allTags) {
            for (tag in allTags) {
                if (tag.namespace != TagNamespace.AUTHOR && tag.namespace != TagNamespace.CIRCLE) continue
                if (tag.name.lowercase().contains(clean) || tag.aliases.any { it.lowercase().contains(clean) }) {
                    results.add(tag)
                    if (results.size >= limit) break
                }
            }
        }
        return results
    }

    fun searchArtists(query: String, limit: Int = 15): List<CanonicalTag> {
        if (query.isBlank()) return emptyList()
        val clean = query.lowercase().trim()
        val results = mutableListOf<CanonicalTag>()

        synchronized(allTags) {
            for (tag in allTags) {
                if (tag.namespace != TagNamespace.ARTIST) continue
                if (tag.name.lowercase().contains(clean) || tag.aliases.any { it.lowercase().contains(clean) }) {
                    results.add(tag)
                    if (results.size >= limit) break
                }
            }
        }
        return results
    }
}
// KMK <--
