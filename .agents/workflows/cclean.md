---
description: Clear .aaa/cache/ temporary files only. Never touches findings.md, context_snapshot.md, errors_cache.md, workspacerules.md, or any other permanent .aaa/ files.
---
When the user executes `/cclean`:
1. Target ONLY the `.aaa/cache/` subdirectory in the active workspace root.
2. List what will be deleted first:
   ```powershell
   Get-ChildItem ".aaa\cache\" -Recurse | Select-Object Name, Length
   ```
3. Ask for confirmation if the cache contains more than 5 files or >1MB total, otherwise proceed automatically.
4. Delete all contents inside `.aaa/cache/` but preserve the folder itself:
   ```powershell
   Get-ChildItem ".aaa\cache\" -Recurse | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
   ```
5. NEVER delete or modify:
   - `.aaa/findings.md`
   - `.aaa/context_snapshot.md`
   - `.aaa/errors_cache.md`
   - `.aaa/workspacerules.md`
   - Any other `.aaa/` files outside `cache/`
6. Report: files deleted, space freed.

## What belongs in .aaa/cache/
- Scraped data dumps (`scraped_*.json`, `scraped_*.html`)
- Temporary API responses
- Downloaded/extracted files used for analysis (e.g. decompiled APK temp outputs)
- Any file that can be re-fetched or re-generated
