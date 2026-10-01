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
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
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
import eu.kanade.domain.ui.model.MediaType
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.anime.bulk.animeBulkSearchTab
import eu.kanade.tachiyomi.ui.browse.anime.duplicate.animeDuplicateTab
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.bulk.bulkSearchTab
import eu.kanade.tachiyomi.ui.browse.duplicate.duplicateSourceTab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.local.browseLocalTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.novel.bulk.novelBulkSearchTab
import eu.kanade.tachiyomi.ui.browse.novel.duplicate.novelDuplicateTab
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionsTab
import eu.kanade.tachiyomi.ui.browse.novel.migration.sources.migrateNovelSourceTab
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.browse.search.searchTab
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
        val uiPreferences = Injekt.get<UiPreferences>()
        val activeMediaType = uiPreferences.activeMediaType().get()
        if (activeMediaType == MediaType.ANIME) {
            navigator.push(eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen(openSearchOnStart = true))
        } else if (activeMediaType == MediaType.NOVEL) {
            navigator.push(eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen(openSearchOnStart = true))
        } else {
            navigator.push(GlobalSearchScreen(openSearchOnStart = true))
        }
    }

    private val switchToTabChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)
    val nextSubTabEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
    val prevSubTabEvent = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showSource() {
        switchToTabChannel.trySend(0)
    }

    fun showExtension() {
        switchToTabChannel.trySend(1)
    }

    fun showLocal() {
        switchToTabChannel.trySend(2)
    }

    fun showMigration() {
        val isManga = Injekt.get<UiPreferences>().activeMediaType().get() == MediaType.MANGA
        switchToTabChannel.trySend(if (isManga) 3 else 2)
    }

    fun showDuplicate() {
        val isManga = Injekt.get<UiPreferences>().activeMediaType().get() == MediaType.MANGA
        switchToTabChannel.trySend(if (isManga) 4 else 3)
    }

    fun showBulkSearch() {
        val isManga = Injekt.get<UiPreferences>().activeMediaType().get() == MediaType.MANGA
        switchToTabChannel.trySend(if (isManga) 5 else 4)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val activeMediaType by uiPreferences.activeMediaType().collectAsStateWithLifecycle()

        // Hoisted for active extensions tab's search bar only
        val (tabs, extensionsSearchQuery, onExtensionsSearchQueryChange) = when (activeMediaType) {
            MediaType.NOVEL -> {
                val novelExtensionsScreenModel = rememberScreenModel { NovelExtensionsScreenModel() }
                val novelExtensionsState by novelExtensionsScreenModel.state.collectAsStateWithLifecycle()
                Triple(
                    persistentListOf(
                        novelSourcesTab(),
                        novelExtensionsTab(novelExtensionsScreenModel),
                        migrateNovelSourceTab(),
                        novelDuplicateTab(),
                        searchTab(MediaType.NOVEL),
                    ),
                    novelExtensionsState.searchQuery,
                    { query: String? -> novelExtensionsScreenModel.search(query) },
                )
            }
            MediaType.ANIME -> {
                val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
                val animeExtensionsState by animeExtensionsScreenModel.state.collectAsStateWithLifecycle()
                Triple(
                    persistentListOf(
                        animeSourcesTab(),
                        animeExtensionsTab(animeExtensionsScreenModel),
                        migrateAnimeSourceTab(),
                        animeDuplicateTab(),
                        searchTab(MediaType.ANIME),
                    ),
                    animeExtensionsState.searchQuery,
                    { query: String? -> animeExtensionsScreenModel.search(query) },
                )
            }
            MediaType.MANGA -> {
                val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
                val extensionsState by extensionsScreenModel.state.collectAsStateWithLifecycle()
                Triple(
                    persistentListOf(
                        sourcesTab(),
                        extensionsTab(extensionsScreenModel),
                        browseLocalTab(),
                        migrateSourceTab(),
                        duplicateSourceTab(),
                        searchTab(MediaType.MANGA),
                    ),
                    extensionsState.searchQuery,
                    { query: String? -> extensionsScreenModel.search(query) },
                )
            }
        }

        val state = rememberPagerState { tabs.size }

        LaunchedEffect(state) {
            snapshotFlow { state.settledPage }.collect {
                currentPageIndex = it
            }
        }

        val scope = rememberCoroutineScope()
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsStateWithLifecycle()
        val hideTopBarOnScroll by uiPreferences.hideTopBarOnScroll().collectAsStateWithLifecycle()
        val showTopTabBar by uiPreferences.showTopTabBar().collectAsStateWithLifecycle()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsStateWithLifecycle()
        val hazeState = LocalHazeState.current

        val bottomBarVisible by HomeScreen.showBottomNavFlow.collectAsStateWithLifecycle()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier),
        ) {
            TabbedScreen(
                titleRes = MR.strings.browse,
                tabs = tabs,
                state = state,
                searchQuery = extensionsSearchQuery,
                onChangeSearchQuery = onExtensionsSearchQueryChange,
                showAppBar = false,
                showTabs = if (showTopTabBar) {
                    if (hideTopBarOnScroll) bottomBarVisible else true
                } else {
                    false
                },
            )
        }
        LaunchedEffect(Unit) {
            launch {
                switchToTabChannel.receiveAsFlow()
                    .collectLatest {
                        if (kotlin.math.abs(it - state.currentPage) <= 1) {
                            state.animateScrollToPage(it)
                        } else {
                            state.scrollToPage(it)
                        }
                    }
            }
            launch {
                nextSubTabEvent.receiveAsFlow()
                    .collectLatest {
                        val totalTabs = tabs.size
                        if (totalTabs > 1) {
                            val next = (state.currentPage + 1).coerceAtMost(totalTabs - 1)
                            state.animateScrollToPage(next)
                        }
                    }
            }
            launch {
                prevSubTabEvent.receiveAsFlow()
                    .collectLatest {
                        val totalTabs = tabs.size
                        if (totalTabs > 1) {
                            val prev = (state.currentPage - 1).coerceAtLeast(0)
                            state.animateScrollToPage(prev)
                        }
                    }
            }
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
