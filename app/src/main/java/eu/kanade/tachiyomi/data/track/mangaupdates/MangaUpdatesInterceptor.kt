package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MangaUpdatesInterceptor(
    mangaUpdates: MangaUpdates,
) : Interceptor {

    private var token: String? = mangaUpdates.restoreSession()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "Kisara v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")

        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }

    fun newAuth(token: String?) {
        this.token = token
    }
}
