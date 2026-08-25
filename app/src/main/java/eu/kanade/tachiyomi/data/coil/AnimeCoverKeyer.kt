package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeKeyer(
    private val coverCache: AnimeCoverCache = Injekt.get(),
) : Keyer<Anime> {
    override fun key(data: Anime, options: Options): String {
        return if (data.hasCustomCover(coverCache)) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class AnimeCoverKeyer(
    private val coverCache: AnimeCoverCache = Injekt.get(),
) : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return if (coverCache.getCustomCoverFile(data.animeId).exists()) {
            "${data.animeId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
