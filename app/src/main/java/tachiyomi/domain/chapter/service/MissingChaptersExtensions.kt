package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter

fun List<Double>.missingChaptersCount(): Int {
    if (this.isEmpty()) return 0
    val chapters = filterNot { it == -1.0 }.map(Double::toInt).distinct().sorted()
    if (chapters.isEmpty()) return 0
    var missingChaptersCount = 0
    var previousChapter = 0
    for (i in chapters.indices) {
        val currentChapter = chapters[i]
        if (currentChapter > previousChapter + 1) {
            missingChaptersCount += currentChapter - previousChapter - 1
        }
        previousChapter = currentChapter
    }
    return missingChaptersCount
}

fun calculateChapterGap(higherChapter: Chapter?, lowerChapter: Chapter?): Int {
    if (higherChapter == null || lowerChapter == null) return 0
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0
    val higher = higherChapter.chapterNumber
    val lower = lowerChapter.chapterNumber
    var gap = (higher - lower).toInt() - 1
    if (higher % 1.0 > 0.0) gap++
    return if (gap > 0) gap else 0
}

fun getChapterSort(
    manga: tachiyomi.domain.manga.model.Manga,
    sortDescending: Boolean = manga.sortDescending(),
): (
    Chapter,
    Chapter,
) -> Int {
    return when (manga.sorting) {
        tachiyomi.domain.manga.model.Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        tachiyomi.domain.manga.model.Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapterNumber.compareTo(c1.chapterNumber) }
            false -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
        }
        tachiyomi.domain.manga.model.Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        tachiyomi.domain.manga.model.Manga.CHAPTER_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c1, c2 -> c2.name.compareTo(c1.name) }
            false -> { c1, c2 -> c1.name.compareTo(c2.name) }
        }
        else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
    }
}
