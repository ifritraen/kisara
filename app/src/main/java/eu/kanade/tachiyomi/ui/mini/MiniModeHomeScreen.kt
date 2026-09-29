package eu.kanade.tachiyomi.ui.mini

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
data class MiniModeHomeScreen(
    val sourceId: Long,
    val slotIndex: Int,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val sourceName = remember(sourceId) {
            try {
                Injekt.get<SourceManager>().get(sourceId)?.name
                    ?: Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>().get(sourceId)?.name
                    ?: Injekt.get<tachiyomi.domain.source.novel.service.NovelSourceManager>().get(sourceId)?.name
                    ?: "Mini"
            } catch (_: Throwable) {
                "Mini"
            }
        }
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            topBar = {
                if (selectedTab != 0) {
                    TopAppBar(
                        title = {
                            Text(
                                when (selectedTab) {
                                    1 -> "$sourceName Library"
                                    2 -> "$sourceName History"
                                    else -> sourceName
                                },
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Close",
                                )
                            }
                        },
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = "Browse") },
                        label = { Text("Browse") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = "Library") },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                        label = { Text("History") },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (selectedTab != 0) innerPadding.calculateTopPadding() else 0.dp,
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
            ) {
                when (selectedTab) {
                    0 -> {
                        val browseScreen = remember(sourceId) {
                            BrowseSourceScreen(sourceId = sourceId, listingQuery = null)
                        }
                        browseScreen.Content()
                    }
                    1 -> MiniLibraryTab(sourceId = sourceId, navigator = navigator)
                    2 -> MiniHistoryTab(sourceId = sourceId, navigator = navigator)
                }
            }
        }
    }
}
// KMK <--

