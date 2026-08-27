package eu.kanade.translation.recognizer

import android.graphics.Rect
import android.graphics.RectF
import eu.kanade.translation.model.TranslationBlock
import kotlin.math.max
import kotlin.math.min

/**
 * Disjoint-Set Union-Find (DSU) Speech Bubble & Text Grouping Coordinator.
 *
 * Resolves fragmented multi-line OCR detections (especially Japanese vertical text)
 * into coherent, natural sentences before translation.
 */
class BubbleGroupingCoordinator(
    private val horizontalProximityThresholdDp: Float = 24f,
    private val verticalProximityThresholdDp: Float = 16f,
) {

    data class TextFragment(
        val block: TranslationBlock,
        val rect: RectF,
        val index: Int,
    )

    /**
     * Groups raw OCR text blocks into unified speech bubble blocks.
     *
     * @param blocks The raw OCR blocks detected on the page.
     * @param bubbleRegions Speech bubble bounding boxes detected by YOLO/CTD (optional).
     * @param isRtl Whether the source language reads Right-to-Left (e.g. Japanese, Chinese, Korean).
     * @return List of consolidated, high-level TranslationBlocks ready for translation.
     */
    fun groupBlocks(
        blocks: List<TranslationBlock>,
        bubbleRegions: List<Rect> = emptyList(),
        isRtl: Boolean = true,
    ): List<TranslationBlock> {
        if (blocks.size <= 1) return blocks

        val fragments = blocks.mapIndexed { idx, b ->
            TextFragment(
                block = b,
                rect = RectF(b.x, b.y, b.x + b.width, b.y + b.height),
                index = idx,
            )
        }

        // Initialize Disjoint-Set Union-Find
        val parent = IntArray(fragments.size) { it }

        fun find(i: Int): Int {
            var curr = i
            while (parent[curr] != curr) {
                parent[curr] = parent[parent[curr]] // Path compression
                curr = parent[curr]
            }
            return curr
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootI] = rootJ
            }
        }

        // 1. Group fragments that lie inside the same detected speech bubble region
        if (bubbleRegions.isNotEmpty()) {
            for (bubble in bubbleRegions) {
                val bubbleRectF = RectF(bubble)
                val insideIndices = fragments.filter { f ->
                    val overlap = RectF()
                    if (overlap.setIntersect(bubbleRectF, f.rect)) {
                        val overlapArea = overlap.width() * overlap.height()
                        val fragArea = f.rect.width() * f.rect.height()
                        overlapArea / fragArea > 0.4f
                    } else {
                        false
                    }
                }.map { it.index }

                for (k in 1 until insideIndices.size) {
                    union(insideIndices[0], insideIndices[k])
                }
            }
        }

        // 2. Spatial proximity clustering for remaining fragments
        for (i in fragments.indices) {
            for (j in i + 1 until fragments.size) {
                if (find(i) == find(j)) continue
                val r1 = fragments[i].rect
                val r2 = fragments[j].rect

                if (areFragmentsInSameBubble(r1, r2, isRtl)) {
                    union(i, j)
                }
            }
        }

        // 3. Group by DSU root and sort internally in natural reading order
        val groups = fragments.groupBy { find(it.index) }
        val consolidatedBlocks = mutableListOf<TranslationBlock>()

        for ((_, group) in groups) {
            if (group.isEmpty()) continue

            // Sort fragments in reading order:
            // For Japanese/CJK Manga (RTL): Right to left columns, top to bottom within column
            // For Western/Manhwa (LTR): Top to bottom rows, left to right within row
            val sortedGroup = if (isRtl) {
                group.sortedWith(
                    compareByDescending<TextFragment> { it.rect.centerX() }
                        .thenBy { it.rect.top },
                )
            } else {
                group.sortedWith(
                    compareBy<TextFragment> { it.rect.top }
                        .thenBy { it.rect.left },
                )
            }

            // Compose text with CJK vs Latin whitespace rules
            val composedText = composeText(sortedGroup.map { it.block.text }, isRtl)

            // Calculate encompassing bounding box
            val minX = sortedGroup.minOf { it.rect.left }
            val minY = sortedGroup.minOf { it.rect.top }
            val maxX = sortedGroup.maxOf { it.rect.right }
            val maxY = sortedGroup.maxOf { it.rect.bottom }

            val width = maxX - minX
            val height = maxY - minY

            val isBubble = sortedGroup.any { it.block.isBubble } || (bubbleRegions.any { b ->
                val br = RectF(b)
                val inter = RectF()
                inter.setIntersect(br, RectF(minX, minY, maxX, maxY))
            })

            consolidatedBlocks.add(
                TranslationBlock(
                    text = composedText,
                    width = width,
                    height = height,
                    x = minX,
                    y = minY,
                    symWidth = width / max(composedText.length, 1),
                    symHeight = height / max(composedText.length, 1),
                    angle = if (height > width * 1.3f) 90f else 0f,
                    isBubble = isBubble,
                ),
            )
        }

        return consolidatedBlocks
    }

    /**
     * Determines whether two text bounding boxes are spatially close enough to belong
     * to the same continuous thought / speech bubble.
     */
    private fun areFragmentsInSameBubble(r1: RectF, r2: RectF, isRtl: Boolean): Boolean {
        val dx = max(0f, max(r1.left, r2.left) - min(r1.right, r2.right))
        val dy = max(0f, max(r1.top, r2.top) - min(r1.bottom, r2.bottom))

        val avgWidth = (r1.width() + r2.width()) / 2f
        val avgHeight = (r1.height() + r2.height()) / 2f

        // Check vertical column proximity (for Japanese vertical text)
        val isVerticalColumn = if (isRtl) {
            dx < avgWidth * 1.5f && dy < avgHeight * 0.6f
        } else {
            dx < avgWidth * 0.6f && dy < avgHeight * 1.2f
        }

        // Check tight bounding box intersection or overlap
        val isOverlapping = dx <= 8f && dy <= 8f

        return isVerticalColumn || isOverlapping
    }

    /**
     * Composes text pieces into a coherent sentence without unwanted extra spaces
     * between CJK characters, but retaining spacing between Latin words.
     */
    private fun composeText(lines: List<String>, isRtl: Boolean): String {
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (sb.isNotEmpty()) {
                val lastChar = sb.last()
                val firstChar = trimmed.first()

                val isLastCjk = isCjkCharacter(lastChar)
                val isFirstCjk = isCjkCharacter(firstChar)

                if (isLastCjk && isFirstCjk) {
                    // No space between consecutive CJK characters
                } else if (lastChar == '-' || lastChar == '—' || lastChar == '・') {
                    // Direct continuation
                } else {
                    sb.append(" ")
                }
            }
            sb.append(trimmed)
        }
        return sb.toString()
    }

    private fun isCjkCharacter(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }
}
