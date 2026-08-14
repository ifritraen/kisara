package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.history.components.HistoryFilterDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object HistoryTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = HistoryTab

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    // SY -->
    @Composable
    override fun isEnabled(): Boolean {
        val scope = rememberCoroutineScope()
        return remember {
            Injekt.get<UiPreferences>().showNavHistory().asState(scope)
        }.value
    }
    // SY <--

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { HistoryScreenModel() }
        val state by screenModel.state.collectAsState()
        // KMK -->
        val settingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
        val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()
        // KMK <--

        HistoryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = screenModel::updateSearchQuery,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = screenModel::getNextChapterForManga,
            onDialogChange = screenModel::setDialog,
            onClickFavorite = screenModel::addFavorite,
            // KMK -->
            toggleSelectionMode = screenModel::toggleSelectionMode,
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onHistorySelected = screenModel::toggleSelection,
            onFilterClicked = screenModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
            usePanoramaCover = usePanoramaCover,
            // KMK <--
        )

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        // KMK -->
                        if (all) {
                            screenModel.removeAllFromHistory(dialog.histories)
                        } else {
                            screenModel.removeFromHistory(dialog.histories)
                        }
                        // KMK <--
                    },
                )
            }
            is HistoryScreenModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = screenModel::removeAllHistory,
                )
            }
            is HistoryScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(dialog.manga, it) },
                    // KMK -->
                    targetManga = dialog.manga,
                    // KMK <--
                )
            }
            is HistoryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                    onDuplicateCheck = {
                        onDismissRequest()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen(dialog.manga.id))
                    },
                )
            }
            is HistoryScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            // KMK -->
            is HistoryScreenModel.Dialog.FilterSheet -> {
                HistoryFilterDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                )
            }
            // KMK <--
            null -> {}
        }

        // KMK -->
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                // KMK <--
                (context as? MainActivity)?.ready = true

                // AM (DISCORD) -->
                with(DiscordRPCService) {
                    discordScope.launchIO { setScreen(context, DiscordScreen.HISTORY) }
                }
                // <-- AM (DISCORD)
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { e ->
                when (e) {
                    HistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryScreenModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, screenModel.getNextChapter())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
}

// KMK -->
@Composable
fun Screen.historyTab(
    screenModel: HistoryScreenModel,
    settingsScreenModel: HistorySettingsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_recent_manga,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                iconTint = if (state.hasActiveFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                onClick = screenModel::showFilterDialog,
            ),
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.Checklist,
                onClick = screenModel::toggleSelectionMode,
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            val context = LocalContext.current
            val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()

            val scope = rememberCoroutineScope()
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val delta = available.y
                        if (delta < -10f) {
                            scope.launch { HomeScreen.showBottomNav(false) }
                        } else if (delta > 10f) {
                            scope.launch { HomeScreen.showBottomNav(true) }
                        }
                        return Offset.Zero
                    }
                }
            }

            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                HistoryScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onSearchQueryChange = screenModel::updateSearchQuery,
                    onClickCover = { navigator.push(MangaScreen(it)) },
                    onClickResume = screenModel::getNextChapterForManga,
                    onDialogChange = screenModel::setDialog,
                    onClickFavorite = screenModel::addFavorite,
                    toggleSelectionMode = screenModel::toggleSelectionMode,
                    onSelectAll = screenModel::toggleAllSelection,
                    onInvertSelection = screenModel::invertSelection,
                    onHistorySelected = screenModel::toggleSelection,
                    onFilterClicked = screenModel::showFilterDialog,
                    hasActiveFilters = state.hasActiveFilters,
                    usePanoramaCover = usePanoramaCover,
                    showAppBar = false,
                )
            }

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is HistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                screenModel.removeAllFromHistory(dialog.histories)
                            } else {
                                screenModel.removeFromHistory(dialog.histories)
                            }
                        },
                    )
                }
                is HistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                is HistoryScreenModel.Dialog.DuplicateManga -> {
                    DuplicateMangaDialog(
                        duplicates = dialog.duplicates,
                        onDismissRequest = onDismissRequest,
                        onConfirm = { screenModel.addFavorite(dialog.manga) },
                        onOpenManga = { navigator.push(MangaScreen(it.id)) },
                        onMigrate = { screenModel.showMigrateDialog(dialog.manga, it) },
                        targetManga = dialog.manga,
                    )
                }
                is HistoryScreenModel.Dialog.ChangeCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = { navigator.push(CategoryScreen()) },
                        onConfirm = { include, _ ->
                            screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                        },
                        onDuplicateCheck = {
                            onDismissRequest()
                            navigator.push(eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen(dialog.manga.id))
                        },
                        onDeleteManga = {
                            screenModel.removeFavorite(dialog.manga)
                        },
                        manga = dialog.manga,
                    )
                }
                is HistoryScreenModel.Dialog.Migrate -> {
                    MigrateMangaDialog(
                        current = dialog.current,
                        target = dialog.target,
                        onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                        onDismissRequest = onDismissRequest,
                    )
                }
                is HistoryScreenModel.Dialog.FilterSheet -> {
                    HistoryFilterDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                    )
                }
                null -> {}
            }

            LaunchedEffect(state.selectionMode) {
                HomeScreen.showBottomNav(!state.selectionMode)
            }

            LaunchedEffect(state.isLoading) {
                if (!state.isLoading) {
                    (context as? MainActivity)?.ready = true
                    with(DiscordRPCService) {
                        discordScope.launchIO { setScreen(context, DiscordScreen.HISTORY) }
                    }
                }
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { e ->
                    when (e) {
                        HistoryScreenModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                        HistoryScreenModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                        is HistoryScreenModel.Event.OpenChapter -> {
                            val chapter = e.chapter
                            if (chapter != null) {
                                val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
                                context.startActivity(intent)
                            } else {
                                snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                eu.kanade.tachiyomi.ui.home.HomeTab.resumeHistoryEvent.receiveAsFlow().collectLatest {
                    val chapter = screenModel.getNextChapter()
                    if (chapter != null) {
                        val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
                        context.startActivity(intent)
                    } else {
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
                    }
                }
            }
        },
    )
}
// KMK <--
