package eu.kanade.tachiyomi.data.coil

import java.io.File

data class AuroraPosterRequest(
    val primaryUrl: String? = null,
    val fallbackUrl: String? = null,
    val refererUrl: String? = null,
    val headers: Map<String, String>? = null,
    val customCoverFile: File? = null,
    val coverLastModified: Long = 0L,
)
