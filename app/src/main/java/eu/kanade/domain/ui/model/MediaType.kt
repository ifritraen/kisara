package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class MediaType(
    val shortName: String,
    val titleRes: StringResource,
    val icon: ImageVector,
) {
    MANGA(
        shortName = "M",
        titleRes = KMR.strings.label_media_type_manga,
        icon = Icons.Outlined.AutoStories,
    ),
    ANIME(
        shortName = "A",
        titleRes = KMR.strings.label_media_type_anime,
        icon = Icons.Outlined.Movie,
    ),
    NOVEL(
        shortName = "N",
        titleRes = KMR.strings.label_media_type_novel,
        icon = Icons.Outlined.MenuBook,
    ),
    ;

    fun next(): MediaType = when (this) {
        MANGA -> ANIME
        ANIME -> NOVEL
        NOVEL -> MANGA
    }

    fun previous(): MediaType = when (this) {
        MANGA -> NOVEL
        ANIME -> MANGA
        NOVEL -> ANIME
    }
}
