package eu.kanade.tachiyomi.ui.suggestions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.BrowseSourceListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun suggestionsTab(
    screenModel: SuggestionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = KMR.strings.action_suggestions,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = { screenModel.triggerRefresh(context) },
            ),
        ),
        content = { paddingValues, _ ->
            var isRefreshing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            PullRefresh(
                refreshing = isRefreshing,
                enabled = true,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        screenModel.triggerRefresh(context)
                        isRefreshing = false
                    }
                },
            ) {
                if (state.isLoading) {
                    LoadingScreen(modifier = Modifier.padding(paddingValues))
                } else if (state.suggestions.isEmpty()) {
                    EmptyScreen(
                        modifier = Modifier.padding(paddingValues),
                        message = stringResource(KMR.strings.pref_suggestions_summary) + "\n\nPull down or tap Refresh to search sources.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                        ),
                    ) {
                        items(
                            items = state.suggestions,
                            key = { "suggestion-${it.manga.id}" },
                        ) { suggestion ->
                            BrowseSourceListItem(
                                manga = suggestion.manga,
                                onClick = { navigator.push(MangaScreen(suggestion.manga.id)) },
                                onLongClick = { /* Do nothing */ },
                                isSelected = false,
                                metadata = null,
                            )
                        }
                    }
                }
            }
        },
    )
}
