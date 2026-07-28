package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.bulk.bulkSearchTab
import eu.kanade.tachiyomi.ui.browse.duplicate.duplicateSourceTab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {
    private fun readResolve(): Any = BrowseTab

    var currentPageIndex by mutableStateOf(0)

    val sourcesGlobalSearchEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    var sourcesNsfwOnly by mutableStateOf(false)
    val sourcesNsfwToggleEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val sourcesFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val extensionsSearchEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    var extensionsNsfwOnly by mutableStateOf(false)
    val extensionsNsfwToggleEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val extensionsWebViewRefreshEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val extensionsFilterEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val extensionsReposEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val extensionsInstallJarEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    val migrateHelpEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToTabChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    fun showSource() {
        switchToTabChannel.trySend(0)
    }

    fun showExtension() {
        switchToTabChannel.trySend(1)
    }

    fun showMigration() {
        switchToTabChannel.trySend(2)
    }

    fun showDuplicate() {
        switchToTabChannel.trySend(3)
    }

    fun showBulkSearch() {
        switchToTabChannel.trySend(4)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        // Hoisted for extensions tab's search bar
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsState by extensionsScreenModel.state.collectAsState()

        // KMK -->
        val feedScreenModel = rememberScreenModel { FeedScreenModel() }
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val tabs = persistentListOf(
            sourcesTab(),
            extensionsTab(extensionsScreenModel),
            migrateSourceTab(),
            duplicateSourceTab(),
            bulkSearchTab(),
        )

        val state = rememberPagerState { tabs.size }
        // KMK <--

        LaunchedEffect(state.currentPage) {
            currentPageIndex = state.currentPage
        }

        val scope = rememberCoroutineScope()
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsState()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsState()
        val hazeState = LocalHazeState.current

        var bottomBarVisible by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (delta < -10f) {
                        if (bottomBarVisible) {
                            bottomBarVisible = false
                            scope.launch { HomeScreen.showBottomNav(false) }
                        }
                    } else if (delta > 10f) {
                        if (!bottomBarVisible) {
                            bottomBarVisible = true
                            scope.launch { HomeScreen.showBottomNav(true) }
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier)
                .nestedScroll(nestedScrollConnection),
        ) {
            TabbedScreen(
                titleRes = MR.strings.browse,
                tabs = tabs,
                state = state,
                searchQuery = extensionsState.searchQuery,
                onChangeSearchQuery = extensionsScreenModel::search,
                showAppBar = false,
                showTabs = if (showTopTabBar) {
                    if (hideTopBarOnScroll) bottomBarVisible else true
                } else {
                    false
                },
                feedScreenModel = feedScreenModel,
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            )
        }
        LaunchedEffect(Unit) {
            switchToTabChannel.receiveAsFlow()
                .collectLatest { state.scrollToPage(it) }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true

            // AM (DISCORD) -->
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context, DiscordScreen.BROWSE) }
            }
            // <-- AM (DISCORD)
        }
    }
}
