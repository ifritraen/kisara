---
description: Push project to GitHub under `ifritraen` identity, auto-sanitize cross-identity mentions, manage persistent keystore signing, and execute self-healing GitHub Actions build monitoring.
---
# /ghra Workflow (Identity: ifritraen)

## 0. Target Repository Persistence (AUTO-SAVE)
- **Saved Target Repo**: ifritraen/kisara
- **Persistence Rule**: If a GitHub repository is provided in the user prompt (e.g., `ifritraen/AnymeX` or URL) or detected via `git remote get-url origin`:
  - If **Saved Target Repo** is `[None - specify once to auto-save]` or different, instantly edit and update the `- **Saved Target Repo**: <owner/repo>` line in this `.agents/workflows/ghra.md` file.
  - All subsequent `/ghra` runs will automatically read and reuse this saved repository without asking again.

## 1. Identity Sanitization & Credential Gate (STRICT)
- **Active Profile**: `ifritraen` (`ifrit.raen@gmail.com`)
- **Credential Source**: Dynamically read token from `C:\Users\akhla\.gemini\.agents\workflows\InfoBank\sensitive.md` (Do NOT hardcode tokens).
- **Sanitization Checklist**:
  - Scan staged files, commit messages, code comments, and metadata.
  - Instantly purge/remove any occurrences of `akhlak` or `akhla`.
- Configure Git credentials:
  ```powershell
  git config user.name "ifritraen"
  git config user.email "ifrit.raen@gmail.com"
  ```

## 2. Version Bump Engine (Single Source of Truth)
- Always sync versioning from `pubspec.yaml` so tag, title, and APK filenames match 100%.
- Inspect prompt to determine increment tier:
  - **New Major Version** (contains 'new version' or 'v2'): `+1.0.0`
  - **Feature Update** (contains 'major' or 'big'): `+0.1.0`
  - **Patch Update** (default): `+0.0.1`
- Update `pubspec.yaml` version line (`version: X.Y.Z+B`).

## 3. Persistent Keystore & CI/CD Workflow Audit
- Verify `.github/workflows/build.yml` exists. If missing, write full CI/CD configuration:
  - Trigger on push to `main` and `v*` tags.
  - Dynamically extract version string from `pubspec.yaml`.
  - Restore persistent release keystore Base64 before build.
  - Run `flutter analyze --no-fatal-infos --no-fatal-warnings`.
  - Build split-ABI release APKs (`flutter build apk --split-per-abi`).
  - Rename output APKs (`<AppName>_v<Version>_<ABI>-release.apk`).
  - Publish GitHub Release via `softprops/action-gh-release@v2`.

## 4. Commit & Push
- Stage specific changed files (never `git add .`).
- Commit message: `release: v<Version> - update under ifritraen identity`
- Push to remote using Saved Target Repo: `git push origin main` and `git push origin v<Version>`

## 5. Self-Healing Build Polling & Repair Loop (Max 3 Attempts)
- Monitor build status using Saved Target Repo: `gh run list --repo <SavedTargetRepo>`
- **If Success**: Output GitHub Release URL to user and stop.
- **If Failure**:
  - Fetch failure logs: `gh run view <RunID> --log-failed --repo <SavedTargetRepo>`
  - Diagnose exact root cause from traceback.
  - Fix code / workflow file.
  - Commit under `ifritraen` identity and push.
  - Repeat polling until green.
