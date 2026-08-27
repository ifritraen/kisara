package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Universal OpenAI-Compatible LLM Translation Client.
 *
 * Connects seamlessly with DeepSeek, OpenAI, Alibaba Qwen, Anthropic Claude,
 * Zhipu GLM, Moonshot Kimi, OpenRouter, and any custom OpenAI-format endpoint.
 */
class OpenAiCompatibleTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKeys: List<String>,
    val endpoint: String = "https://api.deepseek.com/v1/chat/completions",
    val modelName: String = "deepseek-chat",
    val maxOutputTokens: Int = 8192,
    val temperature: Float = 0.2f,
    val customHeaders: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) : TextTranslator {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        onProgress: suspend (translatedBlocks: Int, totalBlocks: Int) -> Unit,
    ) {
        val allBlocks = mutableListOf<Pair<TranslationBlock, String>>() // Block to parent page key
        for ((pageKey, pageTrans) in pages) {
            for (block in pageTrans.blocks) {
                if (block.text.isNotBlank()) {
                    allBlocks.add(Pair(block, pageKey))
                }
            }
        }

        if (allBlocks.isEmpty()) return

        val totalBlocks = allBlocks.size
        var completedBlocks = 0
        val batchSize = 25

        val chunks = allBlocks.chunked(batchSize)
        val activeKey = apiKeys.firstOrNull { it.isNotBlank() }.orEmpty()

        for (chunk in chunks) {
            val texts = chunk.map { it.first.text }
            val translations = translateBatch(texts, activeKey)

            for ((idx, translatedText) in translations.withIndex()) {
                if (idx < chunk.size) {
                    chunk[idx].first.translation = translatedText
                }
            }

            completedBlocks += chunk.size
            onProgress(completedBlocks.coerceAtMost(totalBlocks), totalBlocks)
            delay(100) // Respect rate limits
        }
    }

    private suspend fun translateBatch(
        texts: List<String>,
        apiKey: String,
    ): List<String> {
        val systemPrompt = "You are a professional comic and manga translation engine. " +
            "Translate the given array of Japanese/Korean/Chinese text blocks into natural, fluent ${toLang.label}. " +
            "Maintain the tone, slang, and context of the comic characters. " +
            "Discard website watermarks (e.g. colamanga, rawkuma) by replacing them with empty string. " +
            "You MUST reply with ONLY a JSON array of strings containing the translated texts in the exact same order and length as the input array."

        val userJsonArray = JSONArray(texts)
        val userPrompt = "Translate this JSON array of ${fromLang.label} texts to ${toLang.label}:\n$userJsonArray"

        val payload = JSONObject().apply {
            put("model", modelName.ifBlank { "deepseek-chat" })
            put("temperature", temperature)
            put("max_tokens", maxOutputTokens)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                },
            )
        }

        val requestBuilder = Request.Builder()
            .url(endpoint.ifBlank { "https://api.deepseek.com/v1/chat/completions" })
            .post(payload.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        for ((k, v) in customHeaders) {
            requestBuilder.header(k, v)
        }

        val response = client.newCall(requestBuilder.build()).await()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw IllegalStateException("LLM Translation request failed (${response.code}): $responseBody")
        }

        return parseTranslationResponse(responseBody, texts.size)
    }

    private fun parseTranslationResponse(responseBody: String, expectedSize: Int): List<String> {
        val rootObj = JSONObject(responseBody)
        val choices = rootObj.optJSONArray("choices") ?: JSONArray()
        if (choices.length() == 0) return List(expectedSize) { "" }

        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.optJSONObject("message")
        val content = message?.optString("content").orEmpty().trim()

        val sanitized = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val jsonArray = JSONArray(sanitized)
            val result = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.optString(i, ""))
            }
            if (result.size < expectedSize) {
                result.addAll(List(expectedSize - result.size) { "" })
            }
            result.take(expectedSize)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse OpenAI-compatible JSON array: $sanitized" }
            List(expectedSize) { "" }
        }
    }

    override fun close() {
        // OkHttpClient managed by NetworkHelper
    }
}
