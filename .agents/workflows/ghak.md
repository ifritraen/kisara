---
description: Push project to GitHub under `akhlak` identity, auto-sanitize cross-identity mentions, manage persistent keystore signing, and execute self-healing GitHub Actions build monitoring.
---
# /ghak Workflow (Identity: akhlak)

## 0. Target Repository Persistence (AUTO-SAVE)
- **Saved Target Repo**: [None - specify once to auto-save]
- **Persistence Rule**: If a GitHub repository is provided in the user prompt (e.g., `akhlakurrahman1011/AnymeX` or URL) or detected via `git remote get-url origin`:
  - If **Saved Target Repo** is `[None - specify once to auto-save]` or different, instantly edit and update the `- **Saved Target Repo**: <owner/repo>` line in this `.agents/workflows/ghak.md` file.
  - All subsequent `/ghak` runs will automatically read and reuse this saved repository without asking again.

## 1. Identity Sanitization & Credential Gate (STRICT)
- **Active Profile**: `akhlakurrahman1011` (`akhlak-pro-red@gmail.com`)
- **Credential Source**: Dynamically read token from `C:\Users\akhla\.gemini\.agents\workflows\InfoBank\sensitive.md` (Do NOT hardcode tokens).
- **Sanitization Checklist**:
  - Scan staged files, commit messages, code comments, and metadata.
  - Instantly purge/remove any occurrences of `ifritraen`, `ifrit`, or `raen`.
- Configure Git credentials:
  ```powershell
  git config user.name "akhlakurrahman1011"
  git config user.email "akhlak-pro-red@gmail.com"
  ```

## 2. Version Bump Engine (Single Source of Truth)
- Always sync versioning from `pubspec.yaml` so tag, title, and APK filenames match 100%.
- Inspect prompt to determine increment tier:
  - **New Major Version** (contains 'new version' or 'v2'): `+1.0.0`
  - **Feature Update** (contains 'major' or 'big'): `+0.1.0`
  - **Patch Update** (default): `+0.0.1`
- Update `pubspec.yaml` version line (`version: X.Y.Z+B`).

## 3. Persistent Keystore & CI/CD Workflow Audit
- Verify `.github/workflows/build.yml` exists. If missing or inconsistent, write full CI/CD configuration:
  - Trigger on push to `master`, `main` and `v*` tags.
  - Dynamically extract version string from `pubspec.yaml` or `build.gradle.kts`.
  - **Build Signature Consistency (CRITICAL)**: Always restore and decode persistent release keystore Base64 (`PS_RELEASE_KEY_FILE` or `GH_RELEASE_KEYSTORE_PATH`) before compilation so that both release and CI workflows produce identically signed release APKs (`<AppName>_v<Version>-release.apk`).
  - Run code analysis/checks prior to build.
  - Build signed release APKs/AABs.
  - Publish GitHub Release via `softprops/action-gh-release@v2`.

## 4. Commit & Push
- Stage specific changed files (never `git add .`).
- Commit message: `release: v<Version> - update under akhlak identity`
- Push to remote using Saved Target Repo: `git push origin main` and `git push origin v<Version>`

## 5. Self-Healing Build Polling & Repair Loop (Max 3 Attempts)
- **Timer Standard**: Always use a single **300s (5-minute) timer** (`schedule` tool with `DurationSeconds=300`) to check any job status. Never use shorter polling intervals.
- Monitor build status using Saved Target Repo: `gh run list --repo <SavedTargetRepo>`
- **If Success**: Output GitHub Release URL to user and stop.
- **If Failure**:
  - Fetch failure logs: `gh run view <RunID> --log-failed --repo <SavedTargetRepo>`
  - Diagnose exact root cause from traceback.
  - Fix code / workflow file.
  - Commit under `akhlak` identity and push.
  - Repeat polling until green.
