package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.collections.immutable.ImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickReorderPin: (List<Source>, Int, Int) -> Unit,
    // KMK -->
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onChangeSearchQuery: (String?) -> Unit,
    onSelectTag: (String?) -> Unit = {},
    onClickManageTags: (Source) -> Unit = {},
    // KMK <--
) {
    // KMK -->
    val lazyListState = rememberLazyListState()

    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) {
        onChangeSearchQuery("")
    }
    // KMK <--

    val items = remember(state.items) { state.items.toMutableStateList() }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromItemKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toItemKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (!fromItemKey.startsWith("source-") || !toItemKey.startsWith("source-")) return@rememberReorderableLazyListState

        val fromId = fromItemKey.removePrefix("source-").toLongOrNull() ?: return@rememberReorderableLazyListState
        val toId = toItemKey.removePrefix("source-").toLongOrNull() ?: return@rememberReorderableLazyListState

        val fromIdx = items.indexOfFirst { it is SourceUiModel.Item && it.source.id == fromId }
        val toIdx = items.indexOfFirst { it is SourceUiModel.Item && it.source.id == toId }

        if (fromIdx != -1 && toIdx != -1) {
            val item = items.removeAt(fromIdx)
            items.add(toIdx, item)

            val pinnedList = items.filterIsInstance<SourceUiModel.Item>()
                .map { it.source }
                .filter { Pin.Pinned in it.pin }
            val newFromIdx = pinnedList.indexOfFirst { it.id == fromId }
            val newToIdx = pinnedList.indexOfFirst { it.id == toId }
            if (newFromIdx != -1 && newToIdx != -1) {
                onClickReorderPin(pinnedList, newFromIdx, newToIdx)
            }
        }
    }

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        // KMK -->
        state.searchQuery.isNullOrEmpty() &&
            state.selectedTag == null &&
            state.isEmpty -> EmptyScreen(
            MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            val topPadding = contentPadding.calculateTopPadding()
            val startPadding = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val endPadding = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            Box(
                modifier = Modifier.padding(top = topPadding, start = startPadding, end = endPadding),
            ) {
                val density = LocalDensity.current
                var searchBoxHeight by remember { mutableStateOf(SOURCE_SEARCH_BOX_HEIGHT) }

                FastScrollLazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = searchBoxHeight) + PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    // KMK <--
                ) {
                    if (items.isEmpty()) {
                        item(key = "source-empty-state") {
                            EmptyScreen(
                                stringResource(MR.strings.no_results_found),
                                modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.padding.medium),
                                scrollable = false,
                            )
                        }
                    } else {
                        items.forEach { model ->
                            when (model) {
                                is SourceUiModel.Header -> {
                                    stickyHeader(
                                        key = "$STICKY_HEADER_KEY_PREFIX-header-${model.hashCode()}",
                                        contentType = "header",
                                    ) {
                                        SourceHeader(
                                            modifier = Modifier
                                                .animateItemFastScroll()
                                                .background(MaterialTheme.colorScheme.background)
                                                .fillMaxWidth(),
                                            language = model.language,
                                            // SY -->
                                            isCategory = model.isCategory,
                                            // SY <--
                                        )
                                    }
                                }
                                is SourceUiModel.Item -> {
                                    val isPinned = Pin.Pinned in model.source.pin
                                    item(
                                        key = "source-${model.source.key()}",
                                        contentType = "item",
                                    ) {
                                        if (isPinned) {
                                            ReorderableItem(reorderableState, key = "source-${model.source.key()}") {
                                                SourceItem(
                                                    modifier = Modifier.animateItemFastScroll(),
                                                    dragHandle = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.DragHandle,
                                                            contentDescription = "Drag to reorder",
                                                            modifier = Modifier
                                                                .padding(horizontal = 4.dp)
                                                                .draggableHandle(),
                                                        )
                                                    },
                                                    source = model.source,
                                                    showLatest = state.showLatest,
                                                    showPin = state.showPin,
                                                    onClickItem = onClickItem,
                                                    onLongClickItem = onLongClickItem,
                                                    onClickPin = onClickPin,
                                                )
                                            }
                                        } else {
                                            SourceItem(
                                                modifier = Modifier.animateItemFastScroll(),
                                                source = model.source,
                                                showLatest = state.showLatest,
                                                showPin = state.showPin,
                                                onClickItem = onClickItem,
                                                onLongClickItem = onLongClickItem,
                                                onClickPin = onClickPin,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // KMK -->
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .align(Alignment.TopCenter),
                ) {
                    AnimatedFloatingSearchBox(
                        listState = lazyListState,
                        searchQuery = state.searchQuery,
                        onChangeSearchQuery = onChangeSearchQuery,
                        placeholderText = stringResource(KMR.strings.action_search_for_source),
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                        onGloballyPositioned = { layoutCoordinates ->
                            searchBoxHeight = with(density) { layoutCoordinates.size.height.toDp() + 2 * MaterialTheme.padding.small + 48.dp }
                        },
                    )
                    TagFilterChipBar(
                        tags = state.allTags.toList(),
                        selectedTag = state.selectedTag,
                        onSelectTag = onSelectTag,
                    )
                }
                // KMK <--
            }
        }
    }
}

