@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

// KMK -->
import kotlinx.serialization.json.JsonObject
// KMK <--
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    // KMK -->
    /**
     * Extra metadata associated with the chapter.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * chapter data.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject
        get() = JsonObject(emptyMap())
        set(value) {}
    // KMK <--

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        // KMK -->
        memo = other.memo
        // KMK <--
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }

        // SY -->
        operator fun invoke(
            name: String,
            url: String,
            date_upload: Long = 0,
            chapter_number: Float = -1F,
            scanlator: String? = null,
        ): SChapter {
            return create().apply {
                this.name = name
                this.url = url
                this.date_upload = date_upload
                this.chapter_number = chapter_number
                this.scanlator = scanlator
            }
        }
        // SY <--
    }
}
