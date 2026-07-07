package eu.kanade.tachiyomi.extension

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.AndroidBitmapWrapper
import eu.kanade.tachiyomi.source.JarCatalogueSource
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import org.koitharu.kotatsu.parsers.webview.InterceptedRequest
import org.koitharu.kotatsu.parsers.webview.InterceptionConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Method
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
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

    fun getInstalledJars(): List<LoadedJarPlugin> = plugins.values.toList()

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
            val repoName = getRepoNameForJar(context, plugin.jarName)
            for (source in plugin.sources) {
                wrappedSources.add(
                    JarCatalogueSource(source, repoName) {
                        instantiateMangaParser(plugin, source, loaderContext)
                    },
                )
            }
        }

        // Disable newly installed JAR sources by default on first install
        try {
            val prefs = context.getSharedPreferences("jar_extension_prefs", Context.MODE_PRIVATE)
            val seenSources = prefs.getStringSet("seen_sources", emptySet()) ?: emptySet()
            val newSeenSources = seenSources.toMutableSet()
            val toDisable = mutableSetOf<String>()

            for (wrappedSource in wrappedSources) {
                val sourceIdStr = wrappedSource.id.toString()
                if (sourceIdStr !in seenSources) {
                    toDisable.add(sourceIdStr)
                    newSeenSources.add(sourceIdStr)
                }
            }

            if (toDisable.isNotEmpty()) {
                val sourcePreferences = Injekt.get<SourcePreferences>()
                val disabled = sourcePreferences.disabledSources().get()
                sourcePreferences.disabledSources().set(disabled + toDisable)
            }

            if (newSeenSources.size > seenSources.size) {
                prefs.edit().putStringSet("seen_sources", newSeenSources).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("JarExtensionManager", "Failed to default disable new JAR sources: ${e.message}", e)
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
        val okHttpClient = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().client
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

            override suspend fun evaluateJs(script: String): String? {
                return evaluateJs("about:blank", script, 10000L)
            }

            override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        val webView = android.webkit.WebView(context)
                        webView.setDefaultSettings()
                        webView.settings.userAgentString = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().defaultUserAgentProvider()

                        var isFinished = false
                        val cleanUp = {
                            if (!isFinished) {
                                isFinished = true
                                try {
                                    webView.stopLoading()
                                    webView.destroy()
                                } catch (e: Exception) {}
                            }
                        }

                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        val timeoutRunnable = Runnable {
                            cleanUp()
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.failure(TimeoutException("JS evaluation timed out")))
                            }
                        }
                        handler.postDelayed(timeoutRunnable, timeout)

                        continuation.invokeOnCancellation {
                            handler.removeCallbacks(timeoutRunnable)
                            cleanUp()
                        }

                        webView.webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                val cleanUrl = url?.substringBefore("?")?.removeSuffix("/")
                                val cleanBase = baseUrl.substringBefore("?")?.removeSuffix("/")
                                if (cleanUrl == cleanBase || url == "about:blank" || cleanUrl == "about:blank") {
                                    webView.evaluateJavascript(script) { result ->
                                        handler.removeCallbacks(timeoutRunnable)
                                        cleanUp()
                                        if (continuation.isActive) {
                                            val cleanResult = if (result == "null" || result == null) null else result.trim('"')
                                            continuation.resumeWith(Result.success(cleanResult))
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    handler.removeCallbacks(timeoutRunnable)
                                    cleanUp()
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.failure(Exception("Page load error: ${error?.description}")))
                                    }
                                }
                            }
                        }
                        webView.loadUrl(baseUrl)
                    }
                }
            }

            override suspend fun interceptWebViewRequests(
                url: String,
                interceptorScript: String,
                timeout: Long,
            ): List<InterceptedRequest> {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        val webView = android.webkit.WebView(context)
                        webView.setDefaultSettings()
                        webView.settings.userAgentString = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().defaultUserAgentProvider()

                        val captured = Collections.synchronizedList(mutableListOf<InterceptedRequest>())

                        var isFinished = false
                        val cleanUp = {
                            if (!isFinished) {
                                isFinished = true
                                try {
                                    webView.stopLoading()
                                    webView.destroy()
                                } catch (e: Exception) {}
                            }
                        }

                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        val timeoutRunnable = Runnable {
                            cleanUp()
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(captured.toList()))
                            }
                        }
                        handler.postDelayed(timeoutRunnable, timeout)

                        continuation.invokeOnCancellation {
                            handler.removeCallbacks(timeoutRunnable)
                            cleanUp()
                        }

                        webView.webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                            ): android.webkit.WebResourceResponse? {
                                if (request != null) {
                                    val reqUrl = request.url.toString()
                                    val reqMethod = request.method
                                    val reqHeaders = request.requestHeaders ?: emptyMap()

                                    var matches = true
                                    if (interceptorScript.isNotEmpty() && interceptorScript != "return true;") {
                                        val matchRegex = Regex("['\"]([^'\"]+)['\"]")
                                        val keywords = matchRegex.findAll(interceptorScript).map { it.groupValues[1] }.toList()
                                        if (keywords.isNotEmpty()) {
                                            matches = keywords.any { reqUrl.contains(it, ignoreCase = true) }
                                        }
                                    }

                                    if (matches) {
                                        captured.add(
                                            InterceptedRequest(
                                                url = reqUrl,
                                                method = reqMethod,
                                                headers = reqHeaders,
                                                timestamp = System.currentTimeMillis(),
                                            ),
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        webView.loadUrl(url)
                    }
                }
            }

            override suspend fun captureWebViewUrls(
                pageUrl: String,
                urlPattern: Regex,
                timeout: Long,
            ): List<String> {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        val webView = android.webkit.WebView(context)
                        webView.setDefaultSettings()
                        webView.settings.userAgentString = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>().defaultUserAgentProvider()

                        val captured = Collections.synchronizedList(mutableListOf<String>())

                        var isFinished = false
                        val cleanUp = {
                            if (!isFinished) {
                                isFinished = true
                                try {
                                    webView.stopLoading()
                                    webView.destroy()
                                } catch (e: Exception) {}
                            }
                        }

                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        val timeoutRunnable = Runnable {
                            cleanUp()
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(captured.toList()))
                            }
                        }
                        handler.postDelayed(timeoutRunnable, timeout)

                        continuation.invokeOnCancellation {
                            handler.removeCallbacks(timeoutRunnable)
                            cleanUp()
                        }

                        webView.webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                            ): android.webkit.WebResourceResponse? {
                                if (request != null) {
                                    val reqUrl = request.url.toString()
                                    if (urlPattern.containsMatchIn(reqUrl)) {
                                        captured.add(reqUrl)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        webView.loadUrl(pageUrl)
                    }
                }
            }

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

    fun getRepoNameForJar(context: Context, jarName: String): String? {
        try {
            val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
            val repoMap = uiPreferences.jarExtensionRepoMap().get()
            return repoMap.find { it.startsWith("$jarName:") }?.substringAfter(":")
        } catch (_: Exception) {
            return null
        }
    }

    fun uninstallJar(context: Context, filename: String): Boolean {
        return try {
            try {
                val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                val repoMap = uiPreferences.jarExtensionRepoMap().get().toMutableSet()
                repoMap.removeAll { it.startsWith("$filename:") }
                uiPreferences.jarExtensionRepoMap().set(repoMap)
            } catch (_: Exception) {}

            val extensionDir = File(context.filesDir, "jar_extensions")
            val file = File(extensionDir, filename)
            if (file.exists()) {
                file.delete()
            }
            initialize(context)
            true
        } catch (e: Exception) {
            android.util.Log.e("JarExtensionManager", "Failed to uninstall JAR: ${e.message}", e)
            false
        }
    }

    suspend fun downloadAndInstallJar(context: Context, url: String, filename: String, repoName: String? = null): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (repoName != null) {
                    try {
                        val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                        val repoMap = uiPreferences.jarExtensionRepoMap().get().toMutableSet()
                        repoMap.removeAll { it.startsWith("$filename:") }
                        repoMap.add("$filename:$repoName")
                        uiPreferences.jarExtensionRepoMap().set(repoMap)
                    } catch (_: Exception) {}
                }

                val extensionDir = File(context.filesDir, "jar_extensions")
                if (!extensionDir.exists()) {
                    extensionDir.mkdirs()
                }
                val destFile = File(extensionDir, filename)
                val request = okhttp3.Request.Builder().url(url).build()
                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    initialize(context)
                }
                true
            } catch (e: Exception) {
                android.util.Log.e("JarExtensionManager", "Failed to download and install JAR: ${e.message}", e)
                false
            }
        }
    }

    suspend fun fetchRepositoryIndex(repoUrl: String): List<JarExtensionInfo> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(repoUrl).build()
                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = org.json.JSONArray(body)
                val list = mutableListOf<JarExtensionInfo>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.getString("name")
                    val pkg = obj.getString("pkg")
                    val versionCode = if (obj.has("versionCode")) obj.getInt("versionCode") else obj.getInt("code")
                    val version = obj.getString("version")
                    val rawUrl = if (obj.has("url")) obj.getString("url") else obj.getString("apk")
                    val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                        rawUrl
                    } else {
                        val base = repoUrl.substringBeforeLast('/')
                        "$base/$rawUrl"
                    }
                    val iconUrl = obj.optString("iconUrl", null)
                    list.add(
                        JarExtensionInfo(
                            name = name,
                            pkg = pkg,
                            versionCode = versionCode,
                            version = version,
                            url = url,
                            iconUrl = iconUrl,
                        ),
                    )
                }
                list
            } catch (e: Exception) {
                android.util.Log.e("JarExtensionManager", "Failed to fetch repository index: ${e.message}", e)
                emptyList()
            }
        }
    }
}

data class JarExtensionInfo(
    val name: String,
    val pkg: String,
    val versionCode: Int,
    val version: String,
    val url: String,
    val iconUrl: String?,
)
