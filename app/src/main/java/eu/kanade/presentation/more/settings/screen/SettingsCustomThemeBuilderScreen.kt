package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val PRESET_PRIMARY_COLORS = listOf(
    Color(0xFFDF0090),
    Color(0xFF6750A4),
    Color(0xFF0061A4),
    Color(0xFF006B5D),
    Color(0xFF825500),
    Color(0xFFB3261E),
    Color(0xFF006874),
)

val PRESET_SECONDARY_COLORS = listOf(
    Color(0xFF00B0FF),
    Color(0xFF00E676),
    Color(0xFFFF9100),
    Color(0xFFE040FB),
    Color(0xFFFFD600),
    Color(0xFF7C4DFF),
)

class SettingsCustomThemeBuilderScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        val primaryColorPref = uiPreferences.colorTheme()
        val primaryColorInt by primaryColorPref.collectAsState()

        val secondaryColorPref = uiPreferences.colorSecondaryTheme()
        val secondaryColorInt by secondaryColorPref.collectAsState()

        val gradientEnabledPref = uiPreferences.themeGradientEnabled()
        val gradientEnabled by gradientEnabledPref.collectAsState()

        val glassOpacityPref = uiPreferences.themeGlassOpacity()
        val glassOpacity by glassOpacityPref.collectAsState()

        val darkModeDepthPref = uiPreferences.darkModeDepth()
        val darkModeDepth by darkModeDepthPref.collectAsState()

        val coverBasedPref = uiPreferences.themeCoverBased()
        val coverBased by coverBasedPref.collectAsState()

        val primaryColor = Color(primaryColorInt)
        val secondaryColor = Color(secondaryColorInt)

        val darkDepthBg = when (darkModeDepth) {
            0 -> Color.Black
            1 -> Color(0xFF0A0E1A)
            2 -> Color(0xFF121212)
            else -> Color(0xFF1E1E2C)
        }

        val appThemePref = uiPreferences.appTheme()
        val currentAppTheme by appThemePref.collectAsState()
        val isCustomThemeActive = currentAppTheme == eu.kanade.domain.ui.model.AppTheme.CUSTOM

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
        ) {
            AppBar(
                title = "Theme Builder",
                navigateUp = { navigator.pop() },
            )

            if (!isCustomThemeActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
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
                                text = "Custom Theme Inactive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "Activate 'Custom Theme' to apply your custom colors app-wide.",
                                fontSize = 12.sp,
                            )
                        }
                        Button(
                            onClick = {
                                appThemePref.set(eu.kanade.domain.ui.model.AppTheme.CUSTOM)
                                (context as? Activity)?.let { ActivityCompat.recreate(it) }
                            },
                        ) {
                            Text("Activate")
                        }
                    }
                }
            }

            // --- Sticky Live Interactive Preview Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = darkDepthBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (gradientEnabled) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.35f * glassOpacity),
                                            secondaryColor.copy(alpha = 0.20f * glassOpacity),
                                            darkDepthBg.copy(alpha = glassOpacity),
                                        ),
                                    ),
                                )
                            } else {
                                Modifier.background(darkDepthBg.copy(alpha = glassOpacity))
                            },
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Live Theme Preview",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(primaryColor)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Active", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(primaryColor, secondaryColor),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Book, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sample Manga Card",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "Glassmorphism & Color Blend",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Primary", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Secondary", color = secondaryColor)
                        }
                    }
                }
            }

            // --- Customization Controls ---
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Primary Accent Color", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PRESET_PRIMARY_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    primaryColorPref.set(color.toArgb())
                                    uiPreferences.appTheme().set(eu.kanade.domain.ui.model.AppTheme.CUSTOM)
                                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color.toArgb() == primaryColorInt) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Secondary Accent Color", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PRESET_SECONDARY_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    secondaryColorPref.set(color.toArgb())
                                    uiPreferences.appTheme().set(eu.kanade.domain.ui.model.AppTheme.CUSTOM)
                                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color.toArgb() == secondaryColorInt) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gradient Surface Blending", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Enable smooth multi-color gradients on headers & surfaces", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = gradientEnabled,
                        onCheckedChange = { gradientEnabledPref.set(it) },
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Surface Glassmorphism Opacity: ${(glassOpacity * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Slider(
                    value = glassOpacity,
                    onValueChange = { glassOpacityPref.set(it) },
                    valueRange = 0.2f..1.0f,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text("Dark Mode Theme Depth", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val depthLabels = listOf("OLED Black", "Midnight", "Charcoal", "Soft Dark")
                    depthLabels.forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (darkModeDepth == index) primaryColor else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    darkModeDepthPref.set(index)
                                    uiPreferences.appTheme().set(eu.kanade.domain.ui.model.AppTheme.CUSTOM)
                                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (darkModeDepth == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cover-Based Dynamic Colors", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Auto-adapt UI theme colors to currently reading manga cover", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = coverBased,
                        onCheckedChange = { coverBasedPref.set(it) },
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
