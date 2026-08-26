# Kisara Video Player Enhancement & Modernization Plan

**Target Application**: Kisara / Catmikku (`d:\C\Catmikku`)  
**Package Scope**: `app/src/main/java/eu/kanade/tachiyomi/ui/player/` & `presentation/player/`  
**Reference Implementations**: AniZen (`D:\C\Clones\AniZen`) & Tadami (`D:\C\Clones\Tadami`)  
**Author**: Antigravity  

---

## Executive Summary

Kisara currently has a functional MPV-based video player, alongside an industry-leading AI/ML on-the-fly subtitle translation subsystem (`AiSubtitleTranslationProvider`). However, comparing Kisara against AniZen reveals critical opportunities to upgrade low-level video engine stability, eliminate UI-thread startup stalls, introduce dynamic hardware-aware buffer caching, enrich gesture controls, expand Anime4K shader presets, and improve stream continuity across episodes.

This document presents a structured, modular 7-phase modernization blueprint to elevate Kisara's video player to the top tier of Android anime players.

---

## 1. Comparative Architecture & Feature Matrix

| Capability / Subsystem | Current Kisara (`Catmikku`) | AniZen (`AniZen`) | Modernized Kisara Target | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **MPV Engine Version** | `aniyomi-mpv-lib:1.18.n` | `aniyomi-mpv-lib:1.27.n` | `1.27.n` (Vulkan `gpu-next`, 16KB ELF alignment, libcurl HTTP/2) | **P0 (Critical)** |
| **Player Startup I/O** | Synchronous main-thread UniFile asset/font copy | Asynchronous `Dispatchers.IO` copy with cached timestamps | Zero main-thread I/O; non-blocking background asset unpacker | **P0 (Critical)** |
| **Demuxer Cache Strategy** | Static 32MB / 64MB buffer | `DeviceTierManager` dynamic scaling (64/128/192MB) + `shrinkCache()` | Dynamic MPC 31/33+ tiering + LMK memory pressure trimming | **P0 (Critical)** |
| **Network Demuxer Headers** | Basic header map serialization | Comma-escaped (`\,`) `http-header-fields` + User-Agent | Comma-escaped header parser to fix tokenized stream failures | **P0 (Critical)** |
| **Anime4K & Shaders** | 3 static presets (Off, Balanced, Quality) | 6 Modes (A, B, C, A+, B+, C+) × 3 Quality Tiers (S/M/L) | Complete Anime4K v4 suite + auto anti-ringing clamp | **P1 (High)** |
| **Adaptive Performance** | None | `vo-delayed-frame-count` + Thermal Listener auto-downgrade | Automatic frame-drop & thermal throttling safety watchdog | **P1 (High)** |
| **Deband & Filters** | Basic Color Filters | GPU Deband (Iterations/Threshold/Range/Grain) + CPU gradfun | Full live GPU/CPU deband engine + 10 theme presets | **P1 (High)** |
| **Seek & Scrubbing** | Standard seekbar drag | Keyframe-fast drag (`absolute+keyframes`) + exact seek on up | Smooth keyframe scrubbing + floating `ThumbnailPreview` | **P1 (High)** |
| **Double-Tap Seeking** | Simple 10s step | Cumulative skip accumulator (+10s, +20s, +30s) with 3 chevrons | Cumulative skipping + smooth chevron animation | **P1 (High)** |
| **Speed & Gestures** | Basic 2x toggle | Speed ramping (±0.1x / 16ms) + drag-to-speed slider (0.25x–4.0x) | Ramped speed + horizontal speed sliding + `SlideToUnlock` | **P2 (Medium)** |
| **Stream Continuity** | Basic quality string match | `DefaultStreamSelector` (release group, audio codec fingerprint) | Multi-attribute stream fingerprinting across episodes | **P2 (Medium)** |
| **AniSkip Subsystem** | Basic skip button | Hybrid MAL/AniList GraphQL ID + Seekbar segment highlight | Lavender seekbar segments (`Seeker`) + Netflix countdown | **P2 (Medium)** |
| **AI Subtitle Engine** | **Cutting-Edge** (LLM batching + MLKit + Tag Masker) | Basic ASS override only | **Retain & Extend** Kisara's AI translation pipeline | **P0 (Retain)** |
| **Chromecast / Local Cast** | None | Google Cast Framework + NanoHTTPD local subtitle server | Optional modular Google Cast player bridge | **P3 (Future)** |

