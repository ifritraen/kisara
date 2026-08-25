package eu.kanade.tachiyomi.ui.browse.anime.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.AnimeFeedOrderScreen
import eu.kanade.presentation.browse.anime.AnimeFeedScreen
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.TreeMap

@Composable
fun Screen.animeFeedTab(
    screenModel: AnimeFeedScreenModel = rememberScreenModel { AnimeFeedScreenModel() },
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = KMR.strings.feed,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(KMR.strings.feed_add),
                icon = Icons.Outlined.Add,
                onClick = screenModel::openAddSourceDialog,
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.SwapVert,
                onClick = screenModel::toggleReordering,
            ),
        ),
        content = { contentPadding, _ ->
            if (state.isReordering) {
                AnimeFeedOrderScreen(
                    state = state,
                    onClickDelete = { feed -> screenModel.openDeleteDialog(feed) },
                    onChangeOrder = { feed, newIndex -> screenModel.reorderFeed(feed, newIndex) },
                )
            } else {
                AnimeFeedScreen(
                    state = state,
                    contentPadding = contentPadding,
                    onClickSource = { source, item ->
                        navigator.push(BrowseAnimeSourceScreen(source.id, null, item.feed.savedSearch))
                    },
                    onClickAnime = { anime ->
                        navigator.push(AnimeScreen(anime.id, true))
                    },
                    onLongClickAnime = screenModel::onAnimeLongClick,
                    getAnimeState = { anime -> screenModel.getAnime(anime) },
                    onRefresh = screenModel::refresh,
                )
            }

            state.dialog?.let { dialog ->
                when (dialog) {
                    is AnimeFeedScreenModel.Dialog.AddSource -> {
                        FeedAddSourceDialog(
                            sources = dialog.sources,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = { source -> screenModel.onSourceSelected(source) },
                        )
                    }
                    is AnimeFeedScreenModel.Dialog.AddSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.savedSearches,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = { savedSearch -> screenModel.addFeed(dialog.source, savedSearch) },
                        )
                    }
                    is AnimeFeedScreenModel.Dialog.DeleteSource -> {
                        FeedDeleteSourceDialog(
                            source = dialog.source,
                            onDismiss = screenModel::dismissDialog,
                            onConfirm = { screenModel.removeSource(dialog.feed) },
                        )
                    }
                    is AnimeFeedScreenModel.Dialog.ChangeCategory -> {
                        val changeCategoryAnime = dialog.anime
                        ChangeCategoryDialog(
                            initialSelection = dialog.initialSelection,
                            onDismissRequest = screenModel::dismissDialog,
                            onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreen()) },
                            onConfirm = { included, _ ->
                                screenModel.setAnimeCategories(changeCategoryAnime, included)
                            },
                            onDuplicateCheck = {
                                screenModel.dismissDialog()
                                navigator.push(eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen(changeCategoryAnime.id))
                            },
                            onDelete = {
                                screenModel.deleteAnime(changeCategoryAnime)
                            },
                            anime = changeCategoryAnime,
                            onCreateCategory = screenModel::createCategory,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FeedAddSourceDialog(
    sources: List<AnimeCatalogueSource>,
    onDismiss: () -> Unit,
    onAdd: (AnimeCatalogueSource) -> Unit,
) {
    val grouped = remember(sources) {
        TreeMap<String, MutableList<AnimeCatalogueSource>>().apply {
            sources.forEach { source ->
                val langName = LocaleHelper.getLocalizedDisplayName(source.lang)
                getOrPut(langName) { mutableListOf() }.add(source)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.feed_add)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                grouped.forEach { (lang, langSources) ->
                    item(key = "header_$lang") {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    items(langSources, key = { it.id }) { source ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(source) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = LocaleHelper.getLocalizedDisplayName(source.lang),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FeedAddSearchDialog(
    source: AnimeCatalogueSource,
    savedSearches: List<SavedSearch>,
    onDismiss: () -> Unit,
    onAdd: (SavedSearch?) -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }
    val latestLabel = stringResource(KMR.strings.feed_latest)
    val popularLabel = stringResource(KMR.strings.feed_popular)

    val defaultLabel = if (source.supportsLatest) latestLabel else popularLabel
    val options = remember(savedSearches) {
        buildList {
            add(null as SavedSearch?)
            savedSearches.forEach { add(it) }
        }
    }
    val labels = remember(options, defaultLabel) {
        buildList {
            add(defaultLabel)
            savedSearches.forEach { add(it.name) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.name) },
        text = {
            LazyColumn {
                items(labels.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(labels[index], style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selected >= 0) {
                        val savedSearch = options[selected]
                        onAdd(savedSearch)
                    }
                },
                enabled = selected >= 0,
            ) {
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FeedDeleteSourceDialog(
    source: AnimeCatalogueSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.feed_delete_source_title)) },
        text = { Text(stringResource(KMR.strings.feed_delete_source_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
