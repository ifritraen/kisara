package exh.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.EhBasedSource
import tachiyomi.domain.manga.model.Manga

var metadataDelegatedSourceIds: List<Long> = emptyList()
var nHentaiSourceIds: List<Long> = emptyList()
var lanraragiSourceIds: List<Long> = emptyList()
var mangaDexSourceIds: List<Long> = emptyList()
var LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(
    EH_SOURCE_ID,
    EXH_SOURCE_ID,
    PURURIN_SOURCE_ID,
)

fun Manga.isEhBasedManga(): Boolean = source in eHentaiSourceIds

fun Source.isEhBasedSource(): Boolean = this is EhBasedSource && id in eHentaiSourceIds

fun Source.isMdBasedSource(): Boolean = id in mangaDexSourceIds

@JvmName("sourceIsMetadataSource")
fun Source.isMetadataSource(): Boolean = this is eu.kanade.tachiyomi.source.online.MetadataSource<*, *> || (this is EnhancedHttpSource && this.source() is eu.kanade.tachiyomi.source.online.MetadataSource<*, *>)

@JvmName("longIsMetadataSource")
fun Long.isMetadataSource(): Boolean = this in metadataDelegatedSourceIds

fun isMetadataSource(source: Long): Boolean = source in metadataDelegatedSourceIds
fun isMetadataSource(source: Source): Boolean = source.isMetadataSource()

inline fun <reified T> Source.anyIs(): Boolean {
    return this is T || (this is EnhancedHttpSource && this.source() is T)
}

fun Source.getMainSource(): Source = if (this is EnhancedHttpSource) {
    this.source()
} else {
    this
}

@JvmName("getMainSourceGeneric")
inline fun <reified T : Source> Source.getMainSource(): T? = if (this is EnhancedHttpSource) {
    this.source() as? T
} else {
    this as? T
}
