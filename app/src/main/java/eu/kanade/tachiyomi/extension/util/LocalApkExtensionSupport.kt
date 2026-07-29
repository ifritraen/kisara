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
