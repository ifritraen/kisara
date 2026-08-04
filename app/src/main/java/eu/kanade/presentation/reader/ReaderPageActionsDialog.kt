package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.reader.settings.ColorFilterPage
import eu.kanade.presentation.reader.settings.GeneralPage
import eu.kanade.presentation.reader.settings.ReadingModePage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: (useExtraPage: Boolean) -> Unit,
    onShare: (copyToClipboard: Boolean, useExtraPage: Boolean) -> Unit,
    onSave: (useExtraPage: Boolean) -> Unit,
    onShareCombined: (copyToClipboard: Boolean) -> Unit,
    onSaveCombined: () -> Unit,
    hasExtraPage: Boolean,

    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    pageBookmarked: Boolean = false,
    onTogglePageBookmarked: () -> Unit = {},
    isAutoScroll: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    onClickRetryAll: () -> Unit,
    onClickBoostPage: () -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,

    showTranslationEnabled: Boolean,
    onToggleShowTranslation: () -> Unit,
    onStartTranslationEditMode: () -> Unit,
    onClickColorFilter: (() -> Unit)? = null,
    screenModel: ReaderSettingsScreenModel? = null,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    var useExtraPage by remember { mutableStateOf(false) }

    val tabTitles = persistentListOf(
        stringResource(MR.strings.action_menu),
        stringResource(MR.strings.pref_category_reading_mode),
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.custom_filter),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()

    BoxWithConstraints {
        TabbedDialog(
            modifier = Modifier.heightIn(max = maxHeight * 0.85f),
            onDismissRequest = onDismissRequest,
            tabTitles = tabTitles,
            pagerState = pagerState,
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Row 1: Cover, Copy, Share, Save (4 buttons)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (hasExtraPage) {
                                        SYMR.strings.action_set_first_page_cover
                                    } else {
                                        MR.strings.set_as_cover
                                    },
                                ),
                                icon = Icons.Outlined.Photo,
                                onClick = { showSetCoverDialog = true },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (hasExtraPage) {
                                        KMR.strings.action_copy_to_clipboard_first_page
                                    } else {
                                        MR.strings.action_copy_to_clipboard
                                    },
                                ),
                                icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    onShare(true, false)
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (hasExtraPage) {
                                        SYMR.strings.action_share_first_page
                                    } else {
                                        MR.strings.action_share
                                    },
                                ),
                                icon = Icons.Outlined.Share,
                                onClick = {
                                    onShare(false, false)
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (hasExtraPage) {
                                        SYMR.strings.action_save_first_page
                                    } else {
                                        MR.strings.action_save
                                    },
                                ),
                                icon = Icons.Outlined.Save,
                                onClick = {
                                    onSave(false)
                                    onDismissRequest()
                                },
                            )
                        }
                        if (hasExtraPage) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(SYMR.strings.action_set_second_page_cover),
                                    icon = Icons.Outlined.Photo,
                                    onClick = {
                                        showSetCoverDialog = true
                                    },
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(KMR.strings.action_copy_to_clipboard_second_page),
                                    icon = Icons.Outlined.ContentCopy,
                                    onClick = {
                                        onShare(true, true)
                                        onDismissRequest()
                                    },
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(SYMR.strings.action_share_second_page),
                                    icon = Icons.Outlined.Share,
                                    onClick = {
                                        onShare(false, true)
                                        onDismissRequest()
                                    },
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(SYMR.strings.action_save_second_page),
                                    icon = Icons.Outlined.Save,
                                    onClick = {
                                        onSave(true)
                                        onDismissRequest()
                                    },
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(KMR.strings.action_copy_to_clipboard_combined_page),
                                    icon = Icons.Outlined.ContentCopy,
                                    onClick = {
                                        onShareCombined(true)
                                        onDismissRequest()
                                    },
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(SYMR.strings.action_share_combined_page),
                                    icon = Icons.Outlined.Share,
                                    onClick = {
                                        onShareCombined(false)
                                        onDismissRequest()
                                    },
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(SYMR.strings.action_save_combined_page),
                                    icon = Icons.Outlined.Save,
                                    onClick = {
                                        onSaveCombined()
                                        onDismissRequest()
                                    },
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Row 2: Bookmark Chapter, Bookmark Page, Autoscroll, Retry All (4 buttons)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (bookmarked) {
                                        MR.strings.action_remove_bookmark
                                    } else {
                                        MR.strings.action_bookmark
                                    },
                                ),
                                icon = if (bookmarked) {
                                    Icons.Outlined.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                onClick = {
                                    onToggleBookmarked()
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    if (pageBookmarked) {
                                        KMR.strings.action_remove_page_bookmark
                                    } else {
                                        KMR.strings.action_bookmark_page
                                    },
                                ),
                                icon = if (pageBookmarked) {
                                    Icons.Outlined.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                onClick = {
                                    onTogglePageBookmarked()
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(SYMR.strings.eh_autoscroll),
                                icon = if (isAutoScroll) {
                                    Icons.Outlined.Pause
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                onClick = {
                                    onToggleAutoscroll(!isAutoScroll)
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(SYMR.strings.eh_retry_all),
                                icon = Icons.Outlined.Refresh,
                                onClick = {
                                    onClickRetryAll()
                                    onDismissRequest()
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Row 3: Boost Page, Show/Hide Translation, Edit Translation, Color Filter (4 buttons)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = stringResource(SYMR.strings.eh_boost_page),
                                icon = Icons.Outlined.Bolt,
                                onClick = {
                                    onClickBoostPage()
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = if (showTranslationEnabled) "Hide Translation" else "Show Translation",
                                icon = Icons.Outlined.Translate,
                                onClick = {
                                    onToggleShowTranslation()
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = "Edit Translation",
                                icon = Icons.Outlined.Edit,
                                onClick = {
                                    onStartTranslationEditMode()
                                    onDismissRequest()
                                },
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                title = "Color Filter",
                                icon = Icons.Outlined.ColorLens,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(3) }
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        var sliderValue by remember(autoScrollFrequency) {
                            mutableStateOf(autoScrollFrequency.toFloatOrNull() ?: 3.0f)
                        }
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = stringResource(SYMR.strings.eh_autoscroll) + ": ${String.format(java.util.Locale.US, "%.1f", sliderValue)}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Slider(
                                value = sliderValue,
                                valueRange = 0.5f..60.0f,
                                steps = 118,
                                onValueChange = {
                                    sliderValue = it
                                    onSetAutoScrollFrequency(String.format(java.util.Locale.US, "%.1f", it))
                                },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                1 -> {
                    if (screenModel != null) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = TabbedDialogPaddings.Vertical)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ReadingModePage(screenModel)
                        }
                    }
                }

                2 -> {
                    if (screenModel != null) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = TabbedDialogPaddings.Vertical)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            GeneralPage(screenModel)
                        }
                    }
                }

                3 -> {
                    if (screenModel != null) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = TabbedDialogPaddings.Vertical)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ColorFilterPage(screenModel)
                        }
                    }
                }
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover(useExtraPage)
                showSetCoverDialog = false
                useExtraPage = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
