# Monsoon Script: agent guide (read this first, then STATUS.md)

Android keyboard (IME): English + Bangla (Avro phonetic, Provat, Bijoy), sleek Gboard-like UI, very fast and light,
and it must never break physical-keyboard input. Owner: i-am-sleepy. They use only a phone (no PC), the free Claude
tier, and test APKs built by GitHub Actions. Four Claude accounts work on it in parallel and in turn: claude-l, claude-e, claude-g, claude-s (D20).
**This repo is the only shared memory. Anything not pushed here is lost.**

## Who you are
The owner's message starts with "You are claude-X" (l, e, g or s). That is your tag for the whole session. If it is missing,
ask the owner one short question ("which name am I?") before touching anything. Never act as another Claude.

## Start of every session (about 3k tokens; do not explore beyond this)
1. Read this file, then `STATUS.md` (shared board), then `status/claude-X.md` (your notes). Handle your "Warnings" section first.
2. Peek at the other three `status/claude-*.md` files (short). Do not read their code or re-audit anything: ARCHITECTURE.md is the map.
3. CI: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If `result:` or `tests:` is not success, see "Broken build".
4. Then do the owner's request. Add it to STATUS.md as your own task first (new ids: your letter + counter, e.g. E1, E2, so Claudes
   never pick the same number). No request given: continue your own next step from your notes file.
5. Use targeted reads (Grep, line ranges). For CI failures read only the failing tail of the log.

## Multi-Claude rules (D20-D22)
- **Ownership.** Every task line in STATUS.md ends with its owner, e.g. `[~] T9 (claude-e)`. Edit only your own task lines. Never start,
  finish, rewrite or "rescue" another Claude's task, even if it looks abandoned (D22). Commit messages start with your tag: `[claude-e] T9.2: ...`.
- **Steps with progress.** In your notes file number the steps of each task and update them after every step: `T9.1 [x]`, `T9.2 [~]`,
  `T9.3 [ ]`. The marker + number is how others see what is yours and how far it got.
- **Broken build = the only thing you take over.** If the newest finished build on main failed, fix it before anything else, even when
  another Claude's commit caused it (see the `[claude-x]` prefix in `git log`). Steps: `git pull --rebase`; check no fix was already pushed
  after the failing commit; if the failing commit is under ~10 min old, wait for CI instead of assuming; write "Fixing red CI #N from claude-x's
  commit abc123" in your notes and a Warning in that Claude's file; push; then make the smallest fix. Do not finish their task.
- **Unfinished but not broken = leave it** and do the owner's request anyway (D21). If your change touches files or behaviour that another
  Claude owns or has half-done, append a Warning to the bottom of their file, e.g.
  `- [claude-s -> claude-e] 2026-11-17 abc123: renamed X to Y in Language.kt; re-pull and adapt before editing it`. Append only; the file's
  owner deletes the line once handled. Say what changed, which files, and what the other Claude must do or check.
- **Hand-offs.** Shared agreements are listed in STATUS.md "Hand-offs". Keep them; if you must break one, warn the Claudes it affects.
- **Git safety.** `git pull --rebase origin main` before every push, never force-push. A rebase conflict in a file you do not own: keep both
  sides' lines. You edit only your own `status/claude-X.md` (plus appended Warnings elsewhere) and your own task lines in STATUS.md.
- CI state lives only on the `ci-status` branch, not in STATUS.md.
- Password-manager engine (E1): KeePassDX is the git submodule `third_party/keepassdx` (never edit it). Plain keyboard work needs nothing from it. Its tests run in `.github/workflows/engine.yml` only when engine files change;
  result on the branch `ci-status-engine` (`git fetch origin ci-status-engine && git show FETCH_HEAD:last-build.txt`). See ARCHITECTURE.md "Password manager engine".

## While working
- Commit straight to `main` in small steps and push after every finished step. Never end a turn with unpushed work.
- Every push must keep CI green. If you break the build, fix it first or revert. You cannot compile locally (Maven is blocked in the
  sandbox): after pushing code, wait ~4-8 min, then read the ci-status file again. The release APK at the `latest` link contains everyone's
  merged work, so name your own commit in your test checklist.
- After each finished step, update your notes file and your task lines (state, not history; notes file under ~30 lines).
- "checkpoint" from the user (or usage nearly out): commit and push WIP now, then update your notes file with the exact next action,
  half-done files and known bugs, even if the work is unfinished. Another Claude reads that file to know what you left.
- Tasks live ONLY in STATUS.md (steps in your notes file). No issue tracker, no TODO files. A code TODO is fine only if it also has a task.
- Don't limit yourself: refactor, add files or change structure whenever the work needs it. These docs are notes, not cages.
  If you change structure, update ARCHITECTURE.md in the same commit.

## Asking the user
- Ask BEFORE any UI/design decision, and before anything changing the stack, license, permissions or user-visible behavior.
  Use the question tool if you have one (2-4 options, mark a recommended default, at most 4 questions per ask); otherwise
  plain text. Record every answer in DECISIONS.md. Put unanswered questions in STATUS.md under "Needs user", tagged with your name.
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
