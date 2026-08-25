package eu.kanade.presentation.entries.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

@Immutable
data class AuroraEntryTranslationState(
    val title: String? = null,
    val description: String? = null,
    val titleTranslated: Boolean = false,
    val descriptionTranslated: Boolean = false,
)

@Composable
fun rememberAuroraEntryTranslation(
    title: String?,
    description: String?,
    sourceLanguage: String?,
    enabled: Boolean,
    allowedSourceFamilies: Set<String>,
): AuroraEntryTranslationState {
    return remember(title, description, sourceLanguage, enabled, allowedSourceFamilies) {
        AuroraEntryTranslationState(
            title = title,
            description = description,
            titleTranslated = false,
            descriptionTranslated = false,
        )
    }
}
