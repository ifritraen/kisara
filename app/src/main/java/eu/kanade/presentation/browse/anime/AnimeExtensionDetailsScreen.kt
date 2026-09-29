package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val repoUrl = remember(state.extension) { state.extension?.repoUrl?.takeIf { it.isNotBlank() } }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (repoUrl != null) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_open_repo),
                                            icon = Icons.AutoMirrored.Outlined.Launch,
                                            onClick = { uriHandler.openUri(repoUrl) },
                                        ),
                                    )
                                }
                                addAll(
                                    listOf(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_enable_all),
                                            onClick = onClickEnableAll,
                                        ),
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_disable_all),
                                            onClick = onClickDisableAll,
                                        ),
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_clear_cookies),
                                            onClick = onClickClearCookies,
                                        ),
                                    ),
                                )
                            }
                            .build(),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val extension = state.extension
        if (extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        ExtensionDetails(
            contentPadding = paddingValues,
            extension = extension,
            sources = state.sources,
            incognitoMode = state.isIncognito,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickUninstall = onClickUninstall,
            onClickSource = onClickSource,
            onClickIncognito = onClickIncognito,
        )
    }
}

@Composable
private fun ExtensionDetails(
    contentPadding: PaddingValues,
    extension: AnimeExtension.Installed,
    sources: ImmutableList<AnimeExtensionSourceItem>,
    incognitoMode: Boolean,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    ScrollbarLazyColumn(contentPadding = contentPadding) {
        item {
            DetailsHeader(
                extension = extension,
                extIncognitoMode = incognitoMode,
                onClickUninstall = onClickUninstall,
                onExtIncognitoChange = onClickIncognito,
            )
        }

        items(
            items = sources,
            key = { "anime-ext-details-${it.source.id}" },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItem(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }
    }
}

@Composable
private fun DetailsHeader(
    extension: AnimeExtension.Installed,
    extIncognitoMode: Boolean,
    onClickUninstall: () -> Unit,
    onExtIncognitoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .padding(top = MaterialTheme.padding.medium, bottom = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val debugInfo = buildString {
                            append("Extension name: ${extension.name} (lang: ${extension.lang}; pkg: ${extension.pkgName})\n")
                            append("Version: ${extension.versionName} (${extension.versionCode})\n")
                            append("Store: ${extension.repoName ?: extension.repoUrl.orEmpty()}\n")
                        }
                        context.copyToClipboard("Anime extension debug information", debugInfo)
                    }
                    .padding(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val iconBitmap = remember(extension.icon) {
                    extension.icon?.toBitmap()?.asImageBitmap()
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                } else {
                    Box(modifier = Modifier.size(96.dp))
                }
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = extension.pkgName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.extraLarge, vertical = MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = extension.versionName,
                    secondaryText = stringResource(MR.strings.ext_info_version),
                )
                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = LocaleHelper.getSourceDisplayName(extension.lang, context),
                    secondaryText = stringResource(MR.strings.ext_info_language),
                )
            }
        }

        OutlinedButton(
            onClick = onClickUninstall,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(MR.strings.ext_uninstall))
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            TextPreferenceWidget(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                widget = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = extIncognitoMode,
                            onCheckedChange = onExtIncognitoChange,
                            modifier = Modifier.padding(start = TrailingWidgetBuffer),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun InfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = primaryText, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SourceSwitchPreference(
    source: AnimeExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    TextPreferenceWidget(
        modifier = modifier,
        title = if (source.labelAsName) {
            source.source.toString()
        } else {
            LocaleHelper.getSourceDisplayName(source.source.lang, context)
        },
        widget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.source is ConfigurableAnimeSource) {
                    IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Switch(
                    checked = source.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = { onClickSource(source.source.id) },
    )
}