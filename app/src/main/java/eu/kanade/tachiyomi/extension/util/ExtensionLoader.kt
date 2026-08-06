package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.extensionrepo.interactor.GetExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
internal object ExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustExtension by injectLazy()

    // KMK -->
    private val getExtensionRepo: GetExtensionRepo by injectLazy()
    // KMK <--

    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.5, 1.6)

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context): File {
        val internalDir = File(context.filesDir, "exts")
        val externalDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exts").apply { mkdirs() }
        if (internalDir.exists() && internalDir.isDirectory && internalDir != externalDir) {
            try {
                internalDir.listFiles()?.forEach { file ->
                    val target = File(externalDir, file.name)
                    if (!target.exists()) {
                        file.copyTo(target, overwrite = true)
                    }
                    file.delete()
                }
                internalDir.delete()
            } catch (_: Exception) {}
        }
        return externalDir
    }

    private fun getPrivateTempExtensionDir(context: Context): File {
        return File(getPrivateExtensionDir(context), "temp").apply { mkdirs() }
    }

    fun cleanTemporaryExtensions(context: Context) {
        try {
            getPrivateTempExtensionDir(context).deleteRecursively()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to clean temporary extensions" }
        }
    }

    fun installPrivateExtensionFile(context: Context, file: File, isTemporary: Boolean = false): Boolean {
        val extension = getPackageArchiveInfoWithCache(context, file, PACKAGE_FLAGS)
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getExtensionPackageInfoFromPkgName(context, extension.packageName)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val dir = if (isTemporary) getPrivateTempExtensionDir(context) else getPrivateExtensionDir(context)
        val target = File(dir, "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                ExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                ExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        invalidateCacheForPackage(context, pkgName)
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
        File(getPrivateTempExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
        LocalApkExtensionSupport.deleteSideloadedApk(context, pkgName)
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadExtensions(context: Context): List<LoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val files = (getPrivateExtensionDir(context).listFiles() ?: emptyArray()) +
            (getPrivateTempExtensionDir(context).listFiles() ?: emptyArray())
        val legacyPrivateExtPkgs = files
            .asSequence()
            .filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            .mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                getPackageArchiveInfoWithCache(context, it, PACKAGE_FLAGS)
            }
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = false) }

        val sideloadedExtPkgs = LocalApkExtensionSupport.getLocalApkFiles(context)
            .asSequence()
            .mapNotNull {
                getPackageArchiveInfoWithCache(context, it, PACKAGE_FLAGS)
            }
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = false) }

        val privateExtPkgs = legacyPrivateExtPkgs + sideloadedExtPkgs

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension with throttled concurrency (Semaphore) to prevent ART ClassLinker lock contention & GC blocks
        val semaphore = Semaphore(8)
        return runBlocking {
            // KMK -->
            val extRepos = getExtensionRepo.getAll()
            // KMK <--
            val deferred = extPkgs.map {
                async {
                    semaphore.withPermit {
                        loadExtension(
                            context,
                            it,
                            // KMK -->
                            extRepos,
                            // KMK <--
                        )
                    }
                }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): LoadResult {
        val extensionPackage = getExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return LoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): ExtensionInfo? {
        val cleanPkgName = when {
            pkgName.contains("-") -> {
                val suffix = pkgName.substringAfterLast("-")
                if (suffix.toLongOrNull() != null || suffix.all { it.isDigit() }) {
                    val base = pkgName.substringBeforeLast("-")
                    if (base.endsWith("_")) base.dropLast(1) else base
                } else {
                    pkgName
                }
            }
            pkgName.contains("_") -> {
                val suffix = pkgName.substringAfterLast("_")
                if (suffix.toLongOrNull() != null || suffix.all { it.isDigit() }) {
                    pkgName.substringBeforeLast("_")
                } else {
                    pkgName
                }
            }
            else -> pkgName
        }

        var privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        if (!privateExtensionFile.isFile) {
            privateExtensionFile = File(getPrivateExtensionDir(context), "$cleanPkgName.$PRIVATE_EXTENSION_EXTENSION")
        }
        if (!privateExtensionFile.isFile) {
            privateExtensionFile = File(getPrivateTempExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        }
        if (!privateExtensionFile.isFile) {
            privateExtensionFile = File(getPrivateTempExtensionDir(context), "$cleanPkgName.$PRIVATE_EXTENSION_EXTENSION")
        }

        val privatePkg = if (privateExtensionFile.isFile) {
            getPackageArchiveInfoWithCache(context, privateExtensionFile, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            var sideloadedFile = File(LocalApkExtensionSupport.getSideloadDir(context), "$pkgName.apk")
            if (!sideloadedFile.isFile) {
                sideloadedFile = File(LocalApkExtensionSupport.getSideloadDir(context), "$cleanPkgName.apk")
            }
            if (sideloadedFile.isFile) {
                getPackageArchiveInfoWithCache(context, sideloadedFile, PACKAGE_FLAGS)
                    ?.takeIf { isPackageAnExtension(it) }
                    ?.let {
                        ExtensionInfo(
                            packageInfo = it,
                            isShared = false,
                        )
                    }
            } else {
                null
            }
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfoCompat(pkgName, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: Exception) {
            try {
                context.packageManager.getPackageInfoCompat(cleanPkgName, PACKAGE_FLAGS)
                    ?.takeIf { isPackageAnExtension(it) }
                    ?.let {
                        ExtensionInfo(
                            packageInfo = it,
                            isShared = true,
                        )
                    }
            } catch (innerError: Exception) {
                null
            }
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(
        context: Context,
        extensionInfo: ExtensionInfo,
        // KMK -->
        extRepos: List<ExtensionRepo>? = null,
        // KMK <--
    ): LoadResult {
        // KMK -->
        val repos = extRepos ?: getExtensionRepo.getAll()
        // KMK <--
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = appInfo.metaData.getString(METADATA_NAME)
            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return LoadResult.Error
        }

        // Validate lib version
        val libVersion = appInfo.metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only version(s) ${SUPPORTED_LIB_VERSIONS.joinToString()} are supported"
            }
            return LoadResult.Error
        }

        val isSideloaded = LocalApkExtensionSupport.getLocalApkFiles(context).any { it.nameWithoutExtension == pkgName }
        // KMK -->
        // temporarilySideloadedPkgs may hold suffixed pkg names (e.g. "eu.pkg_1234567") from repo metadata
        // while pkgName here is the clean name from the APK manifest. Strip numeric suffixes before matching.
        val isTemporary = eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel.temporarilySideloadedPkgs.any { stored ->
            if (stored == pkgName) return@any true
            val sep = stored.lastIndexOfAny(charArrayOf('_', '-'))
            if (sep > 0) {
                val suffix = stored.substring(sep + 1)
                val base = stored.substring(0, sep).trimEnd('_')
                suffix.toLongOrNull() != null && base == pkgName
            } else {
                false
            }
        }
        // KMK <--
        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return LoadResult.Error
        }

        val isNsfw = appInfo.metaData.getInt(METADATA_CONTENT_WARNING) > 0 ||
            appInfo.metaData.getInt(METADATA_NSFW) == 1
        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return LoadResult.Error
        }
        val loadPath = if (isSideloaded) {
            LocalApkExtensionSupport.prepareLoadableApkPath(context, pkgName, appInfo.sourceDir)
        } else {
            appInfo.sourceDir
        }
        val loadFile = java.io.File(loadPath)
        if (loadFile.exists()) {
            loadFile.setReadOnly()
        }
        val classLoader = try {
            ChildFirstPathClassLoader(loadPath, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return LoadResult.Error
        }

        val sources = appInfo.metaData.getString(METADATA_SOURCE_CLASS)!!
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    return LoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val repo = repos.firstOrNull { repo ->
            signatures.all { it == repo.signingKeyFingerprint }
        }
        val extension = Extension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = null,
            isShared = extensionInfo.isShared,
            // KMK -->
            signatureHash = signatures.last(),
            repoName = repo?.let {
                it.shortName.takeIf { !it.isNullOrBlank() } ?: it.name
            },
            repoUrl = repo?.baseUrl,
            // KMK <--
        )
        return LoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private fun PackageManager.getPackageArchiveInfoCompat(archiveFilePath: String, flags: Int): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageArchiveInfo(archiveFilePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                getPackageArchiveInfo(archiveFilePath, flags)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                getPackageInfo(packageName, flags)
            }
        } catch (e: Exception) {
            null
        }
    }

    private val pkgInfoMemoryCache = java.util.concurrent.ConcurrentHashMap<String, PackageInfo>()

    fun invalidateCacheForPackage(context: Context, packageName: String) {
        pkgInfoMemoryCache.keys.removeIf { key -> key.startsWith("${packageName}_") }
        try {
            val cacheDir = File(context.cacheDir, "ext_pkg_info_cache")
            val prefix = "${packageName}_"
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun getPackageArchiveInfoWithCache(
        context: Context,
        file: File,
        flags: Int = PACKAGE_FLAGS,
    ): PackageInfo? {
        if (!file.isFile) return null
        val cacheKey = "${file.nameWithoutExtension}_${file.lastModified()}_${file.length()}_$flags"

        pkgInfoMemoryCache[cacheKey]?.let { cachedInfo ->
            cachedInfo.applicationInfo?.fixBasePaths(file.absolutePath)
            return cachedInfo
        }

        val cacheDir = File(context.cacheDir, "ext_pkg_info_cache").apply { mkdirs() }
        val cacheFile = File(cacheDir, "$cacheKey.bin")

        if (cacheFile.isFile) {
            try {
                val bytes = cacheFile.readBytes()
                val parcel = Parcel.obtain()
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val pkgInfo = PackageInfo.CREATOR.createFromParcel(parcel)
                parcel.recycle()
                if (pkgInfo != null) {
                    pkgInfo.applicationInfo?.fixBasePaths(file.absolutePath)
                    pkgInfoMemoryCache[cacheKey] = pkgInfo
                    return pkgInfo
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to read cached PackageInfo for ${file.name}" }
                cacheFile.delete()
            }
        }

        val pkgInfo = context.packageManager.getPackageArchiveInfoCompat(file.absolutePath, flags)
            ?: return null
        pkgInfo.applicationInfo?.fixBasePaths(file.absolutePath)

        pkgInfoMemoryCache[cacheKey] = pkgInfo

        try {
            val prefix = "${file.nameWithoutExtension}_"
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith(prefix) && f.name != "$cacheKey.bin") {
                    f.delete()
                }
            }
            val parcel = Parcel.obtain()
            pkgInfo.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            cacheFile.writeBytes(bytes)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Failed to cache PackageInfo for ${file.name}" }
        }

        return pkgInfo
    }

    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
