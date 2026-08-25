package eu.kanade.domain.description

object DescriptionEngine {
    fun beautify(description: String): List<DescriptionBlock> {
        if (description.isBlank()) return emptyList()
        return listOf(DescriptionBlock.Paragraph(description))
    }
}

sealed interface DescriptionBlock {
    data class Paragraph(val text: String) : DescriptionBlock
    data class Header(val text: String) : DescriptionBlock
    data class KeyValue(val key: String, val value: String) : DescriptionBlock
}
