---
description: Display current project state — active task, pending items, key decisions, known issues from context_snapshot.md
---
When the user executes `/status`:
1. Check for `.aaa/context_snapshot.md` in the active workspace. If missing, state "No snapshot found. Run `/snapshot` to create one."
2. Read `.aaa/context_snapshot.md` and display it in a clean formatted summary:
   - **Active Task**: What is being worked on
   - **Pending**: Numbered list of next steps
   - **Decisions**: Key architectural/design decisions made
   - **Known Issues**: Any blockers or bugs
   - **Delta Paths**: Files created since last megadir update
3. Also show: active `workspacerules.md` custom commands if present.
4. Do NOT modify any files. Read-only operation.
