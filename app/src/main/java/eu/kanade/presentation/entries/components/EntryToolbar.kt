package eu.kanade.presentation.entries.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun EntryToolbar(
    title: String,
    hasFilters: Boolean,
    navigateUp: () -> Unit,
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: () -> Unit,
    onClickMigrate: (() -> Unit)?,
    onClickSettings: (() -> Unit)? = null,
    onClickOmniBuilder: (() -> Unit)? = null,
    onToggleAutoJumpToNext: (() -> Unit)? = null,
    autoJumpToNextLabel: String? = null,
    changeAnimeSkipIntro: (() -> Unit)? = null,
    actionModeCounter: Int,
    onCancelActionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float,
    onClickEditInfo: (() -> Unit)? = null,
    isManga: Boolean = false,
    overflowActions: ImmutableList<AppBar.OverflowAction> = persistentListOf(),
    modifier: Modifier = Modifier,
) {
    val isActionMode = actionModeCounter > 0
    AppBar(
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(
                    title = title,
                    modifier = Modifier.alpha(titleAlphaProvider()),
                )
            }
        },
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme
            .surfaceColorAtElevation(3.dp)
            .copy(alpha = backgroundAlphaProvider()),
        navigateUp = navigateUp,
        actions = {
            var downloadExpanded by remember { mutableStateOf(false) }
            if (isActionMode) {
                AppBarActions(
                    persistentListOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_select_all),
                            icon = Icons.Outlined.SelectAll,
                            onClick = onSelectAll,
                        ),
                        AppBar.Action(
                            title = stringResource(MR.strings.action_select_inverse),
                            icon = Icons.Outlined.FlipToBack,
                            onClick = onInvertSelection,
                        ),
                    ),
                )
            } else {
                val actions = persistentListOf<AppBar.AppBarAction>().builder().apply {
                    add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = Icons.Outlined.FilterList,
                            iconTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                            onClick = onClickFilter,
                        ),
                    )
                    if (onClickDownload != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.manga_download),
                                icon = Icons.Outlined.Download,
                                onClick = { downloadExpanded = !downloadExpanded },
                            ),
                        )
                    }
                    if (onClickShare != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = onClickShare,
                            ),
                        )
                    }
                    if (onClickRefresh != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_webview_refresh),
                                onClick = onClickRefresh,
                            ),
                        )
                    }
                    if (onClickEditCategory != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_edit_categories),
                                onClick = onClickEditCategory,
                            ),
                        )
                    }
                    if (onClickMigrate != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_migrate),
                                onClick = onClickMigrate,
                            ),
                        )
                    }
                    if (onClickEditInfo != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.action_edit_info),
                                onClick = onClickEditInfo,
                            ),
                        )
                    }
                    if (onToggleAutoJumpToNext != null && autoJumpToNextLabel != null) {
                        add(
                            AppBar.OverflowAction(
                                title = autoJumpToNextLabel,
                                onClick = onToggleAutoJumpToNext,
                            ),
                        )
                    }
                    if (changeAnimeSkipIntro != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.pref_enable_auto_skip_intro),
                                onClick = changeAnimeSkipIntro,
                            ),
                        )
                    }
                    if (onClickOmniBuilder != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.pref_source_omni_source),
                                onClick = onClickOmniBuilder,
                            ),
                        )
                    }
                    if (onClickSettings != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_settings),
                                icon = Icons.Outlined.Settings,
                                onClick = onClickSettings,
                            ),
                        )
                    }
                    addAll(overflowActions)
                }.build()
                AppBarActions(actions)
                if (onClickDownload != null) {
                    DownloadDropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false },
                        onDownloadClicked = onClickDownload,
                    )
                }
            }
        },
    )
}
