package eu.kanade.tachiyomi.ui.suggestions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.BrowseSourceListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class SuggestionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { SuggestionsScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.action_suggestions),
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(
                            onClick = { screenModel.triggerRefresh(context) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(MR.strings.action_webview_refresh),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.isLoading) {
                LoadingScreen(modifier = Modifier.padding(paddingValues))
                return@Scaffold
            }

            if (state.suggestions.isEmpty()) {
                val lastError = remember { screenModel.getLastError(context) }
                Column(
                    modifier = Modifier.padding(paddingValues).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyScreen(
                        message = stringResource(KMR.strings.pref_suggestions_summary) + "\n\nTap Refresh above to search sources.",
                    )
                    if (lastError != null) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, lastError)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Error Details"))
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("Share Last Error")
                        }
                    }
                }
                return@Scaffold
            }

            LazyColumn(
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
                        onLongClick = { /* Do nothing or open details */ },
                        isSelected = false,
                        metadata = null,
                    )
                }
            }
        }
    }
}
