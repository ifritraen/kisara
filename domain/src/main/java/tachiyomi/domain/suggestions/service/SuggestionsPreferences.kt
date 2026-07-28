package tachiyomi.domain.suggestions.service

import tachiyomi.core.common.preference.PreferenceStore

class SuggestionsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun isSuggestionsEnabled() = preferenceStore.getBoolean("suggestions_enabled_key", true)
    fun maxTagsToMatch() = preferenceStore.getInt("suggestions_max_tags_to_match_key", 10)
    fun maxSourcesToFetch() = preferenceStore.getInt("suggestions_max_sources_to_fetch_key", 5)
    fun suggestionsLoggingEnabled() = preferenceStore.getBoolean("suggestions_logging_enabled_key", true)
    fun maxSuggestionsToDisplay() = preferenceStore.getInt("suggestions_max_to_display_key", 100)
}
