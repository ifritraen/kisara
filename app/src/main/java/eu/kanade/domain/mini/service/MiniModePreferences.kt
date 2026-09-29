package eu.kanade.domain.mini.service

import tachiyomi.core.common.preference.PreferenceStore

// KMK -->
/**
 * Preferences for Mini Mode slot assignments.
 * Each slot (1..10) holds a source ID (-1L = empty).
 */
class MiniModePreferences(
    private val preferenceStore: PreferenceStore,
) {

    private fun slotKey(slot: Int) = "mini_mode_slot_$slot"

    fun getMiniSlotSource(slot: Int): Long =
        preferenceStore.getLong(slotKey(slot), -1L).get()

    fun setMiniSlotSource(slot: Int, sourceId: Long) {
        preferenceStore.getLong(slotKey(slot), -1L).set(sourceId)
    }

    fun clearMiniSlot(slot: Int) = setMiniSlotSource(slot, -1L)

    fun findSlotForSource(sourceId: Long): Int? =
        (1..SLOT_COUNT).firstOrNull { getMiniSlotSource(it) == sourceId }

    fun findFirstEmptySlot(): Int? =
        (1..SLOT_COUNT).firstOrNull { getMiniSlotSource(it) == -1L }

    fun getAllAssignedSlots(): Map<Int, Long> =
        (1..SLOT_COUNT)
            .mapNotNull { slot ->
                val id = getMiniSlotSource(slot)
                if (id != -1L) slot to id else null
            }
            .toMap()

    companion object {
        const val SLOT_COUNT = 10
    }
}
// KMK <--
