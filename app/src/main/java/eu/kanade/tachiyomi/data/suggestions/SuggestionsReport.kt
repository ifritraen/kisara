package eu.kanade.tachiyomi.data.suggestions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.domain.suggestions.service.SuggestionsPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SuggestionsReport {
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String, // "INFO", "WARNING", "ERROR"
        val message: String,
        val exceptionTrace: String? = null,
    )

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(level: String, message: String, exception: Throwable? = null) {
        val prefs = try {
            Injekt.get<SuggestionsPreferences>()
        } catch (e: Exception) {
            null
        }
        if (prefs != null && !prefs.suggestionsLoggingEnabled().get()) {
            return
        }
        val entry = LogEntry(
            level = level,
            message = message,
            exceptionTrace = exception?.stackTraceToString(),
        )
        _logs.update { (it + entry).takeLast(1000) }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
