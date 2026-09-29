package eu.kanade.tachiyomi.ui.mini

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import eu.kanade.domain.mini.service.MiniModePreferences
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
object MiniModeManager {

    private val prefs: MiniModePreferences get() = Injekt.get()

    /** Slot index -> fully qualified activity class name */
    private val slotClassNames = mapOf(
        1 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot1",
        2 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot2",
        3 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot3",
        4 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot4",
        5 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot5",
        6 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot6",
        7 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot7",
        8 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot8",
        9 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot9",
        10 to "eu.kanade.tachiyomi.ui.mini.MiniModeSlot10",
    )

    /** Slot icon resource IDs (mapped at runtime; uses launcher icon as default) */
    private val slotIconRes = mapOf(
        1 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_aka,
        2 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_ao,
        3 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_midori,
        4 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_ki,
        5 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_murasaki,
        6 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_daidai,
        7 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_momoiro,
        8 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_mizu,
        9 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_kon,
        10 to eu.kanade.tachiyomi.R.mipmap.ic_launcher_mini_sumi,
    )

    /**
     * Assign a source to the next available slot.
     * Enables the slot activity in the launcher.
     * @return assigned slot number, or null if all 10 slots are full.
     */
    fun assignSlot(context: Context, sourceId: Long, sourceName: String): Int? {
        val existing = prefs.findSlotForSource(sourceId)
        if (existing != null) return existing

        val slot = prefs.findFirstEmptySlot() ?: return null
        prefs.setMiniSlotSource(slot, sourceId)
        setSlotEnabled(context, slot, true)
        return slot
    }

    fun assignSlot(context: Context, source: Source): Int? = assignSlot(context, source.id, source.name)

    /**
     * Release a source's slot and hide its launcher icon.
     */
    fun releaseSlot(context: Context, sourceId: Long) {
        val slot = prefs.findSlotForSource(sourceId) ?: return
        setSlotEnabled(context, slot, false)
        prefs.clearMiniSlot(slot)
    }

    fun releaseSlot(context: Context, source: Source) = releaseSlot(context, source.id)

    fun getSlotForSource(sourceId: Long): Int? = prefs.findSlotForSource(sourceId)

    fun isSourceInstalled(sourceId: Long): Boolean = prefs.findSlotForSource(sourceId) != null

    /** Enable or disable a slot's launcher Activity via PackageManager. */
    fun setSlotEnabled(context: Context, slot: Int, enabled: Boolean) {
        val className = slotClassNames[slot] ?: return
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, className),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private val slotClasses = mapOf(
        1 to MiniModeSlot1::class.java,
        2 to MiniModeSlot2::class.java,
        3 to MiniModeSlot3::class.java,
        4 to MiniModeSlot4::class.java,
        5 to MiniModeSlot5::class.java,
        6 to MiniModeSlot6::class.java,
        7 to MiniModeSlot7::class.java,
        8 to MiniModeSlot8::class.java,
        9 to MiniModeSlot9::class.java,
        10 to MiniModeSlot10::class.java,
    )

    /**
     * Offer a pinned home-screen shortcut for this source.
     * Shows the system "Add to Home Screen?" dialog.
     * Can be called unlimited times for any number of sources without consuming any of the 10 app-drawer slots.
     */
    fun offerPinnedShortcut(context: Context, sourceId: Long, sourceName: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return

        val intent = android.content.Intent(context, MiniModeShortcutActivity::class.java).apply {
            action = android.content.Intent.ACTION_MAIN
            putExtra("source_id", sourceId)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "mini_source_$sourceId")
            .setShortLabel(sourceName.take(15))
            .setLongLabel(sourceName)
            .setIcon(IconCompat.createWithResource(context, eu.kanade.tachiyomi.R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun offerPinnedShortcut(context: Context, source: Source) = offerPinnedShortcut(context, source.id, source.name)
}
// KMK <--
