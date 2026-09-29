package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class GoogleTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {
    private val client1 = "gtx"
    private val client2 = "webapp"
    val okHttpClient = OkHttpClient()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        onProgress: suspend (translatedBlocks: Int, totalBlocks: Int) -> Unit,
    ) = coroutineScope {
        val totalBlocks = pages.values.sumOf { it.blocks.size }
        if (totalBlocks == 0) return@coroutineScope
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val jobs = pages.values.flatMap { page ->
            page.blocks.map { block ->
                async {
                    try {
                        val result = translateText(toLang.code, block.text)
                        if (result.isBlank()) {
                            throw Exception("Google Translate returned empty result")
                        }
                        block.translation = result
                    } finally {
                        val done = completed.incrementAndGet()
                        onProgress(done, totalBlocks)
                    }
                }
            }
        }
        jobs.awaitAll()
        Unit
    }

    private suspend fun translateText(lang: String, text: String): String {
        try {
            val access = getTranslateUrl(lang, text)
            val build: Request = Request.Builder()
                .url(access)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()
            val response = okHttpClient.newCall(build).await()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val rootArray = JSONArray(body)
                    val segmentsArray = rootArray.optJSONArray(0)
                    if (segmentsArray != null) {
                        val sb = StringBuilder()
                        for (i in 0 until segmentsArray.length()) {
                            val segment = segmentsArray.optJSONArray(i)
                            if (segment != null && !segment.isNull(0)) {
                                sb.append(segment.getString(0))
                            }
                        }
                        val result = sb.toString().trim()
                        if (result.isNotBlank()) return result
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to web endpoint below
        }

        // Resilient web endpoint fallback
        val encoded = URLEncoder.encode(text, "utf-8")
        val webUrl = "https://translate.google.com/m?sl=auto&tl=$lang&q=$encoded"
        val webReq = Request.Builder()
            .url(webUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
            .build()
        val webResp = okHttpClient.newCall(webReq).await()
        if (!webResp.isSuccessful) {
            throw Exception("Google Translate API error ${webResp.code}: ${webResp.message}")
        }
        val html = webResp.body?.string() ?: throw Exception("Empty response body")
        val match = Regex("""class="result-container">([^<]+)</div>""").find(html)
        val extracted = match?.groupValues?.get(1)?.trim()
        if (!extracted.isNullOrBlank()) {
            return android.text.Html.fromHtml(extracted, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        }
        throw Exception("Failed to parse Google Translate response")
    }

    private fun getTranslateUrl(lang: String, text: String): String {
        try {
            val client = client1
            val calculateToken = calculateToken(text)
            val encode: String = URLEncoder.encode(text, "utf-8")
            return "https://translate.google.com/translate_a/single?client=$client&sl=auto&tl=$lang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken&q=$encode"
        } catch (unused: UnsupportedEncodingException) {
            val client2 = client1
            val calculateToken2 = calculateToken(text)
            return "https://translate.google.com/translate_a/single?client=$client2&sl=auto&tl=$lang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken2&q=$text"
        }
    }

    private fun calculateToken(str: String): String {
        val list = mutableListOf<Int>()
        var i = 0

        while (i < str.length) {
            val charCodeAt = str.codePointAt(i)
            when {
                charCodeAt < 128 -> list.add(charCodeAt)
                charCodeAt < 2048 -> {
                    list.add((charCodeAt shr 6) or 192)
                    list.add((charCodeAt and 63) or 128)
                }
                charCodeAt in 55296..57343 && i + 1 < str.length -> {
                    val nextChar = str.codePointAt(i + 1)
                    if (nextChar in 56320..57343) {
                        val codePoint = ((charCodeAt and 1023) shl 10) + (nextChar and 1023) + 65536
                        list.add((codePoint shr 18) or 240)
                        list.add(((codePoint shr 12) and 63) or 128)
                        list.add(((codePoint shr 6) and 63) or 128)
                        list.add((codePoint and 63) or 128)
                        i++
                    }
                }
                else -> {
                    list.add((charCodeAt shr 12) or 224)
                    list.add(((charCodeAt shr 6) and 63) or 128)
                    list.add((charCodeAt and 63) or 128)
                }
            }
            i++
        }

        var j: Long = 406644
        for (num in list) {
            j = rl(j + num.toLong(), "+-a^+6")
        }
        var rL = rl(j, "+-3^+b+-f") xor 3293161072L
        if (rL < 0) {
            rL = (rL and 2147483647L) + 2147483648L
        }
        val j2 = rL % 1000000L
        return "$j2.${406644L xor j2}"
    }

    private fun rl(j: Long, str: String): Long {
        var result = j
        var i = 0
        while (i < str.length - 2) {
            val shift = if (str[i + 2] in 'a'..'z') str[i + 2].code - 'W'.code else str[i + 2].digitToInt()
            val shiftValue = if (str[i + 1] == '+') result ushr shift else result shl shift
            result = if (str[i] == '+') (result + shiftValue) and 4294967295L else result xor shiftValue
            i += 3
        }
        return result
    }

    override fun close() {
    }
}
