// KMK -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsAiToolsScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = KMR.strings.pref_category_ai_tools

    @Composable
    override fun getPreferences(): List<Preference> {
        // Delegate to the comprehensive AI & Translation preferences engine
        return SettingsTranslationScreen.getPreferences()
    }
}
// KMK <--
