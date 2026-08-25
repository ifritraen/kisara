package eu.kanade.domain.source.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val Source.icon: ImageBitmap?
    get() {
        val mangaIcon = Injekt.get<ExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
        if (mangaIcon != null) return mangaIcon

        val animeIcon = try {
            Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>().getAppIconForSource(id)
                ?.toBitmap()
                ?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        return animeIcon
    }

// AM (BROWSE) -->
// Add an extra property to Source for it to get access to ExtensionManager
val Source.installedExtension: Extension.Installed?
    get() {
        val mangaExt = Injekt.get<ExtensionManager>()
            .installedExtensionsFlow
            .value
            .find { ext -> ext.sources.any { it.id == id } }
        if (mangaExt != null) return mangaExt

        val animeExt = try {
            Injekt.get<eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager>()
                .installedExtensionsFlow
                .value
                .find { ext -> ext.sources.any { it.id == id } }
                ?.let { ext ->
                    Extension.Installed(
                        name = ext.name,
                        pkgName = ext.pkgName,
                        versionName = ext.versionName,
                        versionCode = ext.versionCode,
                        libVersion = ext.libVersion,
                        lang = ext.lang,
                        isNsfw = ext.isNsfw,
                        signatureHash = "",
                        pkgFactory = ext.pkgFactory,
                        sources = emptyList(),
                        icon = ext.icon,
                        hasUpdate = ext.hasUpdate,
                        isObsolete = ext.isObsolete,
                        isShared = ext.isShared,
                    )
                }
        } catch (e: Exception) {
            null
        }
        return animeExt
    }
// <-- AM (BROWSE)