@Composable
private fun SourceHeader(
    language: String,
    // SY -->
    isCategory: Boolean,
    // SY <--
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        // SY -->
        text = if (!isCategory) {
            LocaleHelper.getSourceDisplayName(language, context)
        } else {
            language
        },
        // SY <--
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    source: Source,
    // SY -->
    showLatest: Boolean,
    showPin: Boolean,
    // SY <--
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (RowScope.() -> Unit)? = null,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        dragHandle = dragHandle,
        action = {
            if (source.supportsLatest /* SY --> */ && showLatest /* SY <-- */) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            // SY -->
            if (showPin) {
                SourcePinButton(
                    isPinned = Pin.Pinned in source.pin,
                    onClick = { onClickPin(source) },
                )
            }
            // SY <--
        },
    )
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickSetCategories: (() -> Unit)?,
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    onDismiss: () -> Unit,
    // KMK -->
    onClickSettings: (() -> Unit)? = null,
    onClickManageTags: (() -> Unit)? = null,
    onClickSelectMultiple: (() -> Unit)? = null,
    onClickUninstall: (() -> Unit)? = null,
    // KMK <--
    onClickMoveUp: (() -> Unit)? = null,
    onClickMoveDown: (() -> Unit)? = null,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                if (onClickManageTags != null) {
                    Text(
                        text = "Tags",
                        modifier = Modifier
                            .clickable(onClick = onClickManageTags)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                if (onClickSelectMultiple != null) {
                    Text(
                        text = "Select Multiple",
                        modifier = Modifier
                            .clickable(onClick = onClickSelectMultiple)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                val isPinned = Pin.Pinned in source.pin
                val textId = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (isPinned) {
                    if (onClickMoveUp != null) {
                        Text(
                            text = "Move Up",
                            modifier = Modifier
                                .clickable(onClick = onClickMoveUp)
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                    if (onClickMoveDown != null) {
                        Text(
                            text = "Move Down",
                            modifier = Modifier
                                .clickable(onClick = onClickMoveDown)
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                }
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickSetCategories != null) {
                    Text(
                        text = stringResource(MR.strings.categories),
                        modifier = Modifier
                            .clickable(onClick = onClickSetCategories)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(SYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(SYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
                // KMK -->
                if (onClickSettings != null &&
                    source.installedExtension !== null &&
                    source.id !in listOf(LocalSource.ID, EH_SOURCE_ID, EXH_SOURCE_ID)
                ) {
                    Text(
                        text = stringResource(MR.strings.label_extension_info),
                        modifier = Modifier
                            .clickable(onClick = onClickSettings)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }

                if (onClickUninstall != null && source.installedExtension != null) {
                    Text(
                        text = stringResource(MR.strings.ext_uninstall),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable(onClick = onClickUninstall)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // KMK <--
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface SourceUiModel {
    data class Item(val source: Source) : SourceUiModel
    data class Header(val language: String, val isCategory: Boolean) : SourceUiModel
}

// SY -->
@Composable
fun SourceCategoriesDialog(
    source: Source,
    categories: ImmutableList<String>,
    onClickCategories: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val newCategories = remember(source) {
        mutableStateListOf<String>().also { it += source.categories }
    }
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    LabeledCheckbox(
                        label = category,
                        checked = category in newCategories,
                        onCheckedChange = {
                            if (it) {
                                newCategories += category
                            } else {
                                newCategories -= category
                            }
                        },
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onClickCategories(newCategories.toList()) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// SY <--