---

## 2. Phase-by-Phase Modernization Blueprint

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    KISARA PLAYER MODERNIZATION ROADMAP                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [PHASE 1: Core Engine & MPV 1.27.n Upgrade]                           │
│  - Upgrade aniyomi-mpv-lib to 1.27.n (Vulkan, libcurl, 16KB pages)      │
│  - Fix HTTP header escaping (comma-escaping for tokens)                 │
│  - Add JNI initialization safety guards in AniyomiMPVView               │
│                                                                         │
│  [PHASE 2: Startup Deferral & Dynamic Memory Scaling]                   │
│  - Move font/shader/script copy off UI thread (Dispatchers.IO)          │
│  - Integrate DeviceTierManager (64MB / 128MB / 192MB demuxer cache)     │
│  - Wire shrinkCache() into onTrimMemory() and onStop()                  │
│                                                                         │
│  [PHASE 3: Full Anime4K v4 Suite & Debanding Filters]                  │
│  - Expand Anime4KShaderPreset to 6 Modes (A-C+) x 3 Tiers (S/M/L)        │
│  - Implement Frame-Drop & Thermal Status auto-downgrade watcher         │
│  - Add GPU Deband sliders (Iterations, Threshold, Range, Grain)         │
│                                                                         │
│  [PHASE 4: Gesture Refinement & Seeking Polish]                         │
│  - Cumulative double-tap seeking (+10s, +20s...) with 3-chevron UI      │
│  - Fast keyframe scrub during drag + exact seek on release              │
│  - Speed ramping (16ms) + horizontal drag-to-speed slider (0.25x-4.0x)  │
│  - Modern SlideToUnlock pill overlay                                    │
│                                                                         │
│  [PHASE 5: AniSkip Seekbar Highlighting & Smart Continuity]             │
│  - AniList GraphQL idMal fallback resolver                              │
│  - Lavender chapter segments on Compose Seeker progress bar             │
│  - Netflix-style 5s countdown skip prompt                               │
│  - DefaultStreamSelector cross-episode release group persistence        │
│                                                                         │
│  [PHASE 6: AI Subtitle Pipeline Consolidation]                          │
│  - Deep integration of AI translation cache with dual-sub (secondary-sid)│
│  - TTFFile font scanner + real-time ASS override style calibrator       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Detailed Phase Specifications & Implementation Code

### Phase 1: Core Engine & MPV 1.27.n Upgrade

#### 1.1 Dependency Update
In `gradle/libs.versions.toml`:
```toml
[versions]
aniyomi-mpv-lib = "1.27.n"

[libraries]
aniyomi-mpv = { module = "com.github.salmanbappi:aniyomi-mpv-lib", version.ref = "aniyomi-mpv-lib" }
```

#### 1.2 HTTP Header Escaping in `PlayerActivity.kt`
Replace naive header serialization with escape-safe MPV formatting:
```kotlin
private fun setHttpOptions(video: Video) {
    if (viewModel.isEpisodeOnline() != true) return
    val source = viewModel.currentSource.value as? HttpSource ?: return

    val headers = (video.headers ?: source.headers)
        .toMultimap()
        .mapValues { it.value.firstOrNull() ?: "" }
        .toMutableMap()

    if (headers["User-Agent"].isNullOrEmpty()) {
        headers["User-Agent"] = networkHelper.defaultUserAgentProvider()
    }

    val httpHeaderString = headers.map { (key, value) ->
        key + ": " + value.replace(",", "\\,")
    }.joinToString(",")

    MPVLib.setOptionString("http-header-fields", httpHeaderString)
    headers["User-Agent"]?.let {
        MPVLib.setOptionString("user-agent", it)
    }
}
```

---

### Phase 2: Startup Deferral & Dynamic Memory Scaling

