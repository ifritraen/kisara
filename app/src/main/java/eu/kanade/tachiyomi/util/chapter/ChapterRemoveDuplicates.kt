package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.floor

private val OFFICIAL_KEYWORDS = setOf(
    "official",
    "mangaplus",
    "manga plus",
    "bilibili",
    "comikey",
    "webtoon",
    "webtoons",
    "viz",
    "viz media",
    "tapas",
    "tappytoon",
    "lezhin",
    "alpha manga",
    "kmanga",
    "k manga",
    "pocket comics",
    "azuki",
)

private val EXTRA_KEYWORDS = listOf(
    "extra",
    "omake",
    "special",
    "side story",
    "side-story",
    "bonus",
    "interlude",
    "epilogue",
    "prologue",
    "afterword",
)

fun isOfficialScanlator(scanlator: String?): Boolean {
    if (scanlator.isNullOrBlank()) return false
    val lower = scanlator.lowercase().trim()
    return OFFICIAL_KEYWORDS.any { lower.contains(it) }
}

fun isExtraChapterName(name: String): Boolean {
    val lower = name.lowercase()
    return EXTRA_KEYWORDS.any { lower.contains(it) }
}

/**
 * Returns a copy of the list with duplicate chapters removed while preserving unbroken serial continuity.
 *
 * Requirements:
 * 1. Single copy per chapter: If multiple versions of the same chapter exist, selects one copy.
 * 2. Unbroken serial: Prioritizes official releases, then current/preferred scanlator, while automatically
 *    falling back to any available scanlator so no chapter gaps exist in sequence.
 * 3. Whole vs. fractional splits: If a complete whole chapter (e.g. 216) is available, splits from competing
 *    scanlators (e.g. 216.1, 216.2) covering the same integer interval are excluded unless the integer chapter is absent.
 */
fun <T> List<T>.deduplicateChapters(
    currentChapterId: Long? = null,
    preferredScanlator: String? = null,
    getChapter: (T) -> Chapter,
): List<T> {
    if (size <= 1) return this

    val originalIndices = mapIndexed { index, item -> item to index }.toMap()

    val scanlatorCounts = mapNotNull {
        getChapter(it).scanlator?.trim()?.ifBlank { null }
    }.groupingBy { it }.eachCount()

    fun scoreChapter(item: T): Long {
        val chapter = getChapter(item)
        var score = 0L

        if (currentChapterId != null && chapter.id == currentChapterId) {
            score += 100_000_000L
        }
        if (!preferredScanlator.isNullOrBlank() && chapter.scanlator.equals(preferredScanlator, ignoreCase = true)) {
            score += 10_000_000L
        }
        if (isOfficialScanlator(chapter.scanlator)) {
            score += 1_000_000L
        }
        if (chapter.read) {
            score += 100_000L
        }
        if (chapter.bookmark) {
            score += 50_000L
        }

        val count = chapter.scanlator?.trim()?.let { scanlatorCounts[it] } ?: 0
        score += count * 100L

        if (chapter.dateUpload > 0L) {
            score += (chapter.dateUpload / 1000).coerceAtLeast(0)
        }
        return score
    }

    val (recognized, unrecognized) = partition { getChapter(it).isRecognizedNumber }

    val selectedUnrecognized = unrecognized
        .groupBy { getChapter(it).name.trim().lowercase() }
        .values
        .mapNotNull { group -> group.maxByOrNull { scoreChapter(it) } }

    val byBaseInteger = recognized.groupBy { floor(getChapter(it).chapterNumber).toInt() }

    val selectedRecognized = mutableListOf<T>()

    for ((baseInt, group) in byBaseInteger) {
        val wholeChapters = group.filter { getChapter(it).chapterNumber == baseInt.toDouble() }
        val fractionalChapters = group.filter { getChapter(it).chapterNumber != baseInt.toDouble() }

        if (wholeChapters.isNotEmpty()) {
            val bestWhole = wholeChapters.maxByOrNull { scoreChapter(it) }!!
            selectedRecognized.add(bestWhole)
            val bestWholeScanlator = getChapter(bestWhole).scanlator?.trim()

            for (frac in fractionalChapters) {
                val fracChapter = getChapter(frac)
                val isCurrent = currentChapterId != null && fracChapter.id == currentChapterId
                val isSameScanlatorExtra = !bestWholeScanlator.isNullOrBlank() &&
                    fracChapter.scanlator.equals(bestWholeScanlator, ignoreCase = true) &&
                    isExtraChapterName(fracChapter.name)

                if (isCurrent || isSameScanlatorExtra) {
                    selectedRecognized.add(frac)
                }
            }
        } else {
            val byExactFrac = fractionalChapters.groupBy { getChapter(it).chapterNumber }
            for ((_, fracGroup) in byExactFrac) {
                fracGroup.maxByOrNull { scoreChapter(it) }?.let { selectedRecognized.add(it) }
            }
        }
    }

    val allSelected = selectedUnrecognized + selectedRecognized
    return allSelected.sortedBy { originalIndices[it] ?: Int.MAX_VALUE }
}

/**
 * Returns a copy of the list with duplicate chapters removed
 */
fun List<Chapter>.removeDuplicates(
    currentChapter: Chapter? = null,
    preferredScanlator: String? = currentChapter?.scanlator,
): List<Chapter> {
    return deduplicateChapters(
        currentChapterId = currentChapter?.id,
        preferredScanlator = preferredScanlator,
        getChapter = { it },
    )
}

/**
 * Returns a copy of the list of chapter items with duplicate chapters removed
 */
@JvmName("removeDuplicatesChapterItems")
fun List<ChapterList.Item>.removeDuplicates(
    currentChapter: Chapter? = null,
    preferredScanlator: String? = currentChapter?.scanlator,
): List<ChapterList.Item> {
    return deduplicateChapters(
        currentChapterId = currentChapter?.id,
        preferredScanlator = preferredScanlator,
        getChapter = { it.chapter },
    )
}
