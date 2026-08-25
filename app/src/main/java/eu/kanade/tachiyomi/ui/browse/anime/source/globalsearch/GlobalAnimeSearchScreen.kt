package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            GlobalAnimeSearchScreenModel(
                initialQuery = searchQuery,
            )
        }
        val state by screenModel.state.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            GlobalAnimeSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                getAnime = { screenModel.getAnime(it) },
                onClickSource = {
                    navigator.push(BrowseAnimeSourceScreen(it.id, state.searchQuery ?: ""))
                },
                onClickItem = { anime ->
                    navigator.push(AnimeScreen(anime.id, true))
                },
                onLongClickItem = { anime ->
                    navigator.push(AnimeScreen(anime.id, true))
                },
                onToggleClean = screenModel::toggleSearchClean,
                onToggleFormat = screenModel::toggleSearchFormat,
                onToggleFuzzy = screenModel::toggleSearchFuzzy,
            )
        }
    }
}
