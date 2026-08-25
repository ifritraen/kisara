---
description: Build Kisara APK using optimized Gradle strategy, custom build flags, and optional adb deployment.
---
When the user executes `/build` or starts their request with `/build`:

1. **Verify Gradle Performance Properties (`gradle.properties`)**:
   Ensure `gradle.properties` includes performance auto-config flags:
   - `org.gradle.daemon=true`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `org.gradle.configureondemand=true`
   - `org.gradle.vfs.watch=true`, `org.gradle.workers.max=3`, `org.gradle.daemon.idletimeout=300000`
   - `org.gradle.jvmargs=-Xmx2048m -Xms512m -XX:+UseG1GC -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8`
   - `kotlin.daemon.jvmargs=-Xmx1536m -XX:+UseG1GC`
   - `kotlin.incremental=true`, `kotlin.incremental.useClasspathSnapshot=true`, `kapt.incremental.apt=true`, `kapt.use.worker.api=true`

2. **Execute Build Command (Target Active/Requested Variant)**:
   - **Fast Compilation Check**:
     `./gradlew :app:compile<Variant>Kotlin --build-cache --parallel --configuration-cache 2>&1 | Where-Object { "$_" -match "^e:\s|e:\s*file:|FAILURE:|FAILED|Compilation error|Unresolved|Type mismatch" } | Select-Object -First 40 | ForEach-Object { "$_".Trim() }` (matches target variant: `compileAlphaKotlin`, `compileDebugKotlin`, `compilePreviewKotlin`, `compileReleaseKotlin`)
   - **Ultra-Fast Local Assemble Command (Measures Time & arm64-v8a Single ABI)**:
      ```powershell
      $raw = @(); $elapsed = Measure-Command {
        $raw = @(& ./gradlew :app:assemble<Variant> `
          "-Pdisable-code-shrink" `
          "-Pandroid.injected.build.abi=arm64-v8a" `
          --build-cache 2>&1 | ForEach-Object { "$_" })
      }
      if ($LASTEXITCODE -eq 0) {
        $apk = (Get-Item app/build/outputs/apk/<variant>/*-arm64-v8a-*.apk, app/build/intermediates/apk/<variant>/*-arm64-v8a-*.apk -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
        Write-Host "BUILD SUCCESSFUL in $($elapsed.TotalSeconds.ToString('F1'))s: $apk" -ForegroundColor Green
        Write-Host "Installing $apk..."
        adb devices
        adb install -r -t $apk
      } else {
        Write-Host "BUILD FAILED in $($elapsed.TotalSeconds.ToString('F1'))s (Exit code: $LASTEXITCODE)" -ForegroundColor Red
        $errs = $raw | Where-Object { $_ -match "^e:\s|e:\s*file:|FAILURE:|FAILED|Compilation error|Unresolved|Type mismatch|Cannot cast" }
        if ($errs) { $errs | Select-Object -First 30 } else { $raw | Select-Object -Last 25 }
      }
      ```

3. **Check Build Output**:
   - Report compiled output location under `app/build/outputs/apk/<variant>/`.
   - Report build duration cleanly.
