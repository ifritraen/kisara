package tachiyomi.domain.storage.service

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import java.io.File

class StorageManager(
    private val context: Context,
    private val storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = null

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        scope.launch {
            baseDir = getBaseDir(storagePreferences.baseStorageDirectory().get())
            baseDir?.let { parent ->
                parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                parent.createDirectory(LOCAL_SOURCE_PATH)
                parent.createDirectory(DOWNLOADS_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                // KMK -->
                parent.createDirectory(TRANSLATION_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                parent.createDirectory(COLORIZER_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                parent.createDirectory(SUPER_RESOLUTION_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                parent.createDirectory(MODELS_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                parent.createDirectory(EXTENSIONS_PATH).also {
                    DiskUtil.createNoMediaFile(it, context)
                }
                // KMK <--
            }
        }

        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    // KMK -->
                    parent.createDirectory(TRANSLATION_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(COLORIZER_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(SUPER_RESOLUTION_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(MODELS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(EXTENSIONS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    // KMK <--
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        if (uri.isBlank()) return null
        val parsedUri = uri.toUri()
        val uniFile = UniFile.fromUri(context, parsedUri) ?: return null

        // KMK --> Prefer direct RawFile if physical file path is accessible (e.g. MANAGE_EXTERNAL_STORAGE)
        var path = uniFile.filePath
        if (path.isNullOrBlank() && parsedUri.scheme == "content" && parsedUri.authority == "com.android.externalstorage.documents") {
            try {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(parsedUri)
                if (docId.startsWith("primary:")) {
                    path = File(Environment.getExternalStorageDirectory(), docId.substringAfter("primary:")).absolutePath
                }
            } catch (_: Exception) {}
        }

        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead() && file.canWrite()) {
                val rawFile = UniFile.fromFile(file)
                if (rawFile?.isAccessibleDirectory == true) {
                    return rawFile
                }
            }
        }
        // KMK <--

        return uniFile.takeIf {
            // KMK -->
            it.isAccessibleDirectory
            // KMK <--
        }
    }

    private fun ensureBaseDir(): UniFile? {
        return baseDir ?: getBaseDir(storagePreferences.baseStorageDirectory().get())?.also { baseDir = it }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalSourceDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(LOCAL_SOURCE_PATH)
    }

    fun getLocalAnimeSourceDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(LOCAL_ANIME_SOURCE_PATH)
    }

    fun getLocalNovelSourceDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(LOCAL_NOVEL_SOURCE_PATH)
    }

    fun getAnimeDownloadsDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(ANIME_DOWNLOADS_PATH)
    }

    fun getNovelDownloadsDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(NOVEL_DOWNLOADS_PATH)
    }

    // SY -->
    fun getLogsDirectory(): UniFile? {
        return ensureBaseDir()?.createDirectory(LOGS_PATH)
    }
    // SY <--

    // KMK -->
    fun getTranslationsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(TRANSLATION_PATH) ?: dir?.createDirectory(TRANSLATION_PATH)
    }

    fun getColorizerDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(COLORIZER_PATH) ?: dir?.createDirectory(COLORIZER_PATH)
    }

    fun getSuperResolutionDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(SUPER_RESOLUTION_PATH) ?: dir?.createDirectory(SUPER_RESOLUTION_PATH)
    }

    fun getFontsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(FONTS_PATH) ?: dir?.createDirectory(FONTS_PATH)
    }

    fun getScriptsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(SCRIPTS_PATH) ?: dir?.createDirectory(SCRIPTS_PATH)
    }

    fun getScriptOptsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(SCRIPT_OPTS_PATH) ?: dir?.createDirectory(SCRIPT_OPTS_PATH)
    }

    fun getShadersDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(SHADERS_PATH) ?: dir?.createDirectory(SHADERS_PATH)
    }

    fun getMPVConfigDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(MPV_CONFIG_PATH) ?: dir?.createDirectory(MPV_CONFIG_PATH)
    }

    fun getModelsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(MODELS_PATH) ?: dir?.createDirectory(MODELS_PATH)
    }

    fun getExtensionsDirectory(): UniFile? {
        val dir = ensureBaseDir()
        return dir?.findFile(EXTENSIONS_PATH)
            ?: dir?.findFile(LEGACY_EXTENSIONS_PATH)
            ?: dir?.createDirectory(EXTENSIONS_PATH)
    }
    // KMK <--

    companion object {
        // KMK -->
        /**
         * Extension property to check if a UniFile is an accessible directory
         */
        val UniFile.isAccessibleDirectory: Boolean
            get() = exists() && isDirectory && canWrite() && canRead()

        /**
         * Check if a directory is accessible
         */
        fun directoryAccessible(context: Context, uri: String): Boolean {
            if (uri.isBlank()) return false
            val parsedUri = uri.toUri()
            val uniFile = UniFile.fromUri(context, parsedUri) ?: return false
            var path = uniFile.filePath
            if (path.isNullOrBlank() && parsedUri.scheme == "content" && parsedUri.authority == "com.android.externalstorage.documents") {
                try {
                    val docId = android.provider.DocumentsContract.getTreeDocumentId(parsedUri)
                    if (docId.startsWith("primary:")) {
                        path = File(Environment.getExternalStorageDirectory(), docId.substringAfter("primary:")).absolutePath
                    }
                } catch (_: Exception) {}
            }
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.canRead() && file.canWrite()) {
                    return true
                }
            }
            return uniFile.isAccessibleDirectory
        }

        /**
         * Call FilePicker to allow access to storage or request All Files Access Permission if not available.
         */
        fun allowAccessStorage(
            context: Context,
            storageDirPref: Preference<String>,
            pickStorageLocation: () -> Unit,
        ) {
            try {
                val documentTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (isIntentAvailable(context, documentTreeIntent)) {
                    pickStorageLocation()
                } else {
                    handleStoragePermission(context, storageDirPref)
                }
            } catch (e: ActivityNotFoundException) {
                fallbackToScopedStorage(context, storageDirPref)
            }
        }

        /**
         * Handle storage permissions for Android R and above
         */
        private fun handleStoragePermission(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            if (hasManageExternalStoragePermission(context)) {
                updateStoragePreference(context, storageDirPref)
            } else {
                requestManageExternalStoragePermission(context)
            }
        }

        private fun hasManageExternalStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }

        private fun requestManageExternalStoragePermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    1001,
                )
            } else {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001,
                )
            }
        }

        /**
         * Update storage preference with the selected directory
         */
        private fun updateStoragePreference(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            UniFile.fromUri(context, storageDirPref.get().toUri())?.let {
                it.mkdir()
                storageDirPref.set("") // Trigger recompose
                storageDirPref.set(it.uri.toString())
            }
        }

        /**
         * Fallback to scoped storage if no other options are available
         */
        private fun fallbackToScopedStorage(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            val fallbackDir = File(context.getExternalFilesDir(null), context.stringResource(MR.strings.app_name))
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            storageDirPref.set("") // Trigger recompose
            storageDirPref.set(fallbackDir.toUri().toString())
            context.toast("Using default directory: ${fallbackDir.absolutePath}")
        }

        /**
         * Used to check if system is able to open contract [ActivityResultContracts.OpenDocumentTree]
         * by checking if intent [Intent.ACTION_OPEN_DOCUMENT_TREE] is available and not being stub (on Android TV)
         */
        private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
            val packageManager = context.packageManager
            // Android TV: ResolveInfo{c236166 com.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub m=0x108000 userHandle=UserHandle{0}}
            val resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return resolveInfo.any {
                it.activityInfo.packageName != null && it.activityInfo.packageName != "com.android.tv.frameworkpackagestubs"
            }
        }
        // KMK -->
        const val MODELS_PATH = "models"
        const val EXTENSIONS_PATH = "extensions"
        const val LEGACY_EXTENSIONS_PATH = "sideloaded_extensions"
        // KMK <--
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"
private const val LOCAL_ANIME_SOURCE_PATH = "localanime"
private const val LOCAL_NOVEL_SOURCE_PATH = "localnovel"
private const val ANIME_DOWNLOADS_PATH = "animedownloads"
private const val NOVEL_DOWNLOADS_PATH = "noveldownloads"

// SY -->
private const val LOGS_PATH = "logs"
// SY <--

// KMK -->
const val FONTS_PATH = "fonts"
const val SCRIPTS_PATH = "scripts"
const val SCRIPT_OPTS_PATH = "script-opts"
const val SHADERS_PATH = "shaders"
const val TRANSLATION_PATH = "translations"
const val COLORIZER_PATH = "colorizer"
const val SUPER_RESOLUTION_PATH = "superres"
const val MPV_CONFIG_PATH = "mpv-config"
const val MODELS_PATH = "models"
const val EXTENSIONS_PATH = "extensions"
const val LEGACY_EXTENSIONS_PATH = "sideloaded_extensions"
// KMK <--
