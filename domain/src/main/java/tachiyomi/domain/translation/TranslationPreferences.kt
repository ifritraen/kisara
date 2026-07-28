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
    fun translationLoggingEnabled() = preferenceStore.getBoolean("translation_logging_enabled", true)
    fun hasAutoDownloadedBubbleModel() = preferenceStore.getBoolean("has_auto_downloaded_bubble_model", false)
    fun maxLettersPerLine() = preferenceStore.getInt("translation_max_letters_per_line", 15)
    fun fullBoundingBoxCover() = preferenceStore.getBoolean("translation_full_bounding_box_cover", false)
    fun paddingTextHorizontal() = preferenceStore.getInt("translation_padding_text_horizontal", 2)
    fun paddingTextVertical() = preferenceStore.getInt("translation_padding_text_vertical", 1)
    fun showWholeWordEvenIfOutside() = preferenceStore.getBoolean("translation_show_whole_word_even_if_outside", false)
    // KMK <--

    // Colorizer settings
    fun useColorizer() = preferenceStore.getBoolean("use_colorizer", false)
    fun colorizerKaggleUsername() = preferenceStore.getString("colorizer_kaggle_username", "")
    fun colorizerKaggleApiKey() = preferenceStore.getString("colorizer_kaggle_api_key", "")
    fun colorizerNgrokAuthToken() = preferenceStore.getString("colorizer_ngrok_auth_token", "")
    fun colorizerKaggleKernelSlug() = preferenceStore.getString("colorizer_kaggle_kernel_slug", "binitdox/manga-colorizer")
    fun autoColorizeAfterDownload() = preferenceStore.getBoolean("auto_colorize_after_download", false)
}
