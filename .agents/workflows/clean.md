---
description: Project-type-aware clean — removes build artifacts and generated files. Never touches .aaa/ directory.
---
When the user executes `/clean`:
1. Detect the active project type by checking for these markers (in order):
   - `pubspec.yaml` → **Flutter/Dart**
   - `package.json` → **Node.js / React / Next.js**
   - `requirements.txt` or `pyproject.toml` → **Python**
   - `build.gradle` or `build.gradle.kts` (without pubspec.yaml) → **Android Native**
   - `Cargo.toml` → **Rust**

2. Run the appropriate clean command(s):

   **Flutter/Dart**:
   ```powershell
   flutter clean
   ```

   **Node.js / React / Next.js**:
   ```powershell
   Remove-Item -Recurse -Force node_modules, .next, dist, out, .cache -ErrorAction SilentlyContinue
   ```

   **Python**:
   ```powershell
   Get-ChildItem -Recurse -Include __pycache__, *.pyc, *.pyo, .pytest_cache, dist, build, *.egg-info | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
   ```

   **Android Native**:
   ```powershell
   .\gradlew clean
   ```

   **Rust**:
   ```powershell
   cargo clean
   ```

3. NEVER touch `.aaa/` or any of its contents.
4. Report what was cleaned and the space freed (if determinable).
