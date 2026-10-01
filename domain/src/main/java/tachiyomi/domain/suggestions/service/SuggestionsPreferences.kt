package tachiyomi.domain.suggestions.service

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

// KMK -->
class SuggestionsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    enum class NsfwSuggestionMode {
        SAFE_ONLY,
        NSFW_ONLY,
        BALANCED,
    }

    fun isSuggestionsEnabled() = preferenceStore.getBoolean("suggestions_enabled_key", true)
    fun maxTagsToMatch() = preferenceStore.getInt("suggestions_max_tags_to_match_key", 10)
    fun maxSourcesToFetch() = preferenceStore.getInt("suggestions_max_sources_to_fetch_key", 5)
    fun suggestionsLoggingEnabled() = preferenceStore.getBoolean("suggestions_logging_enabled_key", true)
    fun maxSuggestionsToDisplay() = preferenceStore.getInt("suggestions_max_to_display_key", 100)
    fun suggestionsInterval() = preferenceStore.getInt("suggestions_interval_hours_key", 24)
    fun lastSuggestionsFetchTime() = preferenceStore.getLong("suggestions_last_fetch_timestamp_key", 0L)

    fun nsfwSuggestionMode() = preferenceStore.getEnum("suggestions_nsfw_mode_key", NsfwSuggestionMode.BALANCED)
    fun manuallySafeSources() = preferenceStore.getStringSet("suggestions_manual_safe_sources_key", emptySet())
    fun aiSynopsisTaggingEnabled() = preferenceStore.getBoolean("suggestions_ai_synopsis_tagging_key", true)
}
// KMK <--
