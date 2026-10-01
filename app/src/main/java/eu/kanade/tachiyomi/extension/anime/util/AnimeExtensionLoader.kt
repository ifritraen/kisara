package eu.kanade.tachiyomi.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Class that handles the loading of the extensions installed in the system.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object AnimeExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
    private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    private const val METADATA_HAS_README = "tachiyomi.animeextension.hasReadme"
    private const val METADATA_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
    private const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val LIB_VERSION_MIN = 12.0
    const val LIB_VERSION_MAX = 16.0

    val SUPPORTED_LIB_VERSIONS: ClosedFloatingPointRange<Double> = LIB_VERSION_MIN..LIB_VERSION_MAX

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private var isMigrated = false

    private fun getPrivateExtensionDir(context: Context): File {
        val targetDir = File(context.filesDir, "anime_exts")
        return targetDir
    }

    private fun canReplacePrivateExtension(
        installedVersionCode: Long,
        newVersionCode: Long,
        installedSignatures: List<String>,
        newSignatures: List<String>,
    ): Boolean {
        if (newVersionCode < installedVersionCode) return false
        if (newSignatures.isEmpty()) return false
        return installedSignatures.isEmpty() || newSignatures.containsAll(installedSignatures)
    }

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )?.takeIf { isPackageAnExtension(it) } ?: return false

        val pkgName = extension.packageName
        if (!pkgName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"))) {
            logcat(LogPriority.ERROR) { "Invalid package name: $pkgName" }
            return false
        }
        val currentExtension = getAnimeExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )
        val newSignatures = getSignatures(extension)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = newSignatures
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            if (!canReplacePrivateExtension(
                    installedVersionCode = PackageInfoCompat.getLongVersionCode(currentExtension),
                    newVersionCode = PackageInfoCompat.getLongVersionCode(extension),
                    installedSignatures = getSignatures(currentExtension).orEmpty(),
                    newSignatures = extensionSignatures,
                )
            ) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val privateExtensionDir = getPrivateExtensionDir(context)
        if (!privateExtensionDir.exists() && !privateExtensionDir.mkdirs()) {
            logcat(LogPriority.ERROR) { "Failed to create private extension directory." }
            return false
        }

        val target = File(
            privateExtensionDir,
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                AnimeExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
                newSignatures?.lastOrNull()?.let { signatureHash ->
                    trustExtension.trustIfSameSigner(
                        extension.packageName,
                        PackageInfoCompat.getLongVersionCode(extension),
                        signatureHash,
                    )
                }
            } else {
                AnimeExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
        eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.deleteSideloadedApk(context, pkgName)
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadExtensions(context: Context): List<AnimeLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { AnimeExtensionInfo(packageInfo = it, isShared = true) }

        val legacyPrivateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                eu.kanade.tachiyomi.extension.util.ExtensionLoader.getPackageArchiveInfoWithCache(context, it, PACKAGE_FLAGS)
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { AnimeExtensionInfo(packageInfo = it, isShared = false) }
            ?.toList()
            .orEmpty()

        val sideloadedExtPkgs = eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.getLocalApkFiles(context)
            .asSequence()
            .mapNotNull {
                eu.kanade.tachiyomi.extension.util.ExtensionLoader.getPackageArchiveInfoWithCache(context, it, PACKAGE_FLAGS)
            }
            .filter { isPackageAnExtension(it) }
            .map { AnimeExtensionInfo(packageInfo = it, isShared = false) }
            .toList()

        val deduplicatedPrivateExtPkgs = (legacyPrivateExtPkgs + sideloadedExtPkgs)
            .groupBy { it.packageInfo.packageName }
            .values
            .mapNotNull { group ->
                group.maxByOrNull { PackageInfoCompat.getLongVersionCode(it.packageInfo) }
            }

        val privateExtPkgsByPkgName = deduplicatedPrivateExtPkgs.associateBy { it.packageInfo.packageName }
        val allPkgNames = (sharedExtPkgs.map { it.packageInfo.packageName } + deduplicatedPrivateExtPkgs.map { it.packageInfo.packageName }).distinct()

        val extPkgs = allPkgNames.mapNotNull { pkgName ->
            val shared = sharedExtPkgs.firstOrNull { it.packageInfo.packageName == pkgName }
            val private = privateExtPkgsByPkgName[pkgName]
            selectExtensionPackage(shared, private)
        }.toList()

        if (extPkgs.isEmpty()) return emptyList()

        val trustedFingerprints = runBlocking {
            trustExtension.getTrustedFingerprints()
        }

        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it, trustedFingerprints) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): AnimeLoadResult {
        val extensionPackage = getAnimeExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return AnimeLoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getAnimeExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getAnimeExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getAnimeExtensionInfoFromPkgName(context: Context, pkgName: String): AnimeExtensionInfo? {
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
        val legacyPrivatePkg = if (privateExtensionFile.isFile) {
            eu.kanade.tachiyomi.extension.util.ExtensionLoader.getPackageArchiveInfoWithCache(
                context,
                privateExtensionFile,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sideloadedFile = eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.findApkForPackage(context, pkgName)
            ?: eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.findApkForPackage(context, cleanPkgName)
            ?: File(context.filesDir, "sideloaded_extensions/$pkgName.apk").takeIf { it.isFile }
            ?: File(context.filesDir, "sideloaded_extensions/$cleanPkgName.apk").takeIf { it.isFile }
        val sideloadedPkg = if (sideloadedFile != null && sideloadedFile.isFile) {
            eu.kanade.tachiyomi.extension.util.ExtensionLoader.getPackageArchiveInfoWithCache(
                context,
                sideloadedFile,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo?.fixBasePaths(sideloadedFile.absolutePath)
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val privatePkg = sideloadedPkg ?: legacyPrivatePkg

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    AnimeExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            try {
                context.packageManager.getPackageInfo(cleanPkgName, PACKAGE_FLAGS)
                    .takeIf { isPackageAnExtension(it) }
                    ?.let {
                        AnimeExtensionInfo(
                            packageInfo = it,
                            isShared = true,
                        )
                    }
            } catch (e: Exception) {
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
        extensionInfo: AnimeExtensionInfo,
        trustedFingerprints: Set<String>? = null,
    ): AnimeLoadResult {
        val pkgManager = context.packageManager

        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = appInfo.metaData?.getString(METADATA_NAME)
            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return AnimeLoadResult.Error
        }

        // Validate lib version
        val rawLibVersion = appInfo.metaData?.getDouble(METADATA_EXTENSION_LIB)?.takeUnless { it == 0.0 }
            ?: appInfo.metaData?.getFloat(METADATA_EXTENSION_LIB)?.toDouble()?.takeUnless { it == 0.0 }
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val libVersion = if (rawLibVersion != null) kotlin.math.round(rawLibVersion * 100.0) / 100.0 else null
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return AnimeLoadResult.Error
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return AnimeLoadResult.Error
        }

        val isNsfw = (appInfo.metaData?.getInt(METADATA_CONTENT_WARNING) ?: 0) > 0 ||
            appInfo.metaData?.getInt(METADATA_NSFW) == 1
        val isTorrent = appInfo.metaData?.getInt(METADATA_TORRENT) == 1
        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return AnimeLoadResult.Error
        }

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
        val isSideloaded = !extensionInfo.isShared || eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.findApkForPackage(context, pkgName) != null
        val loadPath = if (isSideloaded) {
            eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.prepareLoadableApkPath(context, pkgName, appInfo.sourceDir)
        } else {
            appInfo.sourceDir
        }
        val loadFile = File(loadPath)
        if (loadFile.exists()) {
            loadFile.setReadOnly()
        }

        val classLoader = try {
            ChildFirstPathClassLoader(loadPath, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return AnimeLoadResult.Error
        }

        val sourceMeta = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
        if (sourceMeta.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing source class for extension $extName" }
            return AnimeLoadResult.Error
        }
        val sources = sourceMeta
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
                        is AnimeSource -> listOf(obj)
                        is AnimeSourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: LinkageError) {
                    try {
                        val fallBackClassLoader = PathClassLoader(loadPath, null, context.classLoader)
                        when (
                            val obj = Class.forName(
                                it,
                                false,
                                fallBackClassLoader,
                            ).getDeclaredConstructor().newInstance()
                        ) {
                            is AnimeSource -> {
                                listOf(obj)
                            }
                            is AnimeSourceFactory -> obj.createSources()
                            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                        return AnimeLoadResult.Error
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    return AnimeLoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<AnimeCatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            isTorrent = isTorrent,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )

        val apkFile = File(appInfo.sourceDir ?: "")
        if (apkFile.isFile) {
            eu.kanade.tachiyomi.extension.util.LocalApkExtensionSupport.extractAndCacheApkIcon(
                context = context,
                apkFile = apkFile,
                packageName = pkgName,
                sourceIds = sources.map { it.id },
            )
        }

        return AnimeLoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: AnimeExtensionInfo?, private: AnimeExtensionInfo?): AnimeExtensionInfo? {
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
        sourceDir = apkPath
        publicSourceDir = apkPath
    }

    private data class AnimeExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
