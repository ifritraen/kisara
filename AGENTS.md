# Kisara – AI Agent Guide
Stack: Android (min SDK 26, target SDK 36, JVM 17 / Kotlin), Jetpack Compose + Material3, Voyager, SQLDelight, Injekt DI. Forked from Komikku (Mihon + TachiyomiSY).

## Package IDs & Versioning
- `com.raen.kisara` (Stable `1.y.z`) | `com.raen.kisara.beta` (`0.1.0-beta.x`)
- `com.raen.kisara.alpha` (`0.0.1-alpha.[commit]`) | `com.raen.kisara.dev` (`0.0.1-dev-[commit]`)

---

## Mandatory Rules
1. **Git**: Work on feature branches (`git checkout -b <type>/<desc>`). Never commit or push directly to `master`/`main`.
2. **Strings (i18n)**:
   - Komikku features: `KMR` (`i18n-kmk/.../base/`). Never put in `i18n/` or `i18n-sy/`.
   - Shared Mihon: `MR` (`i18n/.../base/`) | TachiyomiSY: `SYMR` (`i18n-sy/.../base/`).
   - Never edit non-`base` locales (managed by Weblate).
3. **Builds & Spotless**: NEVER run `./gradlew`, builds, tests, or spotless automatically without explicit prompt request (`/build`, `/up`). Always use `./gradlew` (never `gradlew.bat`).
4. **Fork Markers**: Preserve inline markers (`// KMK -->`, `// SY -->`, `// EXH -->`).

---

## Skills & Reference
- **Architecture & DI/DB/Voyager**: `kisara-dev` skill
- **Fast Gradle & Compilation Slicing**: `gradle-fast` skill
- **Optimization Reference**: `D:\C\Clones\Tadami` (benchmark for startup, memory, Compose rendering).

