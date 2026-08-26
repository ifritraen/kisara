package eu.kanade.tachiyomi.util.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object DeviceTierManager {
    enum class Tier { LOW, MID, HIGH }

    fun getTier(context: Context): Tier {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val performanceClass = Build.VERSION.MEDIA_PERFORMANCE_CLASS
            if (performanceClass >= Build.VERSION_CODES.TIRAMISU) {
                return Tier.HIGH
            } else if (performanceClass >= Build.VERSION_CODES.S) {
                return Tier.MID
            }
        }

        // Fallback based on total RAM
        val activityManager = context.getSystemService<ActivityManager>()
        if (activityManager != null) {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024L * 1024L * 1024L)
            return when {
                totalRamGb >= 8 -> Tier.HIGH
                totalRamGb >= 6 -> Tier.MID
                else -> Tier.LOW
            }
        }

        return Tier.LOW
    }
}
