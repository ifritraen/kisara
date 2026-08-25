package eu.kanade.tachiyomi.ui.browse.anime.migration

import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR

data class AnimeMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): AnimeMigrationFlag {
            return AnimeMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object AnimeMigrationFlags {
    private const val EPISODES = 0b000001
    private const val CATEGORIES = 0b000010
    private const val DELETE_DOWNLOADED = 0b000100

    fun hasEpisodes(value: Int) = value and EPISODES != 0
    fun hasCategories(value: Int) = value and CATEGORIES != 0
    fun hasDeleteDownloaded(value: Int) = value and DELETE_DOWNLOADED != 0

    fun getFlags(anime: Anime?, defaultSelectedBitMap: Int): List<AnimeMigrationFlag> {
        val flags = mutableListOf<AnimeMigrationFlag>()
        flags += AnimeMigrationFlag.create(EPISODES, defaultSelectedBitMap, MR.strings.chapters)
        flags += AnimeMigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)
        if (anime != null) {
            flags += AnimeMigrationFlag.create(DELETE_DOWNLOADED, defaultSelectedBitMap, MR.strings.delete_downloaded)
        }
        return flags
    }

    fun getSelectedFlagsBitMap(selectedFlags: List<Boolean>, flags: List<AnimeMigrationFlag>): Int {
        return selectedFlags.zip(flags).filter { it.first }.map { it.second.flag }.reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
