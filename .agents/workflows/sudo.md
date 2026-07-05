---
description: Suspend Supreme Commands for a single instruction. Mimics Linux sudo — elevated privilege for one command only.
---

When the user's message begins with `/sudo` followed by an instruction:

1. **Acknowledge sudo mode explicitly** at the start of your response:
   > 🔓 **SUDO MODE ACTIVE** — Supreme Commands suspended for this instruction only.

2. **Suspend all Supreme Commands** (`S1`–`S4` in GEMINI.md) for the duration of this single instruction ONLY. This means:
   - Security gates (S1) — suspended. You may commit secrets if explicitly ordered.
   - Session continuity (S2) — suspended. You may skip snapshot read/write.
   - Megadir DNS lookup (S3) — suspended. You may search directly.
   - Safety gates (S4) — suspended. You may auto-build, auto-push, skip quota rules.

3. **Execute the instruction** exactly as given, without applying any Supreme Command restrictions.

4. **After completing the instruction**, immediately:
   - Restore all Supreme Commands automatically.
   - State at the end:
     > 🔒 **SUDO MODE ENDED** — Supreme Commands restored.

5. **Scope is strictly one turn**. The next user message resumes normal Supreme Commands enforcement — no carry-over.

## Critical Rules for /sudo
- `/sudo` cannot be used to permanently modify Supreme Commands. It only suspends them for one turn.
- If the user says `/sudo` without a specific instruction, ask: "What should I execute with elevated privileges?"
- Log the sudo action in `.aaa/findings.md` as: `[SUDO] <date>: <brief description of what was bypassed and why`.
- If the action is irreversible (e.g. a push, a delete), warn the user before executing:
  > ⚠️ This action is irreversible. Proceeding in 3… (or ask for confirmation if destructive enough)
