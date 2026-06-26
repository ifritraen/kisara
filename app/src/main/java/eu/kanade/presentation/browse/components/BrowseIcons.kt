package eu.kanade.presentation.browse.components

import android.util.DisplayMetrics
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import eu.kanade.domain.source.model.icon
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.model.Source
import tachiyomi.source.local.isLocal

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun SourceIcon(
    source: Source,
    modifier: Modifier = Modifier,
) {
    val icon = source.icon

    when {
        source.isStub && icon == null -> {
            Image(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = modifier.then(defaultModifier),
            )
        }
        icon != null -> {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        source.isLocal() -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        else -> {
            val installedExt = source.installedExtension
            if (installedExt?.repoUrl != null) {
                AsyncImage(
                    model = "${installedExt.repoUrl}/icon/${installedExt.pkgName}.png",
                    contentDescription = null,
                    placeholder = ColorPainter(Color(0x1F888888)),
                    error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    modifier = modifier.then(defaultModifier)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            } else {
                Image(
                    painter = painterResource(R.mipmap.ic_default_source),
                    contentDescription = null,
                    modifier = modifier.then(defaultModifier),
                )
            }
        }
    }
}

@Composable
fun ExtensionIcon(
    extension: Extension,
    modifier: Modifier = Modifier,
    density: Int = DisplayMetrics.DENSITY_DEFAULT,
) {
    when (extension) {
        is Extension.Available -> {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                modifier = modifier
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        is Extension.Installed -> {
            val repoUrl = extension.repoUrl
            if (repoUrl != null) {
                AsyncImage(
                    model = "$repoUrl/icon/${extension.pkgName}.png",
                    contentDescription = null,
                    placeholder = ColorPainter(Color(0x1F888888)),
                    error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    modifier = modifier
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            } else {
                val icon by extension.getIcon(density)
                when (icon) {
                    is Result.Loading -> Box(modifier = modifier)
                    is Result.Success -> Image(
                        bitmap = (icon as Result.Success<ImageBitmap>).value,
                        contentDescription = null,
                        modifier = modifier,
                    )
                    is Result.Error -> Image(
                        bitmap = ImageBitmap.imageResource(id = R.mipmap.ic_default_source),
                        contentDescription = null,
                        modifier = modifier,
                    )
                }
            }
        }
        is Extension.Untrusted -> Image(
            imageVector = Icons.Filled.Dangerous,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            modifier = modifier.then(defaultModifier),
        )
    }
}

private fun android.content.pm.PackageManager.getPackageInfoCompat(packageName: String, flags: Int): android.content.pm.PackageInfo? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageInfo(packageName, flags)
        }
    } catch (e: Exception) {
        null
    }
}

private fun android.content.pm.PackageManager.getPackageArchiveInfoCompat(archiveFilePath: String, flags: Int): android.content.pm.PackageInfo? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(archiveFilePath, android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageArchiveInfo(archiveFilePath, flags)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun Extension.getIcon(density: Int = DisplayMetrics.DENSITY_DEFAULT): State<Result<ImageBitmap>> {
    val context = LocalContext.current
    return produceState<Result<ImageBitmap>>(initialValue = Result.Loading, this) {
        withIOContext {
            value = try {
                val packageInfo = ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)
                    ?: run {
                        // Sideloaded / Private app might not be registered in the standard manager state yet, parse APK directly
                        val sideloadedFile = java.io.File(eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.getSideloadDir(context), "$pkgName.apk")
                        if (sideloadedFile.isFile) {
                            context.packageManager.getPackageArchiveInfoCompat(sideloadedFile.absolutePath, android.content.pm.PackageManager.GET_META_DATA)
                        } else {
                            val privateExtensionFile = java.io.File(context.filesDir, "exts/$pkgName.ext")
                            if (privateExtensionFile.isFile) {
                                context.packageManager.getPackageArchiveInfoCompat(privateExtensionFile.absolutePath, android.content.pm.PackageManager.GET_META_DATA)
                            } else {
                                context.packageManager.getPackageInfoCompat(pkgName, android.content.pm.PackageManager.GET_META_DATA)
                            }
                        }
                    }
                    ?: throw Exception("Failed to get application info for $pkgName")
                val appInfo = packageInfo.applicationInfo ?: throw Exception("Failed to get application info applicationInfo null")

                // KMK --> Override paths for sideloaded/private APKs to allow internal icon resolution
                val sideloadedFile = java.io.File(eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.getSideloadDir(context), "$pkgName.apk")
                val isSideloadOrPrivate = if (sideloadedFile.isFile) {
                    appInfo.sourceDir = sideloadedFile.absolutePath
                    appInfo.publicSourceDir = sideloadedFile.absolutePath
                    true
                } else {
                    val privateExtensionFile = java.io.File(context.filesDir, "exts/$pkgName.ext")
                    if (privateExtensionFile.isFile) {
                        appInfo.sourceDir = privateExtensionFile.absolutePath
                        appInfo.publicSourceDir = privateExtensionFile.absolutePath
                        true
                    } else {
                        false
                    }
                }
                // KMK <--

                val drawable = try {
                    if (isSideloadOrPrivate) {
                        appInfo.loadIcon(context.packageManager)
                    } else {
                        val appResources = context.packageManager.getResourcesForApplication(appInfo)
                        appResources.getDrawableForDensity(appInfo.icon, density, null)!!
                    }
                } catch (e: Exception) {
                    try {
                        appInfo.loadIcon(context.packageManager)
                    } catch (e2: Exception) {
                        android.util.Log.e("BrowseIcons", "Failed to extract/setup icon for $name", e2)
                        withUIContext {
                            context.toast("Failed to extract/setup icon for $name: " + android.util.Log.getStackTraceString(e2))
                        }
                        throw e2
                    }
                }
                Result.Success(
                    drawable.toBitmap()
                        .asImageBitmap(),
                )
            } catch (e: Exception) {
                android.util.Log.e("BrowseIcons", "Icon loading failed for $name", e)
                withUIContext {
                    context.toast("Icon loading failed for $name: " + android.util.Log.getStackTraceString(e))
                }
                Result.Error
            }
        }
    }
}

sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data object Error : Result<Nothing>()
    data class Success<out T>(val value: T) : Result<T>()
}
