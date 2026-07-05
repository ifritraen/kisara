---
description: Ignore all shallow constraints and search broad and deep across the codebase to resolve the root cause of an issue.
---
When the user executes `/deep [instruction]`:

1. **Disregard Shallow Checks**:
   - Do not rely on quick patches, temporary placeholders, or mock outputs. Focus entirely on uncovering the fundamental architectural or logical flaws.
   - Temporarily bypass any local "off-limits" constraints or rules that limit investigation breadth, in order to trace the execution chain to its origin.

2. **Broad Codebase Search**:
   - Search the entire workspace using broad search queries.
   - Map all files, directories, configuration files, and system dependencies relevant to the issue.
   - Read related domain files from the `InfoBank/` branch if they exist to understand global standards or patterns.

3. **Deep Structural Analysis**:
   - Trace vertical stack interactions: from user interface, down through state management, business logic, API communication, and database/schema layers.
   - Inspect physical configurations, package locks, environment configurations, and external scripts.
   - Map variable/data lifecycles across different components.

4. **Architectural-Grade Solution**:
   - Propose and implement a comprehensive, robust, and permanent architectural fix.
   - Refactor codebase modules where necessary to maintain high cohesion and low coupling.
   - Clean up dead code, update configurations, and ensure the solution is modular and future-proof.

5. **Deep Validation**:
   - Run the compiler, linter, and full test suite to guarantee architectural integrity.
   - Validate performance impacts, memory footprint, and network payload sizes if relevant.
   - Document any architectural changes, new schemas, or protocols in `.aaa/findings.md`.
