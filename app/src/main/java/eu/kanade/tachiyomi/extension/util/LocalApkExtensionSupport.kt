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

    fun getSideloadDir(context: Context): File {
        return File(context.filesDir, SIDELOAD_DIR).apply { mkdirs() }
    }

    fun getLocalApkFiles(context: Context): List<File> {
        val root = getSideloadDir(context)
        return root.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .orEmpty()
    }

    fun getLocalPackageInfoOrNull(
        context: Context,
        pkgManager: PackageManager,
        packageName: String,
    ): PackageInfo? {
        val root = getSideloadDir(context)
        val apkFile = File(root, "$packageName.apk")
        if (!apkFile.isFile) return null
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                pkgManager.getPackageArchiveInfo(apkFile.absolutePath, PACKAGE_FLAGS)
            }
            packageInfo?.apply {
                applicationInfo?.apply {
                    sourceDir = apkFile.absolutePath
                    publicSourceDir = apkFile.absolutePath
                }
            }
        } catch (_: Exception) {
            null
        }
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

        val cacheRoot = File(context.codeCacheDir, LOAD_CACHE_DIR).apply { mkdirs() }
        val targetFile = File(cacheRoot, "$pkgName.apk")

        if (
            targetFile.exists() &&
            targetFile.length() == sourceFile.length() &&
            targetFile.lastModified() == sourceFile.lastModified()
        ) {
            return targetFile.absolutePath
        }

        val tempFile = File(cacheRoot, "$pkgName.apk.tmp")
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
        val root = getSideloadDir(context)
        val apkFile = File(root, "$packageName.apk")
        val cacheRoot = File(context.codeCacheDir, LOAD_CACHE_DIR)
        val cacheFile = File(cacheRoot, "$packageName.apk")

        var deleted = false
        if (apkFile.exists()) {
            apkFile.setWritable(true)
            val del = apkFile.delete()
            if (!del && apkFile.exists()) {
                try {
                    apkFile.writeBytes(ByteArray(0))
                } catch (_: Exception) {}
            }
            deleted = del || deleted
        }
        if (cacheFile.exists()) {
            cacheFile.setWritable(true)
            val del = cacheFile.delete()
            if (!del && cacheFile.exists()) {
                try {
                    cacheFile.writeBytes(ByteArray(0))
                } catch (_: Exception) {}
            }
            deleted = del || deleted
        }
        return deleted
    }
}
