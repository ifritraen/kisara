---
description: Manually force-write the current session state to .aaa/context_snapshot.md
---
When the user executes `/snapshot`:
1. Read the current conversation context — active task, decisions made, files created/modified, pending items, known issues.
2. Write/overwrite `.aaa/context_snapshot.md` in the active workspace root with this format:
   ```markdown
   # Context Snapshot — [ProjectName] — [Date]
   ## Active Task
   [What is currently being worked on]
   ## Key Decisions
   - [Decision 1]
   ## Pending / Next Steps
   - [Item 1]
   ## Critical File Paths (delta since megadir)
   - [Any new paths not yet in megadir]
   ## Known Issues
   - [Any unresolved bugs/blockers]
   ```
3. Confirm: "Snapshot written to `.aaa/context_snapshot.md`."