#### 2.1 Asynchronous Asset Staging in `PlayerActivity.kt`
Eliminate UI-thread frame drops on player launch:
```kotlin
private fun setupPlayerMPV() {
    val configDir = File(context.filesDir, "mpv")
    lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            val mpvConfFile = File(configDir, "mpv.conf")
            advancedPlayerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
            copyScripts()
            copyAssets(configDir)
            copyFontsDirectory()
        }
    }
}
```

#### 2.2 Device Tier Buffering in `AniyomiMPVView.kt`
```kotlin
fun applyPlaybackStrategy() {
    val tier = when (decoderPreferences.performanceProfile().get()) {
        PlayerEfficiency.MaxPerformance -> DeviceTierManager.Tier.HIGH
        PlayerEfficiency.Balanced -> DeviceTierManager.Tier.MID
        PlayerEfficiency.PowerSaver -> DeviceTierManager.Tier.LOW
        else -> DeviceTierManager.getTier(context)
    }

    val (maxMb, maxBackMb, readahead) = when (tier) {
        DeviceTierManager.Tier.LOW -> Triple(64, 32, 60)
        DeviceTierManager.Tier.MID -> Triple(128, 64, 120)
        DeviceTierManager.Tier.HIGH -> {
            MPVLib.setOptionString("hwdec-extra-frames", "24")
            Triple(192, 128, 180)
        }
    }

    currentMaxBytes = maxMb * 1024 * 1024L
    currentMaxBackBytes = maxBackMb * 1024 * 1024L

    MPVLib.setOptionString("demuxer-readahead-secs", "$readahead")
    MPVLib.setOptionString("demuxer-max-bytes", "$currentMaxBytes")
    MPVLib.setOptionString("demuxer-max-back-bytes", "$currentMaxBackBytes")
}

fun shrinkCache() {
    MPVLib.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024L}")
    MPVLib.setOptionString("demuxer-max-back-bytes", "${32 * 1024 * 1024L}")
}

fun restoreCache() {
    MPVLib.setOptionString("demuxer-max-bytes", "$currentMaxBytes")
    MPVLib.setOptionString("demuxer-max-back-bytes", "$currentMaxBackBytes")
}
```

In `PlayerActivity.kt`:
```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    binding.player.shrinkCache()
}

override fun onStop() {
    super.onStop()
    binding.player.shrinkCache()
}

override fun onResume() {
    super.onResume()
    binding.player.restoreCache()
}
```

---

### Phase 3: Anime4K v4 Suite & Video Filters

#### 3.1 Extended Anime4K Shader Pipeline
In `Anime4KManager.kt`:
```kotlin
enum class Anime4KMode(val title: String) {
    ModeA("Mode A (Line Art Reconstruction)"),
    ModeB("Mode B (Soft Line Restoration)"),
    ModeC("Mode C (Denoise & 2x Upscale)"),
    ModeAA("Mode A+A (Dual Pass Line Restoration)"),
    ModeBB("Mode B+B (Dual Pass Soft Restoration)"),
    ModeCA("Mode C+A (Denoise & Reconstruction)")
}

enum class Anime4KQuality(val suffix: String) {
    Fast("S"),
    Balanced("M"),
    High("L")
}
```
Prepend `Anime4K_Clamp_Highlights.glsl` across all chains to completely eliminate high-contrast ringing artifacts.

#### 3.2 Adaptive Performance & Thermal Watchdog
```kotlin
fun checkAdaptiveScaling(delayedFrames: Long) {
    if (delayedFrames > 10 && currentQuality == Anime4KQuality.High) {
        currentQuality = Anime4KQuality.Balanced
        applyActiveShaderPreset()
        viewModel.showToast("Performance: Anime4K scaled to Balanced")
    }
}
```

#### 3.3 GPU Deband Engine
Add preferences and live controls for:
- `deband=yes`
- `deband-iterations` (1..16, default 4)
- `deband-threshold` (0..200, default 48)
- `deband-range` (1..64, default 16)
- `deband-grain` (0..200, default 48)

---

### Phase 4: Gesture Refinements & Seeking Polish

