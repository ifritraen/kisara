package eu.kanade.translation.translator

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage

class MLKitTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(fromLang.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.ENGLISH)
            .build(),
    )

    private var conditions = DownloadConditions.Builder().build()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        onProgress: suspend (translatedBlocks: Int, totalBlocks: Int) -> Unit,
    ) {
        Tasks.await(translator.downloadModelIfNeeded(conditions))
        val totalBlocks = pages.values.sumOf { it.blocks.size }
        if (totalBlocks == 0) return
        var completed = 0
        pages.mapValues { (_, v) ->
            v.blocks.map { b ->
                b.translation = b.text.split("\n").mapNotNull {
                    Tasks.await(translator.translate(it)).takeIf { it.isNotEmpty() }
                }.joinToString("\n")
                completed++
                onProgress(completed, totalBlocks)
            }
        }
    }

    override fun close() {
        translator.close()
    }
}
