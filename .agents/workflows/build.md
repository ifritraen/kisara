---
description: Build Kisara APK using optimized Gradle strategy, custom build flags, and optional adb deployment.
---
When the user executes `/build` or starts their request with `/build`:

1. **Verify Gradle Performance Properties (`gradle.properties`)**:
   Ensure `gradle.properties` includes performance auto-config flags:
   - `org.gradle.daemon=true`, `parallel=true`, `caching=true`, `configureondemand=true`
   - `jvmargs=-Xmx6g -XX:+UseParallelGC -Dfile.encoding=UTF-8`
   - `kotlin.incremental=true`, `kotlin.incremental.useClasspathSnapshot=true`
   - `org.gradle.configuration-cache=false` (Disabled due to `shortcut-helper` plugin compatibility)

2. **Execute Ultra-Fast Build & Deploy Command**:
   - **Default Fast Local Dev Build & Install**:
     `./gradlew assembleDebug --offline --build-cache --parallel; if ($LASTEXITCODE -eq 0) { adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk }`
   - **Fast Preview Build & Install**:
     `./gradlew assemblePreview --offline --build-cache --parallel; if ($LASTEXITCODE -eq 0) { adb install -r app/build/outputs/apk/preview/app-arm64-v8a-preview.apk }`
   - **Full Preview Build** (`r` or `full` flag):
     `./gradlew assemblePreview -Pinclude-telemetry -Penable-updater --build-cache --parallel`
   - **Fast Release Build** (`fast` flag):
     `./gradlew assembleRelease -Pdisable-code-shrink --build-cache --parallel`

3. **Check ADB Deployment Status**:
   - If device connected, the command above automatically installs the APK upon successful build.
   - If no device connected, report compiled output location under `app/build/outputs/apk/`.

4. Report build size details and deployment status cleanly.
