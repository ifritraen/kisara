---
description: Absolute, definitive debugging workflow to diagnose and permanently resolve complex bugs or crashes.
---
When the user executes `/debug [error-or-symptom]`:

1. **Check Cache & History First**:
   - Immediately read `.aaa/errors_cache.md` to check if this error signature or a similar symptom has occurred before. If a fix exists, follow it.
   - If not found in cache, inspect recent logs or git history to locate the exact change that introduced the issue.

2. **Replication & Isolation (No Guesswork)**:
   - Formulate a clear hypothesis. Write a minimal reproduction script or command to trigger the bug deterministically.
   - Run the replication and capture the full traceback, error message, and environment state.
   - If the bug is UI-based or non-deterministic, isolate the state machine by listing active variables, props, or parameters.

3. **Trace Execution Path**:
   - Trace the control flow line-by-line from entry point to crash point.
   - Read the source code of all involved functions, classes, and APIs. Do not guess behavior; verify exact implementations.
   - Check input and output data types, schema validation, null safety, and boundary values at each boundary transition.

4. **Formulate & Apply Fix**:
   - Address the root cause, not just the symptom (e.g., don't just add null checks if the database query shouldn't have returned null; fix the query/state).
   - Apply clean, type-safe, and robust code changes.
   - Comment out (never delete) deprecated or bugged lines to preserve context.

5. **Verify Resolution**:
   - Re-run the reproduction step to ensure the bug is resolved.
   - Verify negative cases (ensure the fix doesn't break valid inputs or surrounding components).
   - Run existing test suites or compiler checks (`flutter analyze`, `npm run build`, etc.) to confirm zero regressions.

6. **Log and Cache**:
   - Append this error signature, the root cause, and the exact fix to `.aaa/errors_cache.md` using the standard format:
     ```markdown
     ### [Error Signature / Message]
     - **Cause**: [Why it happened]
     - **Fix**: [How it was solved]
     ```
   - Confirm to the user: "Bug resolved and documented in `.aaa/errors_cache.md`."