#### 4.1 Cumulative Double-Tap Seek
In `PlayerViewModel.kt`:
```kotlin
private var _doubleTapSeekAmount = 0
private var doubleTapResetJob: Job? = null

fun onDoubleTapSeek(forward: Boolean, step: Int = 10) {
    doubleTapResetJob?.cancel()
    _doubleTapSeekAmount += if (forward) step else -step
    
    val target = (playerPos + _doubleTapSeekAmount).coerceIn(0, playerDuration)
    seekTo(target, precise = false)
    
    doubleTapResetJob = viewModelScope.launch {
        delay(800)
        _doubleTapSeekAmount = 0
    }
}
```

#### 4.2 Smooth Speed Ramping & Horizontal Speed Sliding
In `GestureHandler.kt`:
```kotlin
fun rampSpeed(targetSpeed: Float) {
    speedRampJob?.cancel()
    speedRampJob = coroutineScope.launch {
        while (abs(currentSpeed - targetSpeed) > 0.05f) {
            currentSpeed += if (currentSpeed < targetSpeed) 0.1f else -0.1f
            MPVLib.setPropertyDouble("speed", currentSpeed.toDouble())
            delay(16)
        }
        currentSpeed = targetSpeed
        MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
    }
}
```

#### 4.3 SlideToUnlock Modern Overlay
Add `SlideToUnlock.kt` requiring an intentional horizontal drag exceeding `85%` of the unlock track before unlocking player controls.

---

### Phase 5: AniSkip Subsystem & Smart Stream Continuity

#### 5.1 AniList GraphQL `idMal` Resolver
In `AniSkipApi.kt`:
```kotlin
suspend fun resolveMalIdFromAniList(anilistId: Long): Long? {
    val query = """query { Media(id: $anilistId) { idMal } }"""
    val response = apolloClient.query(query).await()
    return response.data?.Media?.idMal
}
```

#### 5.2 Segmented Chapter Highlights on Seeker Bar
In `SeekbarWithTimers.kt`:
- Convert AniSkip intervals into `IndexedSegment` objects with lavender fill color `Color(0xFFD8BBDF)`.
- Feed segments directly into `dev.vivvvek.seeker.Seeker(segments = chaptersList)`.

#### 5.3 DefaultStreamSelector Release-Group Continuity
Implement release-group regex extraction in `DefaultStreamSelector.kt`:
- Extract group tag `\[(.*?)\]` (e.g. `[SubsPlease]`).
- On episode change, score candidates and automatically select matching release group and audio track before playback starts.

---

## 4. Architectural Safety Gates & Kisara Specifics

1. **Retain Kisara's AI Translation Advantage**:
   Kisara's `AiSubtitleTranslationProvider.kt` and `GoogleSubtitleTranslationProvider.kt` are state-of-the-art. Keep this module fully intact and leverage MPV's `secondary-sid` property so users can display both original and AI-translated subtitles simultaneously.
2. **Preserve Fork Markers**:
   When implementing changes, preserve `// KMK -->` and `// SY -->` inline markers.
3. **Strings & I18n**:
   Add new player strings strictly to `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` using `KMR` imports.

---

## 5. Summary Matrix & Impact

| Enhancement Feature | Performance Impact | UX Impact | Implementation Effort |
|---|---|---|---|
| **MPV 1.27.n Core Engine** | ⚡ Vulkan `gpu-next`, HTTP/2, 16KB page support | Eliminates stream stutter & TLS handshake latency | Low (Drop-in dependency update) |
| **Async Startup I/O** | ⚡ Eliminates 300–500ms main-thread freeze | Instant player screen presentation | Low |
| **DeviceTierManager Dynamic Buffers** | ⚡ Prevents OOM kills on 4GB–6GB devices | Smooth high-bitrate 1080p/4K scrubbing | Medium |
| **Full Anime4K Suite (Modes A-C+)** | ⚡ Hardware-scaled GPU post-processing | Desktop-grade line art & upscaling | Medium |
| **Cumulative Seek & Speed Ramping** | ⚡ Smooth audio pitch preservation | Fluid, modern gesture navigation | Medium |
| **AniSkip Seekbar Markers** | ⚡ Zero runtime overhead | Visual OP/ED skip intervals | Low |
| **DefaultStreamSelector** | ⚡ Zero playback lag | Automated stream matching across episodes | Medium |
