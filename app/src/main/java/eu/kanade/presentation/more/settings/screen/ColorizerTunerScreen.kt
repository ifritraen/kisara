package eu.kanade.presentation.more.settings.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.util.system.toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.ai.AiModelManager
import eu.kanade.tachiyomi.data.ai.ColorizeImageUtils
import eu.kanade.tachiyomi.data.ai.MangaColorizeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import kotlin.math.roundToInt

class ColorizerTunerScreen : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        val storageManager = remember { Injekt.get<StorageManager>() }
        val colorizeEngine = remember { MangaColorizeEngine() }
        val modelManager = remember { AiModelManager(context) }

        // Live Parameters State
        var skinHue by remember { mutableIntStateOf(translationPreferences.colorizerSkinHue().get()) }
        var skinSat by remember { mutableIntStateOf(translationPreferences.colorizerSkinSat().get()) }
        var blueCap by remember { mutableIntStateOf(translationPreferences.colorizerBlueCap().get()) }
        var redFlush by remember { mutableIntStateOf(translationPreferences.colorizerRedFlush().get()) }
        var skinMinLum by remember { mutableIntStateOf(translationPreferences.colorizerSkinMinLum().get()) }
        var paperThresh by remember { mutableIntStateOf(translationPreferences.colorizerPaperThresh().get()) }
        var paperFeather by remember { mutableIntStateOf(translationPreferences.colorizerPaperFeather().get()) }
        var gamma by remember { mutableIntStateOf(translationPreferences.colorizerGamma().get()) }
        var contrast by remember { mutableIntStateOf(translationPreferences.colorizerContrast().get()) }
        var blackFloor by remember { mutableIntStateOf(translationPreferences.colorizerBlackFloor().get()) }
        var lineThresh by remember { mutableIntStateOf(translationPreferences.colorizerLineThresh().get()) }
        var lineExp by remember { mutableIntStateOf(translationPreferences.colorizerLineExp().get()) }
        var satMul by remember { mutableIntStateOf(translationPreferences.colorizerSatMul().get()) }
        var colorMix by remember { mutableIntStateOf(translationPreferences.colorizerColorMix().get()) }
        var clarity by remember { mutableIntStateOf(translationPreferences.colorizerClarity().get()) }

        var activePresetMode by remember { mutableIntStateOf(translationPreferences.colorizerBlendMode().get()) }

        // Image State
        var origBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var rawModelBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isInferringModel by remember { mutableStateOf(false) }
        var inferenceDurationMs by remember { mutableStateOf(0L) }
        var selectedPageTitle by remember { mutableStateOf("No page selected") }

        // UI View Mode State (0 = Full Color, 1 = Split, 2 = Original B&W)
        var viewMode by remember { mutableIntStateOf(0) }
        var splitRatio by remember { mutableFloatStateOf(0.5f) }

        // Dialogs
        var showPagePickerDialog by remember { mutableStateOf(false) }
        var showSavePresetDialog by remember { mutableStateOf(false) }
        var newPresetName by remember { mutableStateOf("") }
        var activePresetName by remember { mutableStateOf("Optimized") }
        var customPresets by remember { mutableStateOf<List<Pair<String, ColorizeImageUtils.ColorizerTuningParams>>>(emptyList()) }
        var downloadedPages by remember { mutableStateOf<List<Pair<String, UniFile>>>(emptyList()) }

        // Collapsible sections
        var section1Expanded by remember { mutableStateOf(true) }
        var section2Expanded by remember { mutableStateOf(true) }
        var section3Expanded by remember { mutableStateOf(true) }

        var updateJob by remember { mutableStateOf<Job?>(null) }

        // Trigger real-time post-processing update
        fun triggerRender() {
            val orig = origBitmap ?: return
            val raw = rawModelBitmap ?: return
            updateJob?.cancel()
            updateJob = scope.launch(Dispatchers.Default) {
                val params = ColorizeImageUtils.ColorizerTuningParams(
                    skinHue = skinHue,
                    skinSat = skinSat,
                    blueCap = blueCap,
                    redFlush = redFlush,
                    skinMinLum = skinMinLum,
                    paperThresh = paperThresh,
                    paperFeather = paperFeather,
                    gamma = gamma,
                    contrast = contrast,
                    blackFloor = blackFloor,
                    lineThresh = lineThresh,
                    lineExp = lineExp,
                    satMul = satMul,
                    colorMix = colorMix,
                    clarity = clarity,
                )
                val blended = ColorizeImageUtils.applyTuningPipeline(orig, raw, params)
                withContext(Dispatchers.Main) {
                    previewBitmap = blended
                }
            }
        }

        // Apply a preset to all sliders
        fun applyPreset(mode: Int) {
            activePresetMode = mode
            val p = ColorizeImageUtils.ColorizerTuningParams.getPreset(mode)
            skinHue = p.skinHue
            skinSat = p.skinSat
            blueCap = p.blueCap
            redFlush = p.redFlush
            skinMinLum = p.skinMinLum
            paperThresh = p.paperThresh
            paperFeather = p.paperFeather
            gamma = p.gamma
            contrast = p.contrast
            blackFloor = p.blackFloor
            lineThresh = p.lineThresh
            lineExp = p.lineExp
            satMul = p.satMul
            colorMix = p.colorMix
            clarity = p.clarity
            triggerRender()
        }

        // Lightweight image registration without running heavy neural network
        fun processNewImage(bitmap: Bitmap, title: String) {
            origBitmap = bitmap
            selectedPageTitle = title
            rawModelBitmap = null
            previewBitmap = null
            inferenceDurationMs = 0L
        }

        // Explicit neural network colorization (run once on demand)
        fun runAiColorize() {
            val bitmap = origBitmap ?: return
            if (isInferringModel) return
            isInferringModel = true
            scope.launch(Dispatchers.Default) {
                try {
                    val modelFile = modelManager.getModelFile(AiModelManager.ModelType.MANGA_COLORIZER_V2)
                    if (modelFile.exists() && modelFile.length() > 0) {
                        val maxDim = 640
                        val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
                        val previewW = if (bitmap.width > bitmap.height) maxDim else (maxDim / aspect).roundToInt().coerceIn(384, 640)
                        val previewH = if (bitmap.width > bitmap.height) (maxDim * aspect).roundToInt().coerceIn(384, 896) else maxDim

                        val scaledInput = Bitmap.createScaledBitmap(bitmap, previewW, previewH, true)
                        val inferStart = System.currentTimeMillis()
                        val rawColor = colorizeEngine.colorize(
                            inputBitmap = scaledInput,
                            modelFile = modelFile,
                            intensity = 1.0f,
                            useNnapi = translationPreferences.colorizerUseNnapi().get(),
                            blendMode = 6,
                            speedTier = translationPreferences.colorizerSpeedTier().get(),
                        )
                        val elapsed = System.currentTimeMillis() - inferStart
                        // Immediately release heavy ONNX neural session and C++ native memory
                        colorizeEngine.unloadSession()
                        System.gc()
                        Runtime.getRuntime().gc()
                        withContext(Dispatchers.Main) {
                            inferenceDurationMs = elapsed
                            rawModelBitmap = rawColor
                            isInferringModel = false
                            triggerRender()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isInferringModel = false
                            context.toast("Please download Manga Colorizer v2 model first")
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to run preview model inference" }
                    withContext(Dispatchers.Main) {
                        isInferringModel = false
                        context.toast("Colorize failed: ${e.message}")
                    }
                }
            }
        }

        // Load downloaded pages list
        fun scanDownloadedPages() {
            scope.launch(Dispatchers.IO) {
                val list = mutableListOf<Pair<String, UniFile>>()
                val root = storageManager.getDownloadsDirectory()
                if (root != null && root.exists()) {
                    for (src in root.listFiles().orEmpty()) {
                        if (!src.isDirectory) continue
                        for (manga in src.listFiles().orEmpty()) {
                            if (!manga.isDirectory) continue
                            for (ch in manga.listFiles().orEmpty()) {
                                if (ch.isDirectory) {
                                    for (p in ch.listFiles().orEmpty()) {
                                        val name = p.name?.lowercase() ?: ""
                                        if (p.isFile && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                                            list.add("${manga.name} • ${ch.name} • ${p.name}" to p)
                                            if (list.size >= 80) break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    downloadedPages = list
                }
            }
        }

        fun loadDefaultSample() {
            scope.launch(Dispatchers.IO) {
                val internalSampleFile = java.io.File(context.filesDir, "manga_tuner_studio_sample.webp")

                // 1. If already downloaded in private app data, load it immediately
                if (internalSampleFile.exists() && internalSampleFile.length() > 50_000) {
                    try {
                        BitmapFactory.decodeFile(internalSampleFile.absolutePath)?.let { bmp ->
                            withContext(Dispatchers.Main) {
                                processNewImage(bmp, "Default Studio Sample (Multi-Panel)")
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to decode cached tuner sample" }
                    }
                }

                // 2. Show bundled sample immediately while auto-downloading
                try {
                    context.assets.open("tuner_sample.webp").use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bmp ->
                            withContext(Dispatchers.Main) {
                                if (origBitmap == null) {
                                    processNewImage(bmp, "Default Studio Sample")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to load bundled tuner sample" }
                }

                // 3. Auto-download big resolution multi-panel manga page from GitHub release to private app data
                try {
                    val url = java.net.URI.create("https://github.com/ifritraen/color_model/releases/download/v0.1/manga_tuner_studio_sample.webp").toURL()
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    if (connection.responseCode in 200..299) {
                        val tempFile = java.io.File(context.filesDir, "manga_tuner_studio_sample.webp.tmp")
                        connection.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.length() > 50_000) {
                            if (internalSampleFile.exists()) internalSampleFile.delete()
                            tempFile.renameTo(internalSampleFile)
                            BitmapFactory.decodeFile(internalSampleFile.absolutePath)?.let { highResBmp ->
                                withContext(Dispatchers.Main) {
                                    processNewImage(highResBmp, "Default Studio Sample (Multi-Panel)")
                                }
                            }
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to auto-download high-res tuner sample" }
                }
            }
        }

        LaunchedEffect(Unit) {
            loadDefaultSample()
            scanDownloadedPages()
        }

        // Custom File Picker Launcher
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        BitmapFactory.decodeStream(stream)?.let { bmp ->
                            withContext(Dispatchers.Main) {
                                processNewImage(bmp, "Imported Image")
                            }
                        }
                    }
                }
            }
        }

        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Colorization Live Tuner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = selectedPageTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSavePresetDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Preset")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // 1. PINNED / FIXED TOP PREVIEW VIEWPORT (Never scrolls away!)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Toolbar: Mode Switcher + Page Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { viewMode = 0 },
                                colors = if (viewMode == 0) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("Color", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewMode = 1 },
                                colors = if (viewMode == 1) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("Split", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewMode = 2 },
                                colors = if (viewMode == 2) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("B&W", fontSize = 11.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { showPagePickerDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Downloads", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick File", fontSize = 11.sp)
                            }
                            if (rawModelBitmap != null) {
                                Button(
                                    onClick = { runAiColorize() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-Colorize", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Preview Box: Full width, taller (430.dp), un-windowed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(430.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isInferringModel) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Running neural colorizer...", color = Color.White, fontSize = 12.sp)
                            }
                        } else if (origBitmap != null) {
                            if (rawModelBitmap == null) {
                                // Original B&W is loaded. Waiting for user to tap "Run AI Colorize"
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Image(
                                        bitmap = origBitmap!!.asImageBitmap(),
                                        contentDescription = "Original B&W",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Button(
                                        onClick = { runAiColorize() },
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Run AI Colorize", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (previewBitmap != null) {
                                when (viewMode) {
                                    0 -> { // Full Color
                                        Image(
                                            bitmap = previewBitmap!!.asImageBitmap(),
                                            contentDescription = "Colorized Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    2 -> { // Full BW
                                        Image(
                                            bitmap = origBitmap!!.asImageBitmap(),
                                            contentDescription = "Original B&W",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    1 -> { // True Split Mode (Canvas Clip)
                                        BeforeAfterSplitViewer(
                                            beforeBitmap = origBitmap!!,
                                            afterBitmap = previewBitmap!!,
                                            splitFraction = splitRatio,
                                            onSplitFractionChange = { splitRatio = it },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Select a manga page from Downloads to preview",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                // 2. SCROLLABLE CONTROLS SECTION (Scrolls up to the fixed image!)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {

                // 2. PRESETS CAROUSEL
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = "⚡ PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    val presets = listOf(
                        0 to "Testing (Default)",
                        1 to "Optimized",
                        2 to "Heavy Darkish",
                        3 to "ColorFul",
                        4 to "Brighter",
                        5 to "LessColor",
                        6 to "V20 (Skin Warmth)",
                        7 to "Custom Tuning",
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(presets) { (modeId, label) ->
                            val isSelected = activePresetMode == modeId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    if (modeId != 7) {
                                        applyPreset(modeId)
                                    } else {
                                        activePresetMode = 7
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        items(customPresets) { (name, customParam) ->
                            val isSelected = activePresetMode == 7 && activePresetName == name
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    skinHue = customParam.skinHue
                                    skinSat = customParam.skinSat
                                    blueCap = customParam.blueCap
                                    redFlush = customParam.redFlush
                                    skinMinLum = customParam.skinMinLum
                                    paperThresh = customParam.paperThresh
                                    paperFeather = customParam.paperFeather
                                    gamma = customParam.gamma
                                    contrast = customParam.contrast
                                    blackFloor = customParam.blackFloor
                                    lineThresh = customParam.lineThresh
                                    lineExp = customParam.lineExp
                                    satMul = customParam.satMul
                                    colorMix = customParam.colorMix
                                    clarity = customParam.clarity
                                    activePresetMode = 7
                                    activePresetName = name
                                    triggerRender()
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "★ $name",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. GRANULAR SLIDERS (15 VARIABLES)

                // SECTION 1: Skin & Anatomy Tuning
                TunerSection(
                    title = "🌸 Section 1: Skin & Anatomy Tuning",
                    isExpanded = section1Expanded,
                    onToggle = { section1Expanded = !section1Expanded },
                ) {
                    TunerSlider(
                        title = "Skin Tone Hue Angle",
                        valueText = "$skinHue° (${if (skinHue < 0) "Magenta" else if (skinHue < 12) "Rosy Pink" else if (skinHue < 25) "Natural Peach" else "Warm Gold"})",
                        value = skinHue.toFloat(),
                        range = -20f..60f,
                        steps = 80,
                        onValueChange = {
                            skinHue = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Skin Warmth Saturation",
                        valueText = "$skinSat%",
                        value = skinSat.toFloat(),
                        range = 0f..100f,
                        steps = 100,
                        onValueChange = {
                            skinSat = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Anatomy Blue/Cyan Suppression",
                        valueText = if (blueCap == 100) "100% (Raw Off)" else "$blueCap% (${if (blueCap < 80) "Strong Warmth" else "Natural"})",
                        value = blueCap.toFloat(),
                        range = 40f..100f,
                        steps = 60,
                        onValueChange = {
                            blueCap = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Red Flush Compensation",
                        valueText = String.format("%.2fx", redFlush / 100f),
                        value = redFlush.toFloat(),
                        range = 100f..135f,
                        steps = 35,
                        onValueChange = {
                            redFlush = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Skin Shadow Depth Limit",
                        valueText = String.format("0.%02d", skinMinLum),
                        value = skinMinLum.toFloat(),
                        range = 0f..40f,
                        steps = 40,
                        onValueChange = {
                            skinMinLum = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                }

                // SECTION 2: Illumination, Paper & Contrast
                TunerSection(
                    title = "☀️ Section 2: Illumination, Paper & Contrast",
                    isExpanded = section2Expanded,
                    onToggle = { section2Expanded = !section2Expanded },
                ) {
                    TunerSlider(
                        title = "Speech Bubble / Paper Whiten",
                        valueText = if (paperThresh == 100) "Off (Natural)" else String.format("0.%02d (Whitened)", paperThresh),
                        value = paperThresh.toFloat(),
                        range = 70f..100f,
                        steps = 30,
                        onValueChange = {
                            paperThresh = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Paper Whiten Feathering",
                        valueText = String.format("0.%02d", paperFeather),
                        value = paperFeather.toFloat(),
                        range = 0f..15f,
                        steps = 15,
                        onValueChange = {
                            paperFeather = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Midtone Brightness Lift (Gamma)",
                        valueText = "${if (gamma >= 100) "+" else ""}${gamma - 100}%",
                        value = gamma.toFloat(),
                        range = 50f..180f,
                        steps = 65,
                        onValueChange = {
                            gamma = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Filmic S-Curve Contrast",
                        valueText = "${if (contrast >= 0) "+" else ""}$contrast%",
                        value = contrast.toFloat(),
                        range = -50f..50f,
                        steps = 50,
                        onValueChange = {
                            contrast = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Shadow Floor (Black Depth)",
                        valueText = "${if (blackFloor >= 0) "+" else ""}$blackFloor",
                        value = blackFloor.toFloat(),
                        range = -30f..30f,
                        steps = 60,
                        onValueChange = {
                            blackFloor = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                }

                // SECTION 3: Line Art & Palette Vibrance
                TunerSection(
                    title = "🎨 Section 3: Line Art & Palette Vibrance",
                    isExpanded = section3Expanded,
                    onToggle = { section3Expanded = !section3Expanded },
                ) {
                    TunerSlider(
                        title = "Line Art Ink Deepness",
                        valueText = String.format("0.%02d", lineThresh),
                        value = lineThresh.toFloat(),
                        range = 0f..45f,
                        steps = 45,
                        onValueChange = {
                            lineThresh = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Line Art Density Curve",
                        valueText = String.format("%.2f", lineExp / 100f),
                        value = lineExp.toFloat(),
                        range = 50f..250f,
                        steps = 40,
                        onValueChange = {
                            lineExp = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Overall Color Vibrance",
                        valueText = String.format("%.2fx", satMul / 100f),
                        value = satMul.toFloat(),
                        range = 20f..250f,
                        steps = 46,
                        onValueChange = {
                            satMul = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Midtone Color Retention",
                        valueText = "$colorMix%",
                        value = colorMix.toFloat(),
                        range = 50f..150f,
                        steps = 20,
                        onValueChange = {
                            colorMix = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                    TunerSlider(
                        title = "Unsharp Texture Clarity",
                        valueText = "$clarity%",
                        value = clarity.toFloat(),
                        range = 0f..300f,
                        steps = 30,
                        onValueChange = {
                            clarity = it.roundToInt()
                            activePresetMode = 7
                            triggerRender()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Downloaded Pages Picker Dialog
            if (showPagePickerDialog) {
                Dialog(onDismissRequest = { showPagePickerDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select Downloaded Page",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )

                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showPagePickerDialog = false
                                                    loadDefaultSample()
                                                },
                                        ) {
                                            Text(
                                                text = "⚡ Default Studio Sample (Built-in B&W Manga Page)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(12.dp),
                                            )
                                        }
                                    }
                                    if (downloadedPages.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "No downloaded manga chapters found.\nUse 'Pick File' to choose any image from storage.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    } else {
                                        items(downloadedPages) { (title, file) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showPagePickerDialog = false
                                                    scope.launch(Dispatchers.IO) {
                                                        file.openInputStream().use { stream ->
                                                            BitmapFactory.decodeStream(stream)?.let { bmp ->
                                                                withContext(Dispatchers.Main) {
                                                                    processNewImage(bmp, title)
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                OutlinedButton(onClick = { showPagePickerDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            if (showSavePresetDialog) {
                AlertDialog(
                    onDismissRequest = { showSavePresetDialog = false },
                    title = { Text("Save Preset") },
                    text = {
                        Column {
                            Text("Save current tuning sliders as active defaults or create a new named preset:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = newPresetName,
                                onValueChange = { newPresetName = it },
                                label = { Text("Preset Name") },
                                placeholder = { Text("e.g. Summer Beach, Vivid Cyberpunk") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val name = newPresetName.trim().ifEmpty { "Custom Preset" }
                                translationPreferences.colorizerSkinHue().set(skinHue)
                                translationPreferences.colorizerSkinSat().set(skinSat)
                                translationPreferences.colorizerBlueCap().set(blueCap)
                                translationPreferences.colorizerRedFlush().set(redFlush)
                                translationPreferences.colorizerSkinMinLum().set(skinMinLum)
                                translationPreferences.colorizerPaperThresh().set(paperThresh)
                                translationPreferences.colorizerPaperFeather().set(paperFeather)
                                translationPreferences.colorizerGamma().set(gamma)
                                translationPreferences.colorizerContrast().set(contrast)
                                translationPreferences.colorizerBlackFloor().set(blackFloor)
                                translationPreferences.colorizerLineThresh().set(lineThresh)
                                translationPreferences.colorizerLineExp().set(lineExp)
                                translationPreferences.colorizerSatMul().set(satMul)
                                translationPreferences.colorizerColorMix().set(colorMix)
                                translationPreferences.colorizerClarity().set(clarity)

                                val newParams = ColorizeImageUtils.ColorizerTuningParams(
                                    skinHue = skinHue,
                                    skinSat = skinSat,
                                    blueCap = blueCap,
                                    redFlush = redFlush,
                                    skinMinLum = skinMinLum,
                                    paperThresh = paperThresh,
                                    paperFeather = paperFeather,
                                    gamma = gamma,
                                    contrast = contrast,
                                    blackFloor = blackFloor,
                                    lineThresh = lineThresh,
                                    lineExp = lineExp,
                                    satMul = satMul,
                                    colorMix = colorMix,
                                    clarity = clarity,
                                )
                                customPresets = customPresets + (name to newParams)
                                activePresetName = name
                                activePresetMode = 7
                                showSavePresetDialog = false
                                context.toast("Preset '$name' saved!")
                            },
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showSavePresetDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    colorizeEngine.unloadSession()
                    origBitmap?.recycle()
                    rawModelBitmap?.recycle()
                    previewBitmap?.recycle()
                    origBitmap = null
                    rawModelBitmap = null
                    previewBitmap = null
                    System.gc()
                    Runtime.getRuntime().gc()
                }
            }
        }
    }
}

    @Composable
    private fun TunerSection(
        title: String,
        isExpanded: Boolean,
        onToggle: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    @Composable
    private fun TunerSlider(
        title: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.height(28.dp),
            )
        }
    }

    @Composable
    private fun BeforeAfterSplitViewer(
        beforeBitmap: Bitmap,
        afterBitmap: Bitmap,
        splitFraction: Float,
        onSplitFractionChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val beforeImage = remember(beforeBitmap) { beforeBitmap.asImageBitmap() }
        val afterImage = remember(afterBitmap) { afterBitmap.asImageBitmap() }

        BoxWithConstraints(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val splitX = widthPx * splitFraction

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(widthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (widthPx > 0f) {
                                val newFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.02f, 0.98f)
                                onSplitFractionChange(newFraction)
                            }
                        }
                    },
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val currentSplitX = canvasW * splitFraction

                // 1. Draw Colorized image full width
                drawImage(
                    image = afterImage,
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    dstSize = androidx.compose.ui.unit.IntSize(canvasW.toInt(), canvasH.toInt()),
                )

                // 2. Draw Original B&W image clipped to [0 .. currentSplitX] on Left
                val leftClipPath = androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, currentSplitX, canvasH))
                }
                clipPath(leftClipPath) {
                    drawImage(
                        image = beforeImage,
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize = androidx.compose.ui.unit.IntSize(canvasW.toInt(), canvasH.toInt()),
                    )
                }

                // 3. Draw vertical divider line
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(currentSplitX, 0f),
                    end = androidx.compose.ui.geometry.Offset(currentSplitX, canvasH),
                    strokeWidth = 3.dp.toPx(),
                )
            }

            // Circular draggable handle in center
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            splitX.roundToInt() - 18.dp.roundToPx(),
                            (heightPx / 2f - 18.dp.toPx()).roundToInt(),
                        )
                    }
                    .size(36.dp)
                    .pointerInput(widthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (widthPx > 0f) {
                                val newFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.02f, 0.98f)
                                onSplitFractionChange(newFraction)
                            }
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("↔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Top labels
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "Original B&W",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "Colorized",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
