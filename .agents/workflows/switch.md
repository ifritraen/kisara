---
description: Switch active project context — load that project's context_snapshot.md and workspacerules.md
---
When the user executes `/switch [project-name-or-path]`:
1. Look up the project in `megadir.md` at `C:\Users\akhla\.gemini\.agents\workflows\InfoBank\megadir.md` using TOC-paging. Resolve the project root path.
2. Check for `.aaa/context_snapshot.md` in that project root. If it exists, read it and display the session state summary.
3. Check for `.aaa/workspacerules.md` in that project root. If it exists, read it and load its rules as the active workspace override.
4. Confirm the switch:
   > ✅ Switched to **[ProjectName]** at `[path]`
   > Active rules: `[workspacerules.md loaded / none]`
   > Last snapshot: `[date from context_snapshot / none]`
5. From this point, treat that project as the active workspace for all subsequent operations in this session.
6. If the project name is ambiguous or not found in megadir, list the closest matches and ask the user to confirm.
