package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object LocalApkExtensionSupport {

    private const val SIDELOAD_DIR = "sideloaded_extensions"
    private const val LOAD_CACHE_DIR = "sideloaded_apk_cache"

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun migrateDir(from: File, to: File) {
        try {
            if (from.exists() && from.isDirectory) {
                to.mkdirs()
                from.listFiles()?.forEach { file ->
                    val target = File(to, file.name)
                    if (!target.exists()) {
                        file.copyTo(target, overwrite = true)
                    }
                    file.delete()
                }
                from.delete()
            }
        } catch (_: Exception) {}
    }

    fun getSideloadDir(context: Context): File {
        val internalDir = File(context.filesDir, SIDELOAD_DIR).apply { mkdirs() }
        val externalDir = File(context.getExternalFilesDir(null) ?: context.filesDir, SIDELOAD_DIR)
        if (externalDir.exists() && externalDir.isDirectory && internalDir != externalDir) {
            migrateDir(externalDir, internalDir)
        }
        return internalDir
    }

    private var cachedLocalApkFiles: List<File>? = null
    private var lastLocalApkCheckTime: Long = 0

    fun invalidateLocalApkCache() {
        cachedLocalApkFiles = null
        lastLocalApkCheckTime = 0
    }

    fun getLocalApkFiles(context: Context): List<File> {
        val now = System.currentTimeMillis()
        if (cachedLocalApkFiles != null && (now - lastLocalApkCheckTime < 10000)) {
            return cachedLocalApkFiles!!
        }
        val root = getSideloadDir(context)
        val files = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .orEmpty()
        cachedLocalApkFiles = files
        lastLocalApkCheckTime = now
        return files
    }

    fun getLocalPackageInfoOrNull(
        context: Context,
        pkgManager: PackageManager,
        packageName: String,
    ): PackageInfo? {
        val root = getSideloadDir(context)
        val apkFile = File(root, "$packageName.apk")
        if (!apkFile.isFile) return null
        return ExtensionLoader.getPackageArchiveInfoWithCache(context, apkFile, PACKAGE_FLAGS)
    }

    fun prepareLoadableApkPath(
        context: Context,
        pkgName: String,
        sourcePath: String,
    ): String {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return sourcePath
        }

        val internalCache = File(context.filesDir, LOAD_CACHE_DIR).apply { mkdirs() }
        val externalCache = File(context.getExternalFilesDir(null) ?: context.filesDir, LOAD_CACHE_DIR)
        if (externalCache.exists() && externalCache.isDirectory && internalCache != externalCache) {
            migrateDir(externalCache, internalCache)
        }
        val cacheRoot = internalCache
        val uniqueName = "${pkgName}_${sourceFile.lastModified()}_${sourceFile.length()}.apk"
        val targetFile = File(cacheRoot, uniqueName)

        if (targetFile.exists()) {
            targetFile.setReadOnly()
            return targetFile.absolutePath
        }

        // Clean up old cached versions of this package
        cacheRoot.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("${pkgName}_") && file.name.endsWith(".apk") && file.name != uniqueName) {
                file.setWritable(true)
                file.delete()
            }
        }

        val tempFile = File(cacheRoot, "$uniqueName.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        sourceFile.copyTo(tempFile, overwrite = true)
        tempFile.setLastModified(sourceFile.lastModified())
        tempFile.setReadOnly()

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            targetFile.setLastModified(sourceFile.lastModified())
            targetFile.setReadOnly()
            tempFile.delete()
        }

        return targetFile.absolutePath
    }

    fun extractIconFromApk(context: Context, apkFile: File): android.graphics.Bitmap? {
        if (!apkFile.isFile || !apkFile.exists()) return null

        // 1. Try loading via AssetManager + Resources reflection (supports Vector XML / Adaptive Icons / Raster)
        try {
            val pkgInfo = ExtensionLoader.getPackageArchiveInfoWithCache(
                context,
                apkFile,
                PackageManager.GET_META_DATA,
            ) ?: context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_META_DATA)

            val appInfo = pkgInfo?.applicationInfo
            if (appInfo != null && appInfo.icon != 0) {
                val assetManager = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assetManager, apkFile.absolutePath)
                val res = android.content.res.Resources(
                    assetManager,
                    context.resources.displayMetrics,
                    context.resources.configuration,
                )
                val drawable = try {
                    androidx.core.content.res.ResourcesCompat.getDrawable(res, appInfo.icon, null)
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    res.getDrawable(appInfo.icon, null)
                }
                if (drawable != null) {
                    val bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                        drawable.bitmap
                    } else {
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        return bmp
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Try appInfo.loadIcon with sourceDir and publicSourceDir fixed
        try {
            val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val appInfo = pkgInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                val drawable = appInfo.loadIcon(context.packageManager)
                val bmp = androidx.core.graphics.drawable.DrawableCompat.wrap(drawable).let {
                    if (it is android.graphics.drawable.BitmapDrawable && it.bitmap != null) {
                        it.bitmap
                    } else {
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            it.intrinsicWidth.coerceAtLeast(1),
                            it.intrinsicHeight.coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        it.setBounds(0, 0, canvas.width, canvas.height)
                        it.draw(canvas)
                        bitmap
                    }
                }
                if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                    return bmp
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: Parse ZipFile directly for bitmap images (.png, .webp, .jpg)
        try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val candidateEntries = entries.filter { entry ->
                    val lower = entry.name.lowercase()
                    (lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) &&
                        !lower.contains(".9.png")
                }

                val bestLauncherEntry = candidateEntries.filter {
                    val lower = it.name.lowercase()
                    lower.contains("ic_launcher") || lower.contains("icon") || lower.contains("logo") || lower.contains("app")
                }.maxByOrNull { entry ->
                    val lower = entry.name.lowercase()
                    when {
                        lower.contains("xxxhdpi") -> 6
                        lower.contains("xxhdpi") -> 5
                        lower.contains("xhdpi") -> 4
                        lower.contains("hdpi") -> 3
                        lower.contains("mdpi") -> 2
                        lower.contains("foreground") -> 1
                        else -> 0
                    }
                }

                val targetEntry = bestLauncherEntry ?: candidateEntries.firstOrNull {
                    it.name.lowercase().startsWith("res/")
                }

                if (targetEntry != null) {
                    val bytes = zip.getInputStream(targetEntry).use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                            return bmp
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun extractAndCacheApkIcon(
        context: Context,
        apkFile: File,
        packageName: String,
        sourceIds: List<Long> = emptyList(),
    ): Boolean {
        if (!apkFile.isFile || !apkFile.exists()) return false
        val bitmap = extractIconFromApk(context, apkFile) ?: return false

        val cleanPkgName = when {
            packageName.contains("-") -> {
                val suffix = packageName.substringAfterLast("-")
                if (suffix.toLongOrNull() != null || suffix.all { it.isDigit() }) {
                    val base = packageName.substringBeforeLast("-")
                    if (base.endsWith("_")) base.dropLast(1) else base
                } else {
                    packageName
                }
            }
            packageName.contains("_") -> {
                val suffix = packageName.substringAfterLast("_")
                if (suffix.toLongOrNull() != null || suffix.all { it.isDigit() }) {
                    packageName.substringBeforeLast("_")
                } else {
                    packageName
                }
            }
            else -> packageName
        }

        try {
            val extDir = File(context.cacheDir, "extension_icons").apply { mkdirs() }
            val extFile = File(extDir, "$packageName.png")
            java.io.FileOutputStream(extFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            if (cleanPkgName != packageName) {
                val cleanExtFile = File(extDir, "$cleanPkgName.png")
                java.io.FileOutputStream(cleanExtFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            if (sourceIds.isNotEmpty()) {
                val sourceIconDir = File(context.filesDir, "source_icons").apply { mkdirs() }
                sourceIds.forEach { sourceId ->
                    val sourceFile = File(sourceIconDir, "$sourceId.png")
                    java.io.FileOutputStream(sourceFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun storeSideloadedApk(
        context: Context,
        packageName: String,
        sourceFile: File,
    ): File {
        deleteSideloadedApk(context, packageName)
        invalidateLocalApkCache()
        val root = getSideloadDir(context)
        val targetFile = File(root, "$packageName.apk")
        sourceFile.copyTo(targetFile, overwrite = true)
        targetFile.setReadOnly()
        extractAndCacheApkIcon(context, targetFile, packageName)
        return targetFile
    }

    fun deleteSideloadedApk(
        context: Context,
        packageName: String,
    ): Boolean {
        invalidateLocalApkCache()
        ExtensionLoader.invalidateCacheForPackage(context, packageName)
        val root = getSideloadDir(context)
        val cacheRoot = File(context.filesDir, LOAD_CACHE_DIR)

        fun deleteFromDir(dir: File): Boolean {
            var deleted = false
            val files = dir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) } ?: return false
            for (file in files) {
                val name = file.nameWithoutExtension
                val matchesName = name == packageName || name.startsWith("$packageName-") || name.startsWith("${packageName}_")
                val matchesPackage = matchesName || try {
                    val info = ExtensionLoader.getPackageArchiveInfoWithCache(context, file, PackageManager.GET_META_DATA)
                    info?.packageName == packageName
                } catch (_: Exception) {
                    false
                }

                if (matchesPackage) {
                    file.setWritable(true)
                    val del = file.delete()
                    if (!del && file.exists()) {
                        try {
                            file.writeBytes(ByteArray(0))
                        } catch (_: Exception) {}
                    }
                    deleted = true
                }
            }
            return deleted
        }

        val d1 = deleteFromDir(root)
        val d2 = deleteFromDir(cacheRoot)
        return d1 || d2
    }
}
