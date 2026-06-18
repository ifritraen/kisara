---
description: Apply the premium haze frosted glass / glassmorphic effect to any layout, dialog, or UI component.
---
When the user executes `/haze` (e.g. `/haze [component/screen/dialog]`), follow this exact protocol to implement frosted glass glassmorphism using the local Haze configuration:

1. **Understand Haze Architecture in Komikku**:
   - `Haze` relies on a source-child relationship. The background layout (the content to be blurred) is marked as a **hazeSource**. The foreground element overlaying it (e.g., top/bottom bars, cards) is marked as a **hazeChild** via the custom `GlassSurface` wrapper.
   - **Important:** If the foreground element is in a separate Android `Window` (like a Compose `Dialog` or bottom sheet), standard Compose Haze cannot capture/blur the main activity window behind it. For dialogs and sheets, we use native OS-level window blur (`FLAG_BLUR_BEHIND`) combined with a transparent window background and a semi-transparent `GlassSurface` card.

2. **Step-by-Step Implementation Guide**:

   ### A. Setting up the Composition Local & Source
   - Check if `LocalHazeState` is already provided at the root of the screen. If not, initialize a `HazeState` and provide it:
     ```kotlin
     import dev.chrisbanes.haze.HazeState
     import eu.kanade.presentation.components.LocalHazeState

     val hazeState = remember { HazeState() }
     CompositionLocalProvider(LocalHazeState provides hazeState) {
         // Screen content here
     }
     ```
   - Apply `hazeSource` to the background content that will be blurred behind the overlays (e.g., the main scrolling list or container):
     ```kotlin
     import dev.chrisbanes.haze.hazeSource

     Box(
         modifier = Modifier
             .fillMaxSize()
             .hazeSource(state = hazeState)
     ) {
         // Background content (will be blurred)
     }
     ```

   ### B. Wrapping Overlay Components in GlassSurface
   - For floating bars, cards, or components that float over the source, wrap them in `GlassSurface` (defined in `eu.kanade.presentation.components.GlassSurface`):
     ```kotlin
     import eu.kanade.presentation.components.GlassDefaults
     import eu.kanade.presentation.components.GlassSurface

     GlassSurface(
         modifier = Modifier.fillMaxWidth(),
         shape = RoundedCornerShape(16.dp),
         style = GlassDefaults.prominentStyle(), // Options: subtleStyle(), regularStyle(), prominentStyle()
         dialogSurface = false
     ) {
         // Composable content here
     }
     ```

   ### C. Implementing Haze in Dialogs & Bottom Sheets
   - Always use `AdaptiveSheet` (or a `TabbedDialog` which uses it under the hood) for popups and bottom sheets.
   - `AdaptiveSheet` automatically handles making the dialog window background transparent and applying OS-level blur behind on Android 12+:
     ```kotlin
     // In AdaptiveSheet.kt (DisposableEffect handles the native window flags):
     window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
     window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
     window.attributes.blurBehindRadius = 60
     ```
   - For elements inside the dialog, wrap them using `dialogSurface = true` in `GlassSurface` so that it renders a premium semi-transparent card overlaying the system-blurred background:
     ```kotlin
     GlassSurface(
         modifier = Modifier.requiredWidthIn(max = 460.dp),
         shape = MaterialTheme.shapes.extraLarge,
         style = GlassDefaults.prominentStyle(),
         dialogSurface = true
     ) {
         // Dialog content
     }
     ```

3. **Verify Formatting & Style Guidelines**:
   - Verify that all imports match and compile properly.
   - Run formatting check:
     ```bash
     ./gradlew spotlessApply
     ./gradlew spotlessCheck
     ```
