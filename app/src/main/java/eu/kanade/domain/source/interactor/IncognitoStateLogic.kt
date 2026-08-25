package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences.IncognitoPolicy

object IncognitoStateLogic {
    fun resolve(
        globalIncognito: Boolean,
        policy: IncognitoPolicy,
        isNsfw: Boolean,
        inExtensionSet: Boolean,
    ): Boolean {
        if (globalIncognito) return true
        return when (policy) {
            IncognitoPolicy.OFF -> false
            IncognitoPolicy.NSFW_ONLY -> isNsfw || inExtensionSet
            IncognitoPolicy.ALL -> inExtensionSet
        }
    }
}
