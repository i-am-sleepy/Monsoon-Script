# Monsoon Script: agent guide (read this first, then STATUS.md)

Android keyboard (IME): English + Bangla (Avro phonetic, Provat, Bijoy), sleek Gboard-like UI, very fast and light,
and it must never break physical-keyboard input. Owner: i-am-sleepy. They use only a phone (no PC), the free Claude
tier, and test APKs built by GitHub Actions. Many Claude sessions/accounts will continue this work in turn.
**This repo is the only shared memory. Anything not pushed here is lost.**

## Start of every session (about 2k tokens; do not explore beyond this)
1. Read this file, then `STATUS.md`. Read `DECISIONS.md`, `ARCHITECTURE.md`, `docs/REQUIREMENTS.md` only if the task needs them.
2. Do the "Next step" in STATUS.md unless the user says otherwise. Do not re-audit the code: ARCHITECTURE.md is the map.
3. If the user asks for something new, add one line to the STATUS.md task list first, then do it.
4. Use targeted reads (Grep, line ranges). For CI failures read only the failing tail of the log.

## While working
- Commit straight to `main` in small steps and push after every finished step. Never end a turn with unpushed work.
- Every push must keep CI green. If you break the build, fix it first or revert. You cannot compile locally (Maven is blocked
  in the sandbox): after pushing code, wait ~4-8 min, then read `git fetch origin ci-status && git show origin/ci-status:last-build.txt`.
- After each finished step, rewrite the relevant lines of STATUS.md (state, not history; keep it under ~1 page).
- "checkpoint" from the user (or usage nearly out): commit and push WIP now, then update STATUS.md with the exact next
  action, half-done files and known bugs, even if the work is unfinished.
- Tasks live ONLY in STATUS.md. No issue tracker, no TODO files. A code TODO is fine only if it also has a STATUS task.
- Don't limit yourself: refactor, add files or change structure whenever the work needs it. These docs are notes, not cages.
  If you change structure, update ARCHITECTURE.md in the same commit.

## Asking the user
- Ask BEFORE any UI/design decision, and before anything changing the stack, license, permissions or user-visible behavior.
  Use the question tool if you have one (2-4 options, mark a recommended default, at most 4 questions per ask); otherwise
  plain text. Record every answer in DECISIONS.md. Put unanswered questions in STATUS.md under "Needs user".
- Do not ask about routine implementation details: pick the simple, fast option and move on.
- The user only tests, reports, supplies files and says what to fix. Never ask them to run commands. With each build,
  give a short numbered test checklist and link the APK.

## Hard rules
- NO code or assets from Ridmik. It is a feature reference only (see docs/REQUIREMENTS.md). Never decompile or copy from it.
- Ported Avro files (derived from `third_party/avro-reference/`) are MPL-2.0: keep the license header, keep them in
  their own files under `.../lang/avro/`, and list them in `third_party/avro-reference/NOTICE.md`.
- Performance first: no Compose in the IME process, no heavy libraries, no per-keypress allocations on the touch/draw path.
- Physical keyboards: pass hardware key events through untouched unless a Bangla mode must translate a character.
  Never swallow Enter, Shift, Ctrl/Alt/Meta combos, arrows or Tab. See ARCHITECTURE.md "Hardware keyboard rules".
- Never commit keystores, tokens or personal data.
