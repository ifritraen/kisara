package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import eu.kanade.domain.ui.model.MediaType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
import eu.kanade.presentation.components.LocalHazeBypass
import eu.kanade.presentation.components.LocalHazeState
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val LocalActiveSubTabPopup = compositionLocalOf<cafe.adriel.voyager.navigator.tab.Tab?> { null }
val LocalEditCategory = staticCompositionLocalOf<(Category) -> Unit> { {} }

object HomeScreen : Screen() {
    private fun readResolve(): Any = HomeScreen

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    val showBottomNavFlow = kotlinx.coroutines.flow.MutableStateFlow(true)

    private const val TAB_ENTER_DURATION = 260
    private const val TAB_EXIT_DURATION = 260
    private val AURORA_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    private val TABS = listOf(
        HomeTab,
        LibraryTab,
        eu.kanade.tachiyomi.ui.track.TrackTab,
        BrowseTab,
        MoreTab,
    )

    private fun tabDirection(
        initialTab: cafe.adriel.voyager.navigator.tab.Tab,
        targetTab: cafe.adriel.voyager.navigator.tab.Tab,
    ): Int {
        val initialIndex = TABS.indexOfFirst { it.key == initialTab.key || it::class == initialTab::class }.takeIf { it >= 0 } ?: 0
        val targetIndex = TABS.indexOfFirst { it.key == targetTab.key || it::class == targetTab::class }.takeIf { it >= 0 } ?: 0
        return if (targetIndex >= initialIndex) 1 else -1
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // SY -->
        val scope = rememberCoroutineScope()
        val alwaysShowLabel by remember {
            Injekt.get<UiPreferences>().bottomBarLabels().asState(scope)
        }
        // SY <--

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val floatingBottomBar by uiPreferences.floatingBottomBar().collectAsState()
        val bottomBarOpacity by uiPreferences.bottomBarOpacity().collectAsState()
        val frostedGlass by uiPreferences.kisaraFrostedGlass().collectAsState()
        val alwaysShowSubTabsHome by uiPreferences.alwaysShowSubTabsHome().collectAsState()
        val alwaysShowSubTabsBrowse by uiPreferences.alwaysShowSubTabsBrowse().collectAsState()
        val showTrackSubBarAtTop by uiPreferences.showTrackSubBarAtTop().collectAsState()
        val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsState()
        val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
        val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
        val standardBottomBarHeight by uiPreferences.standardBottomBarHeight().collectAsState()
        val standardBottomBarBottomMargin by uiPreferences.standardBottomBarBottomMargin().collectAsState()

        val showFloatingMediaModeButton by uiPreferences.showFloatingMediaModeButton().collectAsState()
        val showFloatingActionButton by uiPreferences.showFloatingActionButton().collectAsState()
        val bottomControlsGap by uiPreferences.bottomControlsGap().collectAsState()
        val bottomControlsCornerRadius by uiPreferences.bottomControlsCornerRadius().collectAsState()
        val syncControlsWithDockRadius by uiPreferences.syncControlsWithDockRadius().collectAsState()
        val activeMediaType by uiPreferences.activeMediaType().collectAsState()
        val alwaysShowSubTabsLibrary by uiPreferences.alwaysShowSubTabsLibrary().collectAsState()

        val disableTabTransitions by uiPreferences.disableTabTransitions().collectAsState()
        val bypassBlurOnTransitions by uiPreferences.bypassBlurOnTransitions().collectAsState()
        val tabSwipeGesturesEnabled by uiPreferences.tabSwipeGesturesEnabled().collectAsState()
        val tabSwipeBottomZoneHeight by uiPreferences.tabSwipeBottomZoneHeight().collectAsState()
        val tabSwipeMiddleZoneHeight by uiPreferences.tabSwipeMiddleZoneHeight().collectAsState()

        val hazeState = remember { HazeState() }
        var showActionPopup by remember { mutableStateOf(false) }
        var showVerticalActionPopup by remember { mutableStateOf(false) }
        var showMediaModePopup by remember { mutableStateOf(false) }
        var activeSubTabPopup by remember { mutableStateOf<cafe.adriel.voyager.navigator.tab.Tab?>(null) }
        val subTabButtonBounds = remember { mutableStateMapOf<String, ButtonActionBounds>() }
        var hoveredButtonKey by remember { mutableStateOf<String?>(null) }
        var popupSelectedCategoryId by remember { mutableStateOf<Long?>(null) }
        var popupSelectedSubcategoryId by remember { mutableStateOf<Long?>(null) }
        var categoryToEdit by remember { mutableStateOf<Category?>(null) }
        val categoriesState by produceState<List<Category>>(emptyList(), activeMediaType) {
            when (activeMediaType) {
                MediaType.ANIME -> {
                    val getAnimeCategories = Injekt.get<tachiyomi.domain.category.anime.interactor.GetAnimeCategories>()
                    getAnimeCategories.subscribe().collect { animeCats ->
                        value = animeCats.map { Category(id = it.id, name = it.name, order = it.order, flags = it.flags, hidden = it.hidden, parentId = it.parentId) }
                    }
                }
                MediaType.NOVEL -> {
                    val getNovelCategories = Injekt.get<tachiyomi.domain.category.novel.interactor.GetNovelCategories>()
                    getNovelCategories.subscribe().collect { novelCats ->
                        value = novelCats.map { Category(id = it.id, name = it.name, order = it.order, flags = it.flags, hidden = it.hidden, parentId = it.parentId) }
                    }
                }
                else -> {
                    val getCategories = Injekt.get<GetCategories>()
                    getCategories.subscribe().collect { value = it }
                }
            }
        }

        val startScreen = remember { uiPreferences.startScreen().get() }
        val initialTab = remember {
            when (startScreen) {
                UiPreferences.StartScreen.HOME_LANDING -> {
                    HomeTab.showSubTab(0)
                    HomeTab
                }
                UiPreferences.StartScreen.HOME_FEED -> {
                    HomeTab.showSubTab(1)
                    HomeTab
                }
                UiPreferences.StartScreen.HOME_SUGGESTIONS -> {
                    HomeTab.showSubTab(2)
                    HomeTab
                }
                UiPreferences.StartScreen.HOME_UPDATES -> {
                    HomeTab.showSubTab(3)
                    HomeTab
                }
                UiPreferences.StartScreen.HOME_HISTORY -> {
                    HomeTab.showSubTab(4)
                    HomeTab
                }
                UiPreferences.StartScreen.HOME_FAVORITES -> {
                    HomeTab.showSubTab(5)
                    HomeTab
                }
                UiPreferences.StartScreen.LIBRARY -> LibraryTab
                UiPreferences.StartScreen.TRACK -> eu.kanade.tachiyomi.ui.track.TrackTab
                UiPreferences.StartScreen.BROWSE -> BrowseTab
                UiPreferences.StartScreen.MORE -> MoreTab
            }
        }

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            TabNavigator(
                tab = initialTab,
                key = TAB_NAVIGATOR_KEY,
            ) { tabNavigator ->
                LaunchedEffect(tabNavigator.current) {
                    showBottomNav(true)
                    showActionPopup = false
                    showVerticalActionPopup = false
                    activeSubTabPopup = null
                }
                // Provide usable navigator to content screen
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    val nestedScrollScope = rememberCoroutineScope()
                    val nestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                val delta = available.y
                                if (delta < -10f) {
                                    nestedScrollScope.launch { showBottomNav(false) }
                                } else if (delta > 10f) {
                                    nestedScrollScope.launch { showBottomNav(true) }
                                }
                                return Offset.Zero
                            }
                        }
                    }
                    Scaffold(
                        modifier = Modifier.nestedScroll(nestedScrollConnection),
                        startBar = {
                            if (isTabletUi()) {
                                NavigationRail {
                                    TABS
                                        // SY -->
                                        .fastFilter { it.isEnabled() }
                                        // SY <--
                                        .fastForEach {
                                            NavigationRailItem(it/* SY --> */, alwaysShowLabel/* SY <-- */)
                                        }
                                }
                            }
                        },
                        bottomBar = {
                            if (!isTabletUi() && !floatingBottomBar) {
                                val bottomBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.bottomBarHeight().collectAsState().value
                                val bottomBarWidth = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.bottomBarWidth().collectAsState().value
                                val bottomNavVisible by showBottomNavFlow.collectAsState()
                                AnimatedVisibility(
                                    visible = bottomNavVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Transparent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val scaledWidth = (bottomBarWidth / 100f) * (0.92f + (bottomBarHeight - 64f) / 200f)
                                        val isFloating = scaledWidth < 1f
                                        val barShape = if (isFloating) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                        val barPadding = if (isFloating) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier
                                        NavigationBar(
                                            modifier = Modifier
                                                .then(barPadding)
                                                .fillMaxWidth(scaledWidth.coerceIn(0.3f, 1.0f))
                                                .clip(barShape),
                                            height = bottomBarHeight.dp,
                                        ) {
                                            TABS
                                                // SY -->
                                                .fastFilter { it.isEnabled() }
                                                // SY <--
                                                .fastForEach {
                                                    NavigationBarItem(
                                                        tab = it,
                                                        alwaysShowLabel = alwaysShowLabel && bottomBarHeight >= 56,
                                                        subTabButtonBounds = subTabButtonBounds,
                                                        onHover = { key ->
                                                            hoveredButtonKey = key
                                                            if (key != null) {
                                                                if (key.startsWith("Library_sub_")) {
                                                                    val subId = key.removePrefix("Library_sub_").toLongOrNull()
                                                                    if (subId != null) {
                                                                        popupSelectedSubcategoryId = subId
                                                                    }
                                                                } else if (key.startsWith("Library_")) {
                                                                    val catId = key.removePrefix("Library_").toLongOrNull()
                                                                    if (catId != null) {
                                                                        popupSelectedCategoryId = catId
                                                                        popupSelectedSubcategoryId = null
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onHold = { hold ->
                                                            activeSubTabPopup = if (hold) it else null
                                                        },
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0),
                    ) { contentPadding ->
                        val tabTransition = updateTransition(targetState = tabNavigator.current, label = "tabContentTransition")
                        val isTransitionRunning = tabTransition.currentState != tabTransition.targetState
                        val bypassHaze = isTransitionRunning && bypassBlurOnTransitions

                        val haptic = LocalHapticFeedback.current
                        val density = LocalDensity.current
                        val swipeThresholdPx = with(density) { 45.dp.toPx() }
                        val currentEnabledTabs = TABS.filter { it.isEnabled() }

                        val gestureModifier = if (tabSwipeGesturesEnabled) {
                            Modifier.pointerInput(
                                currentEnabledTabs,
                                tabSwipeBottomZoneHeight,
                                tabSwipeMiddleZoneHeight,
                                tabNavigator.current,
                                activeMediaType,
                            ) {
                                val bottomZoneRatio = tabSwipeBottomZoneHeight / 100f
                                val middleZoneRatio = tabSwipeMiddleZoneHeight / 100f
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startY = down.position.y
                                    val containerHeight = size.height.toFloat()
                                    val yFromBottomRatio = if (containerHeight > 0f) {
                                        (containerHeight - startY) / containerHeight
                                    } else {
                                        0f
                                    }

                                    var totalDx = 0f
                                    var totalDy = 0f
                                    var gestureTriggered = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break

                                        val delta = change.positionChange()
                                        totalDx += delta.x
                                        totalDy += delta.y

                                        if (!gestureTriggered && abs(totalDx) > swipeThresholdPx && abs(totalDx) > abs(totalDy) * 1.5f) {
                                            gestureTriggered = true
                                            val swipeLeft = totalDx < 0f
                                            val currentTab = tabNavigator.current

                                            if (yFromBottomRatio <= bottomZoneRatio) {
                                                // Zone 1: Bottom Zone -> Main Navigation Tabs
                                                val enabledTabs = currentEnabledTabs
                                                val currentIdx = enabledTabs.indexOfFirst {
                                                    it.key == currentTab.key || it::class == currentTab::class
                                                }
                                                if (swipeLeft && currentIdx in 0 until enabledTabs.size - 1) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    tabNavigator.current = enabledTabs[currentIdx + 1]
                                                } else if (!swipeLeft && currentIdx > 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    tabNavigator.current = enabledTabs[currentIdx - 1]
                                                }
                                            } else if (yFromBottomRatio <= middleZoneRatio) {
                                                // Zone 2: Middle Zone -> Sub-Tabs & Subcategories / Subsubtabs
                                                when (currentTab) {
                                                    is HomeTab -> {
                                                        if (swipeLeft) HomeTab.nextSubTabEvent.trySend(Unit)
                                                        else HomeTab.prevSubTabEvent.trySend(Unit)
                                                    }
                                                    is BrowseTab -> {
                                                        if (BrowseTab.currentPageIndex == 4) {
                                                            if (swipeLeft) eu.kanade.tachiyomi.ui.browse.search.SearchTabEvents.nextSubTabEvent.trySend(Unit)
                                                            else eu.kanade.tachiyomi.ui.browse.search.SearchTabEvents.prevSubTabEvent.trySend(Unit)
                                                        } else {
                                                            if (swipeLeft) BrowseTab.nextSubTabEvent.trySend(Unit)
                                                            else BrowseTab.prevSubTabEvent.trySend(Unit)
                                                        }
                                                    }
                                                    is eu.kanade.tachiyomi.ui.track.TrackTab -> {
                                                        if (swipeLeft) eu.kanade.tachiyomi.ui.track.TrackTab.nextSubSubTabEvent.trySend(Unit)
                                                        else eu.kanade.tachiyomi.ui.track.TrackTab.prevSubSubTabEvent.trySend(Unit)
                                                    }
                                                    is LibraryTab -> {
                                                        if (swipeLeft) eu.kanade.tachiyomi.ui.library.LibraryTab.nextSubcategoryEvent.trySend(Unit)
                                                        else eu.kanade.tachiyomi.ui.library.LibraryTab.prevSubcategoryEvent.trySend(Unit)
                                                    }
                                                    else -> {}
                                                }
                                            } else {
                                                // Zone 3: Top Zone -> Library Categories, other screens sub-tabs
                                                if (currentTab is LibraryTab) {
                                                    if (swipeLeft) eu.kanade.tachiyomi.ui.library.LibraryTab.nextCategoryEvent.trySend(Unit)
                                                    else eu.kanade.tachiyomi.ui.library.LibraryTab.prevCategoryEvent.trySend(Unit)
                                                } else {
                                                    when (currentTab) {
                                                        is HomeTab -> {
                                                            if (swipeLeft) HomeTab.nextSubTabEvent.trySend(Unit)
                                                            else HomeTab.prevSubTabEvent.trySend(Unit)
                                                        }
                                                        is BrowseTab -> {
                                                            if (swipeLeft) BrowseTab.nextSubTabEvent.trySend(Unit)
                                                            else BrowseTab.prevSubTabEvent.trySend(Unit)
                                                        }
                                                        is eu.kanade.tachiyomi.ui.track.TrackTab -> {
                                                            if (swipeLeft) eu.kanade.tachiyomi.ui.track.TrackTab.nextSubTabEvent.trySend(Unit)
                                                            else eu.kanade.tachiyomi.ui.track.TrackTab.prevSubTabEvent.trySend(Unit)
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }

                        CompositionLocalProvider(LocalHazeBypass provides bypassHaze) {
                            Box(
                                modifier = Modifier
                                    .padding(contentPadding)
                                    .consumeWindowInsets(contentPadding)
                                    .fillMaxSize()
                                    .then(gestureModifier),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (frostedGlass) Modifier.hazeSource(state = hazeState) else Modifier),
                                ) {
                                    CompositionLocalProvider(
                                        LocalActiveSubTabPopup provides activeSubTabPopup,
                                        LocalEditCategory provides { categoryToEdit = it },
                                    ) {
                                        AnimatedContent(
                                            targetState = tabNavigator.current,
                                            transitionSpec = {
                                                if (disableTabTransitions) {
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                } else {
                                                    val direction = tabDirection(initialState, targetState)
                                                    val enter = slideInHorizontally(
                                                        animationSpec = tween(TAB_ENTER_DURATION, easing = AURORA_EASING),
                                                        initialOffsetX = { width -> direction * (width / 4) },
                                                    ) + fadeIn(
                                                        animationSpec = tween(TAB_ENTER_DURATION, easing = AURORA_EASING),
                                                    )
                                                    val exit = slideOutHorizontally(
                                                        animationSpec = tween(TAB_EXIT_DURATION, easing = AURORA_EASING),
                                                        targetOffsetX = { width -> -direction * (width / 5) },
                                                    ) + fadeOut(
                                                        animationSpec = tween(TAB_EXIT_DURATION, easing = AURORA_EASING),
                                                    )
                                                    (enter togetherWith exit).apply {
                                                        targetContentZIndex = 1f
                                                    }
                                                }
                                            },
                                            label = "tabContent",
                                        ) { currentTab ->
                                            tabNavigator.saveableState(key = "currentTab", currentTab) {
                                                currentTab.Content()
                                            }
                                        }
                                    }
                                    categoryToEdit?.let { category ->
                                        EditCategoryPopup(
                                            category = category,
                                            categories = categoriesState,
                                            onDismissRequest = { categoryToEdit = null },
                                        )
                                    }
                                }

                                // Floating bottom bar overlay
                                if (!isTabletUi() && floatingBottomBar) {
                                    val bottomNavVisible by showBottomNavFlow.collectAsState()

                                    // 1. Determine actions for the current tab
                                    val currentTab = tabNavigator.current
                                    val hasActions = when (currentTab) {
                                        is LibraryTab -> true
                                        is HomeTab -> HomeTab.currentPageIndex in 1..5
                                        is BrowseTab -> BrowseTab.currentPageIndex in 0..3
                                        else -> false
                                    }

                                    // 2. Determine which sub-tab popup is active
                                    val alwaysShowSubTabsTrack by uiPreferences.alwaysShowSubTabsTrack().collectAsState()
                                    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
                                    val categoryBarPinnedPref = remember { libraryPreferences.categoryBarPinned() }
                                    val isCategoryBarPinned by categoryBarPinnedPref.collectAsState()

                                    val activePopup = when {
                                        activeSubTabPopup != null -> activeSubTabPopup
                                        currentTab is HomeTab && alwaysShowSubTabsHome -> currentTab
                                        currentTab is LibraryTab && (alwaysShowSubTabsLibrary || isCategoryBarPinned) -> currentTab
                                        currentTab is BrowseTab && alwaysShowSubTabsBrowse -> currentTab
                                        currentTab is eu.kanade.tachiyomi.ui.track.TrackTab && alwaysShowSubTabsTrack && !showTrackSubBarAtTop -> currentTab
                                        else -> null
                                    }

                                    // 3. Sub-tab popup above bottom bar
                                    AnimatedVisibility(
                                        visible = bottomNavVisible && activePopup != null && !showVerticalActionPopup,
                                        enter = expandVertically(expandFrom = Alignment.Bottom),
                                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                                        modifier = Modifier
                                            .padding(bottom = ((if (floatingBottomBar) (bottomBarHeight + bottomBarBottomMargin) else (standardBottomBarHeight + standardBottomBarBottomMargin)) + subTabsBottomMargin).coerceAtLeast(0).dp)
                                            .align(Alignment.BottomCenter),
                                    ) {
                                        val parentCategories = remember(categoriesState) {
                                            categoriesState.filter { it.parentId == null }.sortedBy { it.order }
                                        }
                                        val childrenByParent = remember(categoriesState) {
                                            categoriesState.filter { it.parentId != null }
                                                .groupBy { it.parentId }
                                                .mapValues { entry -> entry.value.sortedBy { it.order } }
                                        }
                                        val selectedParentId = popupSelectedCategoryId ?: parentCategories.firstOrNull()?.id
                                        val subcategories = selectedParentId?.let { childrenByParent[it] }.orEmpty()

                                        val effectiveCornerRadius = if (syncControlsWithDockRadius) {
                                            uiPreferences.bottomBarCornerRadius().collectAsState().value.dp
                                        } else {
                                            bottomControlsCornerRadius.dp
                                        }

                                        GlassSurface(
                                            shape = RoundedCornerShape(effectiveCornerRadius),
                                            style = GlassDefaults.regularStyle(),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            ) {
                                                if (activePopup is LibraryTab) {
                                                    val editCategory = LocalEditCategory.current
                                                    val kisaraShowSubcategoriesInMainBar by uiPreferences.kisaraShowSubcategoriesInMainBar().collectAsState()
                                                    val tabCategories = if (parentCategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
                                                        parentCategories
                                                    } else {
                                                        categoriesState
                                                    }

                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        // Subcategories Row (if present) - Rendered ABOVE Parent Categories
                                                        if (subcategories.isNotEmpty() && !kisaraShowSubcategoriesInMainBar) {
                                                            Row(
                                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                // "All" button
                                                                SubTabButton(
                                                                    text = "All",
                                                                    selected = popupSelectedSubcategoryId == null,
                                                                    onLongClick = {
                                                                        parentCategories.firstOrNull { it.id == selectedParentId }?.let { editCategory(it) }
                                                                        if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                    },
                                                                ) {
                                                                    popupSelectedSubcategoryId = null
                                                                    tabNavigator.current = LibraryTab
                                                                    selectedParentId?.let { pId ->
                                                                        val parentIndex = categoriesState.indexOfFirst { it.id == pId }
                                                                        if (parentIndex != -1) {
                                                                            LibraryTab.selectCategoryEvent.trySend(parentIndex)
                                                                        }
                                                                    }
                                                                    LibraryTab.selectSubcategoryEvent.trySend(null)
                                                                    if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                }

                                                                subcategories.forEach { sub ->
                                                                    val key = "Library_sub_${sub.id}"
                                                                    SubTabButton(
                                                                        text = sub.visualName,
                                                                        selected = popupSelectedSubcategoryId == sub.id,
                                                                        hovered = hoveredButtonKey == key,
                                                                        onLongClick = {
                                                                            editCategory(sub)
                                                                            if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                        },
                                                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                            subTabButtonBounds[key] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                                popupSelectedSubcategoryId = sub.id
                                                                                tabNavigator.current = LibraryTab
                                                                                LibraryTab.selectSubcategoryEvent.trySend(sub.id)
                                                                                if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                            }
                                                                        },
                                                                    ) {
                                                                        popupSelectedSubcategoryId = sub.id
                                                                        tabNavigator.current = LibraryTab
                                                                        LibraryTab.selectSubcategoryEvent.trySend(sub.id)
                                                                        if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Parent Categories Row with Pin icon
                                                        Row(
                                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            tabCategories.forEach { category ->
                                                                val key = "Library_${category.id}"
                                                                SubTabButton(
                                                                    text = category.visualName,
                                                                    selected = selectedParentId == category.id,
                                                                    hovered = hoveredButtonKey == key,
                                                                    onLongClick = {
                                                                        editCategory(category)
                                                                        if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                    },
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds[key] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            popupSelectedCategoryId = category.id
                                                                            popupSelectedSubcategoryId = null
                                                                            tabNavigator.current = LibraryTab
                                                                            val actualIndex = categoriesState.indexOfFirst { it.id == category.id }
                                                                            if (actualIndex != -1) {
                                                                                LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                            }
                                                                            LibraryTab.selectSubcategoryEvent.trySend(null)
                                                                            if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    popupSelectedCategoryId = category.id
                                                                    popupSelectedSubcategoryId = null
                                                                    tabNavigator.current = LibraryTab
                                                                    val actualIndex = categoriesState.indexOfFirst { it.id == category.id }
                                                                    if (actualIndex != -1) {
                                                                        LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                    }
                                                                    LibraryTab.selectSubcategoryEvent.trySend(null)
                                                                    if (!alwaysShowSubTabsLibrary && !isCategoryBarPinned) activeSubTabPopup = null
                                                                }
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    scope.launch {
                                                                        categoryBarPinnedPref.set(!isCategoryBarPinned)
                                                                    }
                                                                },
                                                                modifier = Modifier.size(32.dp),
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isCategoryBarPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                                    contentDescription = "Pin category bar",
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = if (isCategoryBarPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        when (activePopup) {
                                                            is HomeTab -> {
                                                                SubTabButton(
                                                                    text = "Home",
                                                                    selected = HomeTab.currentPageIndex == 0,
                                                                    hovered = hoveredButtonKey == "Home_Landing",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_Landing"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(0)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(0)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Feed",
                                                                    selected = HomeTab.currentPageIndex == 1,
                                                                    hovered = hoveredButtonKey == "Home_Feed",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_Feed"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(1)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(1)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Suggestion",
                                                                    selected = HomeTab.currentPageIndex == 2,
                                                                    hovered = hoveredButtonKey == "Home_Suggestions",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_Suggestions"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(2)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(2)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Updates",
                                                                    selected = HomeTab.currentPageIndex == 3,
                                                                    hovered = hoveredButtonKey == "Home_Updates",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_Updates"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(3)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(3)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "History",
                                                                    selected = HomeTab.currentPageIndex == 4,
                                                                    hovered = hoveredButtonKey == "Home_History",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_History"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(4)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(4)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Favorite",
                                                                    selected = HomeTab.currentPageIndex == 5,
                                                                    hovered = hoveredButtonKey == "Home_Favorites",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Home_Favorites"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = HomeTab
                                                                            HomeTab.showSubTab(5)
                                                                            if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = HomeTab
                                                                    HomeTab.showSubTab(5)
                                                                    if (!alwaysShowSubTabsHome) activeSubTabPopup = null
                                                                }
                                                            }
                                                            is BrowseTab -> {
                                                                SubTabButton(
                                                                    text = "Sources",
                                                                    selected = BrowseTab.currentPageIndex == 0,
                                                                    hovered = hoveredButtonKey == "Browse_Sources",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Browse_Sources"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = BrowseTab
                                                                            BrowseTab.showSource()
                                                                            if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = BrowseTab
                                                                    BrowseTab.showSource()
                                                                    if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Extensions",
                                                                    selected = BrowseTab.currentPageIndex == 1,
                                                                    hovered = hoveredButtonKey == "Browse_Extensions",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Browse_Extensions"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = BrowseTab
                                                                            BrowseTab.showExtension()
                                                                            if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = BrowseTab
                                                                    BrowseTab.showExtension()
                                                                    if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Migration",
                                                                    selected = BrowseTab.currentPageIndex == 2,
                                                                    hovered = hoveredButtonKey == "Browse_Migration",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Browse_Migration"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = BrowseTab
                                                                            BrowseTab.showMigration()
                                                                            if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = BrowseTab
                                                                    BrowseTab.showMigration()
                                                                    if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Duplicate",
                                                                    selected = BrowseTab.currentPageIndex == 3,
                                                                    hovered = hoveredButtonKey == "Browse_Duplicate",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Browse_Duplicate"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = BrowseTab
                                                                            BrowseTab.showDuplicate()
                                                                            if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = BrowseTab
                                                                    BrowseTab.showDuplicate()
                                                                    if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                }
                                                                SubTabButton(
                                                                    text = "Search",
                                                                    selected = BrowseTab.currentPageIndex == 4,
                                                                    hovered = hoveredButtonKey == "Browse_BulkSearch",
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds["Browse_BulkSearch"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = BrowseTab
                                                                            BrowseTab.showBulkSearch()
                                                                            if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = BrowseTab
                                                                    BrowseTab.showBulkSearch()
                                                                    if (!alwaysShowSubTabsBrowse) activeSubTabPopup = null
                                                                }
                                                            }
                                                            is eu.kanade.tachiyomi.ui.track.TrackTab -> {
                                                                val trackSubTabNames = when (activeMediaType) {
                                                                    MediaType.ANIME -> listOf(
                                                                        "Trending" to 0,
                                                                        "This Season" to 1,
                                                                        "Top 100" to 2,
                                                                        "Search" to 3,
                                                                        "Genres & Tags" to 4,
                                                                        "Studios" to 5,
                                                                        "My Anime List" to 6,
                                                                        "Profile" to 7,
                                                                    )
                                                                    MediaType.NOVEL -> listOf(
                                                                        "Novel Releases" to 0,
                                                                        "Top Novels" to 1,
                                                                        "Novel Directory" to 2,
                                                                        "Novel Search" to 3,
                                                                        "Novel Genres" to 4,
                                                                        "Publishers" to 5,
                                                                        "Novel Reviews" to 6,
                                                                        "My Novel Lists" to 7,
                                                                        "User CP" to 8,
                                                                    )
                                                                    else -> listOf(
                                                                        "New Releases" to 0,
                                                                        "Top Recommended" to 1,
                                                                        "Releases" to 2,
                                                                        "Series Info" to 3,
                                                                        "Scanlators" to 4,
                                                                        "Mangaka" to 5,
                                                                        "Publishers" to 6,
                                                                        "Reviews" to 7,
                                                                        "Genres" to 8,
                                                                        "Search" to 9,
                                                                        "My Lists" to 10,
                                                                        "User CP" to 11,
                                                                    )
                                                                }
                                                                Row(
                                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                ) {
                                                                    trackSubTabNames.forEach { (name, idx) ->
                                                                        SubTabButton(
                                                                            text = name,
                                                                            selected = eu.kanade.tachiyomi.ui.track.TrackTab.currentPageIndex == idx,
                                                                            hovered = hoveredButtonKey == "Track_$idx",
                                                                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                                subTabButtonBounds["Track_$idx"] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                                    tabNavigator.current = eu.kanade.tachiyomi.ui.track.TrackTab
                                                                                    eu.kanade.tachiyomi.ui.track.TrackTab.showSubTab(idx)
                                                                                    if (!alwaysShowSubTabsTrack) activeSubTabPopup = null
                                                                                }
                                                                            },
                                                                        ) {
                                                                            tabNavigator.current = eu.kanade.tachiyomi.ui.track.TrackTab
                                                                            eu.kanade.tachiyomi.ui.track.TrackTab.showSubTab(idx)
                                                                            if (!alwaysShowSubTabsTrack) activeSubTabPopup = null
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 4. Render the actual bottom bar row (with 3 siblings: Media Mode, Main Dock, 3-Dot Action)
                                    AnimatedVisibility(
                                        visible = bottomNavVisible,
                                        enter = expandVertically(),
                                        exit = shrinkVertically(),
                                        modifier = Modifier
                                            .padding(bottom = bottomBarBottomMargin.coerceAtLeast(0).dp)
                                            .align(Alignment.BottomCenter),
                                    ) {
                                        val bottomBarWidth by uiPreferences.bottomBarWidth().collectAsState()
                                        val bottomBarAutoWidth by uiPreferences.bottomBarAutoWidth().collectAsState()
                                        val bottomBarGap by uiPreferences.bottomBarGap().collectAsState()
                                        val bottomBarKeepRatio by uiPreferences.bottomBarKeepRatio().collectAsState()
                                        val bottomBarHorizontalPadding by uiPreferences.bottomBarHorizontalPadding().collectAsState()
                                        val bottomBarVerticalPadding by uiPreferences.bottomBarVerticalPadding().collectAsState()
                                        val bottomBarCornerRadius by uiPreferences.bottomBarCornerRadius().collectAsState()
                                        val bottomBarButtonSizePref by uiPreferences.bottomBarButtonSize().collectAsState()
                                        val bottomBarIconSizePref by uiPreferences.bottomBarIconSize().collectAsState()

                                        val bottomBarButtonSize = if (bottomBarKeepRatio) {
                                            (bottomBarHeight * 0.45f).coerceAtLeast(8f).dp
                                        } else {
                                            bottomBarButtonSizePref.dp
                                        }
                                        val bottomBarIconSize = if (bottomBarKeepRatio) {
                                            (bottomBarHeight * 0.25f).coerceAtLeast(6f).dp
                                        } else {
                                            bottomBarIconSizePref.dp
                                        }

                                        val effectiveCornerRadius = if (syncControlsWithDockRadius) bottomBarCornerRadius.dp else bottomControlsCornerRadius.dp

                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(bottomControlsGap.dp),
                                        ) {
                                            // Far Left: Floating Media Mode Button & Vertical Popup
                                            if (showFloatingMediaModeButton) {
                                                Box(contentAlignment = Alignment.BottomStart) {
                                                    // Vertical Media Mode Stack
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = showMediaModePopup,
                                                        enter = expandVertically(expandFrom = Alignment.Bottom) + androidx.compose.animation.fadeIn(),
                                                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + androidx.compose.animation.fadeOut(),
                                                        modifier = Modifier.padding(bottom = (bottomBarHeight + 8).dp),
                                                    ) {
                                                        GlassSurface(
                                                            shape = RoundedCornerShape(effectiveCornerRadius),
                                                            style = GlassDefaults.regularStyle(),
                                                            modifier = Modifier.wrapContentWidth(),
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                            ) {
                                                                val haptic = LocalHapticFeedback.current
                                                                MediaType.values().forEach { mode ->
                                                                    val isSelected = activeMediaType == mode
                                                                    val bgModifier = if (isSelected) {
                                                                        Modifier.background(
                                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                                            shape = RoundedCornerShape(12.dp),
                                                                        )
                                                                    } else {
                                                                        Modifier
                                                                    }
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(40.dp)
                                                                            .clip(RoundedCornerShape(12.dp))
                                                                            .then(bgModifier)
                                                                            .clickable {
                                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                                scope.launch {
                                                                                    uiPreferences.activeMediaType().set(mode)
                                                                                    showMediaModePopup = false
                                                                                }
                                                                            },
                                                                        contentAlignment = Alignment.Center,
                                                                    ) {
                                                                        Text(
                                                                            text = mode.shortName,
                                                                            style = MaterialTheme.typography.titleMedium.copy(
                                                                                fontWeight = FontWeight.Black,
                                                                                fontSize = 18.sp,
                                                                            ),
                                                                            color = if (isSelected) {
                                                                                MaterialTheme.colorScheme.primary
                                                                            } else {
                                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                                            },
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Floating Media Mode Button (drag / tap)
                                                    val haptic = LocalHapticFeedback.current
                                                    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }
                                                    var dragAccumulatorY by remember { mutableFloatStateOf(0f) }
                                                    val dragThreshold = 35f

                                                    GlassSurface(
                                                        shape = RoundedCornerShape(effectiveCornerRadius),
                                                        style = GlassDefaults.prominentStyle(),
                                                        modifier = Modifier.size(bottomBarHeight.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(effectiveCornerRadius))
                                                                .pointerInput(activeMediaType) {
                                                                    detectDragGestures(
                                                                        onDragStart = {
                                                                            dragAccumulatorX = 0f
                                                                            dragAccumulatorY = 0f
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            dragAccumulatorX += dragAmount.x
                                                                            dragAccumulatorY += dragAmount.y

                                                                            if (dragAccumulatorX > dragThreshold || dragAccumulatorY > dragThreshold) {
                                                                                dragAccumulatorX = 0f
                                                                                dragAccumulatorY = 0f
                                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                                scope.launch {
                                                                                    uiPreferences.activeMediaType().set(activeMediaType.previous())
                                                                                }
                                                                            } else if (dragAccumulatorX < -dragThreshold || dragAccumulatorY < -dragThreshold) {
                                                                                dragAccumulatorX = 0f
                                                                                dragAccumulatorY = 0f
                                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                                scope.launch {
                                                                                    uiPreferences.activeMediaType().set(activeMediaType.next())
                                                                                }
                                                                            }
                                                                        },
                                                                    )
                                                                }
                                                                .clickable {
                                                                    showMediaModePopup = !showMediaModePopup
                                                                    if (showMediaModePopup) {
                                                                        showVerticalActionPopup = false
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                text = activeMediaType.shortName,
                                                                style = MaterialTheme.typography.titleMedium.copy(
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = (bottomBarHeight * 0.4f).coerceAtLeast(16f).sp,
                                                                ),
                                                                color = if (showMediaModePopup) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Center: Main Dock GlassSurface
                                            GlassSurface(
                                                shape = RoundedCornerShape(bottomBarCornerRadius.dp),
                                                style = GlassDefaults.prominentStyle(),
                                                modifier = Modifier
                                                    .height(bottomBarHeight.dp)
                                                    .then(
                                                        if (bottomBarAutoWidth) {
                                                            Modifier.wrapContentWidth()
                                                        } else {
                                                            Modifier.width(bottomBarWidth.dp)
                                                        },
                                                    ),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .padding(horizontal = bottomBarHorizontalPadding.dp, vertical = bottomBarVerticalPadding.dp)
                                                        .wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true)
                                                        .align(Alignment.Center),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(bottomBarGap.dp),
                                                ) {
                                                    // Left part: The Navigation Tabs
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(bottomBarGap.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        TABS.fastFilter { it.isEnabled() }.fastForEach { tab ->
                                                            val selected = tabNavigator.current::class == tab::class
                                                            val tint = if (selected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                            var itemGlobalOffset by remember { mutableStateOf(Offset.Zero) }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(bottomBarButtonSize)
                                                                    .clip(CircleShape)
                                                                    .onGloballyPositioned { coordinates ->
                                                                        itemGlobalOffset = coordinates.positionInRoot()
                                                                    }
                                                                    .subTabBarGestureDetector(
                                                                        tab = tab,
                                                                        selected = selected,
                                                                        itemGlobalOffset = itemGlobalOffset,
                                                                        subTabButtonBounds = subTabButtonBounds,
                                                                        scope = scope,
                                                                        onHover = { key ->
                                                                            hoveredButtonKey = key
                                                                            if (key != null) {
                                                                                if (key.startsWith("Library_sub_")) {
                                                                                    val subId = key.removePrefix("Library_sub_").toLongOrNull()
                                                                                    if (subId != null) {
                                                                                        popupSelectedSubcategoryId = subId
                                                                                    }
                                                                                } else if (key.startsWith("Library_")) {
                                                                                    val catId = key.removePrefix("Library_").toLongOrNull()
                                                                                    if (catId != null) {
                                                                                        popupSelectedCategoryId = catId
                                                                                        popupSelectedSubcategoryId = null
                                                                                    }
                                                                                }
                                                                            }
                                                                        },
                                                                        onHold = { hold ->
                                                                            activeSubTabPopup = if (hold) tab else null
                                                                        },
                                                                        onTap = {
                                                                            if (!selected) {
                                                                                tabNavigator.current = tab
                                                                            } else {
                                                                                scope.launch { tab.onReselect(navigator) }
                                                                            }
                                                                            if (tab is HomeTab && !alwaysShowSubTabsHome) {
                                                                                activeSubTabPopup = null
                                                                            } else if (tab is BrowseTab && !alwaysShowSubTabsBrowse) {
                                                                                activeSubTabPopup = null
                                                                            } else if (tab is LibraryTab && !alwaysShowSubTabsLibrary && !isCategoryBarPinned) {
                                                                                activeSubTabPopup = null
                                                                            } else if (tab !is HomeTab && tab !is BrowseTab && tab !is LibraryTab) {
                                                                                activeSubTabPopup = null
                                                                            }
                                                                        },
                                                                        onLongPress = {
                                                                            if (selected) {
                                                                                if (tab is HomeTab && !alwaysShowSubTabsHome) {
                                                                                    activeSubTabPopup = if (activeSubTabPopup == tab) null else tab
                                                                                } else if (tab is BrowseTab && !alwaysShowSubTabsBrowse) {
                                                                                    activeSubTabPopup = if (activeSubTabPopup == tab) null else tab
                                                                                } else if (tab is LibraryTab && !alwaysShowSubTabsLibrary) {
                                                                                    activeSubTabPopup = if (activeSubTabPopup == tab) null else tab
                                                                                }
                                                                            }
                                                                            if (tab is LibraryTab) {
                                                                                LibraryTab.toggleCategoryBarEvent.trySend(Unit)
                                                                            } else if (tab is MoreTab) {
                                                                                showActionPopup = !showActionPopup
                                                                            }
                                                                        },
                                                                    ),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                CompositionLocalProvider(LocalContentColor provides tint) {
                                                                    NavigationIconItem(tab)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // In-place action buttons if floating 3-dot is disabled
                                                    if (!showFloatingActionButton) {
                                                        AnimatedVisibility(
                                                            visible = hasActions && showActionPopup,
                                                            enter = expandHorizontally(),
                                                            exit = shrinkHorizontally(),
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            ) {
                                                                VerticalDivider(
                                                                    modifier = Modifier
                                                                        .height(24.dp)
                                                                        .padding(horizontal = 4.dp),
                                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                                )

                                                                TabActionGroup(
                                                                    currentTab = currentTab,
                                                                    isVertical = false,
                                                                    bottomBarButtonSize = bottomBarButtonSize,
                                                                    bottomBarIconSize = bottomBarIconSize,
                                                                    onActionExecuted = { showActionPopup = false },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Far Right: 3-Dot Action Button & Vertical Popup (Fixed Slot)
                                            if (showFloatingActionButton) {
                                                if (hasActions) {
                                                    Box(contentAlignment = Alignment.BottomEnd) {
                                                        // Vertical Actions Popup
                                                        androidx.compose.animation.AnimatedVisibility(
                                                            visible = showVerticalActionPopup,
                                                            enter = expandVertically(expandFrom = Alignment.Bottom) + androidx.compose.animation.fadeIn(),
                                                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + androidx.compose.animation.fadeOut(),
                                                            modifier = Modifier.padding(bottom = (bottomBarHeight + 8).dp),
                                                        ) {
                                                            GlassSurface(
                                                                shape = RoundedCornerShape(effectiveCornerRadius),
                                                                style = GlassDefaults.regularStyle(),
                                                                modifier = Modifier.wrapContentWidth(),
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.padding(6.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                ) {
                                                                    TabActionGroup(
                                                                        currentTab = currentTab,
                                                                        isVertical = true,
                                                                        bottomBarButtonSize = 40.dp,
                                                                        bottomBarIconSize = 20.dp,
                                                                        onActionExecuted = { showVerticalActionPopup = false },
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Floating 3-Dot Button
                                                        GlassSurface(
                                                            shape = RoundedCornerShape(effectiveCornerRadius),
                                                            style = GlassDefaults.prominentStyle(),
                                                            modifier = Modifier.size(bottomBarHeight.dp),
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .clip(RoundedCornerShape(effectiveCornerRadius))
                                                                    .clickable {
                                                                        showVerticalActionPopup = !showVerticalActionPopup
                                                                        if (showVerticalActionPopup) {
                                                                            showMediaModePopup = false
                                                                        }
                                                                    },
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.MoreVert,
                                                                    contentDescription = "Toggle Actions Column",
                                                                    tint = if (showVerticalActionPopup) {
                                                                        MaterialTheme.colorScheme.primary
                                                                    } else {
                                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                    },
                                                                    modifier = Modifier.size((bottomBarHeight * 0.4f).coerceAtLeast(18f).dp),
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // KMK --> ponytail: fixed placeholder slot when no actions exist for current tab
                                                    Spacer(modifier = Modifier.size(bottomBarHeight.dp))
                                                    // KMK <--
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val goToLibraryTab = { tabNavigator.current = LibraryTab }
                    BackHandler(
                        enabled = tabNavigator.current != LibraryTab,
                        onBack = goToLibraryTab,
                    )

                    LaunchedEffect(Unit) {
                        launch {
                            librarySearchEvent.receiveAsFlow().collectLatest {
                                goToLibraryTab()
                                LibraryTab.search(it)
                            }
                        }
                        launch {
                            openTabEvent.receiveAsFlow().collectLatest {
                                tabNavigator.current = when (it) {
                                    is Tab.Library -> LibraryTab
                                    Tab.Updates -> {
                                        HomeTab.showSubTab(1)
                                        HomeTab
                                    }
                                    Tab.History -> {
                                        HomeTab.showSubTab(2)
                                        HomeTab
                                    }
                                    is Tab.Browse -> {
                                        if (it.toExtensions) {
                                            BrowseTab.showExtension()
                                        }
                                        BrowseTab
                                    }
                                    is Tab.More -> MoreTab
                                }

                                if (it is Tab.Library && it.mangaIdToOpen != null) {
                                    navigator.push(MangaScreen(it.mangaIdToOpen))
                                }
                                if (it is Tab.More) {
                                    if (it.toDownloads) {
                                        navigator.push(DownloadQueueScreen)
                                        // KMK -->
                                    } else if (it.toLibraryUpdateErrors) {
                                        navigator.push(LibraryUpdateErrorScreen())
                                        // KMK <--
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        // SY -->
        alwaysShowLabel: Boolean,
        // SY <--
        showLabel: Boolean = true,
        subTabButtonBounds: Map<String, ButtonActionBounds>,
        onHover: (String?) -> Unit,
        onHold: (Boolean) -> Unit = {},
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val alwaysShowSubTabsHome by uiPreferences.alwaysShowSubTabsHome().collectAsState()
        val alwaysShowSubTabsBrowse by uiPreferences.alwaysShowSubTabsBrowse().collectAsState()
        var activeSubTabPopup by remember { mutableStateOf<cafe.adriel.voyager.navigator.tab.Tab?>(null) }
        var showActionPopup by remember { mutableStateOf(false) }

        var itemGlobalOffset by remember { mutableStateOf(Offset.Zero) }
        val bottomBarHeight = remember { uiPreferences.bottomBarHeight() }.collectAsState().value
        NavigationBarItem(
            modifier = Modifier
                .height(bottomBarHeight.dp)
                .onGloballyPositioned { coordinates ->
                    itemGlobalOffset = coordinates.positionInRoot()
                }
                .subTabBarGestureDetector(
                    tab = tab,
                    selected = selected,
                    itemGlobalOffset = itemGlobalOffset,
                    subTabButtonBounds = subTabButtonBounds,
                    scope = scope,
                    onHover = onHover,
                    onHold = onHold,
                    onTap = {
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                    onLongPress = {
                        if (selected) {
                            if (tab is HomeTab && !alwaysShowSubTabsHome) {
                                activeSubTabPopup = if (activeSubTabPopup == tab) null else tab
                            } else if (tab is BrowseTab && !alwaysShowSubTabsBrowse) {
                                activeSubTabPopup = if (activeSubTabPopup == tab) null else tab
                            }
                        }
                        if (tab is LibraryTab) {
                            LibraryTab.toggleCategoryBarEvent.trySend(Unit)
                        } else if (tab is MoreTab) {
                            showActionPopup = !showActionPopup
                        }
                    },
                ),
            selected = selected,
            onClick = {},
            icon = { NavigationIconItem(tab) },
            label = if (showLabel && bottomBarHeight >= 56) {
                {
                    Text(
                        text = tab.options.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
            alwaysShowLabel = alwaysShowLabel && bottomBarHeight >= 56,
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }

    @Composable
    fun NavigationRailItem(
        tab: eu.kanade.presentation.util.Tab,
        // SY -->
        alwaysShowLabel: Boolean,
        // SY <--
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = /* SY --> */alwaysShowLabel, /* SY <-- */
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    tab is UpdatesTab -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newShowUpdatesCount().changes(),
                                pref.newUpdatesCount().changes(),
                            ) { show, count -> if (show) count else 0 }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            Injekt.get<SourcePreferences>().extensionUpdatesCount().changes()
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            val bottomBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.bottomBarHeight().collectAsState().value
            val bottomBarIconSize = (bottomBarHeight * 0.38f).coerceAtLeast(22f).dp
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
                // TODO: https://issuetracker.google.com/u/0/issues/316327367
                tint = LocalContentColor.current,
                modifier = Modifier.size(bottomBarIconSize),
            )
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavFlow.value = show
    }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(
            val toDownloads: Boolean,
            // KMK -->
            val toLibraryUpdateErrors: Boolean = false,
            // KMK <--
        ) : Tab
    }
}

@Composable
internal fun SubTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    hovered: Boolean = false,
    carouselStyle: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val subBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.subBarHeight().collectAsState().value
    val fontSize = (subBarHeight * 0.35f).coerceIn(6f, 14f).sp

    val scale by animateFloatAsState(
        targetValue = if (selected && carouselStyle) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "subTabScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected || !carouselStyle) 1.0f else 0.72f,
        animationSpec = tween(150),
        label = "subTabAlpha",
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (hovered) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(subBarHeight.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
            )
        }
    }
}

class ButtonActionBounds(
    val bounds: Rect,
    val action: () -> Unit,
)

fun Modifier.subTabBarGestureDetector(
    tab: cafe.adriel.voyager.navigator.tab.Tab,
    selected: Boolean,
    itemGlobalOffset: Offset,
    subTabButtonBounds: Map<String, ButtonActionBounds>,
    scope: kotlinx.coroutines.CoroutineScope,
    onHover: (String?) -> Unit,
    onHold: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = this.pointerInput(tab, selected, itemGlobalOffset) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val isSubBarTab = tab is HomeTab || tab is BrowseTab || tab is LibraryTab

            var isLongPressed = false
            val longPressJob = scope.launch {
                delay(400)
                isLongPressed = true
                if (!selected && isSubBarTab) {
                    onHold(true)
                } else {
                    onLongPress()
                }
            }

            var pointer = down
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val dragChange = event.changes.firstOrNull { it.id == pointer.id }
                if (dragChange == null || dragChange.pressed.not()) {
                    longPressJob.cancel()
                    if (isLongPressed) {
                        if (!selected && isSubBarTab) {
                            val releasePos = (dragChange?.position ?: pointer.position) + itemGlobalOffset
                            val hoveredButton = subTabButtonBounds.entries.find { it.value.bounds.contains(releasePos) }
                            if (hoveredButton != null) {
                                hoveredButton.value.action()
                            }
                            onHold(false)
                        }
                    } else {
                        onTap()
                    }
                    onHover(null)
                    break
                }
                pointer = dragChange
                if (isLongPressed && !selected && isSubBarTab) {
                    val currentPos = dragChange.position + itemGlobalOffset
                    val hoveredButton = subTabButtonBounds.entries.find { it.value.bounds.contains(currentPos) }
                    onHover(hoveredButton?.key)
                }
            }
        }
    }
}

@Composable
fun EditCategoryPopup(
    category: Category,
    categories: List<Category>,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val renameCategory = remember { Injekt.get<RenameCategory>() }
    val deleteCategory = remember { Injekt.get<DeleteCategory>() }
    val hideCategory = remember { Injekt.get<HideCategory>() }

    var name by remember { mutableStateOf(category.name) }
    var parentId by remember { mutableStateOf(category.parentId) }
    val isHidden = category.hidden

    val parentOptions = remember(categories, category) {
        categories
            .filter { it.parentId == null }
            .filterNot { it.isSystemCategory || it.id == category.id }
    }

    var showParentDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window?.let {
                    it.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    it.attributes.blurBehindRadius = 60
                    it.setDimAmount(0.15f)
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(28.dp)
                .wrapContentHeight(),
        ) {
            GlassSurface(
                shape = RoundedCornerShape(28.dp),
                style = GlassDefaults.prominentStyle(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Edit Category",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(text = stringResource(MR.strings.name)) },
                        singleLine = true,
                    )

                    // Parent selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showParentDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val parentName = parentOptions.find { it.id == parentId }?.name ?: "None (Parent Category)"
                            Text(text = "Parent: $parentName")
                        }
                        DropdownMenu(
                            expanded = showParentDropdown,
                            onDismissRequest = { showParentDropdown = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Parent Category)") },
                                onClick = {
                                    parentId = null
                                    showParentDropdown = false
                                },
                            )
                            parentOptions.forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text(parent.name) },
                                    onClick = {
                                        parentId = parent.id
                                        showParentDropdown = false
                                    },
                                )
                            }
                        }
                    }

                    // Hide / Show and Delete Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        hideCategory.await(category)
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                    onDismissRequest()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = if (isHidden) "Show" else "Hide")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        deleteCategory.await(category.id)
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                    onDismissRequest()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(MR.strings.action_delete))
                        }
                    }

                    // Save / Cancel Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        renameCategory.await(category, name, parentId)
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                    onDismissRequest()
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabActionGroup(
    currentTab: Tab,
    isVertical: Boolean,
    bottomBarButtonSize: androidx.compose.ui.unit.Dp,
    bottomBarIconSize: androidx.compose.ui.unit.Dp,
    onActionExecuted: () -> Unit,
) {
    val buttonModifier = Modifier.size(bottomBarButtonSize)
    val iconModifier = Modifier.size(bottomBarIconSize)

    when (currentTab) {
        is LibraryTab -> {
            var showLibraryMoreMenu by remember { mutableStateOf(false) }

            IconButton(
                onClick = {
                    LibraryTab.searchEvent.trySend(Unit)
                    onActionExecuted()
                },
                modifier = buttonModifier,
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", modifier = iconModifier)
            }
            IconButton(
                onClick = {
                    LibraryTab.filterSettingsEvent.trySend(Unit)
                    onActionExecuted()
                },
                modifier = buttonModifier,
            ) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filter", modifier = iconModifier)
            }
            Box {
                IconButton(
                    onClick = { showLibraryMoreMenu = true },
                    modifier = buttonModifier,
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More Options", modifier = iconModifier)
                }
                DropdownMenu(
                    expanded = showLibraryMoreMenu,
                    onDismissRequest = { showLibraryMoreMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Update library") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.globalUpdateEvent.trySend(Unit)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Update category") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.categoryUpdateEvent.trySend(Unit)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Open random entry") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.randomMangaEvent.trySend(Unit)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reindex download") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.reindexDownloadEvent.trySend(Unit)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sync EH favorites") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.syncFavoritesEvent.trySend(Unit)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sync library") },
                        onClick = {
                            showLibraryMoreMenu = false
                            onActionExecuted()
                            LibraryTab.syncEvent.trySend(Unit)
                        },
                    )
                }
            }
        }
        is HomeTab -> {
            when (HomeTab.currentPageIndex) {
                1 -> { // Feed
                    IconButton(
                        onClick = {
                            HomeTab.addFeedEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add Feed", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.sortFeedEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "Sort Feed", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.bulkSelectEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Checklist, contentDescription = "Bulk Select", modifier = iconModifier)
                    }
                }
                2 -> { // Suggestions
                    IconButton(
                        onClick = {
                            HomeTab.suggestionsRefreshEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Suggestions", modifier = iconModifier)
                    }
                }
                3 -> { // Updates
                    IconButton(
                        onClick = {
                            HomeTab.updatesUpdateLibraryEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Update Library", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.updatesCalendarEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.updatesFilterEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter Updates", modifier = iconModifier)
                    }
                }
                4 -> { // History
                    IconButton(
                        onClick = {
                            HomeTab.historySearchEvent.trySend(null)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search History", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.historyFilterEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter History", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            HomeTab.historyChecklistEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Checklist, contentDescription = "Clear History", modifier = iconModifier)
                    }
                }
            }
        }
        is BrowseTab -> {
            when (BrowseTab.currentPageIndex) {
                0 -> { // Sources
                    IconButton(
                        onClick = {
                            BrowseTab.sourcesGlobalSearchEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.TravelExplore, contentDescription = "Global Search", modifier = iconModifier)
                    }
                    val nsfwTint = if (BrowseTab.sourcesNsfwOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(
                        onClick = {
                            BrowseTab.sourcesNsfwToggleEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined._18UpRating, contentDescription = "NSFW Toggle", modifier = iconModifier, tint = nsfwTint)
                    }
                    IconButton(
                        onClick = {
                            BrowseTab.sourcesFilterEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter Sources", modifier = iconModifier)
                    }
                }
                1 -> { // Extensions
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsSearchEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search Extensions", modifier = iconModifier)
                    }
                    val nsfwTint = if (BrowseTab.extensionsNsfwOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsNsfwToggleEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined._18UpRating, contentDescription = "NSFW Toggle", modifier = iconModifier, tint = nsfwTint)
                    }
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsWebViewRefreshEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Extensions", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsFilterEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter Extensions", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsReposEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = "Repos", modifier = iconModifier)
                    }
                    IconButton(
                        onClick = {
                            BrowseTab.extensionsInstallJarEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Outlined.Extension, contentDescription = "Install Kotatsu JAR", modifier = iconModifier)
                    }
                }
                2 -> { // Migrate
                    IconButton(
                        onClick = {
                            BrowseTab.migrateHelpEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Help Guide", modifier = iconModifier)
                    }
                }
                3 -> { // Duplicate
                    IconButton(
                        onClick = {
                            eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateTab.resolveDuplicatesEvent.trySend(Unit)
                            onActionExecuted()
                        },
                        modifier = buttonModifier,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Resolve Duplicates", modifier = iconModifier)
                    }
                }
            }
        }
        else -> {}
    }
}
