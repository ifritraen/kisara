# AniZen Architecture & Technical Deep-Dive

**Repository**: [AniZen (GitHub)](https://github.com/salmanbappi/AniZen.git)  
**Clone Path**: `D:\C\Clones\AniZen`  
**Base Lineage**: Aniyomi / Komikku / Mihon  
**Target Platform**: Android (Min SDK 26, Target SDK 35/36, JVM 17, AGP 8.8.x, Kotlin 2.1.x)  

---

## Table of Contents
1. [System Architecture & Module Hierarchy](#1-system-architecture--module-hierarchy)
2. [Dependency Injection & Data Persistence](#2-dependency-injection--data-persistence)
3. [Jetpack Compose UI & Dynamic Theming](#3-jetpack-compose-ui--dynamic-theming)
4. [Anikku Tracker Subsystem](#4-anikku-tracker-subsystem)
5. [Video Player Core Engine (libmpv)](#5-video-player-core-engine-libmpv)
6. [Touch Gestures & Player UX Architecture](#6-touch-gestures--player-ux-architecture)
7. [Anime4K GLSL Shaders & Video Post-Processing](#7-anime4k-glsl-shaders--video-post-processing)
8. [Subtitle Engine (libass) & Customization](#8-subtitle-engine-libass--customization)
9. [Audio Pipeline & Routing](#9-audio-pipeline--routing)
10. [AniSkip Integration & Chapter Subsystem](#10-aniskip-integration--chapter-subsystem)
11. [Stream Resolution, Caching & Performance Optimizations](#11-stream-resolution-caching--performance-optimizations)

---

## 1. System Architecture & Module Hierarchy

AniZen is structured as a clean-architecture, multi-module Kotlin Android project designed for high throughput, sub-second cold starts, and reactive state distribution.

```
AniZen Root (settings.gradle.kts)
├── app                     # Main application entry, activities, Voyager screens, ViewModels, player bridge
├── domain                  # Pure Kotlin domain models, interactors, repository contracts
├── data                    # SQLDelight database drivers, migrations, local repository implementations
├── core                    # Base utilities, dispatchers, common extensions, resource wrappers
├── core-metadata           # Media metadata extractors (MIME, ComicInfo, audio/video stream probes)
├── source-api              # Public API contracts for Anime/Manga/Novel extensions
├── source-local            # SAF & local filesystem media scanner and source adapter
├── presentation-core       # Reusable Compose foundation, Material 3 design system, themes, icons
├── presentation-widget     # Glance & Android AppWidgets (Home feed, continue watching)
├── anikku-tracker          # High-performance tracker sync engine (AniList GraphQL, MAL REST, SIMKL)
├── i18n / i18n-ank / ...   # Moko-resources multi-module localization
├── macrobenchmark          # AndroidX Macrobenchmark test suite & baseline profiles
└── telemetry               # Crash diagnostics & opt-in telemetry
```

### Module Responsibilities:
- **`domain`**: Fully decoupled from the Android runtime (`kotlin("multiplatform")` or pure JVM). Contains core domain entities (`Anime`, `Episode`, `History`, `Track`, `Category`) and use cases (`GetAnime`, `GetEpisodes`, `SetAnimeCategories`, `UpdateEpisode`).
- **`data`**: Implements domain repositories using SQLDelight generated queries, reactive Kotlin `Flow` wrappers, and Injekt singletons.
- **`source-api`**: Exposes lightweight contracts for third-party extensions to provide video streams (`Video`, `Track`, `Hoster`), episode listings, and search functionality.

---

## 2. Dependency Injection & Data Persistence

### 2.1 Dependency Injection: Injekt (`uy.kohesive.injekt`)
AniZen bypasses heavy annotation-processing DI frameworks (Dagger/Hilt) in favor of **Injekt**:
- Zero reflection overhead at runtime and near-zero build time penalty.
- Configured in `AppModule.kt` on application launch (`Injekt.importModule(AppModule(app))`).
- Provides fast constructor injection and lazy singleton resolution:
  ```kotlin
  val getAnime: GetAnime by injectLazy()
  val preferences: BasePreferences = Injekt.get()
  ```

### 2.2 Database: SQLDelight
- **Schema**: Written in declarative SQL files (`data/src/main/sqldelight/`).
- **Type-Safe Queries**: Compiles SQL directly into Kotlin data classes and executable statements at build time.
- **Reactive Streaming**: Leverages `app.cash.sqldelight:coroutines-extensions` with `AndroidSqliteDriver`, converting table queries into instant reactive `Flow` emissions (`asFlow().mapToList()`, `mapToOneOrDefault()`).
- **Migrations**: Automated schema version tracking via `.sqm` migration scripts with compile-time integrity checks.

---

## 3. Jetpack Compose UI & Dynamic Theming

- **Voyager Navigation**: Screens implement `cafe.adriel.voyager.core.screen.Screen`, bound to dedicated `ScreenModel` instances (`StateFlow<ScreenState>`) enforcing unidirectional data flow (UDF).
- **Cover-Derived Dynamic Palette**:
  - `CoverColorObserver.vibrantColors` extracts vibrant and dominant color swatches from cover art via AndroidX Palette / Coil bitmap renderers.
  - Injects dynamic palettes into `DynamicTachiyomiTheme` / `AniZenTheme`, automatically tinting player controls, sheet headers, and navigation indicators.
- **Compose Stability**:
  - Domain models annotated with `@Immutable` / `@Stable` to prevent recomposition churn.
  - UI state models utilize `kotlinx.collections.immutable.ImmutableList` and `ImmutableMap`.
  - Draw-phase animations: Scale and alpha animations (navigation icons, chips) execute in `.graphicsLayer { ... }` to bypass measurement/layout passes.

---

## 4. Anikku Tracker Subsystem

The `anikku-tracker` module powers comprehensive tracking across global anime/manga metadata providers:
1. **AniList**: Full GraphQL API v2 integration (`https://graphql.anilist.co`) for batch media matching, progress updates, custom score formatting, and remote ID resolution.
2. **MyAnimeList (MAL)**: OAuth2 REST API v2 client with token refresh and fallback parser.
3. **Kitsu & SIMKL**: JSON:API endpoints supporting watch status synchronization (`CURRENT`, `COMPLETED`, `PAUSED`, `DROPPED`, `PLANNING`).
4. **Bi-Directional Auto-Sync**: Background worker `TrackingSyncJob` automatically updates remote tracker progress, episode scores, and timestamps when an episode is marked as completed.

---

## 5. Video Player Core Engine (libmpv)

### 5.1 Native Artifacts & Packaging
AniZen uses **`aniyomi-mpv-lib`** (`com.github.salmanbappi:aniyomi-mpv-lib:1.27.n`):
- Native libraries preserved: `libmpv.so`, `libplayer.so`, `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswscale.so`, `libswresample.so`, `libgojni.so` (TorrServer).
- Target ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (with 16KB ELF page alignment for Android 15+).

### 5.2 JNI Bridge & View Architecture
- **`MPVLib.java`**: Direct static JNI bridge to `libmpv`. Methods: `create()`, `init()`, `destroy()`, `command(String[])`, `setPropertyString()`, `getPropertyString()`, `observeProperty()`.
- **`AniyomiMPVView.kt`**: Extends `BaseMPVView` (Android `SurfaceView`), managing surface lifecycle, track delegates (`sid`, `aid`, `secondarySid`), dynamic performance tiers, and shader initialization.
- **Lua IPC Bridge**: `aniyomi.lua` script runs inside the MPV instance, communicating with Kotlin via `user-data/aniyomi/*` properties.

### 5.3 Video Output & Hardware Acceleration
- **VO Profiles**:
  - `vo=gpu` (OpenGL ES via `gpu-context=android`): Standard, rock-solid compatibility with Anime4K GLSL shader chains.
  - `vo=gpu-next` (libplacebo via `gpu-context=androidvk` or OpenGL): Next-generation renderer for enhanced color grading.
- **Hardware Decoding (`hwdec`)**:
  - Configured as `"mediacodec,mediacodec-copy"`.
  - Zero-copy direct surface rendering (`mediacodec`) with seamless fallback to copy mode (`mediacodec-copy`) when software video filters or debanding are applied.
- **High-Quality Scaler**: Configurable `spline36` scaler with `fruit` dithering.

### 5.4 Network Demuxer & Token Escaping
When passing stream URLs with session tokens, cookies, or CDN headers to MPV:
```kotlin
val httpHeaderString = headers.map {
    it.key + ": " + it.value.replace(",", "\\,")
}.joinToString(",")

MPVLib.setOptionString("http-header-fields", httpHeaderString)
headers["User-Agent"]?.let { MPVLib.setOptionString("user-agent", it) }
```
*Note*: Escaping commas (`\,`) is critical to prevent the MPV option parser from splitting tokens.

---

## 6. Touch Gestures & Player UX Architecture

The player UI uses a **hybrid architecture**: native OpenGL/Vulkan `AniyomiMPVView` behind a 100% Jetpack Compose overlay (`PlayerControls.kt`).

```
┌────────────────────────────────────────────────────────────┐
│                    Compose Overlay Layer                   │
│                                                            │
│  Top Bar: Title, Episode Picker, Quick Buttons             │
│  Left Drag: Brightness Slider                              │
│  Right Drag: Volume + Software Boost Slider                │
│  Center: Play/Pause/Buffer + Fast Scrub Indicator         │
│  Double Tap: Left/Right Cumulative Skips (+10s, +20s...)  │
│  Long Press: Speed Ramping & Horizontal Speed Sliding     │
│  Bottom: Chapter-Notched Seekbar + Floating Thumbnail      │
│  Bottom Center: SlideToUnlock Pill                         │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│              AniyomiMPVView (Native Surface)               │
└────────────────────────────────────────────────────────────┘
```

### 6.1 Gesture Engine (`GestureHandler.kt`)
1. **Vertical Sliders**:
   - Left side: Hardware screen brightness (0.0 to 1.0) + software dimming overlay (-0.75 to 0.0) for dark environments.
   - Right side: System media volume (0%–100%) + MPV digital preamp gain boost (up to 200%).
2. **Horizontal Scrub Seeking**:
   - **Fast Keyframe Scrub** during drag (`MPVLib.command(arrayOf("seek", pos, "absolute+keyframes"))`).
   - **Exact Seek** on release (`"absolute"`).
   - Floats `ThumbnailPreview` card showing timestamp and current chapter.
3. **Cumulative Double-Tap Seek**:
   - Consecutive taps accumulate skip time (`+10s`, `+20s`, `+30s`) with an 800ms debounce.
   - Animated 3-stage chevrons (`DoubleTapSeekTriangles`) and oval ripples (`DoubleTapToSeekOvals`).
4. **Long-Press Speed Ramping & Sliding**:
   - Holding down smoothly ramps playback speed (`±0.1f` per 16ms) to 2.0x without audio pops.
   - Dragging horizontally while holding scrubs speed dynamically from `0.25x` to `4.0x` with floating `DoubleSpeedIndicator`.
5. **Pinch-to-Zoom & Pan**:
   - Tracks pointer distance and midpoint, driving MPV properties `video-zoom`, `video-pan-x`, `video-pan-y`.
6. **Controls Lock (`SlideToUnlock.kt`)**:
   - Replaces accidental-tap locks with an iOS-style slide-to-unlock pill (requires 85% drag threshold).

---

## 7. Anime4K GLSL Shaders & Video Post-Processing

### 7.1 Anime4K v4 Suite & Preset Pipeline
Shaders are bundled in `assets/shaders/` and unpacked to internal storage by `Anime4KManager.kt`.
Every active chain prepends `Anime4K_Clamp_Highlights.glsl` to eliminate ringing artifacts.

| Mode | Pipeline Stages | Target Content |
|---|---|---|
| **Mode A** | `Clamp` $\to$ `Restore_CNN` $\to$ `Upscale_CNN_x2` $\to$ `Downscale` $\to$ `Upscale_CNN_x2` | 1080p anime / line art reconstruction |
| **Mode B** | `Clamp` $\to$ `Restore_CNN_Soft` $\to$ `Upscale_CNN_x2` $\to$ `Downscale` $\to$ `Upscale_CNN_x2` | Clean anime with soft lines |
| **Mode C** | `Clamp` $\to$ `Upscale_Denoise_CNN_x2` $\to$ `Downscale` $\to$ `Upscale_CNN_x2` | Older 720p/1080p anime with compression noise |
| **Mode A+A** | `Clamp` $\to$ `Restore` $\to$ `Upscale` $\to$ `Downscale` $\to$ `Restore` $\to$ `Upscale` | Aggressive line enhancement |
| **Mode B+B** | `Clamp` $\to$ `Restore_Soft` $\to$ `Upscale` $\to$ `Downscale` $\to$ `Restore_Soft` $\to$ `Upscale` | Heavy blur elimination |
| **Mode C+A** | `Clamp` $\to$ `Upscale_Denoise` $\to$ `Downscale` $\to$ `Restore` $\to$ `Upscale` | Maximum reconstruction & denoise |

Each mode supports 3 quality profiles:
- **Fast (`S`)**: Lightweight CNN for mid-range GPUs.
- **Balanced (`M`)**: Standard CNN (default).
- **High (`L`)**: Heavy CNN for high-end flagships.

### 7.2 Adaptive Scaling & Thermal Throttling
- **Frame Drop Watchdog**: Monitors MPV property `vo-delayed-frame-count`. If delayed frames > 10, automatically downgrades active shader quality (`High` $\to$ `Balanced`).
- **Thermal Safety Gate**: Listens to Android `PowerManager.OnThermalStatusChangedListener`. Triggers defensive shader downgrade upon reaching `THERMAL_STATUS_THROTTLING`.

### 7.3 Debanding Engine (`VideoFilterUtils.kt`)
- **GPU Deband**: Configurable `deband=yes` with live sliders for `deband-iterations` (1–16), `deband-threshold` (0–200), `deband-range` (1–64), and `deband-grain` (0–200).
- **CPU Deband**: Fallback filter `vf="gradfun=radius=12"`.

---

## 8. Subtitle Engine (libass) & Customization

### 8.1 Styling & ASS Overrides
- Rendered via native **`libass`** inside MPV.
- `sub-ass-override`:
  - `"force"`: Strips author styles and applies user font, size, outline, and shadow.
  - `"scale"`: Preserves stylized anime typesetting while scaling proportionally to window size.
- **Customization Options**:
  - Fonts: Scans custom user fonts (`.ttf`, `.otf`) in storage via `TTFFile` parser (`sub-font`).
  - Font Size (`sub-font-size`), scale (`sub-scale`), bold (`sub-bold`), italic (`sub-italic`).
  - Colors: Hex ARGB for text (`sub-color`), border (`sub-border-color`), and background box (`sub-back-color`).
  - Border Style (`sub-border-style`), shadow offset (`sub-shadow-offset`), vertical position (`sub-pos`).

### 8.2 Dual Subtitles & External Injection
- Supports simultaneous primary (`sid`) and secondary (`secondary-sid`) subtitle tracks.
- Dynamic external subtitle loading: `MPVLib.command(arrayOf("sub-add", uri, "select", title, lang))`.
- **Interactive Sync Panel (`SubtitleDelayPanel.kt`)**: Stopwatch calibration ("*Voice heard*" vs "*Text seen*") to calculate millisecond offset for `sub-delay`.

---

## 9. Audio Pipeline & Routing

- **Audio Drivers (`ao`)**: `audiotrack` (universal compatibility) and `aaudio` (low-latency Android 8.1+).
- **Audio Channels**: `auto-safe`, `stereo`, `mono`, and `reverse-stereo` (`af="pan=[stereo|c0=c1|c1=c0]"`).
- **Pitch-Preserving Speed Adjustment**: Uses MPV's `scaletempo2` time-stretch algorithm (`audio-pitch-correction=yes`) to maintain natural vocal pitch at 0.25x–4.0x speeds.
- **Audio Focus & Becoming Noisy**:
  - Requests `AUDIOFOCUS_GAIN` with transient ducking (`volume` multiplied by 0.5x).
  - Listens to `ACTION_AUDIO_BECOMING_NOISY` to pause playback on headphone disconnection.

---

## 10. AniSkip Integration & Chapter Subsystem

```
Episode Loaded
      │
      ▼
Resolve Tracker ID (MAL ID or AniList GraphQL idMal)
      │
      ▼
Fetch Skip Times from https://api.aniskip.com/v2/skip-times
      │
      ▼
Merge Chapter Timestamps with MKV Chapters (ChapterUtils)
      │
      ▼
Seeker Seekbar: Render Lavender Highlight Segments (#D8BBDF)
      │
      ▼
Playback Position Watcher:
  ├─ Auto-Skip Enabled: Instantly seek to chapter end
  ├─ Netflix-Style: 5s Countdown Toast + "Don't Skip" button
  └─ Manual: Display "Skip Opening" button overlay
```

1. **API Integration (`AniSkipApi.kt`)**: Queries `api.aniskip.com` for `op`, `ed`, `recap`, `mixed-op` intervals.
2. **Hybrid ID Resolution**: Uses MAL remote ID, or queries AniList GraphQL (`Media(id: $anilistId) { idMal }`) when tracking via AniList.
3. **Seekbar Integration**: Merges skip timestamps into `Seeker` progress bar as distinct lavender highlight blocks.
4. **Flexible Automation**: Offers instant auto-skip, manual pill button, or Netflix-style 5s countdown with cancel button.

---

## 11. Stream Resolution, Caching & Performance Optimizations

### 11.1 Dynamic Buffer Scaling (`DeviceTierManager`)
AniZen dynamically sets MPV demuxer cache buffers based on `Build.VERSION.MEDIA_PERFORMANCE_CLASS`:

| Tier | RAM / Performance Profile | `demuxer-max-bytes` | `demuxer-max-back-bytes` | `demuxer-readahead-secs` |
|---|---|---|---|---|
| **LOW** | Low-end / MPC < 31 | 64 MB | 32 MB | 60s |
| **MID** | Standard / MPC 31 | 128 MB | 64 MB | 120s |
| **HIGH** | Flagship / MPC 33+ | 192 MB | 128 MB | 180s (+24 hwdec frames) |

- **Lifecycle Cache Trimming**: Automatically triggers `shrinkCache()` (drops buffer to 64MB) in `onTrimMemory()` and `onStop()`, restoring it on `onResume()`.

### 11.2 Smart Stream Continuity (`DefaultStreamSelector.kt`)
A 600-line release-group fingerprinting engine:
- Extracts tags from stream titles (e.g. `[SubsPlease]`, `[Erai-raws]`, `1080p`, `Dual-Audio`).
- Automatically resolves and locks the matching release group, hoster, and audio track across subsequent episode transitions.

### 11.3 Google Cast & Local Subtitle Proxy (`CastManager.kt`)
- Embedded **`NanoHTTPD`** web server extracts embedded MKV subtitles or cached translations and serves them via local HTTP endpoint (`http://<ip>:<port>/subtitles.vtt`).
- Integrates Google Cast Framework (`play-services-cast-framework`) to stream video and styled subtitles directly to Chromecast/Android TV devices.
