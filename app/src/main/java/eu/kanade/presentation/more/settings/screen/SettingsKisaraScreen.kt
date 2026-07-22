package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsBlockedTagsScreen
import eu.kanade.presentation.more.settings.screen.SettingsHomeScreen
import eu.kanade.presentation.more.settings.screen.SettingsSuggestionsScreen
import eu.kanade.presentation.more.settings.screen.SettingsVpnScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsKisaraScreen : SearchableSettings {

    @Suppress("unused")
    private fun readResolve(): Any = SettingsKisaraScreen

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes(): StringResource = KMR.strings.label_kisara_settings

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        // KMK -->
        val sourcePrefs = remember { Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }
        val fuzzyThresholdPref = sourcePrefs.searchFuzzyThreshold()
        val fuzzyThreshold by fuzzyThresholdPref.collectAsState()
        // KMK <--
        val parallelChapterLimit by downloadPreferences.parallelChapterLimit().collectAsState()
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit().collectAsState()
        val parallelPageLimit by downloadPreferences.parallelPageLimit().collectAsState()

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, context.getString(MR.strings.file_select_backup.resourceId))
                }
            },
        ) { uri ->
            if (uri == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }
            navigator.push(RestoreBackupScreen(uri.toString()))
        }

        val chooseJar = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, "Select Kotatsu JAR Extension")
                }
            },
        ) { uri ->
            if (uri != null) {
                if (eu.kanade.tachiyomi.extension.JarExtensionManager.installJar(context, uri)) {
                    context.toast("Installed Kotatsu JAR Extension successfully")
                } else {
                    context.toast("Failed to install Kotatsu JAR Extension")
                }
            }
        }

        val floatingBottomBarPref = uiPreferences.floatingBottomBar()
        val floatingBottomBar by floatingBottomBarPref.collectAsState()

        val bottomBarOpacityPref = uiPreferences.bottomBarOpacity()
        val bottomBarOpacity by bottomBarOpacityPref.collectAsState()

        val bottomBarBlurPref = uiPreferences.bottomBarBlur()
        val bottomBarBlur by bottomBarBlurPref.collectAsState()

        val showCategoryTabsPref = uiPreferences.showCategoryTabs()
        val showCategoryTabs by showCategoryTabsPref.collectAsState()

        val showTopTabBarPref = uiPreferences.showTopTabBar()
        val showTopTabBar by showTopTabBarPref.collectAsState()

        val subTabsBottomMarginPref = uiPreferences.subTabsBottomMargin()
        val subTabsBottomMargin by subTabsBottomMarginPref.collectAsState()

        val bottomBarBottomMarginPref = uiPreferences.bottomBarBottomMargin()
        val bottomBarBottomMargin by bottomBarBottomMarginPref.collectAsState()

        val kisaraGlassColorTypePref = uiPreferences.kisaraGlassColorType()
        val kisaraGlassColorType by kisaraGlassColorTypePref.collectAsState()

        val kisaraGlassColorMixPref = uiPreferences.kisaraGlassColorMix()
        val kisaraGlassColorMix by kisaraGlassColorMixPref.collectAsState()

        val kisaraGlassCustomColorPref = uiPreferences.kisaraGlassCustomColor()
        val kisaraGlassCustomColor by kisaraGlassCustomColorPref.collectAsState()

        val readerAppBarOpacityPref = uiPreferences.readerAppBarOpacity()
        val readerAppBarOpacity by readerAppBarOpacityPref.collectAsState()

        val readerAppBarColorMixPref = uiPreferences.readerAppBarColorMix()
        val readerAppBarColorMix by readerAppBarColorMixPref.collectAsState()

        var showColorPicker by remember { mutableStateOf(false) }

        val kisaraShowItemCountInTabsPref = uiPreferences.kisaraShowItemCountInTabs()
        val kisaraShowItemCountInTabs by kisaraShowItemCountInTabsPref.collectAsState()

        val categoryBarSelectedFontColorTypePref = uiPreferences.categoryBarSelectedFontColorType()
        val categoryBarSelectedFontColorType by categoryBarSelectedFontColorTypePref.collectAsState()

        val categoryBarSelectedFontCustomColorPref = uiPreferences.categoryBarSelectedFontCustomColor()
        val categoryBarSelectedFontCustomColor by categoryBarSelectedFontCustomColorPref.collectAsState()

        val bottomBarHeightPref = uiPreferences.bottomBarHeight()
        val bottomBarHeight by bottomBarHeightPref.collectAsState()

        val subBarHeightPref = uiPreferences.subBarHeight()
        val subBarHeight by subBarHeightPref.collectAsState()

        val kisaraShowSubcategoriesInMainBarPref = uiPreferences.kisaraShowSubcategoriesInMainBar()
        val bottomBarWidthPref = uiPreferences.bottomBarWidth()
        val bottomBarWidth by bottomBarWidthPref.collectAsState()

        val bottomBarGapPref = uiPreferences.bottomBarGap()
        val bottomBarGap by bottomBarGapPref.collectAsState()

        val bottomBarIconSizePref = uiPreferences.bottomBarIconSize()
        val bottomBarIconSize by bottomBarIconSizePref.collectAsState()

        val bottomBarKeepRatioPref = uiPreferences.bottomBarKeepRatio()
        val bottomBarKeepRatio by bottomBarKeepRatioPref.collectAsState()

        val bottomBarAutoWidthPref = uiPreferences.bottomBarAutoWidth()
        val bottomBarAutoWidth by bottomBarAutoWidthPref.collectAsState()

        val bottomBarHorizontalPaddingPref = uiPreferences.bottomBarHorizontalPadding()
        val bottomBarHorizontalPadding by bottomBarHorizontalPaddingPref.collectAsState()

        val bottomBarVerticalPaddingPref = uiPreferences.bottomBarVerticalPadding()
        val bottomBarVerticalPadding by bottomBarVerticalPaddingPref.collectAsState()

        val bottomBarCornerRadiusPref = uiPreferences.bottomBarCornerRadius()
        val bottomBarCornerRadius by bottomBarCornerRadiusPref.collectAsState()

        val bottomBarButtonSizePref = uiPreferences.bottomBarButtonSize()
        val bottomBarButtonSizeVal by bottomBarButtonSizePref.collectAsState()

        val standardBottomBarHeightPref = uiPreferences.standardBottomBarHeight()
        val standardBottomBarHeight by standardBottomBarHeightPref.collectAsState()

        val standardBottomBarWidthPref = uiPreferences.standardBottomBarWidth()
        val standardBottomBarWidth by standardBottomBarWidthPref.collectAsState()

        val standardBottomBarOpacityPref = uiPreferences.standardBottomBarOpacity()
        val standardBottomBarOpacity by standardBottomBarOpacityPref.collectAsState()

        val standardBottomBarBlurPref = uiPreferences.standardBottomBarBlur()
        val standardBottomBarBlur by standardBottomBarBlurPref.collectAsState()

        val standardBottomBarBottomMarginPref = uiPreferences.standardBottomBarBottomMargin()
        val standardBottomBarBottomMargin by standardBottomBarBottomMarginPref.collectAsState()

        val standardBottomBarCornerRadiusPref = uiPreferences.standardBottomBarCornerRadius()
        val standardBottomBarCornerRadius by standardBottomBarCornerRadiusPref.collectAsState()

        val jarExtensionReposPref = uiPreferences.jarExtensionRepos()
        val jarExtensionRepos by jarExtensionReposPref.collectAsState()

        var showJarReposDialog by remember { mutableStateOf(false) }
        var showAddJarRepoDialog by remember { mutableStateOf(false) }

        var showFontColorPicker by remember { mutableStateOf(false) }

        if (showColorPicker) {
            val controller = rememberColorPickerController()
            AlertDialog(
                onDismissRequest = { showColorPicker = false },
                title = { Text(text = "Choose Custom Glass Color") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HsvColorPicker(
                            modifier = Modifier.size(240.dp),
                            controller = controller,
                            initialColor = Color(kisaraGlassCustomColor),
                            onColorChanged = { },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BrightnessSlider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            controller = controller,
                            initialColor = Color(kisaraGlassCustomColor),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedColor = controller.selectedColor.value
                            kisaraGlassCustomColorPref.set(selectedColor.toArgb())
                            showColorPicker = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showColorPicker = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showFontColorPicker) {
            val controller = rememberColorPickerController()
            AlertDialog(
                onDismissRequest = { showFontColorPicker = false },
                title = { Text(text = stringResource(KMR.strings.pref_category_bar_selected_font_custom_color)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HsvColorPicker(
                            modifier = Modifier.size(240.dp),
                            controller = controller,
                            initialColor = Color(categoryBarSelectedFontCustomColor),
                            onColorChanged = { },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BrightnessSlider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            controller = controller,
                            initialColor = Color(categoryBarSelectedFontCustomColor),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedColor = controller.selectedColor.value
                            categoryBarSelectedFontCustomColorPref.set(selectedColor.toArgb())
                            showFontColorPicker = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFontColorPicker = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        if (showJarReposDialog) {
            AlertDialog(
                onDismissRequest = { showJarReposDialog = false },
                title = { Text(text = "JAR Extension Repositories") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (jarExtensionRepos.isEmpty()) {
                            Text(
                                text = "No repositories configured. Add one below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                            ) {
                                items(jarExtensionRepos.toList()) { repoString ->
                                    val parts = repoString.split("|", limit = 2)
                                    val repoNickname = parts.getOrNull(0) ?: "Unknown"
                                    val repoUrl = parts.getOrNull(1) ?: ""
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = repoNickname,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = repoUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val newRepos = jarExtensionRepos.toMutableSet()
                                                newRepos.remove(repoString)
                                                jarExtensionReposPref.set(newRepos)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Repo",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAddJarRepoDialog = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(text = "Add Repository")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showJarReposDialog = false }) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }

        if (showAddJarRepoDialog) {
            var newRepoName by remember { mutableStateOf("") }
            var newRepoUrl by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddJarRepoDialog = false },
                title = { Text(text = "Add Repository") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = newRepoName,
                            onValueChange = { newRepoName = it },
                            label = { Text("Nickname") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newRepoUrl,
                            onValueChange = { newRepoUrl = it },
                            label = { Text("URL") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newRepoName.isNotBlank() && newRepoUrl.isNotBlank()) {
                                val newRepos = jarExtensionRepos.toMutableSet()
                                val repoString = "${newRepoName.trim()}|${newRepoUrl.trim()}"
                                newRepos.add(repoString)
                                jarExtensionReposPref.set(newRepos)
                                showAddJarRepoDialog = false
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddJarRepoDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        val allPreferences = listOf(
            Preference.PreferenceGroup(
                title = "Layout & Appearance",
                preferenceItems = buildList {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = uiPreferences.kisaraCoverTitleStyle(),
                            title = stringResource(KMR.strings.pref_cover_title_style),
                            subtitle = "%s",
                            entries = persistentMapOf(
                                "default" to stringResource(KMR.strings.cover_title_style_default),
                                "compact" to stringResource(KMR.strings.cover_title_style_compact),
                                "ultra_compact" to stringResource(KMR.strings.cover_title_style_ultra_compact),
                                "moderate" to stringResource(KMR.strings.cover_title_style_moderate),
                            ),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = floatingBottomBarPref,
                            title = stringResource(KMR.strings.pref_floating_bottom_bar),
                            subtitle = stringResource(KMR.strings.pref_floating_bottom_bar_summary),
                        ),
                    )

                    if (floatingBottomBar) {
                        add(
                            Preference.PreferenceItem.SwitchPreference(
                                preference = bottomBarAutoWidthPref,
                                title = "Dock Auto Width",
                                subtitle = "Automatically adjust dock width to fit items with horizontal padding",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarWidth,
                                valueRange = 100..600,
                                title = "Dock Width",
                                subtitle = "Width of the floating bottom dock in dp",
                                valueString = "$bottomBarWidth dp",
                                enabled = !bottomBarAutoWidth,
                                onValueChanged = { bottomBarWidthPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SwitchPreference(
                                preference = bottomBarKeepRatioPref,
                                title = "Keep Dock & Icon Ratio",
                                subtitle = "Automatically scale icon size with bottom dock height (2:1 ratio)",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarHeight,
                                valueRange = 10..100,
                                title = "Dock Height",
                                valueString = "$bottomBarHeight dp",
                                onValueChanged = {
                                    bottomBarHeightPref.set(it)
                                    if (bottomBarKeepRatio) {
                                        bottomBarIconSizePref.set(it / 2)
                                    }
                                },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarGap,
                                valueRange = -20..40,
                                title = "Dock Item Gap",
                                subtitle = "Spacing gap between bottom dock items in dp",
                                valueString = "$bottomBarGap dp",
                                onValueChanged = { bottomBarGapPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarHorizontalPadding,
                                valueRange = 0..32,
                                title = "Dock Horizontal Padding",
                                subtitle = "Horizontal padding of the bottom dock in dp",
                                valueString = "$bottomBarHorizontalPadding dp",
                                onValueChanged = { bottomBarHorizontalPaddingPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarVerticalPadding,
                                valueRange = 0..32,
                                title = "Dock Vertical Padding",
                                subtitle = "Vertical padding of the bottom dock in dp",
                                valueString = "$bottomBarVerticalPadding dp",
                                onValueChanged = { bottomBarVerticalPaddingPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarCornerRadius,
                                valueRange = 0..48,
                                title = "Dock Corner Radius",
                                subtitle = "Corner radius of the bottom dock in dp",
                                valueString = "$bottomBarCornerRadius dp",
                                onValueChanged = { bottomBarCornerRadiusPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarBottomMargin,
                                valueRange = -40..80,
                                title = "Dock Bottom Margin",
                                subtitle = "Spacing margin below the bottom dock in dp",
                                valueString = "$bottomBarBottomMargin dp",
                                onValueChanged = { bottomBarBottomMarginPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarOpacity,
                                valueRange = 0..100,
                                title = "Dock Opacity",
                                valueString = "$bottomBarOpacity%",
                                onValueChanged = { bottomBarOpacityPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = bottomBarBlur,
                                valueRange = 0..24,
                                title = "Dock Blur Radius",
                                valueString = if (bottomBarBlur > 0) "$bottomBarBlur dp" else "Disabled",
                                onValueChanged = { bottomBarBlurPref.set(it) },
                            ),
                        )
                        if (!bottomBarKeepRatio) {
                            add(
                                Preference.PreferenceItem.SliderPreference(
                                    value = bottomBarButtonSizeVal,
                                    valueRange = 8..64,
                                    title = "Dock Button Size",
                                    subtitle = "Size of the clickable dock item buttons in dp",
                                    valueString = "$bottomBarButtonSizeVal dp",
                                    onValueChanged = { bottomBarButtonSizePref.set(it) },
                                ),
                            )
                            add(
                                Preference.PreferenceItem.SliderPreference(
                                    value = bottomBarIconSize,
                                    valueRange = 6..48,
                                    title = "Dock Icon Size",
                                    subtitle = "Size of the icons inside the buttons in dp",
                                    valueString = "$bottomBarIconSize dp",
                                    onValueChanged = { bottomBarIconSizePref.set(it) },
                                ),
                            )
                        }
                    } else {
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarHeight,
                                valueRange = 40..120,
                                title = "Standard Bottom Bar Height",
                                valueString = "$standardBottomBarHeight dp",
                                onValueChanged = { standardBottomBarHeightPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarWidth,
                                valueRange = 30..100,
                                title = "Standard Bottom Bar Width (Scale)",
                                subtitle = "Horizontal scaling of the standard bottom bar as a percentage",
                                valueString = "$standardBottomBarWidth%",
                                onValueChanged = { standardBottomBarWidthPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarBottomMargin,
                                valueRange = 0..80,
                                title = "Standard Bottom Bar Bottom Margin",
                                subtitle = "Spacing margin below the standard bottom bar in dp",
                                valueString = "$standardBottomBarBottomMargin dp",
                                onValueChanged = { standardBottomBarBottomMarginPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarCornerRadius,
                                valueRange = 0..48,
                                title = "Standard Bottom Bar Corner Radius",
                                subtitle = "Corner radius of standard bottom bar in dp",
                                valueString = "$standardBottomBarCornerRadius dp",
                                onValueChanged = { standardBottomBarCornerRadiusPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarOpacity,
                                valueRange = 0..100,
                                title = "Standard Bottom Bar Opacity",
                                valueString = "$standardBottomBarOpacity%",
                                onValueChanged = { standardBottomBarOpacityPref.set(it) },
                            ),
                        )
                        add(
                            Preference.PreferenceItem.SliderPreference(
                                value = standardBottomBarBlur,
                                valueRange = 0..24,
                                title = "Standard Bottom Bar Blur Radius",
                                valueString = if (standardBottomBarBlur > 0) "$standardBottomBarBlur dp" else "Disabled",
                                onValueChanged = { standardBottomBarBlurPref.set(it) },
                            ),
                        )
                    }

                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.kisaraFrostedGlass(),
                            title = stringResource(KMR.strings.pref_kisara_frosted_glass),
                            subtitle = stringResource(KMR.strings.pref_kisara_frosted_glass_summary),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.performanceMode(),
                            title = "Performance Mode / Battery Saver",
                            subtitle = "Globally disables all dynamic glass blur effects across the app",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.bypassBlurOnTransitions(),
                            title = "Bypass Glass Blur during Transitions",
                            subtitle = "Disables blur during tab and category transition animations to prevent lag",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.disableGlassInBottomBar(),
                            title = "Disable Bottom Bar Glass Blur",
                            subtitle = "Disables frosted glass blur specifically on the bottom navigation bar",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.disableGlassInCategoryBar(),
                            title = "Disable Category Bar Glass Blur",
                            subtitle = "Disables frosted glass blur specifically on the library category tabs",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SwitchPreference(
                            preference = uiPreferences.disableTabTransitions(),
                            title = "Instant Tab Transitions",
                            subtitle = "Disables tab sliding and fading transition animations entirely",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = kisaraGlassColorTypePref,
                            entries = mapOf(
                                0 to stringResource(KMR.strings.glass_color_type_default),
                                1 to stringResource(KMR.strings.glass_color_type_accent),
                                2 to stringResource(KMR.strings.glass_color_type_surface),
                                3 to stringResource(KMR.strings.glass_color_type_black),
                                4 to stringResource(KMR.strings.glass_color_type_white),
                                5 to "Custom Color",
                            ).toImmutableMap(),
                            title = stringResource(KMR.strings.pref_glass_color_type),
                            subtitle = "%s",
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "Custom Glass Color",
                            subtitle = if (kisaraGlassColorType == 5) "#%08X".format(kisaraGlassCustomColor) else "Tap to choose a custom color",
                            enabled = kisaraGlassColorType == 5,
                            onClick = { showColorPicker = true },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.SliderPreference(
                            value = kisaraGlassColorMix,
                            valueRange = 0..100,
                            title = stringResource(KMR.strings.pref_glass_color_mix),
                            subtitle = stringResource(KMR.strings.pref_glass_color_mix_summary),
                            valueString = "$kisaraGlassColorMix%",
                            enabled = kisaraGlassColorType != 0,
                            onValueChanged = { kisaraGlassColorMixPref.set(it) },
                        ),
                    )
                }.toPersistentList(),
            ),
            Preference.PreferenceGroup(
                title = "Category & Navigation",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabs(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsHome(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_home),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_home_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsLibrary(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_library),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_library_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.alwaysShowSubTabsBrowse(),
                        title = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_browse),
                        subtitle = stringResource(KMR.strings.pref_kisara_always_show_sub_tabs_browse_summary),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = subTabsBottomMargin,
                        valueRange = -40..80,
                        title = stringResource(KMR.strings.pref_sub_tabs_bottom_margin),
                        subtitle = stringResource(KMR.strings.pref_sub_tabs_bottom_margin_summary),
                        valueString = if (subTabsBottomMargin != 0) "$subTabsBottomMargin dp" else stringResource(MR.strings.disabled),
                        onValueChanged = { subTabsBottomMarginPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = subBarHeight,
                        valueRange = 10..60,
                        title = "Sub-Bar Height",
                        valueString = "$subBarHeight dp",
                        onValueChanged = { subBarHeightPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = kisaraShowSubcategoriesInMainBarPref,
                        title = "Show subcategories in category bar",
                        subtitle = "Include subcategories inside the main category bar alongside parent categories",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showTopTabBarPref,
                        title = stringResource(KMR.strings.pref_kisara_show_top_tab_bar),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_top_tab_bar_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showCategoryTabsPref,
                        title = stringResource(KMR.strings.pref_kisara_show_category_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_category_tabs_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.hideTopBarOnScroll(),
                        title = stringResource(KMR.strings.pref_kisara_hide_top_bar),
                        subtitle = stringResource(KMR.strings.pref_kisara_hide_top_bar_summary),
                        enabled = showCategoryTabs || showTopTabBar,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.categoryBarCarouselStyle(),
                        title = stringResource(KMR.strings.pref_kisara_carousel_category_style),
                        subtitle = stringResource(KMR.strings.pref_kisara_carousel_category_style_summary),
                        enabled = showCategoryTabs,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = kisaraShowItemCountInTabsPref,
                        title = stringResource(KMR.strings.pref_kisara_show_item_count_in_tabs),
                        subtitle = stringResource(KMR.strings.pref_kisara_show_item_count_in_tabs_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = categoryBarSelectedFontColorTypePref,
                        entries = mapOf(
                            0 to stringResource(KMR.strings.category_bar_selected_font_color_default),
                            1 to stringResource(KMR.strings.category_bar_selected_font_color_accent),
                            2 to stringResource(KMR.strings.category_bar_selected_font_color_custom),
                        ).toImmutableMap(),
                        title = stringResource(KMR.strings.pref_category_bar_selected_font_color),
                        subtitle = "%s",
                        enabled = showCategoryTabs,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_category_bar_selected_font_custom_color),
                        subtitle = if (categoryBarSelectedFontColorType == 2) "#%08X".format(categoryBarSelectedFontCustomColor) else "Tap to choose a custom color",
                        enabled = showCategoryTabs && categoryBarSelectedFontColorType == 2,
                        onClick = { showFontColorPicker = true },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Reader App Bar",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = readerAppBarOpacity,
                        valueRange = 0..100,
                        title = "Reader App Bar Opacity",
                        subtitle = "Opacity of the reader top and bottom bars",
                        valueString = "$readerAppBarOpacity%",
                        onValueChanged = { readerAppBarOpacityPref.set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = readerAppBarColorMix,
                        valueRange = 0..100,
                        title = "Reader App Bar Color Mix",
                        subtitle = "Color mix ratio of custom color on the reader app bars",
                        valueString = "$readerAppBarColorMix%",
                        enabled = kisaraGlassColorType != 0,
                        onValueChanged = { readerAppBarColorMixPref.set(it) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.disableGlassInReader(),
                        title = "Disable Glass Blur in Reader",
                        subtitle = "Falls back to a solid background for reader app bars to save CPU and battery",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Manga & Chapter Sheets",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.chapterSheetMinHeightDp().get(),
                        valueRange = 40..120,
                        title = stringResource(KMR.strings.pref_kisara_chapter_sheet_min_height),
                        subtitle = stringResource(KMR.strings.pref_kisara_chapter_sheet_min_height_summary),
                        valueString = "${uiPreferences.chapterSheetMinHeightDp().get()} dp",
                        onValueChanged = {
                            uiPreferences.chapterSheetMinHeightDp().set(it)
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.chapterSheetMaxHeightPct().get(),
                        valueRange = 30..90,
                        title = stringResource(KMR.strings.pref_kisara_chapter_sheet_max_height),
                        subtitle = stringResource(KMR.strings.pref_kisara_chapter_sheet_max_height_summary),
                        valueString = "${uiPreferences.chapterSheetMaxHeightPct().get()}%",
                        onValueChanged = {
                            uiPreferences.chapterSheetMaxHeightPct().set(it)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Auto-Translation",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_category_translations),
                        subtitle = stringResource(KMR.strings.pref_translation_summary),
                        onClick = { navigator.push(SettingsTranslationScreen) },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Concurrent Downloads Settings",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = parallelChapterLimit,
                        valueRange = 1..5,
                        title = "Concurrent chapters limit",
                        subtitle = "Number of chapters to download concurrently per source",
                        valueString = "$parallelChapterLimit",
                        onValueChanged = { downloadPreferences.parallelChapterLimit().set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = parallelSourceLimit,
                        valueRange = 1..10,
                        title = stringResource(MR.strings.pref_download_concurrent_sources),
                        valueString = "$parallelSourceLimit",
                        onValueChanged = { downloadPreferences.parallelSourceLimit().set(it) },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = parallelPageLimit,
                        valueRange = 1..15,
                        title = stringResource(MR.strings.pref_download_concurrent_pages),
                        subtitle = stringResource(MR.strings.pref_download_concurrent_pages_summary),
                        valueString = "$parallelPageLimit",
                        onValueChanged = { downloadPreferences.parallelPageLimit().set(it) },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.label_duplicate),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = uiPreferences.duplicateMaxScanCount().get(),
                        valueRange = 0..200,
                        title = stringResource(KMR.strings.pref_kisara_duplicate_max_scan),
                        subtitle = stringResource(KMR.strings.pref_kisara_duplicate_max_scan_summary),
                        valueString = if (uiPreferences.duplicateMaxScanCount().get() > 0) "${uiPreferences.duplicateMaxScanCount().get()} groups" else "Unlimited",
                        onValueChanged = {
                            uiPreferences.duplicateMaxScanCount().set(it)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Import From Other Sources",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = "Restore from other sources (Kotatsu/Venera)",
                        subtitle = "Import data from Kotatsu (.zip) or Venera (.db) backup files",
                        onClick = {
                            if (!BackupRestoreJob.isRunning(context)) {
                                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                    context.toast(MR.strings.restore_miui_warning)
                                }
                                chooseBackup.launch("*/*")
                            } else {
                                context.toast(MR.strings.restore_in_progress)
                            }
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "JAR Extensions Support",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = "Install Kotatsu JAR Extension",
                        subtitle = "Select and install a .jar parser extension from your device",
                        onClick = { chooseJar.launch("*/*") },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Kotatsu JAR Extension Repositories",
                        subtitle = "Manage multiple JAR parser extension repositories",
                        onClick = { showJarReposDialog = true },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_home_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_home_title),
                        subtitle = stringResource(KMR.strings.pref_home_summary),
                        onClick = {
                            navigator.push(SettingsHomeScreen)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Reader Loading Screen Style",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.readerLoadingStyle(),
                        entries = mapOf(
                            UiPreferences.ReaderLoadingStyle.CLASSIC_DARK to "Classic Dark",
                            UiPreferences.ReaderLoadingStyle.AMOLED_BLACK to "Amoled Black",
                            UiPreferences.ReaderLoadingStyle.SUNSET to "Sunset Glow (Orange/Red)",
                            UiPreferences.ReaderLoadingStyle.OCEAN to "Deep Ocean (Blue/Cyan)",
                            UiPreferences.ReaderLoadingStyle.CYBERPUNK to "Cyberpunk (Purple/Magenta)",
                            UiPreferences.ReaderLoadingStyle.AURORA to "Aurora (Green/Blue)",
                        ).toImmutableMap(),
                        title = "Loading Background Theme",
                        subtitle = "%s",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Suggestions & Content Filtering",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = Injekt.get<tachiyomi.domain.suggestions.service.SuggestionsPreferences>().isSuggestionsEnabled(),
                        title = "Enable Suggestions",
                        subtitle = "Enable recommendations based on reading taste",
                        onValueChanged = { isEnabled ->
                            eu.kanade.tachiyomi.data.suggestions.SuggestionsWorker.scheduleBackground(context, isEnabled)
                            true
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Suggestions Settings",
                        subtitle = "Configure, drag-and-drop reorder, or block tags and extensions",
                        onClick = {
                            navigator.push(SettingsSuggestionsScreen())
                        },
                        enabled = Injekt.get<tachiyomi.domain.suggestions.service.SuggestionsPreferences>().isSuggestionsEnabled().get(),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "System Wide Blocked Tags",
                        subtitle = "Globally hide manga containing specified tags (genres)",
                        onClick = {
                            navigator.push(SettingsBlockedTagsScreen())
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Custom NSFW Tags",
                        subtitle = "Configure tags (genres) to automatically mark manga as NSFW (18+)",
                        onClick = {
                            navigator.push(SettingsCustomNsfwTagsScreen())
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.kisaraHideNsfwSuggestions(),
                        title = "Hide NSFW in Suggestions",
                        subtitle = "Exclude NSFW recommendations from Suggestions and Home Feed",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.kisaraBlurNsfwCovers(),
                        title = "Blur NSFW Covers",
                        subtitle = "Apply a blur filter on cover images identified as NSFW",
                    ),
                ),
            ),
            // KMK -->
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_search_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = fuzzyThreshold,
                        valueRange = 0..100,
                        title = stringResource(KMR.strings.pref_search_fuzzy_threshold),
                        subtitle = stringResource(KMR.strings.pref_search_fuzzy_threshold_summary),
                        valueString = "$fuzzyThreshold",
                        onValueChanged = { fuzzyThresholdPref.set(it) },
                    ),
                ),
            ),
            // KMK <--
        ) + SettingsVpnScreen.getPreferences()

        return allPreferences.map { preference ->
            if (preference is Preference.PreferenceGroup) {
                preference.copy(isCollapsible = true, isInitiallyExpanded = false)
            } else {
                preference
            }
        }
    }
}
