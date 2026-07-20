package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.domain.source.model.icon
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.getIcon
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.R
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.source.local.isLocal
import java.io.File

@Composable
internal fun DownloadsBadge(count: Long) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
fun UncensoredBadge() {
    Badge(
        text = "18+",
        color = MaterialTheme.colorScheme.error,
        textColor = MaterialTheme.colorScheme.onError,
    )
}

@Composable
fun ColorizedBadge() {
    val colors = listOf(
        Color(0xFFFF595E),
        Color(0xFFFFCA3A),
        Color(0xFF8AC926),
        Color(0xFF1982C4),
        Color(0xFF6A4C93),
    )
    val brush = remember { Brush.horizontalGradient(colors) }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(brush),
    )
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
    // KMK -->
    useLangIcon: Boolean = true,
    // KMK <--
) {
    // KMK -->
    if (!isLocal && sourceLanguage.isNotEmpty()) {
        if (useLangIcon) {
            val iconResId = getLanguageIconID(sourceLanguage) ?: R.drawable.globe
            Badge(
                painter = painterResource(id = iconResId),
                color = Color.Transparent,
                modifier = Modifier
                    .width(25.dp)
                    .height(18.dp),
            )
        } else {
            // KMK <--
            Badge(
                text = sourceLanguage.uppercase(),
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

// KMK -->
@Composable
fun SourceIconBadge(
    source: Source?,
) {
    if (source == null) return
    val context = LocalContext.current
    val cachedFile = remember(source.id) { File(File(context.filesDir, "source_icons"), "${source.id}.png") }
    var cachedBitmap by remember(source.id) {
        mutableStateOf<ImageBitmap?>(
            if (cachedFile.isFile) {
                try {
                    android.graphics.BitmapFactory.decodeFile(cachedFile.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            },
        )
    }

    val icon = source.icon
    val displayIcon = icon ?: cachedBitmap
    val installedExt = source.installedExtension

    when {
        displayIcon != null -> {
            Badge(
                imageBitmap = displayIcon,
                modifier = Modifier
                    .scale(1.3f)
                    .height(18.dp),
            )
        }
        installedExt != null -> {
            val repoUrl = installedExt.repoUrl
            if (repoUrl != null) {
                val painter = rememberAsyncImagePainter(
                    model = "$repoUrl/icon/${installedExt.pkgName}.png",
                )
                Badge(
                    painter = painter,
                    modifier = Modifier
                        .scale(1.3f)
                        .height(18.dp),
                )
            } else {
                val iconState by installedExt.getIcon()
                when (val iconResult = iconState) {
                    is eu.kanade.presentation.browse.components.Result.Success -> {
                        Badge(
                            imageBitmap = iconResult.value,
                            modifier = Modifier
                                .scale(1.3f)
                                .height(18.dp),
                        )
                    }
                    else -> {
                        Badge(
                            imageVector = Icons.Outlined.LocalLibrary,
                            color = MaterialTheme.colorScheme.tertiary,
                            iconColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
        }
        source.isStub -> {
            Badge(
                imageVector = Icons.Filled.Warning,
                iconColor = MaterialTheme.colorScheme.error,
                color = MaterialTheme.colorScheme.errorContainer,
            )
        }
        source.isLocal() -> {
            Badge(
                imageVector = Icons.Outlined.Folder,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        else -> {
            // Default source icon (if source doesn't have an icon)
            Badge(
                imageVector = Icons.Outlined.LocalLibrary,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}
// KMK <--

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        BadgeGroup {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "en")
            LanguageBadge(isLocal = false, sourceLanguage = "en", useLangIcon = false)
            LanguageBadge(isLocal = false, sourceLanguage = "vi")
        }
    }
}
