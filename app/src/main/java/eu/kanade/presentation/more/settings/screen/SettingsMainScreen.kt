package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import exh.assets.EhAssets
import exh.assets.ehassets.EhLogo
import exh.assets.ehassets.MangadexLogo
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsMainScreen

    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    private fun getPalerSurface(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val dark = isSystemInDarkTheme()
        return remember(surface, dark) {
            val arr = FloatArray(3)
            ColorUtils.colorToHSL(surface.toArgb(), arr)
            arr[2] = if (dark) {
                arr[2] - 0.05f
            } else {
                arr[2] + 0.02f
            }.coerceIn(0f, 1f)
            Color.hsl(arr[0], arr[1], arr[2])
        }
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                // SY -->
                val items = items.filter { it.screen !is SearchableSettings || it.screen.isEnabled() }
                // SY <--
                val indexSelected = if (twoPane) {
                    items.indexOfFirst { it.screen::class == navigator.items.first()::class }
                        .also {
                            LaunchedEffect(Unit) {
                                state.animateScrollToItem(it)
                                if (it > 0) {
                                    topBarState.contentOffset = topBarState.heightOffsetLimit
                                }
                            }
                        }
                } else {
                    null
                }

                LazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> "settings-domain-${item.hashCode()}" },
                    ) { index, item ->
                        val selected = indexSelected == index
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { navigator.navigate(item.screen, twoPane) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                            },
                            tonalElevation = if (selected) 4.dp else 1.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Leading Pastel Icon Badge
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                item.badgeColor
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            item.badgeIconTint
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Title and Rich Subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(item.titleRes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.formatSubtitle(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource,
        val formatSubtitle: @Composable () -> String = { stringResource(subtitleRes) },
        val icon: ImageVector,
        val screen: VoyagerScreen,
        val badgeColor: Color,
        val badgeIconTint: Color,
    )

    private val items = listOf(
        Item(
            titleRes = MR.strings.pref_category_appearance,
            subtitleRes = MR.strings.pref_appearance_summary,
            icon = Icons.Outlined.Palette,
            screen = SettingsAppearanceScreen,
            badgeColor = Color(0xFFE8DEF8),
            badgeIconTint = Color(0xFF6750A4),
        ),
        Item(
            titleRes = MR.strings.pref_category_library,
            subtitleRes = MR.strings.pref_library_summary,
            icon = Icons.Outlined.CollectionsBookmark,
            screen = SettingsLibraryScreen,
            badgeColor = Color(0xFFD0E4FF),
            badgeIconTint = Color(0xFF0061A4),
        ),
        Item(
            titleRes = MR.strings.pref_category_reader,
            subtitleRes = MR.strings.pref_reader_summary,
            icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
            screen = SettingsReaderScreen,
            badgeColor = Color(0xFFFFDBCF),
            badgeIconTint = Color(0xFF8F4C38),
        ),
        Item(
            titleRes = KMR.strings.pref_category_novel_reader,
            subtitleRes = KMR.strings.pref_novel_reader_summary,
            icon = Icons.Outlined.Book,
            screen = SettingsNovelReaderScreen,
            badgeColor = Color(0xFFE2E2D5),
            badgeIconTint = Color(0xFF5E6050),
        ),
        Item(
            titleRes = KMR.strings.label_player,
            subtitleRes = KMR.strings.pref_player_settings_summary,
            icon = Icons.Outlined.VideoSettings,
            screen = PlayerSettingsScreen(mainSettings = true),
            badgeColor = Color(0xFFFFD8E4),
            badgeIconTint = Color(0xFF8C4A60),
        ),
        // KMK (AI & Smart Tools) -->
        Item(
            titleRes = KMR.strings.pref_category_ai_tools,
            subtitleRes = KMR.strings.pref_ai_tools_summary,
            icon = Icons.Outlined.AutoAwesome,
            screen = SettingsAiToolsScreen,
            badgeColor = Color(0xFFC4EED0),
            badgeIconTint = Color(0xFF146C2E),
        ),
        // KMK <--
        Item(
            titleRes = MR.strings.browse,
            subtitleRes = MR.strings.pref_browse_summary,
            icon = Icons.Outlined.Explore,
            screen = SettingsBrowseScreen,
            badgeColor = Color(0xFFFFE088),
            badgeIconTint = Color(0xFF755B00),
        ),
        Item(
            titleRes = MR.strings.pref_category_tracking,
            subtitleRes = MR.strings.pref_tracking_summary,
            icon = Icons.Outlined.Sync,
            screen = SettingsTrackingScreen,
            badgeColor = Color(0xFFD7E3FF),
            badgeIconTint = Color(0xFF2E5DA8),
        ),
        // AM (CONNECTIONS) -->
        Item(
            titleRes = KMR.strings.pref_category_connections,
            subtitleRes = KMR.strings.pref_connections_summary,
            icon = Icons.Outlined.Link,
            screen = SettingsConnectionScreen,
            badgeColor = Color(0xFFF3E5F5),
            badgeIconTint = Color(0xFF7B1FA2),
        ),
        // <-- AM (CONNECTIONS)
        Item(
            titleRes = MR.strings.pref_category_downloads,
            subtitleRes = MR.strings.pref_downloads_summary,
            icon = Icons.Outlined.GetApp,
            screen = SettingsDownloadScreen,
            badgeColor = Color(0xFFE0F2F1),
            badgeIconTint = Color(0xFF00796B),
        ),
        Item(
            titleRes = MR.strings.label_data_storage,
            subtitleRes = MR.strings.pref_backup_summary,
            icon = Icons.Outlined.Storage,
            screen = SettingsDataScreen,
            badgeColor = Color(0xFFEFEBE9),
            badgeIconTint = Color(0xFF5D4037),
        ),
        Item(
            titleRes = MR.strings.pref_category_security,
            subtitleRes = MR.strings.pref_security_summary,
            icon = Icons.Outlined.Security,
            screen = SettingsSecurityScreen,
            badgeColor = Color(0xFFFFEBEE),
            badgeIconTint = Color(0xFFC62828),
        ),
        Item(
            titleRes = KMR.strings.pref_category_vpn,
            subtitleRes = KMR.strings.pref_vpn_summary,
            icon = Icons.Outlined.VpnKey,
            screen = SettingsVpnScreen,
            badgeColor = Color(0xFFEDE7F6),
            badgeIconTint = Color(0xFF512DA8),
        ),
        // SY -->
        Item(
            titleRes = SYMR.strings.pref_category_eh,
            subtitleRes = SYMR.strings.pref_ehentai_summary,
            icon = EhAssets.EhLogo,
            screen = SettingsEhScreen,
            badgeColor = Color(0xFFFFF8E1),
            badgeIconTint = Color(0xFFF57F17),
        ),
        Item(
            titleRes = SYMR.strings.pref_category_mangadex,
            subtitleRes = SYMR.strings.pref_mangadex_summary,
            icon = EhAssets.MangadexLogo,
            screen = SettingsMangadexScreen,
            badgeColor = Color(0xFFFFECB3),
            badgeIconTint = Color(0xFFFF6F00),
        ),
        // SY <--
        Item(
            titleRes = MR.strings.pref_category_advanced,
            subtitleRes = MR.strings.pref_advanced_summary,
            icon = Icons.Outlined.Code,
            screen = SettingsAdvancedScreen,
            badgeColor = Color(0xFFECEFF1),
            badgeIconTint = Color(0xFF455A64),
        ),
        Item(
            titleRes = MR.strings.pref_category_about,
            subtitleRes = StringResource(0),
            formatSubtitle = {
                "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
            },
            icon = Icons.Outlined.Info,
            screen = AboutScreen(),
            badgeColor = Color(0xFFFFF3E0),
            badgeIconTint = Color(0xFFE65100),
        ),
    )
}
