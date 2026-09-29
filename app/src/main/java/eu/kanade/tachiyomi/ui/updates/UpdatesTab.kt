package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.Event
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
object UpdatesTabEvents {
    val nextSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val prevSubTabEvent = kotlinx.coroutines.channels.Channel<Unit>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val selectSubTabEvent = kotlinx.coroutines.channels.Channel<Int>(1, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    var currentPageIndex = 1
}

@Composable
fun Screen.UpdatesTabViewContent(
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    screenModel: UpdatesScreenModel? = null,
    settingsScreenModel: UpdatesSettingsScreenModel? = null,
    animeScreenModel: eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel? = null,
    novelScreenModel: eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesScreenModel? = null,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val activeMediaType by uiPreferences.activeMediaType().collectAsState()
    val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
    val showTopTabBar by uiPreferences.showTopTabBar().collectAsState()

    val tabTitles = persistentListOf(
        stringResource(KMR.strings.tab_calendar),
        stringResource(KMR.strings.tab_updates),
        stringResource(KMR.strings.tab_schedule),
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = UpdatesTabEvents.currentPageIndex.coerceIn(0, tabTitles.size - 1)) { tabTitles.size }

    LaunchedEffect(pagerState.currentPage) {
        UpdatesTabEvents.currentPageIndex = pagerState.currentPage
    }

    LaunchedEffect(pagerState.pageCount) {
        launch {
            UpdatesTabEvents.nextSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
        launch {
            UpdatesTabEvents.prevSubTabEvent.receiveAsFlow().collectLatest {
                if (pagerState.currentPage > 0) {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
        }
        launch {
            UpdatesTabEvents.selectSubTabEvent.receiveAsFlow().collectLatest { index ->
                if (index in 0 until tabTitles.size) {
                    pagerState.animateScrollToPage(index)
                }
            }
        }
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        // Show in-screen tab row ONLY if floating bar is disabled or top tab bar is explicitly enabled
        if (!floatingBottomBar || showTopTabBar) {
            androidx.compose.material3.PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabTitles.forEachIndexed { index, title ->
                    androidx.compose.material3.Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            androidx.compose.material3.Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { pageIndex ->
            when (pageIndex) {
                0 -> {
                    val upcomingScreenModel = rememberScreenModel { mihon.feature.upcoming.UpcomingScreenModel() }
                    val upcomingState by upcomingScreenModel.state.collectAsState()
                    mihon.feature.upcoming.UpcomingScreenContent(
                        state = upcomingState,
                        setSelectedYearMonth = upcomingScreenModel::setSelectedYearMonth,
                        onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
                        showUpdatingMangas = upcomingScreenModel::showUpdatingMangas,
                        hideUpdatingMangas = upcomingScreenModel::hideUpdatingMangas,
                        isPredictReleaseDate = tachiyomi.domain.library.service.LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in upcomingScreenModel.restriction,
                    )
                }
                1 -> {
                    when (activeMediaType) {
                        eu.kanade.domain.ui.model.MediaType.NOVEL -> {
                            val sm = novelScreenModel ?: rememberScreenModel { eu.kanade.tachiyomi.ui.updates.novel.NovelUpdatesScreenModel() }
                            val novelState by sm.state.collectAsState()
                            eu.kanade.presentation.updates.novel.NovelUpdatesScreen(
                                state = novelState,
                                lastUpdated = sm.lastUpdated,
                                onNovelClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(it)) },
                                onChapterClick = { navigator.push(eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen(it)) },
                                onToggleSelection = sm::toggleSelection,
                                onMultiBookmarkClicked = sm::bookmarkUpdates,
                                onMultiMarkAsReadClicked = sm::markUpdatesRead,
                            )
                        }
                        eu.kanade.domain.ui.model.MediaType.ANIME -> {
                            val sm = animeScreenModel ?: rememberScreenModel { eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel() }
                            val animeState by sm.state.collectAsState()
                            eu.kanade.presentation.updates.anime.AnimeUpdatesScreen(
                                state = animeState,
                                onAnimeClick = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(it)) },
                                onPlayClick = { animeId, episodeId ->
                                    val intent = eu.kanade.tachiyomi.ui.player.PlayerActivity.newIntent(context, animeId, episodeId)
                                    context.startActivity(intent)
                                },
                                onToggleSelection = sm::toggleSelection,
                            )
                        }
                        else -> {
                            val sm = screenModel ?: rememberScreenModel { UpdatesScreenModel() }
                            val settingsSM = settingsScreenModel ?: rememberScreenModel { UpdatesSettingsScreenModel() }
                            val state by sm.state.collectAsState()
                            val usePanoramaCover by settingsSM.updatesPreferences.usePanoramaCover().collectAsState()

                            UpdateScreen(
                                state = state,
                                snackbarHostState = sm.snackbarHostState,
                                lastUpdated = sm.lastUpdated,
                                preserveReadingPosition = sm.preserveReadingPosition,
                                onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
                                onSelectAll = sm::toggleAllSelection,
                                onInvertSelection = sm::invertSelection,
                                onUpdateLibrary = sm::updateLibrary,
                                onDownloadChapter = sm::downloadChapters,
                                onMultiBookmarkClicked = sm::bookmarkUpdates,
                                onMultiMarkAsReadClicked = sm::markUpdatesRead,
                                onMultiDeleteClicked = sm::showConfirmDeleteChapters,
                                updateSwipeStartAction = sm.chapterSwipeStartAction,
                                updateSwipeEndAction = sm.chapterSwipeEndAction,
                                onUpdateSwipe = sm::updateSwipe,
                                onUpdateSelected = sm::toggleSelection,
                                onOpenChapter = {
                                    val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                                    context.startActivity(intent)
                                },
                                onCalendarClicked = { scope.launch { pagerState.animateScrollToPage(0) } },
                                onFilterClicked = sm::showFilterDialog,
                                hasActiveFilters = state.hasActiveFilters,
                                usePanoramaCover = usePanoramaCover,
                                collapseToggle = sm::toggleExpandedState,
                                showAppBar = false,
                            )

                            val onDismissDialog = { sm.setDialog(null) }
                            when (val dialog = state.dialog) {
                                is UpdatesScreenModel.Dialog.DeleteConfirmation -> {
                                    UpdatesDeleteConfirmationDialog(
                                        onDismissRequest = onDismissDialog,
                                        onConfirm = { sm.deleteChapters(dialog.toDelete) },
                                    )
                                }
                                is UpdatesScreenModel.Dialog.FilterSheet -> {
                                    UpdatesFilterDialog(
                                        onDismissRequest = onDismissDialog,
                                        screenModel = settingsSM,
                                    )
                                }
                                null -> {}
                            }

                            LaunchedEffect(Unit) {
                                sm.events.collectLatest { event ->
                                    when (event) {
                                        Event.InternalError -> sm.snackbarHostState.showSnackbar(
                                            context.stringResource(MR.strings.internal_error),
                                        )
                                        is Event.LibraryUpdateTriggered -> {
                                            val msg = if (event.started) {
                                                MR.strings.updating_library
                                            } else {
                                                MR.strings.update_already_running
                                            }
                                            sm.snackbarHostState.showSnackbar(context.stringResource(msg))
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(state.selectionMode) {
                                HomeScreen.showBottomNav(!state.selectionMode)
                            }

                            LaunchedEffect(state.isLoading) {
                                if (!state.isLoading) {
                                    (context as? MainActivity)?.ready = true
                                    with(DiscordRPCService) {
                                        discordScope.launchIO { setScreen(context, DiscordScreen.UPDATES) }
                                    }
                                }
                            }

                            DisposableEffect(Unit) {
                                sm.resetNewUpdatesCount()
                                onDispose {
                                    sm.resetNewUpdatesCount()
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val animeScheduleScreenModel = rememberScreenModel { eu.kanade.tachiyomi.ui.schedule.AnimeScheduleScreenModel() }
                    eu.kanade.tachiyomi.ui.schedule.AnimeScheduleScreenContent(screenModel = animeScheduleScreenModel)
                }
            }
        }
    }
}
// KMK <--

data object UpdatesTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = UpdatesTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    // SY -->
    @Composable
    override fun isEnabled(): Boolean {
        val scope = rememberCoroutineScope()
        return remember {
            Injekt.get<UiPreferences>().showNavUpdates().asState(scope)
        }.value
    }
    // SY <--

    @Composable
    override fun Content() {
        UpdatesTabViewContent()
    }
}

// KMK -->
@Composable
fun Screen.updatesTab(
    screenModel: UpdatesScreenModel,
    settingsScreenModel: UpdatesSettingsScreenModel,
): TabContent {
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_recent_updates,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                iconTint = if (state.hasActiveFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                onClick = screenModel::showFilterDialog,
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_view_upcoming),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { UpdatesTabEvents.selectSubTabEvent.trySend(0) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_update_library),
                icon = Icons.Outlined.Refresh,
                onClick = { screenModel.updateLibrary() },
            ),
        ),
        content = { contentPadding, _ ->
            UpdatesTabViewContent(
                contentPadding = contentPadding,
                screenModel = screenModel,
                settingsScreenModel = settingsScreenModel,
            )
        },
    )
}
// KMK <--
