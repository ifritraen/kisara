---
description: Pre-commit security audit — scan staged files for secrets, verify .gitignore safety gates before any git push.
---
When the user executes `/audit`:
1. Run `git status --porcelain` to list staged and modified files.
2. **Secret scan** — For each staged/modified file, grep for patterns that indicate credentials:
   ```powershell
   git diff --cached | Select-String -Pattern "token|key|secret|password|Bearer|ghp_|gho_|sk-|AIza|eyJ" -CaseSensitive:$false
   ```
   If any match found → **ABORT** and report the file and matched line. Do NOT proceed with commit.
3. **GitIgnore gate** — Check that `.gitignore` contains ALL of:
   - `.aaa/`
   - `.env`
   - `secrets.*`
   - `sensitive.*`
   If any are missing → add them to `.gitignore` immediately, then continue.
4. **Findings.md check** — Confirm `.aaa/` is not staged for commit:
   ```powershell
   git diff --cached --name-only | Select-String "\.aaa"
   ```
   If `.aaa/` files are staged → unstage them and warn the user.
5. Report result:
   - ✅ **CLEAN** — No secrets detected. .gitignore gates active. Safe to commit.
   - ❌ **BLOCKED** — List of issues found. Do not commit until resolved.
