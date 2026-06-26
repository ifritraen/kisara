package eu.kanade.translation.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TranslationReport {
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String, // "INFO", "WARNING", "ERROR"
        val component: String, // "MangaOCR", "BubbleDetector", "LLMTranslator", "MLKitOCR", etc.
        val message: String,
        val exceptionTrace: String? = null,
    )

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(level: String, component: String, message: String, exception: Throwable? = null) {
        val entry = LogEntry(
            level = level,
            component = component,
            message = message,
            exceptionTrace = exception?.stackTraceToString(),
        )
        _logs.update { it + entry }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
