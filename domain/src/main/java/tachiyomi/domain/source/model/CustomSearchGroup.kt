// KMK -->
package tachiyomi.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomSearchGroup(
    val id: String,
    val name: String,
    val sourceIds: Set<Long> = emptySet(),
)
// KMK <--
