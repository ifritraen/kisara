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
- Configure Git credentials & active account:
  ```powershell
  gh auth switch --user akhlakurrahman1011
  git config user.name "akhlakurrahman1011"
  git config user.email "akhlak-pro-red@gmail.com"
  ```

## 2. Version Bump Engine (Single Source of Truth)
- Always sync versioning from `pubspec.yaml` or `build.gradle.kts` so tag, title, and APK filenames match 100%.
- Inspect prompt to determine increment tier:
  - **New Major Version** (contains 'new version' or 'v2'): `+1.0.0`
  - **Feature Update** (contains 'major' or 'big'): `+0.1.0`
  - **Patch Update** (default): `+0.0.1`
- Update `pubspec.yaml` version line (`version: X.Y.Z+B`) or `build.gradle.kts` version variables (`major`, `minor`, `patch`).

## 3. Persistent Keystore & CI/CD Workflow Audit (STRICT)
- Verify `.github/workflows/build.yml` exists. If missing or inconsistent, write full CI/CD configuration.
- **Persistent Build Signature Creation & Management (CRITICAL)**:
  1. **Local Keystore Generation**: If a persistent release keystore does not exist for the project, generate a 10,000-day valid RSA keystore (e.g. `.aaa/<app_name>-release.keystore` with alias and password). Ensure `.aaa/` and `*.jks` are in `.gitignore`.
  2. **Base64 Conversion & Secret Upload**:
     - Convert the keystore file to Base64: `[System.Convert]::ToBase64String([IO.File]::ReadAllBytes("<path_to_keystore>"))`.
     - Upload to GitHub Repo secrets via `gh secret set`:
       - `KEYSTORE_BASE64` (Base64-encoded keystore file content)
       - `RELEASE_KEYSTORE_PASSWORD` (keystore password)
       - `RELEASE_KEYSTORE_ALIAS` (key alias name)
       - `RELEASE_KEY_PASSWORD` (key password)
  3. **CI Workflow Signing Step**: Ensure `.github/workflows/build.yml` contains a dedicated signing step using `rnhmjoj/android-sign-action@v1` (or equivalent) that decodes `KEYSTORE_BASE64` and signs compiled APKs before packaging/release:
     ```yaml
     - name: Sign APK with Persistent Keystore
       uses: rnhmjoj/android-sign-action@v1
       with:
         releaseDirectory: app/build/outputs/apk/preview
         signingKeyBase64: ${{ secrets.KEYSTORE_BASE64 }}
         alias: ${{ secrets.RELEASE_KEYSTORE_ALIAS }}
         keyStorePassword: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
         keyPassword: ${{ secrets.RELEASE_KEY_PASSWORD }}
     ```
  4. **Signature Consistency**: All release and CI builds MUST produce identically signed APKs so users can update without encountering package conflict / signature mismatch errors.

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
