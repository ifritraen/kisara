package eu.kanade.presentation.more.settings.screen.player.custombutton.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonScreenState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun CustomButtonScreen(
    state: CustomButtonScreenState.Success,
    onClickFAQ: () -> Unit,
    onClickCreate: () -> Unit,
    onClickPrimary: (CustomButton) -> Unit,
    onClickEdit: (CustomButton) -> Unit,
    onClickDelete: (CustomButton) -> Unit,
    onChangeOrder: (CustomButton, Int) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    tachiyomi.presentation.core.components.material.Scaffold(
        topBar = {
            AppBar(
                title = stringResource(KMR.strings.pref_player_custom_button_header),
                navigateUp = navigateUp,
                actions = {
                    IconButton(onClick = onClickFAQ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(KMR.strings.pref_player_custom_button_guide),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onClickCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(MR.strings.action_add)) },
                expanded = lazyListState.firstVisibleItemIndex == 0,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = KMR.strings.pref_player_custom_button_empty,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            CustomButtonContent(
                customButtons = state.customButtons,
                lazyListState = lazyListState,
                paddingValues = paddingValues,
                onClickPrimary = onClickPrimary,
                onClickEdit = onClickEdit,
                onClickDelete = onClickDelete,
                onChangeOrder = onChangeOrder,
            )
        }
    }
}

@Composable
private fun CustomButtonContent(
    customButtons: List<CustomButton>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickPrimary: (CustomButton) -> Unit,
    onClickEdit: (CustomButton) -> Unit,
    onClickDelete: (CustomButton) -> Unit,
    onChangeOrder: (CustomButton, Int) -> Unit,
) {
    val customButtonsState = remember { customButtons.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = customButtonsState.removeAt(from.index)
        customButtonsState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(customButtons) {
        if (!reorderableState.isAnyItemDragging) {
            customButtonsState.clear()
            customButtonsState.addAll(customButtons)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues +
            topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = customButtonsState,
            key = { customButton -> customButton.key },
        ) { customButton ->
            ReorderableItem(reorderableState, customButton.key) {
                CustomButtonListItem(
                    modifier = Modifier.animateItem(),
                    customButton = customButton,
                    isFavorite = customButton.isFavorite,
                    onTogglePrimary = { onClickPrimary(customButton) },
                    onEdit = { onClickEdit(customButton) },
                    onDelete = { onClickDelete(customButton) },
                )
            }
        }
    }
}

private val CustomButton.key inline get() = "custombutton-$id"
