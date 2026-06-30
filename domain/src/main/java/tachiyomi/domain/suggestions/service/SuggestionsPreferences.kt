package tachiyomi.domain.suggestions.service

import tachiyomi.core.common.preference.PreferenceStore

class SuggestionsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun isSuggestionsEnabled() = preferenceStore.getBoolean("suggestions_enabled_key", true)
}
