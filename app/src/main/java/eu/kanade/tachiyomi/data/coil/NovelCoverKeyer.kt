package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelKeyer(
    private val coverCache: NovelCoverCache = Injekt.get(),
) : Keyer<Novel> {
    override fun key(data: Novel, options: Options): String {
        return if (data.hasCustomCover(coverCache)) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class NovelCoverKeyer(
    private val coverCache: NovelCoverCache = Injekt.get(),
) : Keyer<NovelCover> {
    override fun key(data: NovelCover, options: Options): String {
        return if (coverCache.getCustomCoverFile(data.novelId).exists()) {
            "${data.novelId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
