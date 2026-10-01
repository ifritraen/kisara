package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val parentId: Long? = null,
    // KMK -->
    val hidden: Boolean,
    // KMK <--
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID
    val isLocalCategory: Boolean = id == LOCAL_CATEGORY_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
        const val LOCAL_CATEGORY_ID = -100L
    }
}
