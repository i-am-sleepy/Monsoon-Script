# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-19, session 2. Focus: E2 EMOJI SYSTEM (owner order 2026-11-19: forget every other pending task for now). E1, T9, T11 are paused, not cancelled.

## Warnings from other Claudes (read first; delete a line when handled)
- [claude-l -> claude-e] 2026-11-17 D41: the strip gets a small arrow button at the left (suggestions <-> icons, Gboard style) and next-word suggestions after a space, in every language. I will change view/KeyboardView.kt (strip layout and touch routing) and keep Layouts.stripLeft/stripRight as they are; your password key icon (D27) and the language popup keep working. Re-pull before editing KeyboardView's strip code. (restored by claude-e on 2026-11-18: my earlier full rewrites of this file dropped it by mistake; not handled yet, it matters when the key icon is built)

## Tasks and steps
E2 Emoji system [~] owner brief: offline, Unicode data in assets, Noto Color Emoji, picker (categories, search, recents), skin tones + ZWJ, commitText only (never images), recents persisted, Gboard-like minimal look. Ask the owner before UI decisions.
  E2.1 [x] Research. Sandbox facts: unicode.org is blocked, but Unicode's GitHub mirror works: emoji-test.txt 17.0 at raw.githubusercontent.com/unicode-org/unicodetools/main/unicodetools/data/emoji/17.0/emoji-test.txt
           (3,944 fully-qualified emoji: 1,914 base + 2,030 skin-tone variants, 1,614 ZWJ, 9 groups); CLDR names/keywords: raw.githubusercontent.com/unicode-org/cldr/main/common/annotations/{en,bn}.xml (+ annotationsDerived/en.xml).
           Font: raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/fonts/NotoColorEmoji.ttf (v2.057, 10.7 MB, CBDT, OFL 1.1, has U+1FAEA = Unicode 17; github.com/.../raw/ gives 403, use raw.githubusercontent.com).
  E2.2 [x] Owner answered the design questions (D56): Gboard-style panel, per-emoji remembered skin tones (two-step for couples), English + Bangla search, hide emoji the phone cannot draw. Next free D id: D57.
  E2.3 [ ] `tools/gen_emoji_data.py` -> `assets/emoji/emoji.bin` (names, group, keywords en [+bn], variants as whole strings); font to `assets/emoji/NotoColorEmoji.ttf` (noCompress "ttf"); licenses text in assets/licenses.
  E2.4 [ ] Pure-Kotlin `emoji/` data reader + search + recents model with JUnit tests (run locally with kc.sh trick); recents saved in the prefs file.
  E2.5 [ ] Canvas panel view (grid, fling scroll, category bar, search, skin-tone popup) + wiring in KeyboardView/MonsoonIme (strip emoji key, `onEmoji` -> finishComposing + commitText); ARCHITECTURE.md.
  E2.6 [ ] CI green, owner test checklist.
E1 Password manager [~] PAUSED (summary + facts: docs/PASSWORD_MANAGER.md, Phase 0 log: docs/PASSWORD_MANAGER_PHASE0.md, brief: docs/PASSWORD_MANAGER_BRIEF.md). Design SIGNED OFF (D53).
  E1.1-E1.9 [x] Design talk, stages 1-8, decisions D25-D52.
  E1.10 [x] Sign-off: the owner approved the design and said to start the feasibility check now (D53, 2026-11-18).
  E1.11 [~] Phase 0 feasibility check on the SIDE BRANCH `e1-feasibility` (D49). Never publish a release or touch `ci-status` from it.
    E1.11a [x] Read our build files and CI workflow; read KeePassDX 4.5.8 source (API, native Argon2, Limits, build versions). Findings: docs/PASSWORD_MANAGER_PHASE0.md.
    E1.11b [x] Spike pushed to `e1-feasibility` (commit 7f7bbbe): upstream catalog + modules wired in, `:vault` module with spike code and emulator test, CI job file in `tools/`.
    E1.11c [?] BLOCKED on the owner: move `tools/e1-feasibility.workflow.yml` to `.github/workflows/e1-feasibility.yml` ON THE BRANCH (my tool gets 403 for workflow files). STATUS Needs user #5.
    E1.11d [ ] After that: read the branches `e1-status-build` and `e1-status-emulator` (file status.txt) with the GitHub tool; fix what is red (expect: R8 keep rules, missing SDK/NDK pieces, catalog or
            plugin version conflicts); iterate on the side branch only. Then the extra checks listed in the Phase 0 log (real KeePassXC files, attachments/one-time codes, merge, shrunk-build smoke test, RAM).
    E1.11e [ ] Write the go/no-go report for the owner (APK size growth, RAM, what failed, fallback KeePassJava2 if no-go).
  E1.12 [ ] If go: merge plan (workflow edits need the owner: Gradle 8.14.2, paths-ignore for the submodule), real git submodule instead of the CI clone, LICENSE file (D25, check every Avro header), Phase 1 (D51).
  After every owner answer: add a D-line to DECISIONS.md (next free id: D54; re-read the file first, claude-l also adds lines) and update the docs.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority; the owner may say T9 goes before Phase 0 continues)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
E2: get the owner's answers to the design questions (E2.2), record them in DECISIONS.md, then E2.3. Re-pull first: claude-g edits MonsoonIme/KeyboardView/strings (G1) and LicensesActivity (its FILES list needs a new `licenses/emoji.txt`; append a Warning to status/claude-g.md when I touch it).
(E1, paused) If the owner has moved the workflow file: read `e1-status-build` / `e1-status-emulator` (get_file_contents, ref refs/heads/<name>, path status.txt) and continue E1.11d. If not: ask again, or
work on T9 meanwhile (owner's call). Check ci-status first (last read: CI #8 green; main has moved on, claude-l reports green up to #13).
Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs"). Strip layout changed by claude-l's D41: re-read KeyboardView before touching the strip.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes append warnings to it.

## Half-done files / known bugs
Main: none (E1 only added docs and decisions). Side branch `e1-feasibility`: spike, not built yet, never to be merged as it is (workflow file in tools/, CI clone instead of a submodule).

## Test checklist for my latest build (owner)
(none: no change on main affects the app)
