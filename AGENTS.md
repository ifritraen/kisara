# Kisara – AI Agent Guide

Kisara is an Android manga reader (min SDK 26, target SDK 36, JVM 17 / Kotlin) forked from **Komikku** (which was forked from **Mihon** + **TachiyomiSY**). Stack: Jetpack Compose + Material3, Voyager navigation, SQLDelight, Injekt DI.

## Active package IDs:
- `com.raen.kisara` (stable edition)
- `com.raen.kisara.beta` (preview/beta edition)
- `com.raen.kisara.alpha` (alpha edition)
- `com.raen.kisara.dev` (developer/debug edition)

## Versioning Structure Guidelines:
- **Stable**: Version name format `1.y.z` (starts at `1.0.0`)
- **Beta**: Version name format `0.1.0` (or `0.1.0-beta.x`)
- **Alpha**: Version name format `0.0.1-alpha.[commit-count]`
- **Dev**: Version name format `0.0.1-dev-[commit-count]` (strictly local/developer build)

---

## Mandatory Rules for AI Agents

### 1. Git Branching & Pushing
- Always create a **feature branch** for tasks (`git checkout -b <type>/<short-description>`).
- Commit on feature branch when ready. **Never** commit or push directly to `master` / `main` unless explicitly requested.
- Confirm current branch is not `master` / `main` before `git push` (`git branch --show-current`).

### 2. Internationalization (Strings)
- **Komikku-only strings**: Must use `KMR` (`import tachiyomi.i18n.kmk.KMR`) in `i18n-kmk/src/commonMain/moko-resources/base/`.
- **Shared Mihon strings**: `MR` in `i18n/src/commonMain/moko-resources/base/`.
- **TachiyomiSY-only strings**: `SYMR` in `i18n-sy/src/commonMain/moko-resources/base/`.
- **Inviolable Rules**:
  - Never add Komikku strings to `i18n/` or `i18n-sy/`.
  - Never edit non-`base` locale `strings.xml` or `plurals.xml` files (Weblate owns translations).
  - New Komikku features default to `KMR` + `i18n-kmk`.

### 3. Formatting & Build Verification Gates
- **Do NOT run automatic builds or spotless tasks.** Only run `./gradlew` commands (`spotlessApply`, `spotlessCheck`, `assembleDebug`) when explicitly requested in prompt or via slash commands like `/build`, `/up`, or `/grun`.
- Always use `./gradlew` syntax (never `gradlew.bat`).

### 4. Fork-Origin Markers
Preserve inline markers when editing:
- `// KMK -->` … `// KMK <--` (Komikku)
- `// SY -->` … `// SY <--` (TachiyomiSY)
- `// EXH -->` … `// EXH <--` (E-Hentai / exh)

---

## Skills & Reference Manuals
Detailed architecture, module layouts, and build pipelines are modularized into on-demand skills:
- **Architecture & Subsystems**: See `kisara-dev` skill (Voyager navigation, Injekt DI, SQLDelight DB migrations, Wireguard, Suggestions).
- **Fast Gradle & Compilation Slicing**: See `gradle-fast` skill.
- **Build, Deploy, & Stream Logs**: See `grun` skill.
- **Optimization & Benchmark Reference**: `D:\C\Clones\Tadami` (Tadami is the gold standard for startup, memory, and Compose rendering optimization).

