package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun BaseSourceItem(
    source: Source,
    modifier: Modifier = Modifier,
    showLanguageInContent: Boolean = true,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: @Composable RowScope.(Source) -> Unit = defaultIcon,
    action: @Composable RowScope.(Source) -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable RowScope.(Source, String?, /* KMK --> */ String /* KMK <-- */) -> Unit = defaultContent,
) {
    val sourceLangString = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current).takeIf {
        showLanguageInContent
    }
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = {
            if (dragHandle != null) {
                dragHandle()
                Spacer(modifier = Modifier.width(8.dp))
            }
            icon.invoke(this, source)
        },
        action = { action.invoke(this, source) },
        content = { content.invoke(this, source, sourceLangString, /* KMK --> */ source.lang /* KMK <-- */) },
    )
}

private val defaultIcon: @Composable RowScope.(Source) -> Unit = { source ->
    SourceIcon(source = source)
}

private val defaultContent: @Composable RowScope.(
    Source,
    String?,
    // KMK -->
    String,
    // KMK <--
) -> Unit = { source, sourceLangString, /* KMK --> */ lang /* KMK <-- */ ->
    Column(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium)
            .weight(1f),
    ) {
        Text(
            text = source.name +
                // KMK -->
                (
                    source.installedExtension?.let { extension ->
                        " (${extension.name})".takeIf { extension.name != source.name }
                    } ?: ""
                    ),
            // KMK <--
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // KMK -->
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            // KMK <--
            if (sourceLangString != null) {
                Text(
                    text = /* KMK --> */ FlagEmoji.getEmojiLangFlag(lang) + " " + /* KMK <-- */
                        sourceLangString,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // KMK -->
            if (source.installedExtension?.isNsfw == true) {
                Text(
                    text = stringResource(MR.strings.ext_nsfw_short).uppercase(),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val repoName = source.installedExtension?.storeName ?: run {
                val jarSource = eu.kanade.tachiyomi.extension.JarExtensionManager.sources.value
                    .find { it.id == source.id }
                jarSource?.repoName ?: if (jarSource != null) "Local" else null
            }
            if (repoName != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = repoName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // KMK <--
    }
}
