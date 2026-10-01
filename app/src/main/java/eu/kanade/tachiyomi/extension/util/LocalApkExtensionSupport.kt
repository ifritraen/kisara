package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object LocalApkExtensionSupport {

    private const val SIDELOAD_DIR = "sideloaded_extensions"
    private const val LOAD_CACHE_DIR = "sideloaded_apk_cache"

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun migrateDir(context: Context, from: File, to: File) {
        try {
            if (from.exists() && from.isDirectory) {
                to.mkdirs()
                from.listFiles()?.forEach { file ->
                    val target = File(to, file.name)
                    if (!target.exists()) {
                        copyApkSafely(context, file, target)
                    }
                    file.setWritable(true)
                    file.delete()
                }
                from.delete()
            }
        } catch (_: Exception) {}
    }

    fun getSideloadDir(context: Context): File {
        // Try StorageManager first
        val storageManager = runCatching { Injekt.get<StorageManager>() }.getOrNull()
        val externalUniDir = storageManager?.getExtensionsDirectory()
        val externalPath = externalUniDir?.filePath
        if (!externalPath.isNullOrBlank()) {
            val externalFile = File(externalPath)
            if (externalFile.exists() || externalFile.mkdirs()) {
                val internalDir = File(context.filesDir, SIDELOAD_DIR)
                if (internalDir.exists() && internalDir.isDirectory && internalDir != externalFile) {
                    migrateDir(context, internalDir, externalFile)
                }
                return externalFile
            }
        }

        // Fallback: Check StoragePreferences directly
        val storagePrefs = runCatching { Injekt.get<StoragePreferences>() }.getOrNull()
        val baseUriString = storagePrefs?.baseStorageDirectory()?.get()
        if (!baseUriString.isNullOrBlank()) {
            var basePath = runCatching {
                UniFile.fromUri(context, baseUriString.toUri())?.filePath
            }.getOrNull()

            // If UniFile.filePath failed, decode primary storage path from URI
            if (basePath.isNullOrBlank() && baseUriString.contains("primary", ignoreCase = true)) {
                val decoded = android.net.Uri.decode(baseUriString)
                val rel = decoded.substringAfter("primary:").substringBefore("/document/").trim('/')
                if (rel.isNotBlank()) {
                    basePath = File(android.os.Environment.getExternalStorageDirectory(), rel).absolutePath
                }
            }

            if (!basePath.isNullOrBlank()) {
                val candidateDir = File(basePath, StorageManager.EXTENSIONS_PATH).takeIf { it.exists() }
                    ?: File(basePath, StorageManager.LEGACY_EXTENSIONS_PATH).takeIf { it.exists() }
                    ?: File(basePath, StorageManager.EXTENSIONS_PATH)
                if (candidateDir.exists() || candidateDir.mkdirs()) {
                    val internalDir = File(context.filesDir, SIDELOAD_DIR)
                    if (internalDir.exists() && internalDir.isDirectory && internalDir != candidateDir) {
                        migrateDir(context, internalDir, candidateDir)
                    }
                    return candidateDir
                }
            }
        }

        // Fallback 2: Check standard shared storage location
        val standardDir = File(android.os.Environment.getExternalStorageDirectory(), "Aaaaa/Otaku/Komikku/extensions")
        if (standardDir.exists() && standardDir.isDirectory) {
            return standardDir
        }

        // Final fallback: App-internal storage
        return File(context.filesDir, SIDELOAD_DIR).apply { mkdirs() }
    }

    private var cachedLocalApkFiles: List<File>? = null
    private var lastLocalApkCheckTime: Long = 0

    fun invalidateLocalApkCache(context: Context? = null) {
        cachedLocalApkFiles = null
        lastLocalApkCheckTime = 0
        context?.let {
            try {
                File(it.cacheDir, "ext_pkg_info_cache").deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    fun getLocalApkFiles(context: Context): List<File> {
        val now = System.currentTimeMillis()
        if (cachedLocalApkFiles != null && (now - lastLocalApkCheckTime < 10000)) {
            return cachedLocalApkFiles!!
        }
        val root = getSideloadDir(context)
        val rootFiles = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.length() > 0L }
            .orEmpty()
        val internalDir = File(context.filesDir, SIDELOAD_DIR)
        val internalFiles = if (internalDir != root && internalDir.exists()) {
            internalDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.length() > 0L }
                .orEmpty()
        } else {
            emptyList()
        }
        val files = (rootFiles + internalFiles).distinctBy { it.name }
        if (files.isNotEmpty()) {
            cachedLocalApkFiles = files
            lastLocalApkCheckTime = now
        }
        return files
    }

    fun findApkForPackage(context: Context, packageName: String): File? {
        val cleanPkg = packageName.substringBeforeLast('-').substringBeforeLast('_')

        val candidates = getLocalApkFiles(context).filter { file ->
            val name = file.nameWithoutExtension
            val base = name.substringBeforeLast('-').substringBeforeLast('_')
            name == packageName || base == packageName || name == cleanPkg || base == cleanPkg
        }
        if (candidates.size == 1) return candidates.first()
        if (candidates.size > 1) {
            return candidates.maxWithOrNull(
                compareBy<File> { file ->
                    runCatching {
                        ExtensionLoader.getPackageArchiveInfoWithCache(context, file, PACKAGE_FLAGS)
                            ?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }
                    }.getOrNull() ?: -1L
                }.thenBy { it.lastModified() }
            )
        }

        val root = getSideloadDir(context)
        return File(root, "$packageName.apk").takeIf { it.isFile }
            ?: File(root, "$cleanPkg.apk").takeIf { it.isFile }
    }

    fun getLocalPackageInfoOrNull(
        context: Context,
        pkgManager: PackageManager,
        packageName: String,
    ): PackageInfo? {
        val apkFile = findApkForPackage(context, packageName) ?: return null
        return ExtensionLoader.getPackageArchiveInfoWithCache(context, apkFile, PACKAGE_FLAGS)
    }

    fun prepareLoadableApkPath(
        context: Context,
        pkgName: String,
        sourcePath: String,
    ): String {
        var sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            sourceFile = findApkForPackage(context, pkgName) ?: return sourcePath
        }

        val internalCache = File(context.filesDir, LOAD_CACHE_DIR).apply { mkdirs() }
        val externalCache = File(context.getExternalFilesDir(null) ?: context.filesDir, LOAD_CACHE_DIR)
        if (externalCache.exists() && externalCache.isDirectory && internalCache != externalCache) {
            migrateDir(context, externalCache, internalCache)
        }
        val cacheRoot = internalCache

        // If sourceFile is already inside cacheRoot and read-only, reuse it directly
        if (sourceFile.parentFile?.absolutePath == cacheRoot.absolutePath && !sourceFile.canWrite()) {
            return sourceFile.absolutePath
        }

        val uniqueName = "${pkgName}_${sourceFile.lastModified()}_${sourceFile.length()}.apk"
        val targetFile = File(cacheRoot, uniqueName)

        if (targetFile.exists() && targetFile.length() == sourceFile.length()) {
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
            tempFile.setWritable(true)
            tempFile.delete()
        }

        sourceFile.inputStream().use { input ->
            java.io.FileOutputStream(tempFile, false).use { out ->
                input.copyTo(out)
                out.flush()
            }
        }
        tempFile.setLastModified(sourceFile.lastModified())
        tempFile.setReadOnly()

        if (targetFile.exists()) {
            targetFile.setWritable(true)
            targetFile.delete()
        }

        if (!tempFile.renameTo(targetFile)) {
            targetFile.setWritable(true)
            sourceFile.inputStream().use { input ->
                java.io.FileOutputStream(targetFile, false).use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            targetFile.setLastModified(sourceFile.lastModified())
            targetFile.setReadOnly()
            tempFile.setWritable(true)
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

    fun copyApkSafely(context: Context, sourceFile: File, targetFile: File) {
        val storageManager = runCatching { Injekt.get<StorageManager>() }.getOrNull()
        val extUniDir = storageManager?.getExtensionsDirectory()

        // 1. Try deleting existing target via UniFile if inside external directory
        try {
            val uniTarget = extUniDir?.findFile(targetFile.name)
            uniTarget?.delete()
        } catch (_: Exception) {}

        // 2. Direct stream overwrite (truncates existing file in place, avoids unlink failure)
        var writeSucceeded = false
        try {
            targetFile.setWritable(true)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            java.io.FileOutputStream(targetFile, false).use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }
            writeSucceeded = true
        } catch (_: Exception) {
            writeSucceeded = false
        }

        // 3. Fallback to UniFile if direct file stream failed (e.g. Scoped Storage SAF tree)
        if (!writeSucceeded && extUniDir != null) {
            val destUni = extUniDir.findFile(targetFile.name) ?: extUniDir.createFile(targetFile.name)
            if (destUni != null) {
                destUni.openOutputStream()?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                } ?: throw java.io.IOException("Failed to open UniFile output stream for ${targetFile.name}")
                writeSucceeded = true
            }
        }

        if (!writeSucceeded && !targetFile.exists()) {
            throw java.io.IOException("Failed to copy APK to ${targetFile.absolutePath}")
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
        copyApkSafely(context, sourceFile, targetFile)
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
        var deleted = false

        // 1. Delete from external UniFile directory (supports SAF document deletion across UIDs)
        try {
            val storageManager = runCatching { Injekt.get<StorageManager>() }.getOrNull()
            val extUniDir = storageManager?.getExtensionsDirectory()
            val cleanPkg = packageName.substringBeforeLast('-')
            extUniDir?.listFiles()?.forEach { uniFile ->
                val filename = uniFile.name ?: return@forEach
                if (filename.endsWith(".apk", ignoreCase = true)) {
                    val base = filename.substringBeforeLast('.')
                    val matches = base == packageName || base == cleanPkg ||
                        base.startsWith("$packageName-") || base.startsWith("${packageName}_") ||
                        base.startsWith("$cleanPkg-") || base.startsWith("${cleanPkg}_")
                    if (matches) {
                        if (uniFile.delete()) {
                            deleted = true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        fun deleteFromDir(dir: File): Boolean {
            var dirDeleted = false
            val files = dir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) } ?: return false
            val cleanPkg = packageName.substringBeforeLast('-')
            for (file in files) {
                val name = file.nameWithoutExtension
                val matchesName = name == packageName || name == cleanPkg ||
                    name.startsWith("$packageName-") || name.startsWith("${packageName}_") ||
                    name.startsWith("$cleanPkg-") || name.startsWith("${cleanPkg}_")
                val matchesPackage = matchesName || try {
                    val info = ExtensionLoader.getPackageArchiveInfoWithCache(context, file, PackageManager.GET_META_DATA)
                    val infoPkg = info?.packageName
                    infoPkg == packageName || (infoPkg != null && infoPkg == cleanPkg)
                } catch (_: Exception) {
                    false
                }

                if (matchesPackage) {
                    file.setWritable(true)
                    val del = file.delete()
                    if (!del && file.exists()) {
                        try {
                            java.io.FileOutputStream(file, false).close() // truncate to 0 bytes
                        } catch (_: Exception) {}
                    }
                    dirDeleted = true
                }
            }
            return dirDeleted
        }

        val d1 = deleteFromDir(root)
        val internalDir = File(context.filesDir, SIDELOAD_DIR)
        val d2 = if (internalDir != root && internalDir.exists()) deleteFromDir(internalDir) else false
        val d3 = deleteFromDir(cacheRoot)
        return deleted || d1 || d2 || d3
    }
}
