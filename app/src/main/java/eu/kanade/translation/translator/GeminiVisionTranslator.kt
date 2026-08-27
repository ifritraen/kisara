package eu.kanade.translation.translator

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import logcat.LogPriority
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat

/**
 * End-to-End Multimodal Vision Comic Translator using Google Gemini Vision.
 *
 * Directly extracts speech bubbles, bounding coordinates, and translations in a single pass
 * without requiring local OCR model downloads or heavy on-device CPU/NPU pipelines.
 */
class GeminiVisionTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKeys: List<String>,
    val modelName: String = "gemini-2.0-flash",
    val maxOutputToken: Int = 8192,
    val temp: Float = 0.2f,
) : TextTranslator {

    companion object {
        const val IGNORE_TAG = "KISARA_IGNORE_BLOCK"
    }

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        onProgress: suspend (translatedBlocks: Int, totalBlocks: Int) -> Unit,
    ) {
        // Standard TextTranslator interface placeholder (End-to-End processing is called via translatePageBitmap)
    }

    /**
     * Translates a raw manga page bitmap directly into localized TranslationBlocks with coordinates.
     */
    suspend fun translatePageBitmap(
        pageBitmap: Bitmap,
        targetPageTranslation: PageTranslation,
    ): List<TranslationBlock> {
        val activeKeys = apiKeys.filter { it.isNotBlank() }.ifEmpty { listOf("") }
        var lastException: Exception? = null

        val sourceLangName = fromLang.label
        val targetLangName = toLang.label

        val promptText = buildString {
            appendLine("You are an expert manga/comic translation assistant with precise vision capabilities.")
            appendLine("Analyze this manga/comic page image. Detect all speech bubbles, narration boxes, and text lines in $sourceLangName.")
            appendLine("Translate the detected text accurately, naturally, and contextually into $targetLangName.")
            appendLine("Output MUST be a valid JSON array of objects with no Markdown backticks or commentary.")
            appendLine("Each JSON object MUST have:")
            appendLine("- `coordinates`: [ymin, xmin, ymax, xmax] as 4 integers from 0 to 1000 normalized to the image dimensions.")
            appendLine("- `original_text`: the raw detected text in $sourceLangName.")
            appendLine("- `translated_text`: the natural $targetLangName translation.")
            appendLine("- If the detected text is a pirate watermark (like 'colamanga', 'rawkuma') or non-story background noise, set `translated_text` strictly to '$IGNORE_TAG'.")
        }

        for (apiKey in activeKeys) {
            try {
                val model = GenerativeModel(
                    modelName = modelName.ifBlank { "gemini-2.0-flash" },
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = temp
                        maxOutputTokens = maxOutputToken
                        responseMimeType = "application/json"
                    },
                    safetySettings = listOf(
                        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
                    ),
                )

                val response = model.generateContent(
                    content {
                        image(pageBitmap)
                        text(promptText)
                    },
                )

                val responseText = response.text?.trim().orEmpty()
                if (responseText.isBlank()) continue

                val blocks = parseVisionJsonResponse(responseText, targetPageTranslation.imgWidth, targetPageTranslation.imgHeight)
                if (blocks.isNotEmpty()) {
                    return blocks
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Gemini Vision page translation failed with key" }
                lastException = e
            }
        }

        if (lastException != null) {
            throw lastException
        }
        return emptyList()
    }

    private fun parseVisionJsonResponse(jsonString: String, imgWidth: Float, imgHeight: Float): List<TranslationBlock> {
        val sanitized = jsonString
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonArray = try {
            if (sanitized.startsWith("[")) {
                JSONArray(sanitized)
            } else if (sanitized.startsWith("{")) {
                val obj = JSONObject(sanitized)
                obj.optJSONArray("translations") ?: obj.optJSONArray("blocks") ?: JSONArray()
            } else {
                JSONArray()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse Gemini Vision JSON response: $sanitized" }
            return emptyList()
        }

        val result = mutableListOf<TranslationBlock>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val translatedText = item.optString("translated_text").trim()
            val originalText = item.optString("original_text").trim()

            if (translatedText.isBlank() || translatedText.contains(IGNORE_TAG, ignoreCase = true)) {
                continue
            }

            val coords = item.optJSONArray("coordinates")
            if (coords != null && coords.length() == 4) {
                val ymin = coords.optDouble(0, 0.0).toFloat().coerceIn(0f, 1000f)
                val xmin = coords.optDouble(1, 0.0).toFloat().coerceIn(0f, 1000f)
                val ymax = coords.optDouble(2, 1000.0).toFloat().coerceIn(0f, 1000f)
                val xmax = coords.optDouble(3, 1000.0).toFloat().coerceIn(0f, 1000f)

                val x = (xmin / 1000f) * imgWidth
                val y = (ymin / 1000f) * imgHeight
                val width = kotlin.math.max(((xmax - xmin) / 1000f) * imgWidth, 10f)
                val height = kotlin.math.max(((ymax - ymin) / 1000f) * imgHeight, 10f)

                val block = TranslationBlock(
                    text = originalText.ifBlank { translatedText },
                    width = width,
                    height = height,
                    x = x,
                    y = y,
                    symWidth = width / kotlin.math.max(translatedText.length, 1),
                    symHeight = height / kotlin.math.max(translatedText.length, 1),
                    angle = 0f,
                    isBubble = true,
                ).apply {
                    translation = translatedText
                }
                result.add(block)
            }
        }
        return result
    }

    override fun close() {
        // No native allocations to release
    }
}
