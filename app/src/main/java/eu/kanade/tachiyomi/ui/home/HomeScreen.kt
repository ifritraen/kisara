package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.GlassDefaults
import eu.kanade.presentation.components.GlassSurface
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
    private val showBottomNavEvent = Channel<Boolean>()

    private const val TAB_FADE_DURATION = 200
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    private val TABS = listOf(
        HomeTab,
        LibraryTab,
        BrowseTab,
        MoreTab,
    )

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
        val subTabsBottomMargin by uiPreferences.subTabsBottomMargin().collectAsState()
        val bottomBarBottomMargin by uiPreferences.bottomBarBottomMargin().collectAsState()
        val bottomBarHeight by uiPreferences.bottomBarHeight().collectAsState()
        val hazeState = remember { HazeState() }
        var showActionPopup by remember { mutableStateOf(false) }
        var activeSubTabPopup by remember { mutableStateOf<cafe.adriel.voyager.navigator.tab.Tab?>(null) }
        val subTabButtonBounds = remember { mutableStateMapOf<String, ButtonActionBounds>() }
        var hoveredButtonKey by remember { mutableStateOf<String?>(null) }
        var popupSelectedCategoryId by remember { mutableStateOf<Long?>(null) }
        var categoryToEdit by remember { mutableStateOf<Category?>(null) }
        val categoriesState by remember { Injekt.get<GetCategories>().subscribe() }.collectAsState(initial = emptyList())

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            TabNavigator(
                tab = LibraryTab,
                key = TAB_NAVIGATOR_KEY,
            ) { tabNavigator ->
                LaunchedEffect(tabNavigator.current) {
                    showBottomNav(true)
                    showActionPopup = false
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
                                val bottomNavVisible by produceState(initialValue = true) {
                                    showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                }
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
                                                            if (key != null && key.startsWith("Library_") && !key.startsWith("Library_sub_")) {
                                                                val catId = key.removePrefix("Library_").toLongOrNull()
                                                                if (catId != null) {
                                                                    popupSelectedCategoryId = catId
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
                        Box(
                            modifier = Modifier
                                .padding(contentPadding)
                                .consumeWindowInsets(contentPadding)
                                .fillMaxSize(),
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
                                            materialFadeThroughIn(
                                                initialScale = 1f,
                                                durationMillis = TAB_FADE_DURATION,
                                            ) togetherWith
                                                materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
                                        },
                                        label = "tabContent",
                                        contentKey = { it.key },
                                    ) {
                                        tabNavigator.saveableState(key = "currentTab", it) {
                                            it.Content()
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
                                val bottomNavVisible by produceState(initialValue = true) {
                                    showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                }

                                // 1. Determine actions for the current tab
                                val currentTab = tabNavigator.current
                                val hasActions = when (currentTab) {
                                    is LibraryTab -> true
                                    is HomeTab -> HomeTab.currentPageIndex in 0..2
                                    is BrowseTab -> BrowseTab.currentPageIndex in 0..3
                                    else -> false
                                }

                                // 2. Determine which sub-tab popup is active
                                val activePopup = when {
                                    activeSubTabPopup != null -> activeSubTabPopup
                                    currentTab is HomeTab && alwaysShowSubTabsHome -> currentTab
                                    currentTab is BrowseTab && alwaysShowSubTabsBrowse -> currentTab
                                    else -> null
                                }

                                // 3. Sub-tab popup above bottom bar
                                AnimatedVisibility(
                                    visible = bottomNavVisible && activePopup != null,
                                    enter = expandVertically(expandFrom = Alignment.Bottom),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                                    modifier = Modifier
                                        .padding(bottom = ((if (floatingBottomBar) (72 - 12) else (80 - 12)) + bottomBarBottomMargin + subTabsBottomMargin).coerceAtLeast(0).dp)
                                        .align(Alignment.BottomCenter),
                                ) {
                                    val getCategories = remember { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.category.interactor.GetCategories>() }
                                    val categoriesState by produceState<List<tachiyomi.domain.category.model.Category>>(emptyList()) {
                                        getCategories.subscribe().collect { value = it }
                                    }
                                    val parentCategories = remember(categoriesState) {
                                        categoriesState.filter { it.parentId == null }.sortedBy { it.order }
                                    }
                                    val childrenByParent = remember(categoriesState) {
                                        categoriesState.filter { it.parentId != null }
                                            .groupBy { it.parentId }
                                            .mapValues { entry -> entry.value.sortedBy { it.order } }
                                    }
                                    val subcategories = popupSelectedCategoryId?.let { childrenByParent[it] }.orEmpty()

                                    GlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        style = GlassDefaults.regularStyle(),
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            if (activePopup is LibraryTab) {
                                                val editCategory = LocalEditCategory.current
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    if (subcategories.isNotEmpty()) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            subcategories.forEach { sub ->
                                                                val key = "Library_sub_${sub.id}"
                                                                SubTabButton(
                                                                    text = sub.visualName,
                                                                    selected = false,
                                                                    hovered = hoveredButtonKey == key,
                                                                    onLongClick = {
                                                                        editCategory(sub)
                                                                        activeSubTabPopup = null
                                                                    },
                                                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                        subTabButtonBounds[key] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                            tabNavigator.current = LibraryTab
                                                                            val actualIndex = categoriesState.indexOfFirst { it.id == sub.id }
                                                                            if (actualIndex != -1) {
                                                                                LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                            }
                                                                            activeSubTabPopup = null
                                                                        }
                                                                    },
                                                                ) {
                                                                    tabNavigator.current = LibraryTab
                                                                    val actualIndex = categoriesState.indexOfFirst { it.id == sub.id }
                                                                    if (actualIndex != -1) {
                                                                        LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                    }
                                                                    activeSubTabPopup = null
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        parentCategories.forEach { category ->
                                                            val key = "Library_${category.id}"
                                                            SubTabButton(
                                                                text = category.visualName,
                                                                selected = false,
                                                                hovered = hoveredButtonKey == key,
                                                                onLongClick = {
                                                                    editCategory(category)
                                                                    activeSubTabPopup = null
                                                                },
                                                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                    subTabButtonBounds[key] = ButtonActionBounds(coordinates.boundsInRoot()) {
                                                                        tabNavigator.current = LibraryTab
                                                                        val actualIndex = categoriesState.indexOfFirst { it.id == category.id }
                                                                        if (actualIndex != -1) {
                                                                            LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                        }
                                                                        activeSubTabPopup = null
                                                                    }
                                                                },
                                                            ) {
                                                                popupSelectedCategoryId = category.id
                                                                tabNavigator.current = LibraryTab
                                                                val actualIndex = categoriesState.indexOfFirst { it.id == category.id }
                                                                if (actualIndex != -1) {
                                                                    LibraryTab.selectCategoryEvent.trySend(actualIndex)
                                                                }
                                                            }
                                                        }

                                                        val categoryBarPinnedPref = remember { Injekt.get<tachiyomi.domain.library.service.LibraryPreferences>().categoryBarPinned() }
                                                        val isCategoryBarPinned by categoryBarPinnedPref.collectAsState()
                                                        val scope = rememberCoroutineScope()
                                                        androidx.compose.material3.IconButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    categoryBarPinnedPref.set(!isCategoryBarPinned)
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp),
                                                        ) {
                                                            androidx.compose.material3.Icon(
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
                                                                text = "Feed",
                                                                selected = HomeTab.currentPageIndex == 0,
                                                                hovered = hoveredButtonKey == "Home_Feed",
                                                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                    subTabButtonBounds["Home_Feed"] = ButtonActionBounds(coordinates.boundsInRoot()) {
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
                                                                text = "Updates",
                                                                selected = HomeTab.currentPageIndex == 1,
                                                                hovered = hoveredButtonKey == "Home_Updates",
                                                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                    subTabButtonBounds["Home_Updates"] = ButtonActionBounds(coordinates.boundsInRoot()) {
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
                                                                text = "History",
                                                                selected = HomeTab.currentPageIndex == 2,
                                                                hovered = hoveredButtonKey == "Home_History",
                                                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                                                    subTabButtonBounds["Home_History"] = ButtonActionBounds(coordinates.boundsInRoot()) {
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
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Render the actual bottom bar (with in-place actions)
                                AnimatedVisibility(
                                    visible = bottomNavVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                    modifier = Modifier
                                        .padding(bottom = bottomBarBottomMargin.coerceAtLeast(0).dp)
                                        .align(Alignment.BottomCenter),
                                ) {
                                    val bottomBarButtonSize = (bottomBarHeight * 0.45f).coerceAtLeast(8f).dp
                                    val bottomBarIconSize = (bottomBarHeight * 0.25f).coerceAtLeast(6f).dp
                                    GlassSurface(
                                        shape = RoundedCornerShape(24.dp),
                                        style = GlassDefaults.prominentStyle(),
                                        modifier = Modifier.height(bottomBarHeight.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            // Left part: The 4 Navigation Tabs
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                                                    if (key != null && key.startsWith("Library_") && !key.startsWith("Library_sub_")) {
                                                                        val catId = key.removePrefix("Library_").toLongOrNull()
                                                                        if (catId != null) {
                                                                            popupSelectedCategoryId = catId
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
                                                                    } else if (tab !is HomeTab && tab !is BrowseTab) {
                                                                        activeSubTabPopup = null
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
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        CompositionLocalProvider(LocalContentColor provides tint) {
                                                            NavigationIconItem(tab)
                                                        }
                                                    }
                                                }
                                            }

                                            // Divider & Right part: Contextual Action Buttons in-place
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

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        when (currentTab) {
                                                            is LibraryTab -> {
                                                                var showLibraryMoreMenu by remember { mutableStateOf(false) }

                                                                IconButton(
                                                                    onClick = {
                                                                        LibraryTab.searchEvent.trySend(Unit)
                                                                        showActionPopup = false
                                                                    },
                                                                    modifier = Modifier.size(bottomBarButtonSize),
                                                                ) {
                                                                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(bottomBarIconSize))
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        LibraryTab.filterSettingsEvent.trySend(Unit)
                                                                        showActionPopup = false
                                                                    },
                                                                    modifier = Modifier.size(bottomBarButtonSize),
                                                                ) {
                                                                    Icon(Icons.Outlined.FilterList, contentDescription = "Filter", modifier = Modifier.size(bottomBarIconSize))
                                                                }
                                                                Box {
                                                                    IconButton(
                                                                        onClick = { showLibraryMoreMenu = true },
                                                                        modifier = Modifier.size(bottomBarButtonSize),
                                                                    ) {
                                                                        Icon(Icons.Outlined.MoreVert, contentDescription = "More Options", modifier = Modifier.size(bottomBarIconSize))
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = showLibraryMoreMenu,
                                                                        onDismissRequest = { showLibraryMoreMenu = false },
                                                                    ) {
                                                                        DropdownMenuItem(
                                                                            text = { Text("Update library") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.globalUpdateEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("Update category") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.categoryUpdateEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("Open random entry") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.randomMangaEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("Reindex download") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.reindexDownloadEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("Sync EH favorites") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.syncFavoritesEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                        DropdownMenuItem(
                                                                            text = { Text("Sync library") },
                                                                            onClick = {
                                                                                showLibraryMoreMenu = false
                                                                                showActionPopup = false
                                                                                LibraryTab.syncEvent.trySend(Unit)
                                                                            },
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            is HomeTab -> {
                                                                when (HomeTab.currentPageIndex) {
                                                                    0 -> { // Feed
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.addFeedEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Add, contentDescription = "Add Feed", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.sortFeedEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.SwapVert, contentDescription = "Sort Feed", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.bulkSelectEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Checklist, contentDescription = "Bulk Select", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                    1 -> { // Updates
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.updatesUpdateLibraryEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Refresh, contentDescription = "Update Library", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.updatesCalendarEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.updatesFilterEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter Updates", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                    2 -> { // History
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.historySearchEvent.trySend(null)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Default.Search, contentDescription = "Search History", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.historyFilterEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter History", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                HomeTab.historyChecklistEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Checklist, contentDescription = "Clear History", modifier = Modifier.size(20.dp))
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
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.TravelExplore, contentDescription = "Global Search", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        val nsfwTint = if (BrowseTab.sourcesNsfwOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.sourcesNsfwToggleEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined._18UpRating, contentDescription = "NSFW Toggle", modifier = Modifier.size(20.dp), tint = nsfwTint)
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.sourcesFilterEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter Sources", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                    1 -> { // Extensions
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.extensionsSearchEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Default.Search, contentDescription = "Search Extensions", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        val nsfwTint = if (BrowseTab.extensionsNsfwOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.extensionsNsfwToggleEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined._18UpRating, contentDescription = "NSFW Toggle", modifier = Modifier.size(20.dp), tint = nsfwTint)
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.extensionsWebViewRefreshEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Extensions", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.extensionsFilterEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter Extensions", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.extensionsReposEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Outlined.Folder, contentDescription = "Repos", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                    2 -> { // Migrate
                                                                        IconButton(
                                                                            onClick = {
                                                                                BrowseTab.migrateHelpEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Help Guide", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                    3 -> { // Duplicate
                                                                        IconButton(
                                                                            onClick = {
                                                                                eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateTab.resolveDuplicatesEvent.trySend(Unit)
                                                                                showActionPopup = false
                                                                            },
                                                                            modifier = Modifier.size(36.dp),
                                                                        ) {
                                                                            Icon(Icons.Default.Check, contentDescription = "Resolve Duplicates", modifier = Modifier.size(20.dp))
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
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
        showBottomNavEvent.send(show)
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
private fun SubTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    hovered: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val subBarHeight = remember { uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>() }.subBarHeight().collectAsState().value
    val fontSize = (subBarHeight * 0.35f).coerceIn(6f, 14f).sp
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
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
