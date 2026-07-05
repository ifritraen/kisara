package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "CHINESE")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "ENGLISH")
    fun translationFont() = preferenceStore.getInt("translation_font", 0)

    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-1.5-pro")
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "1")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    fun translationConcurrency() = preferenceStore.getInt("translation_concurrency", 2)
    fun translationEngineApiKeys() = preferenceStore.getStringSet("translation_engine_api_keys", emptySet())

    // KMK --> Optional advanced pipeline
    /** 0 = ML Kit (default), 1 = MangaOCR ONNX, 2 = PaddleOCR ONNX */
    fun ocrEngine() = preferenceStore.getInt("ocr_engine", 0)
    /** When true, runs ONNX bubble detector before OCR to get better bounding boxes */
    fun bubbleDetectionEnabled() = preferenceStore.getBoolean("bubble_detection_enabled", true)
    // KMK <--
}
