package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable

interface TextTranslator : Closeable {
    val fromLang: TextRecognizerLanguage
    val toLang: TextTranslatorLanguage
    suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        onProgress: suspend (translatedBlocks: Int, totalBlocks: Int) -> Unit = { _, _ -> },
    )
}

enum class TextTranslators(val label: String) {
    MLKIT("MlKit (On Device)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI (Text)"),
    GEMINI_VISION("Gemini Multimodal Vision [End-to-End]"),
    OPENROUTER("OpenRouter [API KEY]"),
    OPENAI_COMPATIBLE("OpenAI-Compatible (DeepSeek / Qwen / Claude / Custom)"),
    ;

    fun build(pref: TranslationPreferences = Injekt.get(), fromLang: TextRecognizerLanguage = TextRecognizerLanguage.fromPref(pref.translateFromLanguage()), toLang: TextTranslatorLanguage = TextTranslatorLanguage.fromPref(pref.translateToLanguage())): TextTranslator {
        val maxOutputTokens = pref.translationEngineMaxOutputTokens().get().toIntOrNull() ?: 8192
        val temperature = pref.translationEngineTemperature().get().toFloatOrNull() ?: 0.2f
        val modelName = pref.translationEngineModel().get()
        val endpoint = pref.translationEngineEndpoint().get()
        val apiKeysSet = pref.translationEngineApiKeys().get()
        val apiKeys = apiKeysSet.toList().filter { it.isNotBlank() }.ifEmpty { listOf(pref.translationEngineApiKey().get()) }
        return when (this) {
            MLKIT -> MLKitTranslator(fromLang, toLang)
            GOOGLE -> GoogleTranslator(fromLang, toLang)
            GEMINI -> GeminiTranslator(fromLang, toLang, apiKeys, modelName, maxOutputTokens, temperature)
            GEMINI_VISION -> GeminiVisionTranslator(fromLang, toLang, apiKeys, modelName, maxOutputTokens, temperature)
            OPENROUTER -> OpenRouterTranslator(fromLang, toLang, apiKeys, modelName, maxOutputTokens, temperature)
            OPENAI_COMPATIBLE -> OpenAiCompatibleTranslator(fromLang, toLang, apiKeys, endpoint, modelName, maxOutputTokens, temperature)
        }
    }

    companion object {
        fun fromPref(pref: Preference<Int>): TextTranslators {
            var translator = entries.getOrNull(pref.get())
            if (translator == null) {
                pref.set(0)
                return MLKIT
            }
            return translator
        }
    }
}
