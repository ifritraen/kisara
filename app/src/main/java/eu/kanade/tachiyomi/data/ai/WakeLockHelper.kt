package eu.kanade.tachiyomi.data.ai

import android.content.Context
import android.os.PowerManager

object WakeLockHelper {
    private const val TAG = "Kisara::ColorizerWakeLock"
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = 30 * 60 * 1000L) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
                AppLogger.info("WakeLock acquired (${timeoutMs / 1000}s timeout) to prevent CPU sleep during colorization.")
            }
        } catch (e: Exception) {
            AppLogger.warn("Failed to acquire WakeLock: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.info("WakeLock released.")
            }
        } catch (e: Exception) {
            AppLogger.warn("Failed to release WakeLock: ${e.message}")
        }
    }
}