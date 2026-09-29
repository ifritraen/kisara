package eu.kanade.tachiyomi.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR, AI_STEP
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
)

object AppLogger {
    private const val TAG = "KisaraColorizer"
    private const val MAX_LOGS = 500

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var logFile: File? = null
    private val fileScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, "colorizer.log")
        log(LogLevel.INFO, "AppLogger initialized. File: ${logFile?.absolutePath}")
    }

    fun log(level: LogLevel, message: String) {
        val timeStr = timeFormat.format(Date())
        val entry = LogEntry(timeStr, level, message)

        // Native logcat
        when (level) {
            LogLevel.INFO, LogLevel.AI_STEP -> Log.i(TAG, "[$timeStr] $message")
            LogLevel.SUCCESS -> Log.i(TAG, "[$timeStr] [SUCCESS] $message")
            LogLevel.WARN -> Log.w(TAG, "[$timeStr] [WARN] $message")
            LogLevel.ERROR -> Log.e(TAG, "[$timeStr] [ERROR] $message")
        }

        // In-memory UI buffer
        val current = _logs.value
        val updated = if (current.size >= MAX_LOGS) {
            current.drop(current.size - MAX_LOGS + 1) + entry
        } else {
            current + entry
        }
        _logs.value = updated

        // File persistence for AI direct inspection via ADB
        logFile?.let { file ->
            fileScope.launch {
                try {
                    FileWriter(file, true).use { writer ->
                        writer.write("[$timeStr] [${level.name}] $message\n")
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun step(msg: String) = log(LogLevel.AI_STEP, msg)
    fun success(msg: String) = log(LogLevel.SUCCESS, msg)
    fun warn(msg: String) = log(LogLevel.WARN, msg)
    fun error(msg: String, error: Throwable? = null) {
        val details = if (error != null) "$msg: ${error.message}\n${Log.getStackTraceString(error)}" else msg
        log(LogLevel.ERROR, details)
    }

    fun clear() {
        _logs.value = emptyList()
        fileScope.launch {
            try {
                logFile?.writeText("")
            } catch (_: Exception) {}
        }
    }
}