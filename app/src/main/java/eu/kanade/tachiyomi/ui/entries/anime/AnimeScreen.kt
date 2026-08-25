package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.hasCustomBackground
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.anime.AnimeScreen
import eu.kanade.presentation.entries.anime.DubbingSelectionDialog
import eu.kanade.presentation.entries.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.entries.anime.EpisodeSettingsDialog
import eu.kanade.presentation.entries.anime.SeasonSettingsDialog
import eu.kanade.presentation.entries.anime.components.AnimeImagesDialog
import eu.kanade.presentation.entries.components.aurora.AuroraNoteEditorDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.anime.isLocalOrStub
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeScreen(
    private val animeId: Long,
    val fromSource: Boolean = false,
    private val externalScreenModel: AnimeScreenModel? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val updateAnime = remember { Injekt.get<UpdateAnime>() }
        val screenModel =
            externalScreenModel
                ?: rememberScreenModel { AnimeScreenModel(context, lifecycleOwner.lifecycle, animeId, fromSource) }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is AnimeScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeScreenModel.State.Success
        val isAnimeHttpSource = remember { successState.source is AnimeHttpSource }
        var showNotesDialog by remember { mutableStateOf(false) }

        LaunchedEffect(successState.anime, screenModel.source) {
            if (isAnimeHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAnimeUrl(screenModel.anime, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get anime URL" }
                }
            }
        }

        AnimeScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.anime.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            episodeSwipeStartAction = screenModel.episodeSwipeStartAction,
            episodeSwipeEndAction = screenModel.episodeSwipeEndAction,
            showNextEpisodeAirTime = screenModel.showNextEpisodeAirTime,
            alwaysUseExternalPlayer = screenModel.alwaysUseExternalPlayer,
            navigateUp = navigator::pop,
            onEpisodeClicked = { episode, alt ->
                scope.launchIO {
                    val real = screenModel.resolveEpisodeForOpen(episode)
                    if (screenModel.alwaysAskOnEpisodeClick) {
                        screenModel.showQualitiesDialog(real)
                    } else {
                        val extPlayer = screenModel.alwaysUseExternalPlayer != alt
                        openEpisode(context, real, extPlayer)
                    }
                }
            },
            onDownloadEpisode = screenModel::runEpisodeDownloadActions.takeIf {
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
            },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openAnimeInWebView(
                    navigator,
                    screenModel.anime,
                    screenModel.source,
                )
            }.takeIf { isAnimeHttpSource },
            onWebViewLongClicked = {
                copyAnimeUrl(
                    context,
                    screenModel.anime,
                    screenModel.source,
                )
            }.takeIf { isAnimeHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            }.takeIf { successState.anime.fetchType == FetchType.Episodes },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onGenreClick = { genre -> scope.launch { performGenreSearch(navigator, genre, screenModel.source!!) } },
            onGenreLongClick = null,
            onGenresSearch = { genres ->
                scope.launch { performGenresSearch(navigator, genres, screenModel.source!!) }
            },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueWatching = {
                scope.launchIO {
                    val extPlayer = screenModel.alwaysUseExternalPlayer
                    continueWatching(context, screenModel.getNextUnseenEpisode(), extPlayer)
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showImagesDialog,
            onShareClicked = {
                shareAnime(
                    context,
                    screenModel.anime,
                    screenModel.source,
                )
            }.takeIf { isAnimeHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf {
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
            },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.anime.favorite },
            onEditFetchIntervalClicked = screenModel::showSetAnimeFetchIntervalDialog.takeIf {
                successState.anime.favorite
            },
            onEditNotesClicked = {
                showNotesDialog = true
            },
            onClickEditInfo = null,
            onMigrateClicked = {
                navigator.push(MigrateSearchScreen(successState.anime.id))
            }.takeIf { successState.anime.favorite },
            changeAnimeSkipIntro = null,
            onMultiBookmarkClicked = screenModel::bookmarkEpisodes,
            onMultiFillermarkClicked = screenModel::fillermarkEpisodes,
            onMultiMarkAsSeenClicked = screenModel::markEpisodesSeen,
            onMarkPreviousAsSeenClicked = screenModel::markPreviousEpisodeSeen,
            onMultiDeleteClicked = screenModel::showDeleteEpisodeDialog,
            onEpisodeSwipe = screenModel::episodeSwipe,
            onEpisodeSelected = screenModel::toggleSelection,
            onAllEpisodeSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onSeasonClicked = {
                navigator.push(AnimeScreen(it.id))
            },
            onContinueWatchingClicked = {
                scope.launchIO {
                    val episode = screenModel.getNextUnseenEpisode(it.anime)
                    episode?.let { ep ->
                        openEpisode(context, ep, screenModel.alwaysUseExternalPlayer)
                    }
                }
            },
            onDubbingClicked = {
                screenModel.showDubbingDialog()
            }.takeIf { successState.availableDubbings.isNotEmpty() },
            selectedDubbing = buildString {
                val player = screenModel.getPreferredPlayer()
                val dubbing = screenModel.getPreferredDubbing()
                if (player != eu.kanade.tachiyomi.ui.player.PlaybackPlayerPreference.AUTO) {
                    append(player.name)
                    if (dubbing.isNotBlank()) append(" • ")
                }
                if (dubbing.isNotBlank()) append(dubbing)
            }.takeIf { it.isNotBlank() },
            onRetryMetadata = screenModel::retryMetadataLoad,
            onSuggestionClick = { item ->
                navigator.push(
                    eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen(
                        item.title,
                    ),
                )
            },
            onRetrySuggestions = screenModel::retrySuggestions,
            onOpenSuggestions = {
                navigator.push(
                    eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen(
                        successState.anime.title,
                    ),
                )
            },
        )

        val onDismissRequest = {
            if (screenModel.isFromChangeCategory) {
                screenModel.isFromChangeCategory = false
            }
            screenModel.dismissDialog()
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen(1)) },
                    onConfirm = { include, _ ->
                        screenModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                    },
                    onDuplicateCheck = {
                        onDismissRequest()
                        navigator.push(eu.kanade.tachiyomi.ui.browse.anime.duplicate.DuplicateAnimeScreen(dialog.anime.id))
                    },
                    onDelete = {
                        screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false)
                    }.takeIf { dialog.anime.favorite },
                    anime = dialog.anime,
                    onCreateCategory = screenModel::createCategory,
                )
            }
            is AnimeScreenModel.Dialog.DeleteEpisodes -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteEpisodes(dialog.episodes)
                    },
                )
            }

            is AnimeScreenModel.Dialog.DuplicateAnime -> {
                screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false)
                onDismissRequest()
            }

            is AnimeScreenModel.Dialog.Migrate -> {
                onDismissRequest()
            }
            AnimeScreenModel.Dialog.EpisodeSettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                downloadedOnly = successState.downloadedOnly,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnseenFilterChanged = screenModel::setUnseenFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onFillermarkedFilterChanged = screenModel::setFillermarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onShowPreviewsEnabled = screenModel::showEpisodePreviews,
                onShowSummariesEnabled = screenModel::showEpisodeSummaries,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
            )
            AnimeScreenModel.Dialog.SeasonSettingsSheet -> SeasonSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                downloadedOnly = successState.downloadedOnly,
                onDownloadFilterChanged = screenModel::setSeasonDownloadedFilter,
                onUnseenFilterChanged = screenModel::setSeasonUnseenFilter,
                onStartedFilterChanged = screenModel::setSeasonStartedFilter,
                onCompletedFilterChanged = screenModel::setSeasonCompletedFilter,
                onBookmarkedFilterChanged = screenModel::setSeasonBookmarkedFilter,
                onFillermarkedFilterChanged = screenModel::setSeasonFillermarkedFilter,
                onSortModeChanged = screenModel::setSeasonSorting,
                onDisplayGridModeChanged = screenModel::setSeasonDisplayGridMode,
                onDisplayGridSizeChanged = screenModel::setSeasonDisplayGridSize,
                onOverlayDownloadedChanged = screenModel::setSeasonDownloadOverlay,
                onOverlayUnseenChanged = screenModel::setSeasonUnseenOverlay,
                onOverlayLocalChanged = screenModel::setSeasonLocalOverlay,
                onOverlayLangChanged = screenModel::setSeasonLangOverlay,
                onOverlayContinueChanged = screenModel::setSeasonContinueOverlay,
                onDisplayModeChanged = screenModel::setSeasonDisplayMode,
                onSetAsDefault = screenModel::setSeasonCurrentSettingsAsDefault,
            )
            AnimeScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = AnimeTrackInfoDialogHomeScreen(
                        animeId = successState.anime.id,
                        animeTitle = successState.anime.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is AnimeTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            AnimeScreenModel.Dialog.FullImages -> {
                val sm = rememberScreenModel { AnimeImageScreenModel(successState.anime.id) }
                val anime by sm.state.collectAsStateWithLifecycle()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editImage(context, it)
                    }
                    AnimeImagesDialog(
                        anime = anime!!,
                        snackbarHostState = sm.snackbarHostState,
                        pagerState = sm.pagerState,
                        isCustomCover = remember(anime) { anime!!.hasCustomCover(sm.coverCache) },
                        isCustomBackground = remember(anime) { anime!!.hasCustomBackground(sm.backgroundCache) },
                        onShareClick = { sm.shareImage(context) },
                        onSaveClick = { sm.saveImage(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomImage(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is AnimeScreenModel.Dialog.SetAnimeFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.anime.fetchInterval,
                    nextUpdate = dialog.anime.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.anime, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            AnimeScreenModel.Dialog.ChangeAnimeSkipIntro -> {
                onDismissRequest()
            }
            is AnimeScreenModel.Dialog.ShowQualities -> {
                EpisodeOptionsDialogScreen.onDismissDialog = onDismissRequest
                val episodeTitle = if (dialog.anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                    stringResource(
                        MR.strings.display_mode_chapter,
                        formatEpisodeNumber(dialog.episode.episodeNumber),
                    )
                } else {
                    dialog.episode.name
                }
                NavigatorAdaptiveSheet(
                    screen = EpisodeOptionsDialogScreen(
                        useExternalDownloader = screenModel.useExternalDownloader,
                        episodeTitle = episodeTitle,
                        episodeId = dialog.episode.id,
                        animeId = dialog.anime.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
            is AnimeScreenModel.Dialog.SelectDubbing -> {
                DubbingSelectionDialog(
                    availableDubbings = dialog.availableDubbings,
                    currentPreferences = dialog.currentPreferences,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { preferences ->
                        screenModel.setPlaybackSelectionPreferences(preferences)
                        onDismissRequest()
                    },
                )
            }
        }

        if (showNotesDialog) {
            AuroraNoteEditorDialog(
                initialText = successState.anime.notes,
                onDismissRequest = { showNotesDialog = false },
                onSave = { notes ->
                    scope.launchIO {
                        updateAnime.await(
                            AnimeUpdate(
                                id = successState.anime.id,
                                notes = notes,
                            ),
                        )
                    }
                },
            )
        }
    }

    private suspend fun continueWatching(
        context: Context,
        unseenEpisode: Episode?,
        useExternalPlayer: Boolean,
    ) {
        if (unseenEpisode != null) openEpisode(context, unseenEpisode, useExternalPlayer)
    }

    private suspend fun openEpisode(context: Context, episode: Episode, useExternalPlayer: Boolean) {
        withIOContext {
            val intent = PlayerActivity.newIntent(
                context = context,
                animeId = episode.animeId,
                episodeId = episode.id,
            )
            context.startActivity(intent)
        }
    }

    private fun getAnimeUrl(anime_: Anime?, source_: AnimeSource?): String? {
        val anime = anime_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(anime.toSAnime())
        } catch (e: Exception) {
            null
        }
    }

    private fun openAnimeInWebView(navigator: Navigator, anime_: Anime?, source_: AnimeSource?) {
        getAnimeUrl(anime_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = anime_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareAnime(context: Context, anime_: Anime?, source_: AnimeSource?) {
        try {
            getAnimeUrl(anime_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                LibraryTab.search(query)
            }
            else -> {
                navigator.push(GlobalSearchScreen(query))
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: AnimeSource,
    ) {
        navigator.push(GlobalSearchScreen(genreName))
    }

    private suspend fun performGenresSearch(
        navigator: Navigator,
        genres: List<String>,
        source: AnimeSource,
    ) {
        if (genres.isEmpty()) return
        navigator.push(GlobalSearchScreen(genres.joinToString(" ")))
    }

    /**
     * Copy Anime URL to Clipboard
     */
    private fun copyAnimeUrl(context: Context, anime_: Anime?, source_: AnimeSource?) {
        val anime = anime_ ?: return
        val source = source_ as? AnimeHttpSource ?: return
        val url = source.getAnimeUrl(anime.toSAnime())
        context.copyToClipboard(url, url)
    }
}
