package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerGestureZoneConfig
import eu.kanade.tachiyomi.ui.player.settings.PlayerZoneAction
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PlayerGestureZonesEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val gesturePreferences = remember { Injekt.get<GesturePreferences>() }

        val splitZonesEnabled by gesturePreferences.gestureSplitZonesEnabled().collectAsStateWithLifecycle()
        val configJson by gesturePreferences.gestureSplitZoneConfig().collectAsStateWithLifecycle()
        val config = remember(configJson) { PlayerGestureZoneConfig.deserialize(configJson) }
        val showRipple by gesturePreferences.showGestureRipple().collectAsStateWithLifecycle()
        val showHud by gesturePreferences.showGestureHud().collectAsStateWithLifecycle()

        var selectedZone by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.pref_player_gesture_split_zones),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                    actions = {
                        TextButton(
                            onClick = {
                                gesturePreferences.gestureSplitZoneConfig().set(PlayerGestureZoneConfig.DEFAULT.serialize())
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(text = stringResource(KMR.strings.gesture_reset_defaults))
                        }
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(KMR.strings.pref_player_gesture_split_zones),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(KMR.strings.pref_player_gesture_split_zones_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = splitZonesEnabled,
                                onCheckedChange = { gesturePreferences.gestureSplitZonesEnabled().set(it) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Interactive 5x2 Player Zone Map (Tap to Edit)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                item {
                    // 5x2 Interactive Visual Map
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.85f),
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Row 0: Top Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                for (col in 0..4) {
                                    val action = config.getAction(col, 0)
                                    val colName = getColName(col)
                                    ZoneCell(
                                        colName = colName,
                                        rowName = stringResource(KMR.strings.gesture_zone_top),
                                        action = action,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .clickable { selectedZone = col to 0 },
                                    )
                                }
                            }

                            // Horizontal divider line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f)),
                            )

                            // Row 1: Bottom Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                for (col in 0..4) {
                                    val action = config.getAction(col, 1)
                                    val colName = getColName(col)
                                    ZoneCell(
                                        colName = colName,
                                        rowName = stringResource(KMR.strings.gesture_zone_bottom),
                                        action = action,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .clickable { selectedZone = col to 1 },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "👆 Gesture Behavior Summary",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(KMR.strings.gesture_zones_swipe_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(KMR.strings.pref_player_gesture_ripple),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = stringResource(KMR.strings.pref_player_gesture_ripple_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = showRipple,
                                    onCheckedChange = { gesturePreferences.showGestureRipple().set(it) },
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(KMR.strings.pref_player_gesture_hud),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = stringResource(KMR.strings.pref_player_gesture_hud_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = showHud,
                                    onCheckedChange = { gesturePreferences.showGestureHud().set(it) },
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Zone Action Selection Dialog
        selectedZone?.let { (col, row) ->
            val currentAction = config.getAction(col, row)
            val colName = getColName(col)
            val rowName = if (row == 0) stringResource(KMR.strings.gesture_zone_top) else stringResource(KMR.strings.gesture_zone_bottom)

            AlertDialog(
                onDismissRequest = { selectedZone = null },
                title = {
                    Text(text = "$rowName $colName Zone")
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(PlayerZoneAction.entries) { action ->
                            val isSelected = action == currentAction
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .clickable {
                                        val newConfig = config.withAction(col, row, action)
                                        gesturePreferences.gestureSplitZoneConfig().set(newConfig.serialize())
                                        selectedZone = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(action.titleRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedZone = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun ZoneCell(
        colName: String,
        rowName: String,
        action: PlayerZoneAction,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier
                .border(0.5.dp, Color.White.copy(alpha = 0.15f))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = colName,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        action.name.startsWith("SEEK_FORWARD") -> Color(0xFF2E7D32).copy(alpha = 0.6f)
                        action.name.startsWith("SEEK_BACKWARD") -> Color(0xFFC62828).copy(alpha = 0.6f)
                        action == PlayerZoneAction.PLAY_PAUSE -> Color(0xFF1565C0).copy(alpha = 0.6f)
                        action.name.startsWith("SPEED") -> Color(0xFFE65100).copy(alpha = 0.6f)
                        action == PlayerZoneAction.NONE -> Color.Gray.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    },
                ) {
                    Text(
                        text = stringResource(action.titleRes),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun getColName(col: Int): String {
        return when (col) {
            0 -> stringResource(KMR.strings.gesture_zone_far_left)
            1 -> stringResource(KMR.strings.gesture_zone_center_left)
            2 -> stringResource(KMR.strings.gesture_zone_middle)
            3 -> stringResource(KMR.strings.gesture_zone_center_right)
            4 -> stringResource(KMR.strings.gesture_zone_far_right)
            else -> ""
        }
    }
}
