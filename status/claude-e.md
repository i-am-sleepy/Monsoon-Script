# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 1. Focus: E1 password-manager design talk is DONE, waiting for the owner's sign-off; then Phase 0 or T9 (owner decides), T11.

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
E1 Password manager with the owner [~] (summary + facts: docs/PASSWORD_MANAGER.md, brief: docs/PASSWORD_MANAGER_BRIEF.md). NO code, NO project changes until sign-off.
  E1.1 [x] Read brief and repo docs; CI #8 green; upstream facts verified (KeePassDX GPL-3.0, its modules and folder layout, release rhythm, KeePassJava2, Android inline autofill, BiometricPrompt needs an Activity).
  E1.2 [x] Stage 1 integration approaches: GPL-3.0 (D25), KeePassDX modules behind our own interface, feasibility check first (D26).
  E1.3 [x] Stage 2 how it meets the keyboard: key icon in the strip (D27); picker first, autofill + save prompt later (D28).
  E1.4 [x] Stage 3 UI (D29-D34).  E1.5 [x] Stage 4 database lifecycle (D35-D37).  E1.6 [x] Stage 5 security (D38-D40, D43, D44).
  E1.7 [x] Stage 6 reuse vs own code (D45, D46).  E1.8 [x] Stage 7 integration and update strategy (D47-D49).  E1.9 [x] Stage 8 journeys, merge behavior, build order, one vault (D50-D52).
  E1.10 [~] Sign-off: one-page summary written (docs/PASSWORD_MANAGER.md, "Agreed design") and shown to the owner; waiting for approval (STATUS Needs user #5).
  E1.11 [ ] After sign-off: inspect our project and the upstream source (read code, not just folder names), then Phase 0 feasibility check on a SIDE BRANCH (D49): submodule pinned to the latest
        release that is at least 7 days old and not unstable, AGP/NDK as needed, build in CI, open test .kdbx files, measure APK size and RAM. Report go/no-go. Do NOT publish a release or touch `ci-status` from the side branch.
  E1.12 [ ] Then LICENSE file (D25, check every Avro header), then Phase 1 (D51).
  After every owner answer: add a D-line to DECISIONS.md (next free id: D53; re-read the file first, claude-l also adds lines) and update docs/PASSWORD_MANAGER.md.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority; the owner may say T9 goes before Phase 0)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
Read the owner's sign-off answer. If approved: E1.11 (read the code first, then Phase 0 on a side branch). If changes: update D-lines (new lines overturn old ones). Check ci-status first (last read: CI #8 green).
Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs"). Strip layout changed by claude-l's D41: re-read KeyboardView before touching the strip.

## Half-done files / known bugs
None. (E1 so far only added docs and decisions; no source files touched.)

## Test checklist for my latest build (owner)
(none: no code change yet)
