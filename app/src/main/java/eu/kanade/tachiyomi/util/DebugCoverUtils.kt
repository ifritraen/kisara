package eu.kanade.tachiyomi.util

fun debugTitleCoverFlow(scope: String, message: Any? = null, block: (() -> String)? = null) {
    // No-op or debug logger
}

fun previewTitleCoverUrl(url: String?): String {
    return url?.take(60) ?: "null"
}

fun previewTitleCoverValue(value: Any?): String {
    return value?.toString()?.take(60) ?: "null"
}
