@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

// KMK -->
import kotlinx.serialization.json.JsonObject
// KMK <--

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    // KMK -->
    override var memo: JsonObject = JsonObject(emptyMap())
    // KMK <--
}
