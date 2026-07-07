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
        val internalDir = File(context.filesDir, SIDELOAD_DIR)
        val externalDir = File(context.getExternalFilesDir(null) ?: context.filesDir, SIDELOAD_DIR).apply { mkdirs() }
        if (internalDir.exists() && internalDir.isDirectory && internalDir != externalDir) {
            migrateDir(internalDir, externalDir)
        }
        return externalDir
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

        val internalCache = File(context.filesDir, LOAD_CACHE_DIR)
        val cacheRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, LOAD_CACHE_DIR).apply { mkdirs() }
        if (internalCache.exists() && internalCache.isDirectory && internalCache != cacheRoot) {
            migrateDir(internalCache, cacheRoot)
        }
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
        val cacheRoot = File(context.filesDir, LOAD_CACHE_DIR)

        fun deleteFromDir(dir: File): Boolean {
            var deleted = false
            val files = dir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) } ?: return false
            for (file in files) {
                val name = file.nameWithoutExtension
                val matchesName = name == packageName || name.startsWith("$packageName-") || name.startsWith("${packageName}_")
                val matchesPackage = matchesName || try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                    } else {
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
                    }
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
