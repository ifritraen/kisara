package eu.kanade.tachiyomi.extension

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.AndroidBitmapWrapper
import eu.kanade.tachiyomi.source.JarCatalogueSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.LinkResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap

class PluginClassLoader(
    dexPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name == "org.koitharu.kotatsu.parsers.MangaParserFactoryKt" ||
            name == "org.koitharu.kotatsu.parsers.model.MangaParserSource"
        ) {
            return findLoadedClass(name) ?: findClass(name)
        }

        if (name.startsWith("org.koitharu.kotatsu.parsers.model.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.config.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.webview.") ||
            name == "org.koitharu.kotatsu.parsers.MangaLoaderContext" ||
            name == "org.koitharu.kotatsu.parsers.MangaParser" ||
            name.startsWith("org.koitharu.kotatsu.parsers.util.LinkResolver") ||
            name.startsWith("org.koitharu.kotatsu.parsers.bitmap.")
        ) {
            return super.loadClass(name, resolve)
        }

        if (name.startsWith("org.koitharu.kotatsu.parsers.site.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.core.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.util.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.network.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.exception.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.MangaParserFactory")
        ) {
            try {
                return findClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        return super.loadClass(name, resolve)
    }
}

data class LoadedJarPlugin(
    val jarName: String,
    val classLoader: PluginClassLoader,
    val factoryMethod: Method,
    val sources: List<MangaSource>,
)

object JarExtensionManager {

    private val _sources = MutableStateFlow<List<JarCatalogueSource>>(emptyList())
    val sources: StateFlow<List<JarCatalogueSource>> = _sources.asStateFlow()

    private val plugins = ConcurrentHashMap<String, LoadedJarPlugin>()

    fun initialize(context: Context) {
        val extensionDir = File(context.filesDir, "jar_extensions")
        if (!extensionDir.exists()) {
            extensionDir.mkdirs()
        }

        val loaded = loadFromDirectory(context, extensionDir)
        val wrappedSources = mutableListOf<JarCatalogueSource>()

        val loaderContext = createLoaderContext(context)

        for (plugin in loaded) {
            plugins[plugin.jarName] = plugin
            for (source in plugin.sources) {
                wrappedSources.add(
                    JarCatalogueSource(source) {
                        instantiateMangaParser(plugin, source, loaderContext)
                    },
                )
            }
        }

        _sources.value = wrappedSources
    }

    fun installJar(context: Context, uri: Uri): Boolean {
        return try {
            val extensionDir = File(context.filesDir, "jar_extensions")
            if (!extensionDir.exists()) {
                extensionDir.mkdirs()
            }

            val filename = getFileName(context, uri) ?: "extension_${System.currentTimeMillis()}.jar"
            val destFile = File(extensionDir, filename)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            initialize(context)
            true
        } catch (e: Exception) {
            android.util.Log.e("JarExtensionManager", "Failed to install JAR: ${e.message}", e)
            false
        }
    }

    private fun loadFromDirectory(context: Context, pluginDir: File): List<LoadedJarPlugin> {
        val cacheDir = context.codeCacheDir.absolutePath
        val parentClassLoader = context.classLoader
        val result = mutableListOf<LoadedJarPlugin>()

        val jarFiles = pluginDir.listFiles { file -> file.extension == "jar" } ?: emptyArray()

        for (jarFile in jarFiles) {
            try {
                jarFile.setReadOnly() // Required on Android 14+
                val classLoader = PluginClassLoader(
                    jarFile.absolutePath,
                    cacheDir,
                    null,
                    parentClassLoader,
                )

                val factoryClass = classLoader.loadClass("org.koitharu.kotatsu.parsers.MangaParserFactoryKt")
                val factoryMethod = factoryClass.declaredMethods.firstOrNull { method ->
                    method.name.startsWith("newParser") &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[1] == MangaLoaderContext::class.java
                } ?: continue

                val sourceClass = factoryMethod.parameterTypes[0]
                if (!sourceClass.isEnum) continue

                val sources = sourceClass.enumConstants?.filterIsInstance<MangaSource>().orEmpty()
                if (sources.isNotEmpty()) {
                    result.add(LoadedJarPlugin(jarFile.name, classLoader, factoryMethod, sources))
                }
            } catch (e: Exception) {
                android.util.Log.e("JarExtensionManager", "Failed to load jar ${jarFile.name}: ${e.message}", e)
            }
        }
        return result
    }

    private fun instantiateMangaParser(
        plugin: LoadedJarPlugin,
        source: MangaSource,
        context: MangaLoaderContext,
    ): MangaParser {
        val enumClass = plugin.factoryMethod.parameterTypes[0]
        val matchingEnum = enumClass.enumConstants?.find { (it as? MangaSource)?.name == source.name }
            ?: throw IllegalArgumentException("Source missing in JAR: ${source.name}")
        plugin.factoryMethod.isAccessible = true
        return plugin.factoryMethod.invoke(null, matchingEnum, context) as MangaParser
    }

    private fun createLoaderContext(context: Context): MangaLoaderContext {
        val okHttpClient = OkHttpClient()
        return object : MangaLoaderContext() {
            override val httpClient: OkHttpClient = okHttpClient
            override val cookieJar: CookieJar = okHttpClient.cookieJar

            override fun newParserInstance(source: MangaSource): MangaParser {
                val plugin = plugins.values.find { p -> p.sources.any { it.name == source.name } }
                    ?: throw IllegalArgumentException("Parser not found for: ${source.name}")
                return instantiateMangaParser(plugin, source, this)
            }

            override fun newLinkResolver(link: HttpUrl): LinkResolver {
                return object : LinkResolver {
                    override val link: HttpUrl = link
                    override suspend fun getSource(): MangaSource? = null
                    override suspend fun getManga(): Manga? = null
                }
            }

            override suspend fun evaluateJs(script: String): String? = null

            override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

            override fun getConfig(source: MangaSource): MangaSourceConfig {
                return object : MangaSourceConfig {
                    override fun <T> get(key: ConfigKey<T>): T {
                        return key.defaultValue
                    }
                }
            }

            override fun redrawImageResponse(response: Response, redraw: (KotatsuBitmap) -> KotatsuBitmap): Response {
                val body = response.body ?: return response
                return try {
                    val bytes = body.bytes()
                    val androidBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return response
                    val wrappedInput = AndroidBitmapWrapper(androidBitmap)
                    val wrappedOutput = redraw(wrappedInput) as AndroidBitmapWrapper

                    val stream = ByteArrayOutputStream()
                    wrappedOutput.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    val newBytes = stream.toByteArray()

                    val newBody = ResponseBody.create(body.contentType(), newBytes)
                    response.newBuilder().body(newBody).build()
                } catch (e: Exception) {
                    response
                }
            }

            override fun createBitmap(width: Int, height: Int): KotatsuBitmap {
                val config = android.graphics.Bitmap.Config.ARGB_8888
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, config)
                return AndroidBitmapWrapper(bitmap)
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
